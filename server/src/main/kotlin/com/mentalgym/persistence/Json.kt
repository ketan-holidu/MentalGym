package com.mentalgym.persistence

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import com.mentalgym.domain.CriteriaResults
import com.mentalgym.domain.Rubric
import org.springframework.stereotype.Component

@Component
class Json(private val objectMapper: ObjectMapper) {

    fun write(value: Any): String = objectMapper.writeValueAsString(value)

    fun readRubric(value: String): Rubric = objectMapper.readValue(value)

    fun readCriteriaResults(value: String?): CriteriaResults? = value?.let { objectMapper.readValue(it) }

    fun readStrings(value: String): List<String> = objectMapper.readValue(value)
}
