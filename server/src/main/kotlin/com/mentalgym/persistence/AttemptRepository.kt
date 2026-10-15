package com.mentalgym.persistence

import com.mentalgym.domain.Answer
import com.mentalgym.domain.Attempt
import com.mentalgym.domain.CriteriaResults
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Repository
class AttemptRepository(
    private val jdbcClient: JdbcClient,
    private val json: Json,
) {

    fun insert(testId: UUID, threshold: Double): Attempt {
        val id = UUID.randomUUID()
        jdbcClient.sql("insert into attempts (id, test_id, threshold) values (:id, :testId, :threshold)")
            .param("id", id)
            .param("testId", testId)
            .param("threshold", threshold)
            .update()
        return requireNotNull(findById(id)) { "Attempt for test $testId disappeared right after insert" }
    }

    fun findById(id: UUID): Attempt? = jdbcClient
        .sql("select * from attempts where id = :id")
        .param("id", id)
        .query(::mapAttempt)
        .optional()
        .orElse(null)

    fun findByTest(testId: UUID): List<Attempt> = jdbcClient
        .sql("select * from attempts where test_id = :testId order by submitted_at")
        .param("testId", testId)
        .query(::mapAttempt)
        .list()

    fun findByConcept(conceptId: UUID): List<Attempt> = jdbcClient
        .sql(
            """
            select a.*
            from attempts a
            join tests t on t.id = a.test_id
            where t.concept_id = :conceptId
            order by a.submitted_at
            """
        )
        .param("conceptId", conceptId)
        .query(::mapAttempt)
        .list()

    fun conceptIdOf(attemptId: UUID): UUID? = jdbcClient
        .sql(
            """
            select t.concept_id
            from attempts a
            join tests t on t.id = a.test_id
            where a.id = :attemptId
            """
        )
        .param("attemptId", attemptId)
        .query { rs, _ -> rs.getObject("concept_id", UUID::class.java) }
        .optional()
        .orElse(null)

    fun finalize(id: UUID, score: Double, passed: Boolean, graderVersion: String, gradedAt: Instant) {
        jdbcClient.sql(
            """
            update attempts
               set score = :score, passed = :passed, grader_version = :graderVersion, graded_at = :gradedAt
             where id = :id
            """
        )
            .param("score", score)
            .param("passed", passed)
            .param("graderVersion", graderVersion)
            .param("gradedAt", Timestamp.from(gradedAt))
            .param("id", id)
            .update()
    }

    fun recent(limit: Int): List<RecentAttempt> = jdbcClient
        .sql(
            """
            select a.id, a.submitted_at, a.graded_at, a.score, a.passed, c.name as concept_name, c.id as concept_id
            from attempts a
            join tests t on t.id = a.test_id
            join concepts c on c.id = t.concept_id
            order by a.submitted_at desc
            limit :limit
            """
        )
        .param("limit", limit)
        .query { rs, _ ->
            RecentAttempt(
                attemptId = rs.getObject("id", UUID::class.java),
                conceptId = rs.getObject("concept_id", UUID::class.java),
                conceptName = rs.getString("concept_name"),
                submittedAt = rs.getTimestamp("submitted_at").toInstant(),
                graded = rs.getTimestamp("graded_at") != null,
                score = rs.getBigDecimal("score")?.toDouble(),
                passed = rs.getObject("passed") as Boolean?,
            )
        }
        .list()

    fun submissionDates(zone: ZoneId): List<LocalDate> = jdbcClient
        .sql("select distinct (submitted_at at time zone :zone)::date as day from attempts")
        .param("zone", zone.id)
        .query { rs, _ -> rs.getDate("day").toLocalDate() }
        .list()

    fun insertAnswer(attemptId: UUID, questionId: UUID, answerText: String): Answer {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            "insert into answers (id, attempt_id, question_id, answer_text) values (:id, :attemptId, :questionId, :answerText)"
        )
            .param("id", id)
            .param("attemptId", attemptId)
            .param("questionId", questionId)
            .param("answerText", answerText)
            .update()
        return requireNotNull(findAnswerById(id)) { "Answer for question $questionId disappeared right after insert" }
    }

    fun findAnswerById(id: UUID): Answer? = jdbcClient
        .sql("select * from answers where id = :id")
        .param("id", id)
        .query(::mapAnswer)
        .optional()
        .orElse(null)

    fun findAnswers(attemptId: UUID): List<Answer> = jdbcClient
        .sql(
            """
            select a.*
            from answers a
            join questions q on q.id = a.question_id
            where a.attempt_id = :attemptId
            order by q.position
            """
        )
        .param("attemptId", attemptId)
        .query(::mapAnswer)
        .list()

    fun findAnswer(attemptId: UUID, questionId: UUID): Answer? = jdbcClient
        .sql("select * from answers where attempt_id = :attemptId and question_id = :questionId")
        .param("attemptId", attemptId)
        .param("questionId", questionId)
        .query(::mapAnswer)
        .optional()
        .orElse(null)

    fun saveGrade(answerId: UUID, score: Double, results: CriteriaResults, feedback: String) {
        jdbcClient.sql(
            """
            update answers
               set score = :score, criteria_results = :results::jsonb, feedback = :feedback
             where id = :id
            """
        )
            .param("score", score)
            .param("results", json.write(results))
            .param("feedback", feedback)
            .param("id", answerId)
            .update()
    }

    fun flagAnswer(answerId: UUID, note: String): Int = jdbcClient
        .sql("update answers set flagged = true, flag_note = :note where id = :id")
        .param("note", note)
        .param("id", answerId)
        .update()

    fun findGradedAnswersForConcept(conceptId: UUID): List<GradedAnswerRow> = jdbcClient
        .sql(
            """
            select ans.criteria_results, q.rubric, q.prompt
            from answers ans
            join attempts a on a.id = ans.attempt_id
            join tests t on t.id = a.test_id
            join questions q on q.id = ans.question_id
            where t.concept_id = :conceptId and ans.criteria_results is not null
            """
        )
        .param("conceptId", conceptId)
        .query { rs, _ ->
            GradedAnswerRow(
                criteriaResults = requireNotNull(json.readCriteriaResults(rs.getString("criteria_results"))),
                rubric = json.readRubric(rs.getString("rubric")),
                prompt = rs.getString("prompt"),
            )
        }
        .list()

    fun findFlaggedForConcept(conceptId: UUID): List<FlaggedAnswerRow> = jdbcClient
        .sql(
            """
            select ans.id, ans.flag_note, q.position, q.prompt, a.submitted_at
            from answers ans
            join attempts a on a.id = ans.attempt_id
            join tests t on t.id = a.test_id
            join questions q on q.id = ans.question_id
            where t.concept_id = :conceptId and ans.flagged
            order by a.submitted_at desc, q.position
            """
        )
        .param("conceptId", conceptId)
        .query { rs, _ ->
            FlaggedAnswerRow(
                answerId = rs.getObject("id", UUID::class.java),
                position = rs.getInt("position"),
                prompt = rs.getString("prompt"),
                note = rs.getString("flag_note"),
                submittedAt = rs.getTimestamp("submitted_at").toInstant(),
            )
        }
        .list()

    private fun mapAttempt(rs: ResultSet, rowNum: Int): Attempt = Attempt(
        id = rs.getObject("id", UUID::class.java),
        testId = rs.getObject("test_id", UUID::class.java),
        submittedAt = rs.getTimestamp("submitted_at").toInstant(),
        gradedAt = rs.getTimestamp("graded_at")?.toInstant(),
        score = rs.getBigDecimal("score")?.toDouble(),
        passed = rs.getObject("passed") as Boolean?,
        threshold = rs.getBigDecimal("threshold").toDouble(),
        graderVersion = rs.getString("grader_version"),
    )

    private fun mapAnswer(rs: ResultSet, rowNum: Int): Answer = Answer(
        id = rs.getObject("id", UUID::class.java),
        attemptId = rs.getObject("attempt_id", UUID::class.java),
        questionId = rs.getObject("question_id", UUID::class.java),
        answerText = rs.getString("answer_text"),
        score = rs.getBigDecimal("score")?.toDouble(),
        criteriaResults = json.readCriteriaResults(rs.getString("criteria_results")),
        feedback = rs.getString("feedback"),
        flagged = rs.getBoolean("flagged"),
        flagNote = rs.getString("flag_note"),
    )
}

data class RecentAttempt(
    val attemptId: UUID,
    val conceptId: UUID,
    val conceptName: String,
    val submittedAt: Instant,
    val graded: Boolean,
    val score: Double?,
    val passed: Boolean?,
)

data class GradedAnswerRow(
    val criteriaResults: CriteriaResults,
    val rubric: com.mentalgym.domain.Rubric,
    val prompt: String,
)

data class FlaggedAnswerRow(
    val answerId: UUID,
    val position: Int,
    val prompt: String,
    val note: String?,
    val submittedAt: Instant,
)
