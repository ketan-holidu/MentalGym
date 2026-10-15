package com.mentalgym.service

import com.mentalgym.config.MentalGymProperties
import com.mentalgym.domain.ConceptStatus
import com.mentalgym.persistence.AttemptRepository
import com.mentalgym.persistence.ConceptRepository
import com.mentalgym.persistence.DocRepository
import com.mentalgym.persistence.StatsRepository
import com.mentalgym.rules.Streak
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private const val RECENT_ATTEMPT_LIMIT = 10

@Service
class StatsService(
    private val conceptRepository: ConceptRepository,
    private val docRepository: DocRepository,
    private val attemptRepository: AttemptRepository,
    private val statsRepository: StatsRepository,
    private val conceptService: ConceptService,
    private val properties: MentalGymProperties,
) {

    fun getStats(): StatsView {
        val zone = properties.zoneId
        val activityDays = (attemptRepository.submissionDates(zone) + docRepository.readDates(zone)).toSet()
        val today = LocalDate.now(zone)
        val counts = conceptRepository.countsByStatus()
        val now = Instant.now()

        return StatsView(
            streakDays = Streak.current(activityDays, today),
            longestStreakDays = Streak.longest(activityDays),
            lastActivityDay = activityDays.maxOrNull()?.toString(),
            conceptsByStatus = ConceptStatus.entries.associate { it.wire to (counts[it.wire] ?: 0) },
            dueNow = conceptRepository.list(topic = null, status = null, dueOnly = true).map { concept ->
                DueConcept(
                    conceptId = concept.id,
                    name = concept.name,
                    topic = concept.topicName,
                    nextReviewAt = concept.nextReviewAt,
                    daysOverdue = concept.nextReviewAt
                        ?.let { Duration.between(it, now).toDays().coerceAtLeast(0) }
                        ?: 0,
                )
            },
            recentAttempts = attemptRepository.recent(RECENT_ATTEMPT_LIMIT).map {
                RecentAttemptView(
                    attemptId = it.attemptId,
                    conceptId = it.conceptId,
                    conceptName = it.conceptName,
                    submittedAt = it.submittedAt,
                    status = if (it.graded) "graded" else "grading",
                    score = it.score,
                    passed = it.passed,
                )
            },
            totals = statsRepository.totals(),
            passThreshold = properties.passThreshold,
        )
    }

    fun conceptsByTopic(): List<TopicConcepts> =
        conceptRepository.list(topic = null, status = null, dueOnly = false)
            .map(conceptService::summarize)
            .groupBy { it.topic }
            .map { (topic, concepts) -> TopicConcepts(topic, concepts.sortedBy { it.name }) }
            .sortedBy { it.topic }

    fun trend(conceptId: java.util.UUID): List<TrendPoint> =
        attemptRepository.findByConcept(conceptId).map {
            TrendPoint(attemptId = it.id, submittedAt = it.submittedAt, score = it.score, passed = it.passed)
        }
}
