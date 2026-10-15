package com.mentalgym.mcp

import com.mentalgym.service.AttemptService
import com.mentalgym.service.CriterionGradeInput
import com.mentalgym.service.GradeSavedView
import com.mentalgym.service.GradingMaterial
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class GraderTools(private val attemptService: AttemptService) {

    @McpTool(
        name = "get_grading_material",
        description = "Grader only. One question with its answer key, rubric, wrong answers, the doc text and the learner's answer.",
    )
    fun getGradingMaterial(
        @McpToolParam(description = "Attempt id", required = true)
        attemptId: String,
        @McpToolParam(description = "Question id", required = true)
        questionId: String,
    ): GradingMaterial = attemptService.getGradingMaterial(
        attemptId = uuid("attemptId", attemptId),
        questionId = uuid("questionId", questionId),
    )

    @McpTool(
        name = "save_grade",
        description = "Grader only. Credit per rubric criterion with a verbatim quote as evidence, plus feedback. " +
            "The server computes the scores and finalizes the attempt once every answer is graded.",
    )
    fun saveGrade(
        @McpToolParam(description = "Attempt id", required = true)
        attemptId: String,
        @McpToolParam(description = "Question id", required = true)
        questionId: String,
        @McpToolParam(description = "Version string of the grader prompt, e.g. grade-v1", required = true)
        graderVersion: String,
        @McpToolParam(
            description = "One entry per rubric criterion: id, credit between 0 and 1, and the quote from the " +
                "learner's answer that earns it (required for any credit above 0)",
            required = true,
        )
        criteria: List<CriterionGradeSubmission>,
        @McpToolParam(description = "True when the answer matches one of the listed wrong answers", required = true)
        misconceptionFlagged: Boolean,
        @McpToolParam(description = "What was missing and which doc to reread", required = true)
        feedback: String,
    ): GradeSavedView = attemptService.saveGrade(
        attemptId = uuid("attemptId", attemptId),
        questionId = uuid("questionId", questionId),
        graderVersion = graderVersion,
        criteria = criteria.map { CriterionGradeInput(it.id, it.credit, it.evidence) },
        misconceptionFlagged = misconceptionFlagged,
        feedback = feedback,
    )
}

data class CriterionGradeSubmission(
    val id: String,
    val credit: Double,
    val evidence: String? = null,
)
