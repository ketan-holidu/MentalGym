package com.mentalgym

import com.mentalgym.config.MentalGymProperties
import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.Rubric
import com.mentalgym.domain.RubricCriterion
import com.mentalgym.rules.ReviewLadder
import com.mentalgym.rules.Scoring
import com.mentalgym.rules.Streak
import com.mentalgym.rules.Validation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals

class ScoringTest {

    private val rubric = Rubric(
        listOf(
            RubricCriterion("c1", "container resolves the dependency", 3.0, "core"),
            RubricCriterion("c2", "constructor injection makes it required", 2.0, "core"),
            RubricCriterion("c3", "effect on testability", 1.0, "bonus"),
        )
    )

    @Test
    fun `weights decide the question score`() {
        val score = Scoring.questionScore(rubric, mapOf("c1" to 1.0, "c2" to 0.5, "c3" to 0.0))
        assertEquals(0.6667, score)
    }

    @Test
    fun `full credit scores one`() {
        assertEquals(1.0, Scoring.questionScore(rubric, mapOf("c1" to 1.0, "c2" to 1.0, "c3" to 1.0)))
    }

    @Test
    fun `a missing criterion is an error`() {
        assertThrows<MentalGymException> { Scoring.questionScore(rubric, mapOf("c1" to 1.0, "c2" to 1.0)) }
    }

    @Test
    fun `the test score is the mean of the question scores`() {
        assertEquals(0.5, Scoring.testScore(listOf(1.0, 0.5, 0.0, 0.5)))
    }

    @Test
    fun `a test without questions cannot be scored`() {
        assertThrows<MentalGymException> { Scoring.testScore(emptyList()) }
    }
}

class ReviewLadderTest {

    private val ladder = ReviewLadder(MentalGymProperties(srsLadderDays = listOf(3, 7, 14, 30, 60, 120)))

    @Test
    fun `the first pass uses the first step`() {
        assertEquals(3, ladder.nextIntervalDays(null))
    }

    @Test
    fun `each further pass moves one step up`() {
        assertEquals(7, ladder.nextIntervalDays(3))
        assertEquals(14, ladder.nextIntervalDays(7))
        assertEquals(120, ladder.nextIntervalDays(60))
    }

    @Test
    fun `the last step is the cap`() {
        assertEquals(120, ladder.nextIntervalDays(120))
    }

    @Test
    fun `an empty ladder is rejected`() {
        assertThrows<MentalGymException> { ReviewLadder(MentalGymProperties(srsLadderDays = emptyList())) }
    }
}

class StreakTest {

    private val today = LocalDate.of(2026, 10, 15)

    @Test
    fun `consecutive days ending today count`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, Streak.current(days, today))
    }

    @Test
    fun `a streak ending yesterday still counts`() {
        val days = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, Streak.current(days, today))
    }

    @Test
    fun `a gap of two days ends the streak`() {
        val days = listOf(today.minusDays(2), today.minusDays(3))
        assertEquals(0, Streak.current(days, today))
    }

    @Test
    fun `the longest streak ignores where it sits`() {
        val days = listOf(
            today.minusDays(10), today.minusDays(9), today.minusDays(8), today.minusDays(7),
            today.minusDays(1), today,
        )
        assertEquals(4, Streak.longest(days))
        assertEquals(2, Streak.current(days, today))
    }

    @Test
    fun `no activity means no streak`() {
        assertEquals(0, Streak.current(emptyList(), today))
        assertEquals(0, Streak.longest(emptyList()))
    }
}

class ValidationTest {

    private fun criterion(id: String, weight: Double = 1.0, kind: String = "core") =
        RubricCriterion(id, "text for $id", weight, kind)

    @Test
    fun `a rubric needs three to five criteria`() {
        assertThrows<MentalGymException> {
            Validation.validateRubric(1, Rubric(listOf(criterion("c1"), criterion("c2"))))
        }
        Validation.validateRubric(1, Rubric(listOf(criterion("c1"), criterion("c2"), criterion("c3"))))
    }

    @Test
    fun `criterion ids must be unique`() {
        assertThrows<MentalGymException> {
            Validation.validateRubric(1, Rubric(listOf(criterion("c1"), criterion("c1"), criterion("c3"))))
        }
    }

    @Test
    fun `weights must be above zero`() {
        assertThrows<MentalGymException> {
            Validation.validateRubric(1, Rubric(listOf(criterion("c1", weight = 0.0), criterion("c2"), criterion("c3"))))
        }
    }

    @Test
    fun `an unknown criterion kind is rejected`() {
        assertThrows<MentalGymException> {
            Validation.validateRubric(1, Rubric(listOf(criterion("c1", kind = "critical"), criterion("c2"), criterion("c3"))))
        }
    }

    @Test
    fun `a test needs four to eight questions`() {
        assertThrows<MentalGymException> { Validation.validateQuestionCount(3) }
        assertThrows<MentalGymException> { Validation.validateQuestionCount(9) }
        Validation.validateQuestionCount(5)
    }

    @Test
    fun `credit stays between zero and one`() {
        assertThrows<MentalGymException> { Validation.validateCredit("c1", 1.5) }
        assertThrows<MentalGymException> { Validation.validateCredit("c1", -0.1) }
    }
}
