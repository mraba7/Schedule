package com.mrabah.oneuischedule.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/* ═══════════════════════════════════════════════════════════════
 *  EDIT ZONE 1 — bell times (Al-Ansar, SUMMER timetable)
 *  Arrival 06:45 · assembly 07:00–07:15 · break 09:40–10:10.
 *  Period lengths are NOT uniform: P1–P2 are 50 min, P3–P6 are 45,
 *  P7 is 40 — the progress bar reads each period's own length.
 *  Duplicate this object as WinterBellTimes when the winter
 *  timetable starts, and switch the `active` reference below.
 * ═══════════════════════════════════════════════════════════════ */

data class Bell(val period: Int, val start: LocalTime, val end: LocalTime)

object BellTimes {

    /** Roll call / arrival, before the assembly line-up. */
    val arrival: LocalTime = LocalTime.of(6, 45)

    /** الطابور — assembly. Shown as a pre-school state, not a period. */
    val assemblyStart: LocalTime = LocalTime.of(7, 0)
    val assemblyEnd: LocalTime = LocalTime.of(7, 15)

    val all: List<Bell> = listOf(
        Bell(1, LocalTime.of(7, 15),  LocalTime.of(8, 5)),   // 50 min
        Bell(2, LocalTime.of(8, 5),   LocalTime.of(8, 55)),  // 50 min
        Bell(3, LocalTime.of(8, 55),  LocalTime.of(9, 40)),  // 45 min
        // ── الفسحة 09:40 – 10:10 ──
        Bell(4, LocalTime.of(10, 10), LocalTime.of(10, 55)), // 45 min
        Bell(5, LocalTime.of(10, 55), LocalTime.of(11, 40)), // 45 min
        Bell(6, LocalTime.of(11, 40), LocalTime.of(12, 25)), // 45 min
        Bell(7, LocalTime.of(12, 25), LocalTime.of(13, 5)),  // 40 min
    )

    fun of(period: Int): Bell = all.first { it.period == period }
}

/* ═══════════════════════════════════════════════════════════════
 *  EDIT ZONE 2 — the weekly duty table
 * ═══════════════════════════════════════════════════════════════ */

/** What the teacher is assigned to in a given period. */
sealed interface Duty {
    /** A taught lesson: subject + class section, e.g. Biology 1-2 / 2/3. */
    data class Teach(val subject: String, val section: String) : Duty

    /** حصة انتظار — standby / substitute duty. On campus, not teaching. */
    data object Standby : Duty
}

object Schedule {

    const val TEACHER = "Mohammed Rabah Al-Harbi"
    const val SCHOOL = "Al-Ansar High School"

    private const val BIO = "أحياء 1-2"
    private fun bio(section: String) = Duty.Teach(BIO, section)

    /** Sunday–Thursday. Periods not listed are genuinely off the timetable. */
    val week: Map<DayOfWeek, Map<Int, Duty>> = mapOf(
        DayOfWeek.SUNDAY to mapOf(
            1 to bio("2/3"),
            2 to bio("2/2"),
            3 to Duty.Standby,
            4 to bio("2/4"),
        ),
        DayOfWeek.MONDAY to mapOf(
            1 to bio("2/3"),
            2 to bio("2/4"),
            6 to bio("2/1"),
            7 to bio("2/2"),
        ),
        DayOfWeek.TUESDAY to mapOf(
            1 to bio("2/4"),
            2 to bio("2/2"),
            3 to bio("2/1"),
        ),
        DayOfWeek.WEDNESDAY to mapOf(
            1 to bio("2/4"),
            2 to bio("2/3"),
            5 to Duty.Standby,
            6 to bio("2/1"),
        ),
        DayOfWeek.THURSDAY to mapOf(
            1 to bio("2/2"),
            2 to bio("2/3"),
            5 to Duty.Standby,
            6 to bio("2/1"),
        ),
    )

    val workdays: Set<DayOfWeek> = week.keys

    fun dutiesOn(day: DayOfWeek): Map<Int, Duty> = week[day].orEmpty()
}

/* ═══════════════════════════════════════════════════════════════
 *  STATE — pure functions, no Android dependencies (unit-testable)
 * ═══════════════════════════════════════════════════════════════ */

enum class SlotState { DONE, LIVE, AHEAD }

data class Slot(
    val period: Int,
    val bell: Bell,
    val duty: Duty,
    val state: SlotState,
) {
    val isStandby: Boolean get() = duty is Duty.Standby
    val section: String? get() = (duty as? Duty.Teach)?.section
    val subject: String? get() = (duty as? Duty.Teach)?.subject
}

