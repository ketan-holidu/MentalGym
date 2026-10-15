package com.mentalgym.mcp

import com.mentalgym.domain.Rubric
import com.mentalgym.service.GenerationMaterial
import com.mentalgym.service.QuestionInput
import com.mentalgym.service.SavedTestView
import com.mentalgym.service.TestService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class GeneratorTools(private val testService: TestService) {

    @McpTool(
        name = "get_generation_material",
        description = "Question generator only. Doc text for a concept, prompts already used, criteria the learner " +
            "keeps missing and, with a testId, the reasons its questions were rejected.",
    )
    fun getGenerationMaterial(
        @McpToolParam(description = "Concept id", required = true)
        conceptId: String,
        @McpToolParam(description = "Draft test id when rewriting rejected questions", required = false)
        testId: String?,
    ): GenerationMaterial = testService.getGenerationMaterial(
        conceptId = uuid("conceptId", conceptId),
        testId = optionalUuid("testId", testId),
    )

    @McpTool(
        name = "save_test",
        description = "Question generator only. Store questions with answer keys, rubrics and wrong answers. " +
            "Returns prompts only. Pass testId to replace rejected positions of a draft.",
    )
    fun saveTest(
        @McpToolParam(description = "Concept id", required = true)
        conceptId: String,
        @McpToolParam(description = "Version string of the generator prompt, e.g. gen-v1", required = true)
        generatorVersion: String,
        @McpToolParam(description = "Questions with position, type, prompt, answerKey, rubric and wrongAnswers", required = true)
        questions: List<QuestionSubmission>,
        @McpToolParam(description = "Draft test id when replacing rejected positions", required = false)
        testId: String?,
    ): SavedTestView = testService.saveTest(
        conceptId = uuid("conceptId", conceptId),
        generatorVersion = generatorVersion,
        questions = questions.map {
            QuestionInput(
                position = it.position,
                type = it.type,
                prompt = it.prompt,
                answerKey = it.answerKey,
                rubric = it.rubric,
                wrongAnswers = it.wrongAnswers,
            )
        },
        testId = optionalUuid("testId", testId),
    )
}

data class QuestionSubmission(
    val position: Int,
    val type: String,
    val prompt: String,
    val answerKey: String,
    val rubric: Rubric,
    val wrongAnswers: List<String> = emptyList(),
)
