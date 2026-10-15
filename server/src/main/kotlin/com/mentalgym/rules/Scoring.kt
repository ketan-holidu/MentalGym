package com.mentalgym.rules

import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.Rubric

object Scoring {

    fun questionScore(rubric: Rubric, creditByCriterion: Map<String, Double>): Double {
        val totalWeight = rubric.criteria.sumOf { it.weight }
        if (totalWeight <= 0.0) throw MentalGymException("Rubric has no positive weight")
        val earned = rubric.criteria.sumOf { criterion ->
            val credit = creditByCriterion[criterion.id]
                ?: throw MentalGymException("No credit given for criterion '${criterion.id}'")
            criterion.weight * credit
        }
        return round(earned / totalWeight)
    }

    fun testScore(questionScores: List<Double>): Double {
        if (questionScores.isEmpty()) throw MentalGymException("Cannot score a test without questions")
        return round(questionScores.average())
    }

    private fun round(value: Double): Double = Math.round(value * 10_000.0) / 10_000.0
}
