package com.mentalgym.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

enum class ConceptStatus(val wire: String) {
    NOT_STARTED("not_started"),
    READING("reading"),
    READY_TO_TEST("ready_to_test"),
    MASTERED("mastered");

    companion object {
        fun of(wire: String): ConceptStatus = entries.firstOrNull { it.wire == wire }
            ?: throw MentalGymException("Unknown concept status '$wire'")
    }
}

enum class TestStatus(val wire: String) {
    DRAFT("draft"),
    READY("ready");

    companion object {
        fun of(wire: String): TestStatus = entries.firstOrNull { it.wire == wire }
            ?: throw MentalGymException("Unknown test status '$wire'")
    }
}

enum class Verdict(val wire: String) {
    PENDING("pending"),
    OK("ok"),
    REJECTED("rejected");

    companion object {
        fun of(wire: String): Verdict = entries.firstOrNull { it.wire == wire }
            ?: throw MentalGymException("Unknown verdict '$wire', expected ok or rejected")
    }
}

enum class RejectionCategory(val wire: String) {
    AMBIGUOUS("ambiguous"),
    KEY_WRONG("key_wrong"),
    UNANSWERABLE("unanswerable"),
    RECALL_ONLY("recall_only");

    companion object {
        fun of(wire: String): RejectionCategory = entries.firstOrNull { it.wire == wire }
            ?: throw MentalGymException("Unknown rejection category '$wire'")
    }
}

enum class QuestionType(val wire: String) {
    SCENARIO("scenario"),
    PREDICT_DEBUG("predict_debug"),
    WHAT_BREAKS("what_breaks"),
    COMPARE_SYNTHESIZE("compare_synthesize"),
    EXPLAIN_WHY("explain_why");

    companion object {
        fun of(wire: String): QuestionType = entries.firstOrNull { it.wire == wire }
            ?: throw MentalGymException(
                "Unknown question type '$wire', expected one of ${entries.joinToString { it.wire }}"
            )
    }
}

enum class CriterionKind(val wire: String) {
    CORE("core"),
    BONUS("bonus");

    companion object {
        fun of(wire: String): CriterionKind = entries.firstOrNull { it.wire == wire }
            ?: throw MentalGymException("Unknown criterion kind '$wire', expected core or bonus")
    }
}

data class RubricCriterion(
    val id: String,
    val text: String,
    val weight: Double,
    val kind: String,
)

data class Rubric(val criteria: List<RubricCriterion>)

data class CriterionResult(
    val id: String,
    val credit: Double,
    val evidence: String?,
)

data class CriteriaResults(
    val criteria: List<CriterionResult>,
    @get:JsonProperty("misconception_flagged")
    @param:JsonProperty("misconception_flagged")
    val misconceptionFlagged: Boolean,
)

data class Topic(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
)

data class Concept(
    val id: UUID,
    val topicId: UUID,
    val topicName: String,
    val name: String,
    val status: ConceptStatus,
    val masteredAt: Instant?,
    val nextReviewAt: Instant?,
    val reviewIntervalDays: Int?,
    val reviewCount: Int,
    val createdAt: Instant,
)

data class Doc(
    val id: UUID,
    val conceptId: UUID,
    val url: String,
    val title: String?,
    val content: String,
    val fetchedAt: Instant,
    val readAt: Instant?,
)

data class GeneratedTest(
    val id: UUID,
    val conceptId: UUID,
    val status: TestStatus,
    val generatorVersion: String,
    val createdAt: Instant,
)

data class Question(
    val id: UUID,
    val testId: UUID,
    val position: Int,
    val type: QuestionType,
    val prompt: String,
    val answerKey: String,
    val rubric: Rubric,
    val wrongAnswers: List<String>,
    val verdict: Verdict,
    val verdictReason: String?,
)

data class Attempt(
    val id: UUID,
    val testId: UUID,
    val submittedAt: Instant,
    val gradedAt: Instant?,
    val score: Double?,
    val passed: Boolean?,
    val threshold: Double,
    val graderVersion: String?,
)

data class Answer(
    val id: UUID,
    val attemptId: UUID,
    val questionId: UUID,
    val answerText: String,
    val score: Double?,
    val criteriaResults: CriteriaResults?,
    val feedback: String?,
    val flagged: Boolean,
    val flagNote: String?,
)

class MentalGymException(message: String) : RuntimeException(message)
