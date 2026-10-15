package com.mentalgym.service

import com.mentalgym.config.MentalGymProperties
import com.mentalgym.domain.Answer
import com.mentalgym.domain.Attempt
import com.mentalgym.domain.CriteriaResults
import com.mentalgym.domain.CriterionResult
import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.Question
import com.mentalgym.domain.TestStatus
import com.mentalgym.persistence.AttemptRepository
import com.mentalgym.persistence.ConceptRepository
import com.mentalgym.persistence.DocRepository
import com.mentalgym.persistence.TestRepository
import com.mentalgym.rules.ReviewLadder
import com.mentalgym.rules.Scoring
import com.mentalgym.rules.Validation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AnswerInput(
    val questionId: UUID,
    val text: String,
)

data class CriterionGradeInput(
    val id: String,
    val credit: Double,
    val evidence: String? = null,
)

@Service
class AttemptService(
    private val conceptService: ConceptService,
    private val testService: TestService,
    private val testRepository: TestRepository,
    private val attemptRepository: AttemptRepository,
    private val conceptRepository: ConceptRepository,
    private val docRepository: DocRepository,
    private val docService: DocService,
    private val reviewLadder: ReviewLadder,
    private val properties: MentalGymProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun submitAttempt(testId: UUID, answers: List<AnswerInput>): AttemptCreatedView {
        val test = testService.requireTest(testId, null)
        if (test.status != TestStatus.READY) {
            throw MentalGymException("Test $testId is not ready, it still has unverified questions")
        }
        val questions = testRepository.findQuestions(testId)
        val questionIds = questions.map { it.id }.toSet()
        answers.map { it.questionId }.filterNot { it in questionIds }.takeIf { it.isNotEmpty() }?.let {
            throw MentalGymException("Answers for questions that are not in test $testId: ${it.joinToString()}")
        }
        answers.groupBy { it.questionId }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
            throw MentalGymException("More than one answer for questions ${it.joinToString()}")
        }
        val missing = questionIds - answers.map { it.questionId }.toSet()
        if (missing.isNotEmpty()) {
            throw MentalGymException("Missing answers for ${missing.size} of ${questions.size} questions")
        }

        duplicateOf(testId, answers)?.let { existing ->
            log.info("Reusing ungraded attempt {} for test {}", existing.id, testId)
            return created(existing, questions)
        }

        val attempt = attemptRepository.insert(testId, properties.passThreshold)
        answers.forEach { attemptRepository.insertAnswer(attempt.id, it.questionId, it.text) }
        log.info("Stored attempt {} with {} answers for test {}", attempt.id, answers.size, testId)
        return created(attempt, questions)
    }

    fun getGradingMaterial(attemptId: UUID, questionId: UUID): GradingMaterial {
        val attempt = requireAttempt(attemptId)
        val question = testRepository.findQuestionById(questionId)
            ?: throw MentalGymException("No question with id $questionId")
        if (question.testId != attempt.testId) {
            throw MentalGymException("Question $questionId is not part of attempt $attemptId")
        }
        val answer = attemptRepository.findAnswer(attemptId, questionId)
            ?: throw MentalGymException("Attempt $attemptId has no answer for question $questionId")
        val test = testService.requireTest(attempt.testId, null)
        return GradingMaterial(
            attemptId = attemptId,
            questionId = questionId,
            position = question.position,
            type = question.type.wire,
            prompt = question.prompt,
            answerKey = question.answerKey,
            rubric = question.rubric,
            wrongAnswers = question.wrongAnswers,
            docs = docService.summaries(test.conceptId, inlineText = true),
            learnerAnswer = answer.answerText,
            alreadyGraded = answer.score != null,
        )
    }

    @Transactional
    fun saveGrade(
        attemptId: UUID,
        questionId: UUID,
        graderVersion: String,
        criteria: List<CriterionGradeInput>,
        misconceptionFlagged: Boolean,
        feedback: String,
    ): GradeSavedView {
        val attempt = requireAttempt(attemptId)
        val question = testRepository.findQuestionById(questionId)
            ?: throw MentalGymException("No question with id $questionId")
        val answer = attemptRepository.findAnswer(attemptId, questionId)
            ?: throw MentalGymException("Attempt $attemptId has no answer for question $questionId")
        if (feedback.isBlank()) throw MentalGymException("Feedback must say what was missing")

        val rubricIds = question.rubric.criteria.map { it.id }
        val gradedIds = criteria.map { it.id }
        gradedIds.filterNot { it in rubricIds }.takeIf { it.isNotEmpty() }?.let {
            throw MentalGymException("Criteria ${it.joinToString()} are not in the rubric of question ${question.position}")
        }
        (rubricIds - gradedIds.toSet()).takeIf { it.isNotEmpty() }?.let {
            throw MentalGymException("Missing credit for criteria ${it.joinToString()}")
        }
        criteria.forEach {
            Validation.validateCredit(it.id, it.credit)
            if (it.credit > 0.0 && it.evidence.isNullOrBlank()) {
                throw MentalGymException("Criterion '${it.id}' got credit without a quote from the answer")
            }
        }

        val results = CriteriaResults(
            criteria = criteria.map { CriterionResult(it.id, it.credit, it.evidence?.takeIf { e -> e.isNotBlank() }) },
            misconceptionFlagged = misconceptionFlagged,
        )
        val score = Scoring.questionScore(question.rubric, criteria.associate { it.id to it.credit })
        attemptRepository.saveGrade(answer.id, score, results, feedback.trim())

        val graded = attemptRepository.findAnswers(attemptId)
        val complete = graded.all { it.score != null }
        val result = if (complete) finalize(attempt, graded, graderVersion) else null

        return GradeSavedView(
            attemptId = attemptId,
            questionId = questionId,
            questionScore = score,
            answersGraded = graded.count { it.score != null },
            answersTotal = graded.size,
            attemptStatus = if (complete) "graded" else "grading",
            result = result,
        )
    }

    fun getAttemptResult(attemptId: UUID): AttemptResultView {
        val attempt = requireAttempt(attemptId)
        val test = testService.requireTest(attempt.testId, null)
        val concept = conceptService.require(test.conceptId)
        val questions = testRepository.findQuestions(test.id).associateBy { it.id }
        val answers = attemptRepository.findAnswers(attemptId)
        val graded = attempt.gradedAt != null

        val answerViews = answers.map { answer ->
            val question = questions.getValue(answer.questionId)
            AnswerResultView(
                answerId = answer.id,
                questionId = answer.questionId,
                position = question.position,
                type = question.type.wire,
                prompt = question.prompt,
                learnerAnswer = answer.answerText,
                score = answer.score,
                feedback = answer.feedback,
                missedCriteria = missedCriteria(question, answer),
                criteriaResults = answer.criteriaResults.takeIf { graded },
                answerKey = question.answerKey.takeIf { graded },
                misconceptionFlagged = answer.criteriaResults?.misconceptionFlagged == true,
                flagged = answer.flagged,
                flagNote = answer.flagNote,
            )
        }

        val anythingMissed = answerViews.any { it.missedCriteria.isNotEmpty() }
        return AttemptResultView(
            attemptId = attempt.id,
            testId = test.id,
            conceptId = concept.id,
            conceptName = concept.name,
            status = if (graded) "graded" else "grading",
            score = attempt.score,
            passed = attempt.passed,
            threshold = attempt.threshold,
            submittedAt = attempt.submittedAt,
            gradedAt = attempt.gradedAt,
            conceptStatus = concept.status.wire,
            nextReviewAt = concept.nextReviewAt,
            answers = answerViews,
            docsToReread = if (anythingMissed) {
                docRepository.findByConcept(concept.id).map { it.title ?: it.url }
            } else {
                emptyList()
            },
        )
    }

    @Transactional
    fun flagAnswer(answerId: UUID, note: String): FlagSavedView {
        if (note.isBlank()) throw MentalGymException("A flag needs a one-line reason")
        val updated = attemptRepository.flagAnswer(answerId, note.trim())
        if (updated == 0) throw MentalGymException("No answer with id $answerId")
        return FlagSavedView(answerId = answerId, flagged = true, note = note.trim())
    }

    fun requireAttempt(attemptId: UUID): Attempt =
        attemptRepository.findById(attemptId) ?: throw MentalGymException("No attempt with id $attemptId")

    private fun finalize(attempt: Attempt, answers: List<Answer>, graderVersion: String): AttemptResultView {
        val firstFinalization = attempt.gradedAt == null
        val score = Scoring.testScore(answers.map { requireNotNull(it.score) })
        val passed = score >= attempt.threshold
        attemptRepository.finalize(attempt.id, score, passed, graderVersion, Instant.now())

        if (firstFinalization) {
            applyConceptState(attempt, passed)
        } else {
            log.warn("Attempt {} was re-graded, concept state left untouched", attempt.id)
        }
        return getAttemptResult(attempt.id)
    }

    private fun applyConceptState(attempt: Attempt, passed: Boolean) {
        val conceptId = attemptRepository.conceptIdOf(attempt.id)
            ?: throw MentalGymException("Attempt ${attempt.id} has no concept")
        val concept = conceptService.require(conceptId)
        if (passed) {
            val interval = reviewLadder.nextIntervalDays(concept.reviewIntervalDays)
            conceptRepository.applyPass(
                id = conceptId,
                intervalDays = interval,
                nextReviewAt = Instant.now().plus(interval.toLong(), ChronoUnit.DAYS),
            )
            log.info("Concept {} mastered, next review in {} days", conceptId, interval)
        } else {
            conceptRepository.applyFail(conceptId)
            log.info("Concept {} failed its test, review ladder reset", conceptId)
        }
    }

    private fun missedCriteria(question: Question, answer: Answer): List<MissedCriterion> {
        val results = answer.criteriaResults ?: return emptyList()
        val creditById = results.criteria.associate { it.id to it.credit }
        return question.rubric.criteria
            .filter { (creditById[it.id] ?: 0.0) < 1.0 }
            .map { MissedCriterion(id = it.id, text = it.text, kind = it.kind, credit = creditById[it.id] ?: 0.0) }
    }

    private fun duplicateOf(testId: UUID, answers: List<AnswerInput>): Attempt? =
        attemptRepository.findByTest(testId)
            .filter { it.gradedAt == null }
            .firstOrNull { attempt ->
                val stored = attemptRepository.findAnswers(attempt.id)
                    .associate { it.questionId to it.answerText }
                stored == answers.associate { it.questionId to it.text }
            }

    private fun created(attempt: Attempt, questions: List<Question>) = AttemptCreatedView(
        attemptId = attempt.id,
        testId = attempt.testId,
        conceptId = requireNotNull(attemptRepository.conceptIdOf(attempt.id)),
        answersStored = questions.size,
        status = if (attempt.gradedAt == null) "grading" else "graded",
        questionIdsToGrade = questions.map { it.id },
    )
}
