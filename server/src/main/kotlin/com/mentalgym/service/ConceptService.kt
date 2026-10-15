package com.mentalgym.service

import com.mentalgym.domain.Concept
import com.mentalgym.domain.ConceptStatus
import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.TestStatus
import com.mentalgym.persistence.AttemptRepository
import com.mentalgym.persistence.ConceptRepository
import com.mentalgym.persistence.DocRepository
import com.mentalgym.persistence.TestRepository
import com.mentalgym.persistence.TopicRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

const val DEFAULT_TOPIC = "General"

@Service
class ConceptService(
    private val topicRepository: TopicRepository,
    private val conceptRepository: ConceptRepository,
    private val docRepository: DocRepository,
    private val testRepository: TestRepository,
    private val attemptRepository: AttemptRepository,
) {

    @Transactional
    fun addConcept(name: String, topic: String?): ConceptSummary {
        val conceptName = name.trim().takeIf { it.isNotEmpty() }
            ?: throw MentalGymException("A concept needs a name")
        val topicRow = topicRepository.findOrCreate(topic?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_TOPIC)
        val existing = conceptRepository.findByTopicAndName(topicRow.id, conceptName)
        if (existing != null) return summarize(existing)
        return summarize(conceptRepository.insert(topicRow.id, conceptName))
    }

    fun listConcepts(topic: String?, status: String?, due: Boolean?): List<ConceptSummary> =
        conceptRepository.list(
            topic = topic?.trim()?.takeIf { it.isNotEmpty() },
            status = status?.trim()?.takeIf { it.isNotEmpty() }?.let { ConceptStatus.of(it) },
            dueOnly = due == true,
        ).map(::summarize)

    fun getConcept(conceptId: UUID, includeDocsText: Boolean): ConceptDetail {
        val concept = require(conceptId)
        val docs = docRepository.findByConcept(conceptId).map { doc ->
            DocView(
                docId = doc.id,
                url = doc.url,
                title = doc.title,
                read = doc.readAt != null,
                characters = doc.content.length,
                fetchedAt = doc.fetchedAt,
                content = doc.content.takeIf { includeDocsText },
            )
        }
        val attempts = attemptRepository.findByConcept(conceptId).map { attempt ->
            AttemptSummary(
                attemptId = attempt.id,
                testId = attempt.testId,
                submittedAt = attempt.submittedAt,
                status = if (attempt.gradedAt != null) "graded" else "grading",
                score = attempt.score,
                passed = attempt.passed,
                threshold = attempt.threshold,
            )
        }
        val openTest = testRepository.findByConcept(conceptId)
            .lastOrNull { it.status == TestStatus.READY && attemptRepository.findByTest(it.id).isEmpty() }
        return ConceptDetail(
            concept = summarize(concept),
            docs = docs,
            attempts = attempts,
            openTestId = openTest?.id,
        )
    }

    fun require(conceptId: UUID): Concept =
        conceptRepository.findById(conceptId) ?: throw MentalGymException("No concept with id $conceptId")

    fun summarize(concept: Concept): ConceptSummary {
        val docs = docRepository.findByConcept(concept.id)
        val lastGraded = attemptRepository.findByConcept(concept.id).lastOrNull { it.gradedAt != null }
        return ConceptSummary(
            conceptId = concept.id,
            name = concept.name,
            topic = concept.topicName,
            status = concept.status.wire,
            docCount = docs.size,
            docsRead = docs.isNotEmpty() && docs.all { it.readAt != null },
            lastScore = lastGraded?.score,
            lastAttemptPassed = lastGraded?.passed,
            lastAttemptAt = lastGraded?.submittedAt,
            nextReviewAt = concept.nextReviewAt,
            dueForReview = concept.status == ConceptStatus.MASTERED &&
                concept.nextReviewAt?.isBefore(Instant.now()) == true,
            reviewCount = concept.reviewCount,
        )
    }
}
