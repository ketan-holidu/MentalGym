package com.mentalgym.persistence

import com.mentalgym.domain.Topic
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class TopicRepository(private val jdbcClient: JdbcClient) {

    fun findByName(name: String): Topic? = jdbcClient
        .sql("select id, name, created_at from topics where lower(name) = lower(:name)")
        .param("name", name)
        .query(::map)
        .optional()
        .orElse(null)

    fun findOrCreate(name: String): Topic = findByName(name) ?: insert(name)

    fun findAll(): List<Topic> = jdbcClient
        .sql("select id, name, created_at from topics order by name")
        .query(::map)
        .list()

    private fun insert(name: String): Topic {
        val id = UUID.randomUUID()
        jdbcClient.sql("insert into topics (id, name) values (:id, :name)")
            .param("id", id)
            .param("name", name)
            .update()
        return requireNotNull(findByName(name)) { "Topic '$name' disappeared right after insert" }
    }

    private fun map(rs: ResultSet, rowNum: Int): Topic = Topic(
        id = rs.getObject("id", UUID::class.java),
        name = rs.getString("name"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
