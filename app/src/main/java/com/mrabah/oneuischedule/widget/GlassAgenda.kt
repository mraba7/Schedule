package com.mrabah.oneuischedule.widget

import java.time.LocalTime

/** Timetable rows after the focused lesson; adjacent free bells are combined. */
internal data class GlassRow(val periods: String, val label: String, val start: LocalTime,
    val end: LocalTime, val section: String? = null, val free: Boolean = false)

internal object GlassAgenda {
    fun color(section: String?): String = when(section) {
        "2/1" -> "#88AACC"
        "2/2" -> "#B0A0C6"
        "2/3" -> "#88B8AE"
        "2/4" -> "#CBA080"
        null -> "#A7B5C1"
        else -> listOf("#88AACC", "#B0A0C6", "#88B8AE", "#CBA080", "#BAA3AD", "#ACB78F")[(section.hashCode() and Int.MAX_VALUE) % 6]
    }
    fun rows(day: DesignDay): List<GlassRow> {
        val focus=day.focus ?: return emptyList()
        val last=day.ui.slots.lastOrNull()?.period ?: return emptyList()
        val rows=mutableListOf<GlassRow>()
        var previousEnd=focus.bell.end
        day.ui.config.bells.sortedBy { it.period }.filter { it.period>focus.period && it.period<=last }.forEach { bell ->
            if(bell.start>previousEnd) {
                val isBreak=day.breakRange?.let { it.first==previousEnd && it.second==bell.start }==true
                rows.add(GlassRow("—",if(isBreak) "فسحة" else "استراحة",previousEnd,bell.start))
            }
            val slot=day.ui.slots.firstOrNull { it.period==bell.period }
            val previous=rows.lastOrNull()
            if(slot==null && previous?.free==true && previous.end==bell.start) {
                rows[rows.lastIndex]=previous.copy(periods="${previous.periods.substringBefore("–")}–${bell.period}",end=bell.end)
            } else {
                rows.add(GlassRow(bell.period.toString(),slot?.let(DesignDay::section) ?: "وقت متاح",
                    bell.start,bell.end,slot?.section,slot==null))
            }
            previousEnd=bell.end
        }
        return rows
    }
}
