package com.mentalgym.mcp

import com.mentalgym.domain.MentalGymException
import java.util.UUID

fun uuid(label: String, value: String): UUID = try {
    UUID.fromString(value.trim())
} catch (e: IllegalArgumentException) {
    throw MentalGymException("$label must be a uuid, got '$value'")
}

fun optionalUuid(label: String, value: String?): UUID? =
    value?.trim()?.takeIf { it.isNotEmpty() }?.let { uuid(label, it) }
