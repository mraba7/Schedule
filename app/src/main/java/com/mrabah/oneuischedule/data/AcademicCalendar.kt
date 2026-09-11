package com.mrabah.oneuischedule.data

import java.time.LocalDate

/** Dates transcribed from the user's 1448–1449 first-term calendar for the western cities.
 * Inclusive ranges: National Day 12–13/4, autumn 10–18/6, midyear 30/7–8/8.
 * Exam start is intentionally not a holiday. No annual recurrence is inferred.
 */
internal object AcademicCalendar {
    const val ID="western-first-term-1448-v1"
    val holidays=listOf(
        Holiday(LocalDate.of(2026,9,23),LocalDate.of(2026,9,24),"إجازة اليوم الوطني"),
        Holiday(LocalDate.of(2026,11,20),LocalDate.of(2026,11,28),"إجازة الخريف"),
        Holiday(LocalDate.of(2027,1,8),LocalDate.of(2027,1,16),"إجازة منتصف العام"),
    )
    fun apply(config:Config):Config {
        if(ID in config.appliedCalendars)return config
        val additions=holidays.filter { requested ->
            config.holidays.none { existing -> !existing.from.isAfter(requested.from) && !existing.to.isBefore(requested.to) }
        }
        return config.copy(holidays=(config.holidays+additions).sortedBy {it.from},appliedCalendars=config.appliedCalendars+ID)
    }
}
