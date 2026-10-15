package com.mentalgym.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class StatsRepository(private val jdbcClient: JdbcClient) {

    fun totals(): Map<String, Int> = jdbcClient
        .sql(
            """
            select (select count(*) from topics) as topics,
                   (select count(*) from concepts) as concepts,
                   (select count(*) from docs) as docs,
                   (select count(*) from tests) as tests,
                   (select count(*) from attempts) as attempts,
                   (select count(*) from answers where flagged) as flagged_answers
            """
        )
        .query { rs, _ ->
            mapOf(
                "topics" to rs.getInt("topics"),
                "concepts" to rs.getInt("concepts"),
                "docs" to rs.getInt("docs"),
                "tests" to rs.getInt("tests"),
                "attempts" to rs.getInt("attempts"),
                "flaggedAnswers" to rs.getInt("flagged_answers"),
            )
        }
        .single()
}
