package com.mrabah.oneuischedule.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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
import androidx.glance.unit.ColorProvider
import com.mrabah.oneuischedule.MainActivity
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.SlotState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Three visual treatments for the same day data in the Android widget picker. */
internal enum class DailyStyle { LUXURY, CARDS, COMPACT }

private data class StyleRow(
    val period: Int,
    val section: String,
    val start: String,
    val end: String,
    val state: SlotState,
)

private data class StyleDay(
    val day: String,
    val date: String,
    val remaining: String,
    val currentPeriod: String,
    val currentSection: String,
    val currentSubject: String,
    val currentRange: String,
    val currentStatus: String,
    val minutes: String,
    val isLive: Boolean,
    val segments: List<Pair<Int, Int>>,
    val rows: List<StyleRow>,
    val next: StyleRow?,
    val footer: String,
)

private object StyleDays {
    fun build(context: Context): StyleDay {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val ui = ScheduleEngine.build(context, now)
        val locale = context.resources.configuration.locales.let {
            if (it.isEmpty) Locale.getDefault() else it[0]
        }
        val clock = DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm",
            locale,
        )
        fun time(value: LocalTime) = "\u2066${value.format(clock)}\u2069"

        val focus = ui.focus
        val rows = ui.slots.map { slot ->
            StyleRow(
                period = slot.period,
                section = slot.section ?: "انتظار",
                start = time(slot.bell.start),
                end = time(slot.bell.end),
                state = slot.state,
            )
        }
        val byPeriod = ui.slots.associateBy { it.period }
        val segments = (1..7).map { period ->
            val slot = byPeriod[period]
            when {
                slot == null -> 0xFFFFFFFF.toInt() to 0x1A
                slot.state == SlotState.DONE -> Sections.color(slot.section, false) to 0x72
                slot.state == SlotState.LIVE -> Sections.color(slot.section, false) to 0xFF
                else -> Sections.color(slot.section, false) to 0x44
            }
        }
        val dayLabel = if (!ui.isToday && ui.date == LocalDate.now().plusDays(1)) {
            "غدًا · ${ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)}"
        } else {
            ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
        }
        val dateLabel = ui.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        val range = focus?.let { "${time(it.bell.start)} – ${time(it.bell.end)}" }.orEmpty()
        val currentStatus = when {
            focus == null -> if (ui.isToday) "انتهى دوامك" else "لا توجد حصص"
            ui.live != null -> "الحصة الحالية"
            !ui.isToday -> "أول حصة"
            else -> "الحصة القادمة"
        }
        val next = rows.firstOrNull { it.state == SlotState.AHEAD && it.period != focus?.period }

        return StyleDay(
            day = dayLabel,
            date = dateLabel,
            remaining = if (ui.remaining > 0) "${ui.remaining} حصص متبقية" else "اكتملت الحصص",
            currentPeriod = focus?.let { "الحصة ${it.period}" }.orEmpty(),
            currentSection = focus?.section ?: if (focus == null) "—" else "انتظار",
            currentSubject = focus?.section?.let { ui.config.progress[it]?.next }
                ?.takeIf { it.isNotBlank() }
                ?: if (focus?.section != null) ui.config.subject else "",
            currentRange = range,
            currentStatus = currentStatus,
            minutes = ui.minutesLeftInLive?.toString().orEmpty(),
            isLive = ui.live != null,
            segments = segments,
            rows = rows,
            next = next,
            footer = ui.slots.lastOrNull()?.let {
                "ينتهي دوامك ${time(it.bell.end)}"
            }.orEmpty(),
        )
    }
}

private val StyleMedium = DpSize(260.dp, 190.dp)
private val StyleLarge = DpSize(300.dp, 300.dp)

internal abstract class DailyStyleWidget(
    private val style: DailyStyle,
) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        if (style == DailyStyle.COMPACT) {
            setOf(DpSize(260.dp, 120.dp), StyleMedium)
        } else {
            setOf(StyleMedium, StyleLarge)
        }
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val day = StyleDays.build(context)
        provideContent {
            GlanceTheme {
                when (style) {
                    DailyStyle.LUXURY -> LuxuryLayout(day)
                    DailyStyle.CARDS -> CardsLayout(day)
                    DailyStyle.COMPACT -> CompactLayout(day)
                }
            }
        }
    }
}

