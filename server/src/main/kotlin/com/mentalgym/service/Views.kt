package com.mentalgym.service

import com.mentalgym.domain.CriteriaResults
import com.mentalgym.domain.Rubric
import java.time.Instant
import java.util.UUID

data class ConceptSummary(
    val conceptId: UUID,
    val name: String,
    val topic: String,
    val status: String,
    val docCount: Int,
    val docsRead: Boolean,
    val lastScore: Double?,
    val lastAttemptPassed: Boolean?,
    val lastAttemptAt: Instant?,
    val nextReviewAt: Instant?,
    val dueForReview: Boolean,
    val reviewCount: Int,
)

data class DocView(
    val docId: UUID,
    val url: String,
    val title: String?,
    val read: Boolean,
    val characters: Int,
    val fetchedAt: Instant,
    val content: String? = null,
)

data class AttemptSummary(
    val attemptId: UUID,
    val testId: UUID,
    val submittedAt: Instant,
    val status: String,
    val score: Double?,
    val passed: Boolean?,
    val threshold: Double,
)

data class ConceptDetail(
    val concept: ConceptSummary,
    val docs: List<DocView>,
    val attempts: List<AttemptSummary>,
    val openTestId: UUID?,
)

data class SavedDocsView(
    val conceptId: UUID,
    val status: String,
    val docs: List<DocView>,
    val warnings: List<String>,
)

data class MarkedReadView(
    val conceptId: UUID,
    val status: String,
    val docsMarkedRead: Int,
    val totalDocs: Int,
)

data class DocSummary(
    val docIndex: Int,
    val title: String?,
    val url: String,
    val characters: Int,
    val content: String? = null,
)

data class DocPageView(
    val conceptId: UUID,
    val docCount: Int,
    val docIndex: Int,
    val title: String?,
    val url: String,
    val characters: Int,
    val offset: Int,
    val nextOffset: Int?,
    val content: String,
    val docs: List<DocSummary>,
)

data class WeakCriterion(
    val text: String,
    val timesMissed: Int,
    val timesGraded: Int,
)

data class RejectedQuestion(
    val position: Int,
    val prompt: String,
    val reason: String?,
)

data class GenerationMaterial(
    val conceptId: UUID,
    val conceptName: String,
    val topic: String,
    val docs: List<DocSummary>,
    val previouslyUsedPrompts: List<String>,
    val weakCriteria: List<WeakCriterion>,
    val testIdToRevise: UUID?,
    val rejectedQuestions: List<RejectedQuestion>,
    val questionsToKeep: List<Int>,
    val rules: GenerationRules,
)

data class GenerationRules(
    val minQuestions: Int,
    val maxQuestions: Int,
    val minCriteriaPerQuestion: Int,
    val maxCriteriaPerQuestion: Int,
    val allowedTypes: List<String>,
)

data class QuestionPromptView(
    val questionId: UUID,
    val position: Int,
    val type: String,
    val prompt: String,
)

data class SavedTestView(
    val testId: UUID,
    val conceptId: UUID,
    val status: String,
    val questions: List<QuestionPromptView>,
)

data class VerificationQuestion(
    val position: Int,
    val type: String,
    val prompt: String,
    val answerKey: String,
    val rubric: Rubric,
    val wrongAnswers: List<String>,
    val verdict: String,
)

data class VerificationMaterial(
    val testId: UUID,
    val conceptId: UUID,
    val conceptName: String,
    val docs: List<DocSummary>,
    val positions: List<Int>,
    val questions: List<VerificationQuestion>,
)

data class VerdictView(
    val position: Int,
    val verdict: String,
    val category: String?,
)

data class VerdictsSavedView(
    val testId: UUID,
    val testStatus: String,
    val results: List<VerdictView>,
    val rejectedPositions: List<Int>,
    val readyToTake: Boolean,
)

data class TestQuestionsView(
    val testId: UUID,
    val conceptId: UUID,
    val conceptName: String,
    val questions: List<QuestionPromptView>,
)

data class AttemptCreatedView(
    val attemptId: UUID,
    val testId: UUID,
    val conceptId: UUID,
    val answersStored: Int,
    val status: String,
    val questionIdsToGrade: List<UUID>,
)

data class GradingMaterial(
    val attemptId: UUID,
    val questionId: UUID,
    val position: Int,
    val type: String,
    val prompt: String,
    val answerKey: String,
    val rubric: Rubric,
    val wrongAnswers: List<String>,
    val docs: List<DocSummary>,
    val learnerAnswer: String,
    val alreadyGraded: Boolean,
)

data class GradeSavedView(
    val attemptId: UUID,
    val questionId: UUID,
    val questionScore: Double,
    val answersGraded: Int,
    val answersTotal: Int,
    val attemptStatus: String,
    val result: AttemptResultView?,
)

data class MissedCriterion(
    val id: String,
    val text: String,
    val kind: String,
    val credit: Double,
)

data class AnswerResultView(
    val answerId: UUID,
    val questionId: UUID,
    val position: Int,
    val type: String,
    val prompt: String,
    val learnerAnswer: String,
    val score: Double?,
    val feedback: String?,
    val missedCriteria: List<MissedCriterion>,
    val criteriaResults: CriteriaResults?,
    val answerKey: String?,
    val misconceptionFlagged: Boolean,
    val flagged: Boolean,
    val flagNote: String?,
)

data class AttemptResultView(
    val attemptId: UUID,
    val testId: UUID,
    val conceptId: UUID,
    val conceptName: String,
    val status: String,
    val score: Double?,
    val passed: Boolean?,
    val threshold: Double,
    val submittedAt: Instant,
    val gradedAt: Instant?,
    val conceptStatus: String,
    val nextReviewAt: Instant?,
    val answers: List<AnswerResultView>,
    val docsToReread: List<String>,
)

data class FlagSavedView(
    val answerId: UUID,
    val flagged: Boolean,
    val note: String,
)

data class DueConcept(
    val conceptId: UUID,
    val name: String,
    val topic: String,
    val nextReviewAt: Instant?,
    val daysOverdue: Long,
)

data class RecentAttemptView(
    val attemptId: UUID,
    val conceptId: UUID,
    val conceptName: String,
    val submittedAt: Instant,
    val status: String,
    val score: Double?,
    val passed: Boolean?,
)

data class StatsView(
    val streakDays: Int,
    val longestStreakDays: Int,
    val lastActivityDay: String?,
    val conceptsByStatus: Map<String, Int>,
    val dueNow: List<DueConcept>,
    val recentAttempts: List<RecentAttemptView>,
    val totals: Map<String, Int>,
    val passThreshold: Double,
)

data class TopicConcepts(
    val topic: String,
    val concepts: List<ConceptSummary>,
)

data class TrendPoint(
    val attemptId: UUID,
    val submittedAt: Instant,
    val score: Double?,
    val passed: Boolean?,
)
