package com.mentalgym.mcp

import com.mentalgym.service.TestService
import com.mentalgym.service.VerdictInput
import com.mentalgym.service.VerdictsSavedView
import com.mentalgym.service.VerificationMaterial
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class VerifierTools(private val testService: TestService) {

    @McpTool(
        name = "get_test_for_verification",
        description = "Question verifier only. Draft questions with answer keys, rubrics and wrong answers. " +
            "Pass a position to fetch one question at a time. Read the documents with get_docs.",
    )
    fun getTestForVerification(
        @McpToolParam(description = "Test id", required = true)
        testId: String,
        @McpToolParam(description = "One question by position, or omit for all of them", required = false)
        position: Int?,
    ): VerificationMaterial = testService.getTestForVerification(uuid("testId", testId), position)

    @McpTool(
        name = "save_verdicts",
        description = "Question verifier only. Record ok or rejected per question position with a reason. " +
            "The test becomes ready once every question is ok.",
    )
    fun saveVerdicts(
        @McpToolParam(
            description = "Test id",
            required = true,
        )
        testId: String,
        @McpToolParam(
            description = "One entry per question: position, verdict (ok or rejected), reason, and for rejections " +
                "a category (ambiguous, key_wrong, unanswerable, recall_only)",
            required = true,
        )
        verdicts: List<VerdictSubmission>,
    ): VerdictsSavedView = testService.saveVerdicts(
        testId = uuid("testId", testId),
        verdicts = verdicts.map { VerdictInput(it.position, it.verdict, it.reason, it.category) },
    )
}

data class VerdictSubmission(
    val position: Int,
    val verdict: String,
    val reason: String? = null,
    val category: String? = null,
)