/** Deep violet glass, a restrained gold focus, and cyan upcoming rails. */
internal class LuxuryScheduleWidget : DailyStyleWidget(DailyStyle.LUXURY)

/** A bright editorial list: every class is a card and the current one leads. */
internal class CardsScheduleWidget : DailyStyleWidget(DailyStyle.CARDS)

/** A dense 4x2 glance: current class, countdown/time, and the next class only. */
internal class CompactScheduleWidget : DailyStyleWidget(DailyStyle.COMPACT)

private val violetPanel = 0xE51A1238.toInt()
private val violetCard = 0x4DFFFFFF
private val violetRow = 0x24FFFFFF
private val white = 0xFFFFFFFF.toInt()
private val whiteMuted = 0xBFFFFFFF.toInt()
private val whiteFaint = 0x80FFFFFF.toInt()
private val gold = 0xFFFFD166.toInt()
private val cyan = 0xFF55E6D1.toInt()
private val paper = 0xF5F7F4EF.toInt()
private val paperInk = 0xFF17181C.toInt()
private val paperMuted = 0xFF656970.toInt()
private val paperRow = 0x12000000
private val graphite = 0xED101116.toInt()

private fun color(argb: Int) = ColorProvider(ComposeColor(argb))

@Composable
private fun LuxuryLayout(day: StyleDay) {
    val large = LocalSize.current.height >= StyleLarge.height
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(color(violetPanel))
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        HeaderLine(day, white, whiteMuted)
        Spacer(GlanceModifier.height(10.dp))
        SegmentBar(day.segments)
        Spacer(GlanceModifier.height(12.dp))

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(color(violetCard))
                .cornerRadius(22.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column {
                Text(
                    text = day.currentPeriod,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color(gold)),
                    modifier = GlanceModifier
                        .background(color(0x26FFD166))
                        .cornerRadius(8.dp)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(GlanceModifier.height(7.dp))
                Text(
                    text = "الفصل ${day.currentSection}",
                    style = TextStyle(fontSize = if (large) 30.sp else 25.sp, fontWeight = FontWeight.Bold, color = color(gold)),
                )
                if (day.currentSubject.isNotEmpty()) {
                    Text(day.currentSubject, style = TextStyle(fontSize = 11.sp, color = color(whiteMuted)))
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(day.currentStatus, style = TextStyle(fontSize = 10.sp, color = color(whiteFaint)))
                Text(
                    text = if (day.isLive && day.minutes.isNotEmpty()) day.minutes else day.currentRange,
                    style = TextStyle(
                        fontSize = if (day.isLive) 27.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = color(white),
                    ),
                )
                if (day.isLive) {
                    Text("دقيقة متبقية", style = TextStyle(fontSize = 10.sp, color = color(whiteMuted)))
                }
            }
        }

        val upcoming = day.rows.filter { it.state == SlotState.AHEAD && "الحصة ${it.period}" != day.currentPeriod }
        val visible = if (large) upcoming.take(4) else upcoming.take(2)
        if (visible.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            visible.forEach { row ->
                LuxuryRow(row)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
        if (large && day.footer.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Text(day.footer, style = TextStyle(fontSize = 10.sp, color = color(whiteFaint)))
        }
    }
}

@Composable
private fun HeaderLine(day: StyleDay, ink: Int, muted: Int) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Column {
            Text(day.day, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color(ink)))
            Text(day.date, style = TextStyle(fontSize = 10.sp, color = color(muted)))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(day.remaining, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color(muted)))
    }
}

@Composable
private fun SegmentBar(segments: List<Pair<Int, Int>>) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        segments.forEach { (segmentColor, alpha) ->
            Spacer(
                GlanceModifier
                    .defaultWeight()
                    .height(5.dp)
                    .padding(horizontal = 2.dp)
                    .background(fade(segmentColor, alpha))
                    .cornerRadius(3.dp)
            )
        }
    }
}

