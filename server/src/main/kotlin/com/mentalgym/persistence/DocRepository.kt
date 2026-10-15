package com.mentalgym.persistence

import com.mentalgym.domain.Doc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Repository
class DocRepository(private val jdbcClient: JdbcClient) {

    fun insert(conceptId: UUID, url: String, title: String?, content: String): Doc {
        val id = UUID.randomUUID()
        jdbcClient.sql(
            "insert into docs (id, concept_id, url, title, content) values (:id, :conceptId, :url, :title, :content)"
        )
            .param("id", id)
            .param("conceptId", conceptId)
            .param("url", url)
            .param("title", title)
            .param("content", content)
            .update()
        return requireNotNull(findById(id)) { "Doc $url disappeared right after insert" }
    }

    fun findById(id: UUID): Doc? = jdbcClient
        .sql("select * from docs where id = :id")
        .param("id", id)
        .query(::map)
        .optional()
        .orElse(null)

    fun findByConcept(conceptId: UUID): List<Doc> = jdbcClient
        .sql("select * from docs where concept_id = :conceptId order by fetched_at, url")
        .param("conceptId", conceptId)
        .query(::map)
        .list()

    fun deleteByConcept(conceptId: UUID): Int = jdbcClient
        .sql("delete from docs where concept_id = :conceptId")
        .param("conceptId", conceptId)
        .update()

    fun markAllRead(conceptId: UUID): Int = jdbcClient
        .sql("update docs set read_at = now() where concept_id = :conceptId and read_at is null")
        .param("conceptId", conceptId)
        .update()

    fun readDates(zone: ZoneId): List<LocalDate> = jdbcClient
        .sql("select distinct (read_at at time zone :zone)::date as day from docs where read_at is not null")
        .param("zone", zone.id)
        .query { rs, _ -> rs.getDate("day").toLocalDate() }
        .list()

    private fun map(rs: ResultSet, rowNum: Int): Doc = Doc(
        id = rs.getObject("id", UUID::class.java),
        conceptId = rs.getObject("concept_id", UUID::class.java),
        url = rs.getString("url"),
        title = rs.getString("title"),
        content = rs.getString("content"),
        fetchedAt = rs.getTimestamp("fetched_at").toInstant(),
        readAt = rs.getTimestamp("read_at")?.toInstant(),
    )
}
