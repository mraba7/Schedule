package com.mrabah.oneuischedule.data

import android.content.Context
import org.json.JSONArray
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

data class SectionProgress(
    val taught: Int = 0,
    val last: String = "",
    val next: String = "",
)

/** A closure: no bells, no duties, no notifications. */
data class Holiday(val from: LocalDate, val to: LocalDate, val label: String) {
    fun covers(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)
}

data class Config(
    val bells: List<Bell>,              // the active timetable
    val altBells: List<Bell>,           // the other one, kept intact
    val activeTimetable: String,        // which of the two is live
    val week: Map<DayOfWeek, Map<Int, Duty>>,
    val sections: List<String>,
    val subject: String,
    val notes: Map<String, String>,     // "MONDAY#2" -> note
    val overrides: Map<String, Duty?>,  // "2026-09-07#2" -> duty, or null for cancelled
    val holidays: List<Holiday>,
    val notify: Boolean,
    val schoolBell: Boolean = true,
    val preAlert: Boolean = true,
    val liveUpdate: Boolean = true,
    val progress: Map<String, SectionProgress> = emptyMap(),
) {
    fun bell(period: Int): Bell = bells.first { it.period == period }
    val workdays: Set<DayOfWeek> get() = week.keys

    /** The recurring timetable, before overrides and holidays. */
    fun templateOn(day: DayOfWeek): Map<Int, Duty> = week[day].orEmpty()

    fun noteFor(day: DayOfWeek, period: Int): String = notes["${day.name}#$period"].orEmpty()

    fun holidayOn(date: LocalDate): Holiday? = holidays.firstOrNull { it.covers(date) }
}

object Defaults {

    const val SUBJECT = "أحياء 1-2"
    val SECTIONS = listOf("2/1", "2/2", "2/3", "2/4")

    const val SUMMER = "صيفي"
    const val WINTER = "شتوي"

    /** As given by the teacher. */
    val summerBells = listOf(
        Bell(1, LocalTime.of(7, 15), LocalTime.of(8, 5)),
        Bell(2, LocalTime.of(8, 5), LocalTime.of(8, 55)),
        Bell(3, LocalTime.of(8, 55), LocalTime.of(9, 40)),
        Bell(4, LocalTime.of(10, 10), LocalTime.of(10, 55)),
        Bell(5, LocalTime.of(10, 55), LocalTime.of(11, 40)),
        Bell(6, LocalTime.of(11, 40), LocalTime.of(12, 25)),
        Bell(7, LocalTime.of(12, 25), LocalTime.of(13, 5)),
    )

    /** As printed on the official aSc sheet — 40-minute periods. */
    val winterBells = listOf(
        Bell(1, LocalTime.of(7, 15), LocalTime.of(7, 55)),
        Bell(2, LocalTime.of(7, 55), LocalTime.of(8, 35)),
        Bell(3, LocalTime.of(8, 35), LocalTime.of(9, 15)),
        Bell(4, LocalTime.of(10, 15), LocalTime.of(10, 55)),
        Bell(5, LocalTime.of(10, 55), LocalTime.of(11, 35)),
        Bell(6, LocalTime.of(11, 35), LocalTime.of(12, 15)),
        Bell(7, LocalTime.of(12, 15), LocalTime.of(12, 50)),
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

    val config = Config(
        bells = summerBells,
        altBells = winterBells,
        activeTimetable = SUMMER,
        week = week,
        sections = SECTIONS,
        subject = SUBJECT,
        notes = emptyMap(),
        overrides = emptyMap(),
        holidays = emptyList(),
        notify = false,
    )
}

/* ══════════════════════════════════════════════════════════════
 *  STORAGE
 * ══════════════════════════════════════════════════════════════ */

object ScheduleStore {

    private const val FILE = "schedule_config"
    private const val KEY = "config_json"
    private const val STANDBY = "WAIT"
    private const val CANCELLED = "OFF"

    val DAYS = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    )

    fun load(context: Context): Config {
        val raw = prefs(context).getString(KEY, null) ?: return Defaults.config
        return try {
            parse(JSONObject(raw))
        } catch (t: Throwable) {
            Defaults.config
        }
    }