@Composable
private fun LuxuryRow(row: StyleRow) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(color(violetRow))
            .cornerRadius(15.dp)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Spacer(GlanceModifier.width(3.dp).height(24.dp).background(color(cyan)).cornerRadius(2.dp))
        Spacer(GlanceModifier.width(9.dp))
        Text("الحصة ${row.period}", style = TextStyle(fontSize = 11.sp, color = color(whiteMuted)))
        Spacer(GlanceModifier.width(10.dp))
        Text("الفصل ${row.section}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color(cyan)))
        Spacer(GlanceModifier.defaultWeight())
        Text(row.start, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color(white)))
    }
}

@Composable
private fun CardsLayout(day: StyleDay) {
    val height = LocalSize.current.height
    val visibleCount = if (height >= StyleLarge.height) 5 else 3
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(color(paper))
            .cornerRadius(28.dp)
            .padding(15.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        HeaderLine(day, paperInk, paperMuted)
        Spacer(GlanceModifier.height(11.dp))
        day.rows.take(visibleCount).forEach { row ->
            val focused = "الحصة ${row.period}" == day.currentPeriod
            val rowAccent = Sections.color(row.section, true)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(color(if (focused) 0x1F000000 else paperRow))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 11.dp, vertical = if (focused) 11.dp else 9.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Spacer(
                    GlanceModifier.width(if (focused) 5.dp else 3.dp).height(if (focused) 32.dp else 25.dp)
                        .background(color(rowAccent)).cornerRadius(3.dp)
                )
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(
                        text = row.section,
                        style = TextStyle(
                            fontSize = if (focused) 22.sp else 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = color(rowAccent),
                        ),
                    )
                    Text("الحصة ${row.period}", style = TextStyle(fontSize = 10.sp, color = color(paperMuted)))
                }
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.Horizontal.End) {
                    Text(row.start, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color(paperInk)))
                    Text("حتى ${row.end}", style = TextStyle(fontSize = 9.sp, color = color(paperMuted)))
                }
            }
            Spacer(GlanceModifier.height(7.dp))
        }
        if (height >= StyleLarge.height && day.footer.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Text(day.footer, style = TextStyle(fontSize = 10.sp, color = color(paperMuted)))
        }
    }
}

@Composable
private fun CompactLayout(day: StyleDay) {
    val roomy = LocalSize.current.height >= StyleMedium.height
    val focusAccent = Sections.color(day.currentSection, false)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(color(graphite))
            .cornerRadius(28.dp)
            .padding(15.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(day.day, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color(white)))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                day.currentPeriod,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color(focusAccent)),
                modifier = GlanceModifier
                    .background(fade(focusAccent, 0x2E))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(GlanceModifier.height(if (roomy) 14.dp else 8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column {
                Text(
                    day.currentSection,
                    style = TextStyle(fontSize = if (roomy) 43.sp else 36.sp, fontWeight = FontWeight.Bold, color = color(focusAccent)),
                )
                if (roomy && day.currentSubject.isNotEmpty()) {
                    Text(day.currentSubject, style = TextStyle(fontSize = 10.sp, color = color(whiteFaint)))
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(
                    if (day.isLive && day.minutes.isNotEmpty()) day.minutes else day.currentRange,
                    style = TextStyle(
                        fontSize = if (day.isLive) 32.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = color(white),
                    ),
                )
                Text(
                    if (day.isLive) "دقيقة متبقية" else day.currentStatus,
                    style = TextStyle(fontSize = 10.sp, color = color(whiteMuted)),
                )
            }
        }
        if (roomy && day.next != null) {
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(color(0x19FFFFFF))
                    .cornerRadius(14.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text("التالي", style = TextStyle(fontSize = 10.sp, color = color(whiteFaint)))
                Spacer(GlanceModifier.width(9.dp))
                Text(
                    day.next.section,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = color(Sections.color(day.next.section, false)),
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(day.next.start, style = TextStyle(fontSize = 12.sp, color = color(white)))
            }
        }
    }
}

class LuxuryScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LuxuryScheduleWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleUpdater.schedule(context)
    }
}

class CardsScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsScheduleWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleUpdater.schedule(context)
    }
}

class CompactScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactScheduleWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleUpdater.schedule(context)
    }
}
