package com.mentalgym.rules

import java.time.LocalDate

object Streak {

    fun current(activityDays: Collection<LocalDate>, today: LocalDate): Int {
        val days = activityDays.toSet()
        val start = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var length = 0
        var cursor = start
        while (cursor in days) {
            length++
            cursor = cursor.minusDays(1)
        }
        return length
    }

    fun longest(activityDays: Collection<LocalDate>): Int {
        val sorted = activityDays.toSortedSet().toList()
        if (sorted.isEmpty()) return 0
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index - 1].plusDays(1) == sorted[index]) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }
}
