package com.mentalgym.service

import com.mentalgym.domain.GeneratedTest
import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.Question
import com.mentalgym.domain.QuestionType
import com.mentalgym.domain.RejectionCategory
import com.mentalgym.domain.Rubric
import com.mentalgym.domain.TestStatus
import com.mentalgym.domain.Verdict
import com.mentalgym.persistence.AttemptRepository
import com.mentalgym.persistence.TestRepository
import com.mentalgym.rules.Validation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class QuestionInput(
    val position: Int,
    val type: String,
    val prompt: String,
    val answerKey: String,
    val rubric: Rubric,
    val wrongAnswers: List<String> = emptyList(),
)

data class VerdictInput(
    val position: Int,
    val verdict: String,
    val reason: String? = null,
    val category: String? = null,
)

@Service
class TestService(
    private val conceptService: ConceptService,
    private val docService: DocService,
    private val testRepository: TestRepository,
    private val attemptRepository: AttemptRepository,
) {

    fun getGenerationMaterial(conceptId: UUID, testId: UUID?): GenerationMaterial {
        val concept = conceptService.require(conceptId)
        val draft = testId?.let { requireTest(it, conceptId) }
        val draftQuestions = draft?.let { testRepository.findQuestions(it.id) } ?: emptyList()
        val rejected = draftQuestions.filter { it.verdict == Verdict.REJECTED }

        return GenerationMaterial(
            conceptId = concept.id,
            conceptName = concept.name,
            topic = concept.topicName,
            docs = docService.summaries(conceptId, inlineText = true),
            previouslyUsedPrompts = testRepository.findPromptsForConcept(conceptId),
            weakCriteria = weakCriteria(conceptId),
            testIdToRevise = draft?.id,
            rejectedQuestions = rejected.map { RejectedQuestion(it.position, it.prompt, it.verdictReason) },
            questionsToKeep = draftQuestions.filter { it.verdict == Verdict.OK }.map { it.position },
            rules = GenerationRules(
                minQuestions = Validation.MIN_QUESTIONS,
                maxQuestions = Validation.MAX_QUESTIONS,
                minCriteriaPerQuestion = Validation.MIN_CRITERIA,
                maxCriteriaPerQuestion = Validation.MAX_CRITERIA,
                allowedTypes = QuestionType.entries.map { it.wire },
            ),
        )
    }

    @Transactional
    fun saveTest(
        conceptId: UUID,
        generatorVersion: String,
        questions: List<QuestionInput>,
        testId: UUID?,
    ): SavedTestView {
        conceptService.require(conceptId)
        if (questions.isEmpty()) throw MentalGymException("Pass at least one question")
        questions.groupBy { it.position }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
            throw MentalGymException("Duplicate question positions ${it.joinToString()}")
        }
        questions.forEach { question ->
            if (question.position < 1) throw MentalGymException("Question positions start at 1")
            if (question.prompt.isBlank()) throw MentalGymException("Question ${question.position} has no prompt")
            if (question.answerKey.isBlank()) throw MentalGymException("Question ${question.position} has no answer key")
            QuestionType.of(question.type)
            Validation.validateRubric(question.position, question.rubric)
        }

        val test = if (testId != null) {
            val existing = requireTest(testId, conceptId)
            if (existing.status == TestStatus.READY) {
                throw MentalGymException("Test $testId is already ready and cannot be rewritten")
            }
            testRepository.deleteQuestions(existing.id, questions.map { it.position })
            testRepository.updateGeneratorVersion(existing.id, generatorVersion)
            existing
        } else {
            testRepository.insert(conceptId, generatorVersion)
        }

        questions.forEach {
            testRepository.insertQuestion(
                testId = test.id,
                position = it.position,
                type = QuestionType.of(it.type),
                prompt = it.prompt.trim(),
                answerKey = it.answerKey.trim(),
                rubric = it.rubric,
                wrongAnswers = it.wrongAnswers,
            )
        }

        val stored = testRepository.findQuestions(test.id)
        Validation.validateQuestionCount(stored.size)

        return SavedTestView(
            testId = test.id,
            conceptId = conceptId,
            status = testRepository.findById(test.id)!!.status.wire,
            questions = stored.map(::promptView),
        )
    }

    fun getTestForVerification(testId: UUID, position: Int?): VerificationMaterial {
        val test = requireTest(testId, null)
        val concept = conceptService.require(test.conceptId)
        val questions = testRepository.findQuestions(test.id)
        val selected = if (position == null) {
            questions
        } else {
            listOf(
                questions.firstOrNull { it.position == position }
                    ?: throw MentalGymException("No question at position $position in test $testId")
            )
        }
        return VerificationMaterial(
            testId = test.id,
            conceptId = concept.id,
            conceptName = concept.name,
            docs = docService.summaries(concept.id, inlineText = position != null),
            positions = questions.map { it.position },
            questions = selected.map {
                VerificationQuestion(
                    position = it.position,
                    type = it.type.wire,
                    prompt = it.prompt,
                    answerKey = it.answerKey,
                    rubric = it.rubric,
                    wrongAnswers = it.wrongAnswers,
                    verdict = it.verdict.wire,
                )
            },
        )
    }

    @Transactional
    fun saveVerdicts(testId: UUID, verdicts: List<VerdictInput>): VerdictsSavedView {
        val test = requireTest(testId, null)
        if (test.status == TestStatus.READY) throw MentalGymException("Test $testId is already verified")
        val questions = testRepository.findQuestions(testId).associateBy { it.position }
        if (verdicts.isEmpty()) throw MentalGymException("Pass at least one verdict")

        val results = verdicts.map { input ->
            val question = questions[input.position]
                ?: throw MentalGymException("No question at position ${input.position} in test $testId")
            val verdict = Verdict.of(input.verdict)
            if (verdict == Verdict.PENDING) throw MentalGymException("A verdict must be ok or rejected")
            val category = if (verdict == Verdict.REJECTED) {
                if (input.reason.isNullOrBlank()) {
                    throw MentalGymException("Question ${input.position} was rejected without a reason")
                }
                (input.category?.let { RejectionCategory.of(it) } ?: categorize(input.reason)).wire
            } else {
                null
            }
            testRepository.updateVerdict(testId, question.position, verdict, input.reason?.trim())
            VerdictView(position = question.position, verdict = verdict.wire, category = category)
        }

        val afterUpdate = testRepository.findQuestions(testId)
        val allOk = afterUpdate.isNotEmpty() && afterUpdate.all { it.verdict == Verdict.OK }
        if (allOk) {
            Validation.validateQuestionCount(afterUpdate.size)
            testRepository.markReady(testId)
        }

        return VerdictsSavedView(
            testId = testId,
            testStatus = testRepository.findById(testId)!!.status.wire,
            results = results,
            rejectedPositions = afterUpdate.filter { it.verdict == Verdict.REJECTED }.map { it.position },
            readyToTake = allOk,
        )
    }

    fun getTestQuestions(testId: UUID): TestQuestionsView {
        val test = requireTest(testId, null)
        if (test.status != TestStatus.READY) {
            throw MentalGymException("Test $testId is still a draft, every question must be verified first")
        }
        val concept = conceptService.require(test.conceptId)
        return TestQuestionsView(
            testId = test.id,
            conceptId = concept.id,
            conceptName = concept.name,
            questions = testRepository.findQuestions(test.id).map(::promptView),
        )
    }

    fun requireTest(testId: UUID, conceptId: UUID?): GeneratedTest {
        val test = testRepository.findById(testId) ?: throw MentalGymException("No test with id $testId")
        if (conceptId != null && test.conceptId != conceptId) {
            throw MentalGymException("Test $testId does not belong to concept $conceptId")
        }
        return test
    }

    private fun promptView(question: Question) = QuestionPromptView(
        questionId = question.id,
        position = question.position,
        type = question.type.wire,
        prompt = question.prompt,
    )

    private fun weakCriteria(conceptId: UUID): List<WeakCriterion> {
        val rows = attemptRepository.findGradedAnswersForConcept(conceptId)
        val graded = mutableMapOf<String, Int>()
        val missed = mutableMapOf<String, Int>()
        rows.forEach { row ->
            val textById = row.rubric.criteria.associate { it.id to it.text }
            row.criteriaResults.criteria.forEach { result ->
                val text = textById[result.id] ?: return@forEach
                graded[text] = (graded[text] ?: 0) + 1
                if (result.credit < 1.0) missed[text] = (missed[text] ?: 0) + 1
            }
        }
        return missed.entries
            .sortedByDescending { it.value }
            .map { WeakCriterion(text = it.key, timesMissed = it.value, timesGraded = graded[it.key] ?: it.value) }
    }

    private fun categorize(reason: String): RejectionCategory {
        val text = reason.lowercase()
        return when {
            listOf("not answerable", "unanswerable", "not covered", "outside the docs", "missing information")
                .any { text.contains(it) } -> RejectionCategory.UNANSWERABLE
            listOf("key is wrong", "wrong key", "incorrect key", "key contradicts", "answer key")
                .any { text.contains(it) } -> RejectionCategory.KEY_WRONG
            listOf("recall", "definition", "memor").any { text.contains(it) } -> RejectionCategory.RECALL_ONLY
            else -> RejectionCategory.AMBIGUOUS
        }
    }
}