data class ScheduleUi(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    /** false when the widget is previewing the next workday (Fri/Sat, or after school). */
    val isToday: Boolean,
    val slots: List<Slot>,
    val live: Slot?,
    val next: Slot?,
    /** 0f..1f through the live period; 0f when nothing is running. */
    val progress: Float,
    val minutesLeftInLive: Long?,
    val minutesUntilNext: Long?,
    /** True between 07:00 and 07:15 on a workday — الطابور. */
    val isAssembly: Boolean = false,
) {
    val remaining: Int get() = slots.count { it.state != SlotState.DONE }
    val dayFinished: Boolean get() = isToday && live == null && next == null
    /** The card the hero should render. Null means "nothing left today". */
    val focus: Slot? get() = live ?: next
}

object ScheduleEngine {

    /** Builds everything the widget needs from a single clock reading. */
    fun build(now: LocalDateTime): ScheduleUi {
        val today = now.toLocalDate()
        val todayIsWorkday = today.dayOfWeek in Schedule.workdays
        val lastEndToday = Schedule.dutiesOn(today.dayOfWeek).keys
            .maxOfOrNull { BellTimes.of(it).end }

        val showToday = todayIsWorkday &&
            lastEndToday != null &&
            now.toLocalTime() < lastEndToday

        return if (showToday) forDay(today, now) else forDay(nextWorkday(today), null)
    }

    /**
     * The next moment the rendering would change: a bell, midnight, or the next
     * minute while a period is running. Used to schedule the refresh alarm.
     */
    fun nextRefresh(now: LocalDateTime): LocalDateTime {
        val ui = build(now)
        if (ui.live != null) return now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)

        val today = now.toLocalDate()
        val marks = buildList {
            Schedule.dutiesOn(today.dayOfWeek).keys.map(BellTimes::of).forEach {
                add(today.atTime(it.start))
                add(today.atTime(it.end))
            }
            if (today.dayOfWeek in Schedule.workdays) {
                add(today.atTime(BellTimes.assemblyStart))
                add(today.atTime(BellTimes.assemblyEnd))
            }
            add(today.plusDays(1).atTime(0, 1)) // roll the date header over
        }
        return marks.filter { it.isAfter(now) }.minOrNull()
            ?: now.plusMinutes(30)
    }

    private fun nextWorkday(from: LocalDate): LocalDate {
        var d = from.plusDays(1)
        while (d.dayOfWeek !in Schedule.workdays) d = d.plusDays(1)
        return d
    }

    /** @param now non-null only when [date] is today. */
    private fun forDay(date: LocalDate, now: LocalDateTime?): ScheduleUi {
        val clock = now?.toLocalTime()

        val slots = Schedule.dutiesOn(date.dayOfWeek)
            .map { (period, duty) ->
                val bell = BellTimes.of(period)
                val state = when {
                    clock == null -> SlotState.AHEAD
                    !clock.isBefore(bell.end) -> SlotState.DONE
                    clock.isBefore(bell.start) -> SlotState.AHEAD
                    else -> SlotState.LIVE
                }
                Slot(period, bell, duty, state)
            }
            .sortedBy { it.period }

        val live = slots.firstOrNull { it.state == SlotState.LIVE }
        val next = slots.firstOrNull { it.state == SlotState.AHEAD }

        val progress = if (live != null && clock != null) {
            val total = ChronoUnit.SECONDS.between(live.bell.start, live.bell.end).toFloat()
            val done = ChronoUnit.SECONDS.between(live.bell.start, clock).toFloat()
            (done / total).coerceIn(0f, 1f)
        } else 0f

        return ScheduleUi(
            date = date,
            dayOfWeek = date.dayOfWeek,
            isToday = now != null,
            slots = slots,
            live = live,
            next = next,
            progress = progress,
            minutesLeftInLive = if (live != null && clock != null)
                ChronoUnit.MINUTES.between(clock, live.bell.end).coerceAtLeast(0) else null,
            minutesUntilNext = if (next != null && clock != null)
                ChronoUnit.MINUTES.between(clock, next.bell.start).coerceAtLeast(0) else null,
            isAssembly = clock != null &&
                !clock.isBefore(BellTimes.assemblyStart) &&
                clock.isBefore(BellTimes.assemblyEnd),
        )
    }
}
