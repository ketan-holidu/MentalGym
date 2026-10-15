package com.mentalgym.rules

import com.mentalgym.domain.CriterionKind
import com.mentalgym.domain.MentalGymException
import com.mentalgym.domain.Rubric

object Validation {

    const val MIN_QUESTIONS = 4
    const val MAX_QUESTIONS = 8
    const val MIN_CRITERIA = 3
    const val MAX_CRITERIA = 5

    fun validateRubric(position: Int, rubric: Rubric) {
        val criteria = rubric.criteria
        if (criteria.size !in MIN_CRITERIA..MAX_CRITERIA) {
            throw MentalGymException(
                "Question $position: rubric needs $MIN_CRITERIA-$MAX_CRITERIA criteria, got ${criteria.size}"
            )
        }
        criteria.groupBy { it.id }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
            throw MentalGymException("Question $position: duplicate criterion ids ${it.joinToString()}")
        }
        criteria.forEach { criterion ->
            if (criterion.id.isBlank()) throw MentalGymException("Question $position: criterion id must not be blank")
            if (criterion.text.isBlank()) {
                throw MentalGymException("Question $position: criterion '${criterion.id}' has no text")
            }
            if (criterion.weight <= 0.0) {
                throw MentalGymException("Question $position: criterion '${criterion.id}' needs a weight above 0")
            }
            CriterionKind.of(criterion.kind)
        }
    }

    fun validateQuestionCount(count: Int) {
        if (count !in MIN_QUESTIONS..MAX_QUESTIONS) {
            throw MentalGymException("A test needs $MIN_QUESTIONS-$MAX_QUESTIONS questions, got $count")
        }
    }

    fun validateCredit(criterionId: String, credit: Double) {
        if (credit < 0.0 || credit > 1.0) {
            throw MentalGymException("Credit for criterion '$criterionId' must be between 0 and 1, got $credit")
        }
    }
}
