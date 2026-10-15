package com.mentalgym.rules

import com.mentalgym.config.MentalGymProperties
import com.mentalgym.domain.MentalGymException
import org.springframework.stereotype.Component

@Component
class ReviewLadder(properties: MentalGymProperties) {

    private val steps: List<Int> = properties.srsLadderDays.filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?: throw MentalGymException("SRS_LADDER_DAYS must contain at least one positive number of days")

    fun nextIntervalDays(currentIntervalDays: Int?): Int {
        if (currentIntervalDays == null) return steps.first()
        return steps.firstOrNull { it > currentIntervalDays } ?: steps.last()
    }
}
