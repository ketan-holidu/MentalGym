package com.mentalgym.persistence

import com.mentalgym.domain.Concept
import com.mentalgym.domain.ConceptStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

private const val SELECT = """
    select c.id, c.topic_id, t.name as topic_name, c.name, c.status, c.mastered_at,
           c.next_review_at, c.review_interval_days, c.review_count, c.created_at
    from concepts c
    join topics t on t.id = c.topic_id
"""

@Repository
class ConceptRepository(private val jdbcClient: JdbcClient) {

    fun insert(topicId: UUID, name: String): Concept {
        val id = UUID.randomUUID()
        jdbcClient.sql("insert into concepts (id, topic_id, name) values (:id, :topicId, :name)")
            .param("id", id)
            .param("topicId", topicId)
            .param("name", name)
            .update()
        return requireNotNull(findById(id)) { "Concept '$name' disappeared right after insert" }
    }

    fun findById(id: UUID): Concept? = jdbcClient
        .sql("$SELECT where c.id = :id")
        .param("id", id)
        .query(::map)
        .optional()
        .orElse(null)

    fun findByTopicAndName(topicId: UUID, name: String): Concept? = jdbcClient
        .sql("$SELECT where c.topic_id = :topicId and lower(c.name) = lower(:name)")
        .param("topicId", topicId)
        .param("name", name)
        .query(::map)
        .optional()
        .orElse(null)

    fun findByName(name: String): List<Concept> = jdbcClient
        .sql("$SELECT where lower(c.name) = lower(:name)")
        .param("name", name)
        .query(::map)
        .list()

    fun list(topic: String?, status: ConceptStatus?, dueOnly: Boolean): List<Concept> {
        val conditions = buildList {
            if (topic != null) add("lower(t.name) = lower(:topic)")
            if (status != null) add("c.status = :status")
            if (dueOnly) add("c.status = 'mastered' and c.next_review_at <= now()")
        }
        val where = if (conditions.isEmpty()) "" else "where ${conditions.joinToString(" and ")}"
        var spec = jdbcClient.sql("$SELECT $where order by t.name, c.name")
        if (topic != null) spec = spec.param("topic", topic)
        if (status != null) spec = spec.param("status", status.wire)
        return spec.query(::map).list()
    }

    fun updateStatus(id: UUID, status: ConceptStatus) {
        jdbcClient.sql("update concepts set status = :status where id = :id")
            .param("status", status.wire)
            .param("id", id)
            .update()
    }

    fun applyPass(id: UUID, intervalDays: Int, nextReviewAt: Instant) {
        jdbcClient.sql(
            """
            update concepts
               set status = 'mastered',
                   mastered_at = coalesce(mastered_at, now()),
                   review_count = review_count + 1,
                   review_interval_days = :interval,
                   next_review_at = :nextReviewAt
             where id = :id
            """
        )
            .param("interval", intervalDays)
            .param("nextReviewAt", java.sql.Timestamp.from(nextReviewAt))
            .param("id", id)
            .update()
    }

    fun applyFail(id: UUID) {
        jdbcClient.sql(
            """
            update concepts
               set status = 'ready_to_test',
                   review_interval_days = null,
                   next_review_at = null
             where id = :id
            """
        )
            .param("id", id)
            .update()
    }

    fun countsByStatus(): Map<String, Int> = jdbcClient
        .sql("select status, count(*) as total from concepts group by status")
        .query { rs, _ -> rs.getString("status") to rs.getInt("total") }
        .list()
        .toMap()

    private fun map(rs: ResultSet, rowNum: Int): Concept = Concept(
        id = rs.getObject("id", UUID::class.java),
        topicId = rs.getObject("topic_id", UUID::class.java),
        topicName = rs.getString("topic_name"),
        name = rs.getString("name"),
        status = ConceptStatus.of(rs.getString("status")),
        masteredAt = rs.getTimestamp("mastered_at")?.toInstant(),
        nextReviewAt = rs.getTimestamp("next_review_at")?.toInstant(),
        reviewIntervalDays = rs.getObject("review_interval_days") as Int?,
        reviewCount = rs.getInt("review_count"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
