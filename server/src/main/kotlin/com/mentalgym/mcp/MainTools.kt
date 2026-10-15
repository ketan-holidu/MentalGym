package com.mentalgym.mcp

import com.mentalgym.service.AnswerInput
import com.mentalgym.service.AttemptCreatedView
import com.mentalgym.service.AttemptResultView
import com.mentalgym.service.AttemptService
import com.mentalgym.service.ConceptDetail
import com.mentalgym.service.ConceptService
import com.mentalgym.service.ConceptSummary
import com.mentalgym.service.DocInput
import com.mentalgym.service.DocService
import com.mentalgym.service.FlagSavedView
import com.mentalgym.service.MarkedReadView
import com.mentalgym.service.SavedDocsView
import com.mentalgym.service.StatsService
import com.mentalgym.service.StatsView
import com.mentalgym.service.TestQuestionsView
import com.mentalgym.service.TestService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class MainTools(
    private val conceptService: ConceptService,
    private val docService: DocService,
    private val testService: TestService,
    private val attemptService: AttemptService,
    private val statsService: StatsService,
) {

    @McpTool(
        name = "add_concept",
        description = "Add a concept to learn. Creates the topic if it does not exist yet (default topic: General).",
    )
    fun addConcept(
        @McpToolParam(description = "Concept name, e.g. 'Dependency Injection'", required = true)
        name: String,
        @McpToolParam(description = "Topic name, defaults to General", required = false)
        topic: String?,
    ): ConceptSummary = conceptService.addConcept(name, topic)

    @McpTool(
        name = "list_concepts",
        description = "List concepts with status, last score and next review date. Filter by topic, status or due for review.",
    )
    fun listConcepts(
        @McpToolParam(description = "Topic name filter", required = false)
        topic: String?,
        @McpToolParam(
            description = "Status filter: not_started, reading, ready_to_test or mastered",
            required = false,
        )
        status: String?,
        @McpToolParam(description = "Only concepts due for review", required = false)
        due: Boolean?,
    ): List<ConceptSummary> = conceptService.listConcepts(topic, status, due)

    @McpTool(
        name = "get_concept",
        description = "Concept with its docs and attempt history. Never returns answer keys or rubrics. " +
            "Set includeDocsText to read the stored document text.",
    )
    fun getConcept(
        @McpToolParam(description = "Concept id", required = true)
        conceptId: String,
        @McpToolParam(description = "Include the full stored text of each doc", required = false)
        includeDocsText: Boolean?,
    ): ConceptDetail = conceptService.getConcept(uuid("conceptId", conceptId), includeDocsText == true)

    @McpTool(
        name = "save_docs",
        description = "Store the documents to read for a concept, text included. Sets the concept to reading. " +
            "Use replace=true to swap the whole doc set.",
    )
    fun saveDocs(
        @McpToolParam(description = "Concept id", required = true)
        conceptId: String,
        @McpToolParam(description = "Documents with url, optional title and the fetched text", required = true)
        docs: List<DocInput>,
        @McpToolParam(description = "Replace the existing doc set instead of adding to it", required = false)
        replace: Boolean?,
    ): SavedDocsView = docService.saveDocs(uuid("conceptId", conceptId), docs, replace == true)

    @McpTool(
        name = "mark_docs_read",
        description = "Mark every doc of a concept as read and move it to ready_to_test. Counts as activity for the streak.",
    )
    fun markDocsRead(
        @McpToolParam(description = "Concept id", required = true)
        conceptId: String,
    ): MarkedReadView = docService.markDocsRead(uuid("conceptId", conceptId))

    @McpTool(
        name = "get_test_questions",
        description = "Question prompts of a verified test, in order, without answer keys. Ask them one at a time, closed book.",
    )
    fun getTestQuestions(
        @McpToolParam(description = "Test id", required = true)
        testId: String,
    ): TestQuestionsView = testService.getTestQuestions(uuid("testId", testId))

    @McpTool(
        name = "submit_attempt",
        description = "Submit the learner's answers verbatim, one per question. Returns the attempt id to grade. " +
            "Counts as activity for the streak.",
    )
    fun submitAttempt(
        @McpToolParam(description = "Test id", required = true)
        testId: String,
        @McpToolParam(description = "One answer per question: questionId and the answer text as written", required = true)
        answers: List<AnswerSubmission>,
    ): AttemptCreatedView = attemptService.submitAttempt(
        testId = uuid("testId", testId),
        answers = answers.map { AnswerInput(uuid("questionId", it.questionId), it.text) },
    )

    @McpTool(
        name = "get_attempt_result",
        description = "Graded result of an attempt: score, pass or fail, per question feedback, missed criteria and answer keys.",
    )
    fun getAttemptResult(
        @McpToolParam(description = "Attempt id", required = true)
        attemptId: String,
    ): AttemptResultView = attemptService.getAttemptResult(uuid("attemptId", attemptId))

    @McpTool(
        name = "flag_answer",
        description = "Flag a graded answer whose grade looks wrong, with a one-line reason. Feeds the calibration set.",
    )
    fun flagAnswer(
        @McpToolParam(description = "Answer id from the attempt result", required = true)
        answerId: String,
        @McpToolParam(description = "One line on why the grade looks wrong", required = true)
        note: String,
    ): FlagSavedView = attemptService.flagAnswer(uuid("answerId", answerId), note)

    @McpTool(
        name = "get_stats",
        description = "Streak, concepts due for review, counts by status and recent attempts.",
    )
    fun getStats(): StatsView = statsService.getStats()
}

data class AnswerSubmission(
    val questionId: String,
    val text: String,
)
