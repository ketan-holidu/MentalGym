package com.mentalgym.web

import com.mentalgym.domain.MentalGymException
import com.mentalgym.persistence.AttemptRepository
import com.mentalgym.persistence.FlaggedAnswerRow
import com.mentalgym.service.AttemptResultView
import com.mentalgym.service.AttemptService
import com.mentalgym.service.ConceptDetail
import com.mentalgym.service.ConceptService
import com.mentalgym.service.StatsService
import com.mentalgym.service.StatsView
import com.mentalgym.service.TopicConcepts
import com.mentalgym.service.TrendPoint
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class DashboardConceptDetail(
    val detail: ConceptDetail,
    val latestResult: AttemptResultView?,
    val flagged: List<FlaggedAnswerRow>,
)

@RestController
@RequestMapping("/api")
class DashboardApi(
    private val conceptService: ConceptService,
    private val attemptService: AttemptService,
    private val statsService: StatsService,
    private val attemptRepository: AttemptRepository,
) {

    @GetMapping("/stats")
    fun stats(): StatsView = statsService.getStats()

    @GetMapping("/concepts")
    fun concepts(): List<TopicConcepts> = statsService.conceptsByTopic()

    @GetMapping("/concepts/{id}")
    fun concept(@PathVariable id: UUID): DashboardConceptDetail {
        val detail = conceptService.getConcept(id, includeDocsText = false)
        val latest = detail.attempts.lastOrNull()
        return DashboardConceptDetail(
            detail = detail,
            latestResult = latest?.let { attemptService.getAttemptResult(it.attemptId) },
            flagged = attemptRepository.findFlaggedForConcept(id),
        )
    }

    @GetMapping("/concepts/{id}/trend")
    fun trend(@PathVariable id: UUID): List<TrendPoint> = statsService.trend(id)

    @ExceptionHandler(MentalGymException::class)
    fun handle(exception: MentalGymException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to exception.message))
}
