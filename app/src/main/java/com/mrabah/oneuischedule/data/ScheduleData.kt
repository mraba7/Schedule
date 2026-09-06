package com.mrabah.oneuischedule.data

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/* ══════════════════════════════════════════════════════════════
 *  MODEL
 * ══════════════════════════════════════════════════════════════ */

data class Bell(val period: Int, val start: LocalTime, val end: LocalTime)

sealed interface Duty {
    data class Teach(val section: String) : Duty
    data object Standby : Duty
}

/** How far each section has got through the syllabus. */
data class SectionProgress(
    val taught: Int = 0,
    val last: String = "",
    val next: String = "",
)

/** Everything the user can edit, in one immutable snapshot. */
data class Config(
    val bells: List<Bell>,
    val week: Map<DayOfWeek, Map<Int, Duty>>,
    val notify: Boolean,
    val schoolBell: Boolean = true,
    val preAlert: Boolean = true,
    val liveUpdate: Boolean = true,
    val progress: Map<String, SectionProgress> = emptyMap(),
) {
    fun bell(period: Int): Bell = bells.first { it.period == period }
    fun dutiesOn(day: DayOfWeek): Map<Int, Duty> = week[day].orEmpty()
    val workdays: Set<DayOfWeek> get() = week.keys
}

object Defaults {

    const val SUBJECT = "أحياء 1-2"
    val SECTIONS = listOf("2/1", "2/2", "2/3", "2/4")

    /** Summer timetable as given by the teacher. */
    val bells = listOf(
        Bell(1, LocalTime.of(7, 15), LocalTime.of(8, 5)),
        Bell(2, LocalTime.of(8, 5), LocalTime.of(8, 55)),
        Bell(3, LocalTime.of(8, 55), LocalTime.of(9, 40)),
        Bell(4, LocalTime.of(10, 10), LocalTime.of(10, 55)),
        Bell(5, LocalTime.of(10, 55), LocalTime.of(11, 40)),
        Bell(6, LocalTime.of(11, 40), LocalTime.of(12, 25)),
        Bell(7, LocalTime.of(12, 25), LocalTime.of(13, 5)),
    )

    val assemblyStart: LocalTime = LocalTime.of(7, 0)
    val assemblyEnd: LocalTime = LocalTime.of(7, 15)

    val week: Map<DayOfWeek, Map<Int, Duty>> = mapOf(
        DayOfWeek.SUNDAY to mapOf(
            1 to Duty.Teach("2/3"), 2 to Duty.Teach("2/2"),
            3 to Duty.Standby, 4 to Duty.Teach("2/4"),
        ),
        DayOfWeek.MONDAY to mapOf(
            2 to Duty.Teach("2/3"), 3 to Duty.Teach("2/4"),
            6 to Duty.Teach("2/1"), 7 to Duty.Teach("2/2"),
        ),
        DayOfWeek.TUESDAY to mapOf(
            2 to Duty.Teach("2/4"), 3 to Duty.Teach("2/2"), 4 to Duty.Teach("2/1"),
        ),
        DayOfWeek.WEDNESDAY to mapOf(
            1 to Duty.Teach("2/4"), 2 to Duty.Teach("2/3"),
            5 to Duty.Standby, 6 to Duty.Teach("2/1"),
        ),
        DayOfWeek.THURSDAY to mapOf(
            1 to Duty.Teach("2/2"), 2 to Duty.Teach("2/3"),
            5 to Duty.Standby, 6 to Duty.Teach("2/1"),
        ),
    )

    val config = Config(bells, week, notify = false)
}

/* ══════════════════════════════════════════════════════════════
 *  STORAGE — plain SharedPreferences + JSON, readable from the
 *  app, the widget and the alarm receiver alike.
 * ══════════════════════════════════════════════════════════════ */

object ScheduleStore {

    private const val FILE = "schedule_config"
    private const val KEY = "config_json"
    private const val STANDBY = "WAIT"

    private val DAYS = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    )

    fun load(context: Context): Config {
        val raw = prefs(context).getString(KEY, null) ?: return Defaults.config
        return try {
            parse(JSONObject(raw))
        } catch (t: Throwable) {
            Defaults.config // corrupt file must never brick the widget
        }
    }

    fun save(context: Context, config: Config) {
        prefs(context).edit().putString(KEY, encode(config).toString()).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun encode(c: Config): JSONObject {
        val bells = org.json.JSONArray()
        c.bells.forEach { b ->
            bells.put(
                JSONObject()
                    .put("p", b.period)
                    .put("s", b.start.toString())
                    .put("e", b.end.toString())
            )
        }
        val week = JSONObject()
        DAYS.forEach { day ->
            val duties = JSONObject()
            c.dutiesOn(day).forEach { (p, duty) ->
                duties.put(
                    p.toString(),
                    when (duty) {
                        is Duty.Teach -> duty.section
                        Duty.Standby -> STANDBY
                    }
                )
            }
            week.put(day.name, duties)
        }
        val progress = JSONObject()
        c.progress.forEach { (section, p) ->
            progress.put(
                section,
                JSONObject().put("n", p.taught).put("last", p.last).put("next", p.next)
            )
        }
        return JSONObject()
            .put("bells", bells)
            .put("week", week)
            .put("notify", c.notify)
            .put("schoolBell", c.schoolBell)
            .put("preAlert", c.preAlert)
            .put("liveUpdate", c.liveUpdate)
            .put("progress", progress)
    }

    private fun parse(json: JSONObject): Config {
        val bellArray = json.getJSONArray("bells")
        val bells = (0 until bellArray.length()).map { i ->
            val o = bellArray.getJSONObject(i)
            Bell(o.getInt("p"), LocalTime.parse(o.getString("s")), LocalTime.parse(o.getString("e")))
        }.sortedBy { it.period }

        val weekJson = json.getJSONObject("week")
        val week = DAYS.associateWith { day ->
            val duties = weekJson.optJSONObject(day.name) ?: JSONObject()
            buildMap {
                duties.keys().forEach { key ->
                    val value = duties.getString(key)
                    put(key.toInt(), if (value == STANDBY) Duty.Standby else Duty.Teach(value))
                }
            }
        }

        val progressJson = json.optJSONObject("progress") ?: JSONObject()
        val progress = buildMap {
            progressJson.keys().forEach { section ->
                val o = progressJson.getJSONObject(section)
                put(
                    section,
                    SectionProgress(
                        taught = o.optInt("n", 0),
                        last = o.optString("last", ""),
                        next = o.optString("next", ""),
                    )
                )
            }
        }

        return Config(
            bells = bells,
            week = week,
            notify = json.optBoolean("notify", false),
            schoolBell = json.optBoolean("schoolBell", true),
            preAlert = json.optBoolean("preAlert", true),
            liveUpdate = json.optBoolean("liveUpdate", true),
            progress = progress,
        )
    }
}

