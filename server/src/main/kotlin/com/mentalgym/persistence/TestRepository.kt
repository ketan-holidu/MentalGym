package com.mentalgym.persistence

import com.mentalgym.domain.GeneratedTest
import com.mentalgym.domain.Question
import com.mentalgym.domain.QuestionType
import com.mentalgym.domain.Rubric
import com.mentalgym.domain.TestStatus
import com.mentalgym.domain.Verdict
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class TestRepository(
    private val jdbcClient: JdbcClient,
    private val json: Json,
) {

    fun insert(conceptId: UUID, generatorVersion: String): GeneratedTest {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            "insert into tests (id, concept_id, generator_version) values (:id, :conceptId, :generatorVersion)"
        )
            .param("id", id)
            .param("conceptId", conceptId)
            .param("generatorVersion", generatorVersion)
            .update()
        return requireNotNull(findById(id)) { "Test for concept $conceptId disappeared right after insert" }
    }

    fun findById(id: UUID): GeneratedTest? = jdbcClient
        .sql("select * from tests where id = :id")
        .param("id", id)
        .query(::mapTest)
        .optional()
        .orElse(null)

    fun findByConcept(conceptId: UUID): List<GeneratedTest> = jdbcClient
        .sql("select * from tests where concept_id = :conceptId order by created_at")
        .param("conceptId", conceptId)
        .query(::mapTest)
        .list()

    fun markReady(id: UUID) {
        jdbcClient.sql("update tests set status = 'ready' where id = :id")
            .param("id", id)
            .update()
    }

    fun markDraft(id: UUID) {
        jdbcClient.sql("update tests set status = 'draft' where id = :id")
            .param("id", id)
            .update()
    }

    fun updateGeneratorVersion(id: UUID, generatorVersion: String) {
        jdbcClient.sql("update tests set generator_version = :generatorVersion where id = :id")
            .param("generatorVersion", generatorVersion)
            .param("id", id)
            .update()
    }

    fun insertQuestion(
        testId: UUID,
        position: Int,
        type: QuestionType,
        prompt: String,
        answerKey: String,
        rubric: Rubric,
        wrongAnswers: List<String>,
    ): Question {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            """
            insert into questions (id, test_id, position, type, prompt, answer_key, rubric, wrong_answers)
            values (:id, :testId, :position, :type, :prompt, :answerKey, :rubric::jsonb, :wrongAnswers::jsonb)
            """
        )
            .param("id", id)
            .param("testId", testId)
            .param("position", position)
            .param("type", type.wire)
            .param("prompt", prompt)
            .param("answerKey", answerKey)
            .param("rubric", json.write(rubric))
            .param("wrongAnswers", json.write(wrongAnswers))
            .update()
        return requireNotNull(findQuestionById(id)) { "Question $position disappeared right after insert" }
    }

    fun deleteQuestions(testId: UUID, positions: Collection<Int>) {
        if (positions.isEmpty()) return
        jdbcClient.sql("delete from questions where test_id = :testId and position in (:positions)")
            .param("testId", testId)
            .param("positions", positions.toList())
            .update()
    }

    fun findQuestionById(id: UUID): Question? = jdbcClient
        .sql("select * from questions where id = :id")
        .param("id", id)
        .query(::mapQuestion)
        .optional()
        .orElse(null)

    fun findQuestions(testId: UUID): List<Question> = jdbcClient
        .sql("select * from questions where test_id = :testId order by position")
        .param("testId", testId)
        .query(::mapQuestion)
        .list()

    fun findPromptsForConcept(conceptId: UUID): List<String> = jdbcClient
        .sql(
            """
            select q.prompt
            from questions q
            join tests t on t.id = q.test_id
            where t.concept_id = :conceptId
            order by t.created_at, q.position
            """
        )
        .param("conceptId", conceptId)
        .query { rs, _ -> rs.getString("prompt") }
        .list()

    fun updateVerdict(testId: UUID, position: Int, verdict: Verdict, reason: String?) {
        val updated = jdbcClient.sql(
            "update questions set verdict = :verdict, verdict_reason = :reason where test_id = :testId and position = :position"
        )
            .param("verdict", verdict.wire)
            .param("reason", reason)
            .param("testId", testId)
            .param("position", position)
            .update()
        require(updated == 1) { "No question at position $position in test $testId" }
    }

    private fun mapTest(rs: ResultSet, rowNum: Int): GeneratedTest = GeneratedTest(
        id = rs.getObject("id", UUID::class.java),
        conceptId = rs.getObject("concept_id", UUID::class.java),
        status = TestStatus.of(rs.getString("status")),
        generatorVersion = rs.getString("generator_version"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )

    private fun mapQuestion(rs: ResultSet, rowNum: Int): Question = Question(
        id = rs.getObject("id", UUID::class.java),
        testId = rs.getObject("test_id", UUID::class.java),
        position = rs.getInt("position"),
        type = QuestionType.of(rs.getString("type")),
        prompt = rs.getString("prompt"),
        answerKey = rs.getString("answer_key"),
        rubric = json.readRubric(rs.getString("rubric")),
        wrongAnswers = json.readStrings(rs.getString("wrong_answers")),
        verdict = Verdict.of(rs.getString("verdict")),
        verdictReason = rs.getString("verdict_reason"),
    )
}