    fun save(context: Context, config: Config) {
        prefs(context).edit().putString(KEY, encode(config).toString()).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    fun exportJson(config: Config): String = encode(config).toString(2)

    fun importJson(text: String): Config? = try {
        parse(JSONObject(text))
    } catch (t: Throwable) {
        null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun bellsToJson(bells: List<Bell>) = JSONArray().apply {
        bells.forEach {
            put(JSONObject().put("p", it.period).put("s", it.start.toString()).put("e", it.end.toString()))
        }
    }

    private fun bellsFromJson(array: JSONArray?): List<Bell> {
        if (array == null) return Defaults.summerBells
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Bell(o.getInt("p"), LocalTime.parse(o.getString("s")), LocalTime.parse(o.getString("e")))
        }.sortedBy { it.period }
    }

    private fun dutyToText(duty: Duty?): String = when (duty) {
        null -> CANCELLED
        is Duty.Teach -> duty.section
        Duty.Standby -> STANDBY
    }

    private fun dutyFromText(text: String): Duty? = when (text) {
        CANCELLED -> null
        STANDBY -> Duty.Standby
        else -> Duty.Teach(text)
    }

    private fun encode(c: Config): JSONObject {
        val week = JSONObject()
        DAYS.forEach { day ->
            val duties = JSONObject()
            c.templateOn(day).forEach { (p, duty) -> duties.put(p.toString(), dutyToText(duty)) }
            week.put(day.name, duties)
        }

        val progress = JSONObject()
        c.progress.forEach { (section, p) ->
            progress.put(section, JSONObject().put("n", p.taught).put("last", p.last).put("next", p.next))
        }

        val notes = JSONObject()
        c.notes.forEach { (key, value) -> notes.put(key, value) }

        val overrides = JSONObject()
        c.overrides.forEach { (key, duty) -> overrides.put(key, dutyToText(duty)) }

        val holidays = JSONArray()
        c.holidays.forEach {
            holidays.put(
                JSONObject().put("from", it.from.toString()).put("to", it.to.toString())
                    .put("label", it.label)
            )
        }

        val sections = JSONArray()
        c.sections.forEach { sections.put(it) }

        return JSONObject()
            .put("bells", bellsToJson(c.bells))
            .put("altBells", bellsToJson(c.altBells))
            .put("activeTimetable", c.activeTimetable)
            .put("week", week)
            .put("sections", sections)
            .put("subject", c.subject)
            .put("notes", notes)
            .put("overrides", overrides)
            .put("holidays", holidays)
            .put("notify", c.notify)
            .put("schoolBell", c.schoolBell)
            .put("preAlert", c.preAlert)
            .put("liveUpdate", c.liveUpdate)
            .put("progress", progress)
    }

    private fun parse(json: JSONObject): Config {
        val weekJson = json.getJSONObject("week")
        val week = DAYS.associateWith { day ->
            val duties = weekJson.optJSONObject(day.name) ?: JSONObject()
            buildMap {
                duties.keys().forEach { key ->
                    dutyFromText(duties.getString(key))?.let { put(key.toInt(), it) }
                }
            }
        }

        val progressJson = json.optJSONObject("progress") ?: JSONObject()
        val progress = buildMap {
            progressJson.keys().forEach { section ->
                val o = progressJson.getJSONObject(section)
                put(section, SectionProgress(o.optInt("n"), o.optString("last"), o.optString("next")))
            }
        }

        val notesJson = json.optJSONObject("notes") ?: JSONObject()
        val notes = buildMap<String, String> {
            notesJson.keys().forEach { key -> put(key, notesJson.getString(key)) }
        }

        val overridesJson = json.optJSONObject("overrides") ?: JSONObject()
        val overrides = buildMap<String, Duty?> {
            overridesJson.keys().forEach { key -> put(key, dutyFromText(overridesJson.getString(key))) }
        }

        val holidaysJson = json.optJSONArray("holidays") ?: JSONArray()
        val holidays = (0 until holidaysJson.length()).mapNotNull { i ->
            try {
                val o = holidaysJson.getJSONObject(i)
                Holiday(
                    LocalDate.parse(o.getString("from")),
                    LocalDate.parse(o.getString("to")),
                    o.optString("label", "إجازة"),
                )
            } catch (t: Throwable) {
                null
            }
        }

        val sectionsJson = json.optJSONArray("sections")
        val sections = if (sectionsJson == null) Defaults.SECTIONS
        else (0 until sectionsJson.length()).map { sectionsJson.getString(it) }

        return Config(
            bells = bellsFromJson(json.optJSONArray("bells")),
            altBells = bellsFromJson(json.optJSONArray("altBells")),
            activeTimetable = json.optString("activeTimetable", Defaults.SUMMER),
            week = week,
            sections = sections.ifEmpty { Defaults.SECTIONS },
            subject = json.optString("subject", Defaults.SUBJECT),
            notes = notes,
            overrides = overrides,
            holidays = holidays,
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
    val note: String = "",
    val overridden: Boolean = false,
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
    val holiday: Holiday? = null,
    val config: Config = Defaults.config,
) {
    val remaining: Int get() = slots.count { it.state != SlotState.DONE }
    val focus: Slot? get() = live ?: next
}

object ScheduleEngine {

    /** The timetable for a real date: template, then overrides, then holidays. */
    fun dutiesOn(config: Config, date: LocalDate): Map<Int, Duty> {
        if (config.holidayOn(date) != null) return emptyMap()

        val result = config.templateOn(date.dayOfWeek).toMutableMap()
        val prefix = date.toString() + "#"
        config.overrides.forEach { (key, duty) ->
            if (key.startsWith(prefix)) {
                val period = key.removePrefix(prefix).toIntOrNull() ?: return@forEach
                if (duty == null) result.remove(period) else result[period] = duty
            }
        }
        return result
    }

    fun isOverridden(config: Config, date: LocalDate, period: Int) =
        config.overrides.containsKey("$date#$period")

    fun build(context: Context, now: LocalDateTime): ScheduleUi =
        build(ScheduleStore.load(context), now)

    fun build(config: Config, now: LocalDateTime): ScheduleUi {
        val today = now.toLocalDate()
        val lastEnd = dutiesOn(config, today).keys
            .mapNotNull { p -> config.bells.firstOrNull { it.period == p }?.end }
            .maxOrNull()

        val showToday = lastEnd != null && now.toLocalTime() < lastEnd
        // Rolling forward past a holiday must not lose the fact that today is
        // one — the app still needs to say why the day is empty.
        val todayHoliday = config.holidayOn(today)
        return if (showToday) forDay(config, today, now)
        else forDay(config, nextWorkday(config, today), null, todayHoliday)
    }

    fun nextRefresh(context: Context, now: LocalDateTime): LocalDateTime {
        val config = ScheduleStore.load(context)
        if (build(config, now).live != null) {
            return now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        }

        val nextBoundary = boundaries(config, now.toLocalDate())
            .filter { it.isAfter(now) }
            .minOrNull()
            ?: now.toLocalDate().plusDays(1).atTime(0, 1)

        val date = now.toLocalDate()
        if (dutiesOn(config, date).isNotEmpty()) {
            val closes = config.bells.maxOfOrNull { it.end }
            if (closes != null &&
                !now.toLocalTime().isBefore(Defaults.assemblyStart) &&
                now.toLocalTime().isBefore(closes)
            ) {
                return minOf(nextBoundary, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(5))
            }
        }
        return nextBoundary
    }

    fun boundaries(config: Config, date: LocalDate): List<LocalDateTime> = buildList {
        val duties = dutiesOn(config, date)
        if (duties.isNotEmpty()) {
            duties.keys.forEach { p ->
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
        while (dutiesOn(config, d).isEmpty() && guard++ < 21) d = d.plusDays(1)
        return d
    }

    private fun forDay(
        config: Config,
        date: LocalDate,
        now: LocalDateTime?,
        holidayOverride: Holiday? = null,
    ): ScheduleUi {
        val clock = now?.toLocalTime()

        val slots = dutiesOn(config, date)
            .mapNotNull { (period, duty) ->
                val bell = config.bells.firstOrNull { it.period == period } ?: return@mapNotNull null
                val state = when {
                    clock == null -> SlotState.AHEAD
                    !clock.isBefore(bell.end) -> SlotState.DONE
                    clock.isBefore(bell.start) -> SlotState.AHEAD
                    else -> SlotState.LIVE
                }
                Slot(
                    period = period,
                    bell = bell,
                    duty = duty,
                    state = state,
                    note = config.noteFor(date.dayOfWeek, period),
                    overridden = isOverridden(config, date, period),
                )
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
            holiday = holidayOverride ?: config.holidayOn(date),
            config = config,
        )
    }

    /* ── statistics for the load screen ─────────────────────── */

    data class Load(
        val teaching: Int,
        val standby: Int,
        val perSection: Map<String, Int>,
    )

    /** The recurring week, ignoring one-day changes. */
    fun weeklyLoad(config: Config): Load {
        var teaching = 0
        var standby = 0
        val per = mutableMapOf<String, Int>()
        ScheduleStore.DAYS.forEach { day ->
            config.templateOn(day).values.forEach { duty ->
                when (duty) {
                    is Duty.Teach -> {
                        teaching++
                        per[duty.section] = (per[duty.section] ?: 0) + 1
                    }
                    Duty.Standby -> standby++
                }
            }
        }
        return Load(teaching, standby, per)
    }
}
