package com.mrabah.oneuischedule.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mrabah.oneuischedule.MainActivity
import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.Duty
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.ScheduleStore
import com.mrabah.oneuischedule.data.ScheduleUi
import com.mrabah.oneuischedule.data.SlotState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/* ══════════════════════════════════════════════════════════════
 *  SHARED
 * ══════════════════════════════════════════════════════════════ */

internal suspend fun updateEveryWidget(context: Context) {
    ScheduleWidget().updateAll(context)
    NowWidget().updateAll(context)
    CountdownWidget().updateAll(context)
    WeekGridWidget().updateAll(context)
    TimelineWidget().updateAll(context)
    NextUpWidget().updateAll(context)
    SyllabusWidget().updateAll(context)
}

private fun clockOf(context: Context, locale: Locale) = DateTimeFormatter.ofPattern(
    if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale
)

private fun ltr(text: String) = "\u2066" + text + "\u2069"

private fun localeOf(context: Context): Locale {
    val locales = context.resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

/** A duty somewhere in the future, with the day it falls on. */
private data class Upcoming(
    val date: LocalDate,
    val period: Int,
    val start: LocalTime,
    val section: String?,
)

/** Walks forward across days, so Thursday afternoon shows Sunday morning. */
private fun upcoming(config: Config, now: LocalDateTime, count: Int): List<Upcoming> {
    val out = mutableListOf<Upcoming>()
    var date = now.toLocalDate()
    var guard = 0
    while (out.size < count && guard++ < 10) {
        config.dutiesOn(date.dayOfWeek).toSortedMap().forEach { (period, duty) ->
            val bell = config.bells.firstOrNull { it.period == period } ?: return@forEach
            val at = date.atTime(bell.start)
            if (at.isAfter(now) && out.size < count) {
                out += Upcoming(date, period, bell.start, (duty as? Duty.Teach)?.section)
            }
        }
        date = date.plusDays(1)
    }
    return out
}

@Composable
private fun Panel(g: Glass, padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(provider(g.panel))
            .cornerRadius(28.dp)
            .padding(padding.dp)
            .clickable(actionStartActivity<MainActivity>()),
        content = content,
    )
}

/* ══════════════════════════════════════════════════════════════
 *  1. NOW — one question: where am I going, and when
 * ══════════════════════════════════════════════════════════════ */

class NowWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locale = localeOf(context)
        val clock = clockOf(context, locale)
        val ui = ScheduleEngine.build(context, LocalDateTime.now(ZoneId.systemDefault()))
        val g = GlassPalette.of(context)
        val slot = ui.focus

        val section = slot?.section ?: if (slot == null) "—" else "انتظار"
        val accent = Sections.color(slot?.section, g.light)
        val line = when {
            slot == null -> if (ui.isToday) "انتهى الدوام" else "إجازة"
            ui.live != null -> "باقي ${ui.minutesLeftInLive} دقيقة"
            else -> ltr(slot.bell.start.format(clock))
        }
        val tag = if (slot != null) "الحصة ${slot.period}" else ""

        provideContent {
            GlanceTheme {
                Panel(g) {
                    Text(
                        text = tag,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = provider(g.inkMuted)),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = section,
                        style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = provider(accent)),
                    )
                    Text(
                        text = line,
                        style = TextStyle(fontSize = 13.sp, color = provider(g.inkMuted)),
                    )
                }
            }
        }
    }
}

class NowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowWidget()
}

/* ══════════════════════════════════════════════════════════════
 *  2. COUNTDOWN — the minutes, at a size you read across a room
 * ══════════════════════════════════════════════════════════════ */

class CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = ScheduleEngine.build(context, LocalDateTime.now(ZoneId.systemDefault()))
        val g = GlassPalette.of(context)
        val live = ui.live
        val accent = Sections.color((live ?: ui.next)?.section, g.light)

        val number = when {
            live != null -> "${ui.minutesLeftInLive}"
            ui.minutesUntilNext != null -> "${ui.minutesUntilNext}"
            else -> "—"
        }
        val caption = when {
            live != null -> "دقيقة على نهاية الحصة"
            ui.minutesUntilNext != null -> "دقيقة على الحصة القادمة"
            else -> "لا حصص"
        }
        val fraction = if (live != null) ui.progress else 0f

        provideContent {
            GlanceTheme {
                Panel(g) {
                    Text(
                        text = (ui.focus?.section ?: ""),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = provider(accent)),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = number,
                        style = TextStyle(fontSize = 60.sp, fontWeight = FontWeight.Bold, color = provider(g.ink)),
                    )
                    Text(
                        text = caption,
                        style = TextStyle(fontSize = 11.sp, color = provider(g.inkMuted)),
                    )
                    if (live != null) {
                        Spacer(GlanceModifier.height(8.dp))
                        Bar(g, accent, fraction)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(g: Glass, accent: Int, fraction: Float) {
    val width = LocalSize.current.width.value.toInt() - 28
    val done = (width * fraction).toInt().coerceIn(0, width)
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Spacer(
            GlanceModifier.width(done.dp).height(4.dp)
                .background(provider(accent)).cornerRadius(2.dp)
        )
        Spacer(
            GlanceModifier.defaultWeight().height(4.dp)
                .background(fade(g.ink, 0x26)).cornerRadius(2.dp)
        )
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

/* ══════════════════════════════════════════════════════════════
 *  3. WEEK GRID — the whole week as colour, no reading required
 * ══════════════════════════════════════════════════════════════ */

class WeekGridWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = ScheduleStore.load(context)
        val locale = localeOf(context)
        val g = GlassPalette.of(context)
        val today = LocalDate.now().dayOfWeek

        val days = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        )

        provideContent {
            GlanceTheme {
                Panel(g) {
                    Text(
                        text = "الأسبوع",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = provider(g.ink)),
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    days.forEach { day ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            Text(
                                text = day.getDisplayName(JavaTextStyle.SHORT, locale),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                    color = provider(if (day == today) g.ink else g.inkFaint),
                                ),
                                modifier = GlanceModifier.width(38.dp),
                            )
                            val duties = config.dutiesOn(day)
                            (1..7).forEach { period ->
                                val duty = duties[period]
                                val section = (duty as? Duty.Teach)?.section
                                Spacer(
                                    GlanceModifier
                                        .defaultWeight()
                                        .height(if (day == today) 20.dp else 16.dp)
                                        .padding(horizontal = 2.dp)
                                        .background(
                                            when {
                                                duty == null -> fade(g.ink, 0x14)
                                                section == null -> fade(g.ink, 0x4D)
                                                else -> fade(Sections.color(section, g.light), 0xE6)
                                            }
                                        )
                                        .cornerRadius(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class WeekGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekGridWidget()
}

/* ══════════════════════════════════════════════════════════════
 *  4. TIMELINE — the day drawn to scale, with now on it
 * ══════════════════════════════════════════════════════════════ */

class TimelineWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = ScheduleEngine.build(context, LocalDateTime.now(ZoneId.systemDefault()))
        val locale = localeOf(context)
        val clock = clockOf(context, locale)
        val g = GlassPalette.of(context)

        val open = Defaults.assemblyStart
        val close = ui.config.bells.maxOfOrNull { it.end } ?: open
        val span = (close.toSecondOfDay() - open.toSecondOfDay()).toFloat().coerceAtLeast(1f)
        fun at(t: LocalTime) = ((t.toSecondOfDay() - open.toSecondOfDay()) / span).coerceIn(0f, 1f)

        // build the strip as alternating gaps and blocks, proportional to real time
        data class Piece(val weight: Int, val color: Int?)
        val pieces = mutableListOf<Piece>()
        var cursor = 0f
        ui.slots.sortedBy { it.period }.forEach { slot ->
            val from = at(slot.bell.start)
            val to = at(slot.bell.end)
            if (from > cursor) pieces += Piece(((from - cursor) * 100).toInt(), null)
            pieces += Piece(((to - from) * 100).toInt().coerceAtLeast(2),
                Sections.color(slot.section, g.light))
            cursor = to
        }
        if (cursor < 1f) pieces += Piece(((1f - cursor) * 100).toInt(), null)

        val nowFraction = if (ui.isToday) at(LocalTime.now()) else 0f
        val width = 300

        provideContent {
            GlanceTheme {
                Panel(g) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            text = ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = provider(g.ink)),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            text = ltr(open.format(clock)) + " – " + ltr(close.format(clock)),
                            style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
                        )
                    }
                    Spacer(GlanceModifier.height(12.dp))
                    // the strip: width of each block is its real duration
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        pieces.take(10).forEach { piece ->
                            Spacer(
                                GlanceModifier
                                    .width((width * piece.weight / 100).coerceAtLeast(2).dp)
                                    .height(if (piece.color != null) 22.dp else 6.dp)
                                    .background(
                                        piece.color?.let { fade(it, 0xE6) } ?: fade(g.ink, 0x1A)
                                    )
                                    .cornerRadius(5.dp)
                            )
                        }
                    }
                    if (ui.isToday) {
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                            Spacer(GlanceModifier.width((width * nowFraction).toInt().dp))
                            Spacer(
                                GlanceModifier.width(2.dp).height(6.dp)
                                    .background(provider(g.ink)).cornerRadius(1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

class TimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimelineWidget()
}

/* ══════════════════════════════════════════════════════════════
 *  5. NEXT UP — the next three duties, across day boundaries
 * ══════════════════════════════════════════════════════════════ */

class NextUpWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = ScheduleStore.load(context)
        val locale = localeOf(context)
        val clock = clockOf(context, locale)
        val g = GlassPalette.of(context)
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val list = upcoming(config, now, 3)
        val today = now.toLocalDate()

        provideContent {
            GlanceTheme {
                Panel(g) {
                    Text(
                        text = "القادمة",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = provider(g.inkMuted)),
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    list.forEach { item ->
                        val when_ = when (item.date) {
                            today -> "اليوم"
                            today.plusDays(1) -> "غدًا"
                            else -> item.date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
                        }
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            Spacer(
                                GlanceModifier.width(3.dp).height(26.dp)
                                    .background(provider(Sections.color(item.section, g.light)))
                                    .cornerRadius(2.dp)
                            )
                            Spacer(GlanceModifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.section ?: "انتظار",
                                    style = TextStyle(
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = provider(Sections.color(item.section, g.light)),
                                    ),
                                )
                                Text(
                                    text = "$when_ · الحصة ${item.period}",
                                    style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
                                )
                            }
                            Spacer(GlanceModifier.defaultWeight())
                            Text(
                                text = ltr(item.start.format(clock)),
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = provider(g.ink)),
                            )
                        }
                    }
                }
            }
        }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

/* ══════════════════════════════════════════════════════════════
 *  6. SYLLABUS — which section has fallen behind
 * ══════════════════════════════════════════════════════════════ */

class SyllabusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val config = ScheduleStore.load(context)
        val g = GlassPalette.of(context)
        val lead = config.progress.values.maxOfOrNull { it.taught } ?: 0

        provideContent {
            GlanceTheme {
                Panel(g) {
                    Text(
                        text = "تقدّم المنهج",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = provider(g.inkMuted)),
                    )
                    Spacer(GlanceModifier.height(10.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Defaults.SECTIONS.forEach { section ->
                            val taught = config.progress[section]?.taught ?: 0
                            val behind = lead - taught
                            val color = Sections.color(section, g.light)
                            Column(
                                modifier = GlanceModifier.defaultWeight().padding(horizontal = 3.dp),
                                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                            ) {
                                Text(
                                    text = "$taught",
                                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = provider(color)),
                                )
                                Text(
                                    text = section,
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = provider(g.ink)),
                                )
                                Text(
                                    text = if (behind > 0) "متأخرة $behind" else "في المقدمة",
                                    style = TextStyle(fontSize = 9.sp, color = provider(g.inkFaint)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class SyllabusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SyllabusWidget()
}
