package com.mentalgym

import tools.jackson.databind.ObjectMapper
import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.Rubric
import com.mentalgym.domain.RubricCriterion
import com.mentalgym.mcp.AnswerSubmission
import com.mentalgym.mcp.CriterionGradeSubmission
import com.mentalgym.mcp.DocsTools
import com.mentalgym.mcp.GeneratorTools
import com.mentalgym.mcp.GraderTools
import com.mentalgym.mcp.MainTools
import com.mentalgym.mcp.QuestionSubmission
import com.mentalgym.mcp.VerdictSubmission
import com.mentalgym.mcp.VerifierTools
import com.mentalgym.service.DocInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Testcontainers
class ToolFlowIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17.2")

        /**
         * The container reports ready as soon as its log says so, but a published port can take a moment longer to
         * forward on the host (Rancher Desktop, Colima). Connect for real before Spring does.
         */
        private fun awaitReachable(timeout: Duration = Duration.ofSeconds(60)) {
            val deadline = Instant.now().plus(timeout)
            var failure: Exception? = null
            while (Instant.now().isBefore(deadline)) {
                try {
                    DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
                        .use { return }
                } catch (e: SQLException) {
                    failure = e
                    Thread.sleep(500)
                }
            }
            throw IllegalStateException("Postgres at ${postgres.jdbcUrl} never became reachable", failure)
        }

        @DynamicPropertySource
        @JvmStatic
        fun datasource(registry: DynamicPropertyRegistry) {
            awaitReachable()
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var main: MainTools
    @Autowired lateinit var generator: GeneratorTools
    @Autowired lateinit var verifier: VerifierTools
    @Autowired lateinit var grader: GraderTools
    @Autowired lateinit var docs: DocsTools
    @Autowired lateinit var objectMapper: ObjectMapper

    @Test
    fun `a concept runs from docs to a graded pass and back to review`() {
        val concept = main.addConcept("Dependency Injection ${UUID.randomUUID()}", "Spring")
        assertEquals("not_started", concept.status)
        assertEquals("Spring", concept.topic)

        val conceptId = concept.conceptId.toString()
        val saved = main.saveDocs(
            conceptId = conceptId,
            docs = listOf(
                DocInput("https://docs.example/di", "Container basics", "The container resolves beans at startup."),
                DocInput("https://docs.example/ctor", "Constructor injection", "Constructor injection makes a dependency required."),
            ),
            replace = false,
        )
        assertEquals("reading", saved.status)
        assertEquals(2, saved.docs.size)

        assertEquals("ready_to_test", main.markDocsRead(conceptId).status)

        val material = generator.getGenerationMaterial(conceptId, null)
        assertEquals(2, material.docs.size)
        assertEquals(listOf(0, 1), material.docs.map { it.docIndex })
        assertTrue(
            material.docs.all { it.content != null },
            "a small doc set is inlined for the generator",
        )
        assertTrue(material.previouslyUsedPrompts.isEmpty())

        val draft = generator.saveTest(conceptId, "gen-test", questions(5), null)
        assertEquals("draft", draft.status)
        assertEquals(5, draft.questions.size)

        val draftJson = objectMapper.writeValueAsString(draft)
        assertFalse(draftJson.contains("answerKey"), "save_test leaked an answer key")
        assertFalse(draftJson.contains("rubric"), "save_test leaked a rubric")
        assertFalse(draftJson.contains("wrongAnswers"), "save_test leaked wrong answers")

        val verification = verifier.getTestForVerification(draft.testId.toString(), null)
        assertEquals(5, verification.questions.size)
        assertTrue(verification.questions.all { it.answerKey.isNotBlank() })
        assertTrue(
            verification.docs.all { it.content == null },
            "the whole-test verification payload leaves doc text out",
        )
        val single = verifier.getTestForVerification(draft.testId.toString(), 3)
        assertEquals(1, single.questions.size)
        assertEquals(3, single.questions.single().position)
        assertTrue(
            single.docs.all { it.content != null },
            "a single-question verification payload carries the doc text",
        )

        val firstRound = verifier.saveVerdicts(
            draft.testId.toString(),
            (1..4).map { VerdictSubmission(it, "ok") } +
                VerdictSubmission(5, "rejected", "Two readings are possible, the wording is ambiguous", null),
        )
        assertFalse(firstRound.readyToTake)
        assertEquals(listOf(5), firstRound.rejectedPositions)
        assertEquals("ambiguous", firstRound.results.single { it.position == 5 }.category)
        assertThrows<MentalGymException> { main.getTestQuestions(draft.testId.toString()) }

        val retryMaterial = generator.getGenerationMaterial(conceptId, draft.testId.toString())
        assertEquals(listOf(1, 2, 3, 4), retryMaterial.questionsToKeep)
        assertEquals(1, retryMaterial.rejectedQuestions.size)
        assertTrue(retryMaterial.rejectedQuestions.single().reason!!.contains("ambiguous"))
        assertEquals(5, retryMaterial.previouslyUsedPrompts.size)

        generator.saveTest(conceptId, "gen-test", listOf(question(5, "Rewritten question 5")), draft.testId.toString())
        val secondRound = verifier.saveVerdicts(draft.testId.toString(), listOf(VerdictSubmission(5, "ok")))
        assertTrue(secondRound.readyToTake)
        assertEquals("ready", secondRound.testStatus)

        val questions = main.getTestQuestions(draft.testId.toString())
        assertEquals(5, questions.questions.size)
        assertFalse(objectMapper.writeValueAsString(questions).contains("answerKey"))

        val detailJson = objectMapper.writeValueAsString(main.getConcept(conceptId, true))
        assertFalse(detailJson.contains("answerKey"), "get_concept leaked an answer key")

        val attempt = main.submitAttempt(
            questions.testId.toString(),
            questions.questions.map { AnswerSubmission(it.questionId.toString(), "Answer to ${it.position}") },
        )
        assertEquals(5, attempt.answersStored)

        val repeated = main.submitAttempt(
            questions.testId.toString(),
            questions.questions.map { AnswerSubmission(it.questionId.toString(), "Answer to ${it.position}") },
        )
        assertEquals(attempt.attemptId, repeated.attemptId, "the same answers must not create a second attempt")

        val gradingMaterial = grader.getGradingMaterial(
            attempt.attemptId.toString(),
            questions.questions.first().questionId.toString(),
        )
        assertEquals(2, gradingMaterial.docs.size)
        assertTrue(
            gradingMaterial.docs.all { it.content != null },
            "a small doc set is inlined for the grader",
        )

        questions.questions.dropLast(1).forEach { gradeFull(attempt.attemptId, it.questionId) }
        val last = gradePartial(attempt.attemptId, questions.questions.last().questionId)

        val result = requireNotNull(last.result) { "the final grade must finalize the attempt" }
        assertEquals("graded", result.status)
        assertEquals(0.9667, result.score)
        assertTrue(result.passed!!)
        assertEquals("mastered", result.conceptStatus)
        assertNotNull(result.nextReviewAt)
        assertEquals(
            3,
            Duration.between(Instant.now(), result.nextReviewAt!!).toDays() + 1,
            "the first pass schedules the first ladder step",
        )

        val fetched = main.getAttemptResult(attempt.attemptId.toString())
        assertTrue(fetched.answers.all { it.answerKey != null }, "keys are allowed after grading")
        val weakest = fetched.answers.single { it.missedCriteria.isNotEmpty() }
        assertEquals("c3", weakest.missedCriteria.single().id)
        assertTrue(fetched.docsToReread.isNotEmpty())

        main.flagAnswer(weakest.answerId.toString(), "Criterion c3 was covered in the second sentence")

        val failing = main.submitAttempt(
            questions.testId.toString(),
            questions.questions.map { AnswerSubmission(it.questionId.toString(), "I don't know") },
        )
        questions.questions.forEach { gradeZero(failing.attemptId, it.questionId) }
        val failed = main.getAttemptResult(failing.attemptId.toString())
        assertEquals(0.0, failed.score)
        assertFalse(failed.passed!!)
        assertEquals("ready_to_test", failed.conceptStatus)
        assertNull(failed.nextReviewAt, "a failed review resets the ladder")

        val stats = main.getStats()
        assertTrue(stats.streakDays >= 1)
        assertEquals(1, stats.totals["flaggedAnswers"])
        assertTrue(stats.conceptsByStatus.getValue("ready_to_test") >= 1)
    }

    @Test
    fun `a test outside the allowed question count is rejected`() {
        val concept = main.addConcept("Too small ${UUID.randomUUID()}", "Spring")
        assertThrows<MentalGymException> {
            generator.saveTest(concept.conceptId.toString(), "gen-test", questions(3), null)
        }
    }

    @Test
    fun `credit without a quote from the answer is rejected`() {
        val concept = main.addConcept("Evidence ${UUID.randomUUID()}", "Spring")
        val conceptId = concept.conceptId.toString()
        main.saveDocs(conceptId, listOf(DocInput("https://docs.example/x", "X", "text")), false)
        val test = generator.saveTest(conceptId, "gen-test", questions(4), null)
        verifier.saveVerdicts(test.testId.toString(), (1..4).map { VerdictSubmission(it, "ok") })
        val ready = main.getTestQuestions(test.testId.toString())
        val attempt = main.submitAttempt(
            ready.testId.toString(),
            ready.questions.map { AnswerSubmission(it.questionId.toString(), "something") },
        )
        assertThrows<MentalGymException> {
            grader.saveGrade(
                attemptId = attempt.attemptId.toString(),
                questionId = ready.questions.first().questionId.toString(),
                graderVersion = "grade-test",
                criteria = listOf(
                    CriterionGradeSubmission("c1", 1.0, null),
                    CriterionGradeSubmission("c2", 0.0, null),
                    CriterionGradeSubmission("c3", 0.0, null),
                ),
                misconceptionFlagged = false,
                feedback = "no quote given",
            )
        }
    }

    @Test
    fun `get_docs hands out one document at a time`() {
        val concept = main.addConcept("Paging ${UUID.randomUUID()}", "Spring")
        val conceptId = concept.conceptId.toString()
        val long = "x".repeat(35_000)
        main.saveDocs(conceptId, listOf(
            DocInput("https://docs.example/long", "Long one", long),
            DocInput("https://docs.example/short", "Short one", "short text"),
        ), false)

        assertTrue(
            generator.getGenerationMaterial(conceptId, null).docs.all { it.content == null },
            "a doc set above the inline budget is left to get_docs",
        )

        val first = docs.getDocs(conceptId, null, null)
        assertEquals(2, first.docCount)
        assertEquals(0, first.docIndex)
        assertEquals(20_000, first.content.length)
        assertEquals(20_000, first.nextOffset)
        assertEquals(35_000, first.characters)

        val rest = docs.getDocs(conceptId, 0, first.nextOffset)
        assertEquals(15_000, rest.content.length)
        assertNull(rest.nextOffset)

        val second = docs.getDocs(conceptId, 1, null)
        assertEquals("short text", second.content)
        assertNull(second.nextOffset)

        assertThrows<MentalGymException> { docs.getDocs(conceptId, 2, null) }
        assertThrows<MentalGymException> { docs.getDocs(conceptId, 0, 35_000) }
    }

    private fun gradeFull(attemptId: UUID, questionId: UUID) = grader.saveGrade(
        attemptId = attemptId.toString(),
        questionId = questionId.toString(),
        graderVersion = "grade-test",
        criteria = listOf(
            CriterionGradeSubmission("c1", 1.0, "quote one"),
            CriterionGradeSubmission("c2", 1.0, "quote two"),
            CriterionGradeSubmission("c3", 1.0, "quote three"),
        ),
        misconceptionFlagged = false,
        feedback = "Complete answer",
    )

    private fun gradePartial(attemptId: UUID, questionId: UUID) = grader.saveGrade(
        attemptId = attemptId.toString(),
        questionId = questionId.toString(),
        graderVersion = "grade-test",
        criteria = listOf(
            CriterionGradeSubmission("c1", 1.0, "quote one"),
            CriterionGradeSubmission("c2", 1.0, "quote two"),
            CriterionGradeSubmission("c3", 0.0, null),
        ),
        misconceptionFlagged = false,
        feedback = "Missed the testability angle, reread the constructor injection doc",
    )

    private fun gradeZero(attemptId: UUID, questionId: UUID) = grader.saveGrade(
        attemptId = attemptId.toString(),
        questionId = questionId.toString(),
        graderVersion = "grade-test",
        criteria = listOf(
            CriterionGradeSubmission("c1", 0.0, null),
            CriterionGradeSubmission("c2", 0.0, null),
            CriterionGradeSubmission("c3", 0.0, null),
        ),
        misconceptionFlagged = false,
        feedback = "Nothing to credit, reread both docs",
    )

    private fun questions(count: Int) = (1..count).map { question(it, "Question $it about the container") }

    private fun question(position: Int, prompt: String) = QuestionSubmission(
        position = position,
        type = "scenario",
        prompt = prompt,
        answerKey = "Key for position $position",
        rubric = Rubric(
            listOf(
                RubricCriterion("c1", "names the container as the resolver", 3.0, "core"),
                RubricCriterion("c2", "explains why the dependency is required", 2.0, "core"),
                RubricCriterion("c3", "mentions testability", 1.0, "bonus"),
            )
        ),
        wrongAnswers = listOf("The caller creates the dependency itself"),
    )
}