/* ══════════════════════════════════════════════════════════════
 *  STATE
 * ══════════════════════════════════════════════════════════════ */

enum class SlotState { DONE, LIVE, AHEAD }

data class Slot(
    val period: Int,
    val bell: Bell,
    val duty: Duty,
    val state: SlotState,
) {
    val isStandby: Boolean get() = duty is Duty.Standby
    val section: String? get() = (duty as? Duty.Teach)?.section
}

data class ScheduleUi(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val isToday: Boolean,
    val slots: List<Slot>,
    val live: Slot?,
    val next: Slot?,
    val progress: Float,
    val minutesLeftInLive: Long?,
    val minutesUntilNext: Long?,
    val isAssembly: Boolean = false,
    val config: Config = Defaults.config,
) {
    val remaining: Int get() = slots.count { it.state != SlotState.DONE }
    val focus: Slot? get() = live ?: next
}

object ScheduleEngine {

    fun build(context: Context, now: LocalDateTime): ScheduleUi =
        build(ScheduleStore.load(context), now)

    fun build(config: Config, now: LocalDateTime): ScheduleUi {
        val today = now.toLocalDate()
        val lastEnd = config.dutiesOn(today.dayOfWeek).keys
            .mapNotNull { p -> config.bells.firstOrNull { it.period == p }?.end }
            .maxOrNull()

        val showToday = today.dayOfWeek in config.workdays &&
            lastEnd != null && now.toLocalTime() < lastEnd

        return if (showToday) forDay(config, today, now)
        else forDay(config, nextWorkday(config, today), null)
    }

    /** The next instant the rendering changes: a bell, a minute tick, or midnight. */
    fun nextRefresh(context: Context, now: LocalDateTime): LocalDateTime {
        val config = ScheduleStore.load(context)
        if (build(config, now).live != null) {
            return now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        }
        val nextBoundary = boundaries(config, now.toLocalDate())
            .filter { it.isAfter(now) }
            .minOrNull()
            ?: now.toLocalDate().plusDays(1).atTime(0, 1)

        // inside school hours the card intensity and the day marker move
        // continuously, so tick every five minutes; outside them, never.
        val date = now.toLocalDate()
        if (date.dayOfWeek in config.workdays) {
            val opens = Defaults.assemblyStart
            val closes = config.bells.maxOfOrNull { it.end }
            if (closes != null &&
                !now.toLocalTime().isBefore(opens) && now.toLocalTime().isBefore(closes)
            ) {
                val tick = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(5)
                return minOf(nextBoundary, tick)
            }
        }
        return nextBoundary
    }

    /** Every bell of a given day, used by both the widget and the notifier. */
    fun boundaries(config: Config, date: LocalDate): List<LocalDateTime> = buildList {
        if (date.dayOfWeek in config.workdays) {
            config.dutiesOn(date.dayOfWeek).keys.forEach { p ->
                config.bells.firstOrNull { it.period == p }?.let {
                    add(date.atTime(it.start))
                    add(date.atTime(it.end))
                }
            }
            add(date.atTime(Defaults.assemblyStart))
            add(date.atTime(Defaults.assemblyEnd))
        }
        add(date.plusDays(1).atTime(0, 1))
    }

    fun nextWorkday(config: Config, from: LocalDate): LocalDate {
        var d = from.plusDays(1)
        var guard = 0
        while (d.dayOfWeek !in config.workdays && guard++ < 14) d = d.plusDays(1)
        return d
    }

    private fun forDay(config: Config, date: LocalDate, now: LocalDateTime?): ScheduleUi {
        val clock = now?.toLocalTime()

        val slots = config.dutiesOn(date.dayOfWeek)
            .mapNotNull { (period, duty) ->
                val bell = config.bells.firstOrNull { it.period == period } ?: return@mapNotNull null
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
            if (total <= 0f) 0f else (done / total).coerceIn(0f, 1f)
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
                !clock.isBefore(Defaults.assemblyStart) && clock.isBefore(Defaults.assemblyEnd),
            config = config,
        )
    }
}
