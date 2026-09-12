package com.mrabah.oneuischedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.ScheduleUi
import com.mrabah.oneuischedule.data.SlotState
import com.mrabah.oneuischedule.notify.PeriodNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor

/* ══════════════════════════════════════════════════════════════
 *  SECTION IDENTITY
 *  Each class keeps one colour everywhere: the card tint, the row
 *  edge, the day bar. You recognise where you are going before you
 *  read the number.
 * ══════════════════════════════════════════════════════════════ */

internal object Sections {

    private fun base(section: String?): Int = when (section) {
        "2/1" -> 0xFF3B82F6.toInt() // blue
        "2/2" -> 0xFF10B981.toInt() // green
        "2/3" -> 0xFFF59E0B.toInt() // amber
        "2/4" -> 0xFF8B5CF6.toInt() // violet
        else -> 0xFF94A3B8.toInt()  // standby / unknown
    }

    /**
     * The tint is carried by the text and the rail, never by a wash behind
     * them: amber over a purple wallpaper turns to mud however light the
     * wash is. Lightness is re-pinned to the wallpaper's polarity so every
     * section colour stays legible on either.
     */
    fun color(section: String?, lightWallpaper: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base(section), hsl)
        hsl[1] = hsl[1].coerceAtMost(0.85f)
        hsl[2] = if (lightWallpaper) 0.42f else 0.74f
        return ColorUtils.HSLToColor(hsl)
    }

}

/* ══════════════════════════════════════════════════════════════
 *  GLASS — the widget is translucent, so the wallpaper is the colour.
 *  Only the ink polarity is read from the system.
 * ══════════════════════════════════════════════════════════════ */

internal data class Glass(
    val light: Boolean,
    val panel: Int,
    val row: Int,
    val heroVeil: Int,
    val chip: Int,
    val ink: Int,
    val inkMuted: Int,
    val inkFaint: Int,
)

internal object GlassPalette {

    fun of(context: Context): Glass {
        val light = wallpaperIsLight(context)
        val ink = if (light) android.graphics.Color.rgb(16, 19, 24) else android.graphics.Color.WHITE
        return Glass(
            light = light,
            panel = veil(0xFFFFFF, if (light) 0x73 else 0x2E),
            row = veil(0xFFFFFF, if (light) 0x8A else 0x1F),
            heroVeil = veil(0xFFFFFF, if (light) 0xB0 else 0x38),
            chip = veil(if (light) 0x000000 else 0xFFFFFF, if (light) 0x14 else 0x33),
            ink = ink,
            inkMuted = ColorUtils.setAlphaComponent(ink, 0xC0),
            inkFaint = ColorUtils.setAlphaComponent(ink, 0x80),
        )
    }

    private fun veil(rgb: Int, alpha: Int) =
        ColorUtils.setAlphaComponent(rgb or 0xFF000000.toInt(), alpha)

    private fun wallpaperIsLight(context: Context): Boolean = try {
        val colors = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    } catch (t: Throwable) {
        false
    }
}

/* ══════════════════════════════════════════════════════════════
 *  VIEW MODEL
 * ══════════════════════════════════════════════════════════════ */

private data class RowVm(
    val period: String,
    val time: String,
    val label: String,
    val color: Int,
    val inkAlpha: Int,   // past periods recede progressively
    val note: String = "",
    val isNext: Boolean = false, // the one after the card, emphasised
    val railFill: Float = 1f,    // how full the countdown rail is
    val isBreak: Boolean = false,
)

private data class SegVm(val color: Int, val alpha: Int)

private data class Vm(
    val dayName: String,
    val dateLine: String,
    val remaining: String,
    val segments: List<SegVm>,
    val hasFocus: Boolean,
    val emptyLabel: String,
    val status: String,
    val periodLabel: String,
    val section: String,
    val subject: String,
    val startTime: String,
    val endTime: String,
    val minutesLeft: String,
    val startedAt: String,
    val endsAt: String,
    val isStandby: Boolean,
    val accent: Int,
    val showProgress: Boolean,
    val progress: Float,
    val rows: List<RowVm>,
    val footer: String,
    val dayFraction: Float,  // position of "now" along the day bar
    val heroVeil: Int,       // intensity rises as the period approaches
    val currentSegment: Int, // which of the seven is live or next
    val glass: Glass,
)

private object Vms {

    fun build(context: Context, ui: ScheduleUi): Vm {
        val locale = deviceLocale(context)
        val clock = DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale
        )
        fun t(time: LocalTime) = ltr(time.format(clock))

        val glass = GlassPalette.of(context)
        val slot = ui.focus
        val live = ui.live != null
        val hijri = hijri(ui.date, locale)
        val greg = ui.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))

        // how far "now" sits along the school day, 0..1
        val dayOpen = Defaults.assemblyStart
        val dayClose = ui.config.bells.maxOfOrNull { it.end } ?: dayOpen
        val nowTime = java.time.LocalTime.now()
        val dayFraction = if (!ui.isToday) 0f else {
            val span = (dayClose.toSecondOfDay() - dayOpen.toSecondOfDay()).toFloat()
            if (span <= 0f) 0f
            else ((nowTime.toSecondOfDay() - dayOpen.toSecondOfDay()) / span).coerceIn(0f, 1f)
        }

        // the card grows more present as its period nears
        val minutesAway = ui.minutesUntilNext ?: 0L
        val nearness = when {
            live -> 1f
            !ui.isToday -> 0.35f
            minutesAway <= 5 -> 1f
            minutesAway >= 60 -> 0.35f
            else -> 1f - (minutesAway - 5).toFloat() / 55f * 0.65f
        }

        val byPeriod = ui.slots.associateBy { it.period }
        val segments = (1..7).map { p ->
            val s = byPeriod[p]
            when {
                s == null -> SegVm(glass.ink, 0x1F)
                s.state == SlotState.LIVE -> SegVm(Sections.color(s.section, glass.light), 0xFF)
                s.state == SlotState.DONE -> SegVm(Sections.color(s.section, glass.light), 0x8C)
                else -> SegVm(Sections.color(s.section, glass.light), 0x4D)
            }
        }

        // past periods fade further the older they are
        var doneSeen = 0
        val doneTotal = ui.slots.count { it.state == SlotState.DONE }
        val rows = buildList {
            var crossedBreak = false
            ui.slots.filter { it.period != slot?.period }.forEach { s ->
                if (!crossedBreak && s.period >= 4) {
                    crossedBreak = true
                    ui.config.bells.firstOrNull { it.period == 3 }?.let { third ->
                        ui.config.bells.firstOrNull { it.period == 4 }?.let { fourth ->
                            add(
                                RowVm(
                                    period = "", time = t(third.end) + " – " + t(fourth.start),
                                    label = "الفسحة", color = glass.ink,
                                    inkAlpha = 0x66, isBreak = true,
                                )
                            )
                        }
                    }
                }
                val alpha = if (s.state == SlotState.DONE) {
                    doneSeen++
                    (0x40 + (0x38 * doneSeen / (doneTotal + 1))).coerceIn(0x30, 0x90)
                } else 0xFF
                val isNext = ui.isToday && s.state == SlotState.AHEAD &&
                    s.period == ui.slots.firstOrNull { it.state == SlotState.AHEAD }?.period
                val fill = if (!isNext) 1f else {
                    val away = java.time.temporal.ChronoUnit.MINUTES
                        .between(nowTime, s.bell.start).coerceAtLeast(0L)
                    (1f - away / 60f).coerceIn(0.12f, 1f)
                }
                add(
                    RowVm(
                        period = "${s.period}",
                        time = t(s.bell.start),
                        label = s.section ?: "انتظار",
                        color = Sections.color(s.section, glass.light),
                        inkAlpha = alpha,
                        note = s.note,
                        isNext = isNext,
                        railFill = fill,
                    )
                )
            }
        }

        return Vm(
            dayName = if (!ui.isToday && ui.date == java.time.LocalDate.now().plusDays(1))
                "غدًا · " + ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
            else ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
            dateLine = if (hijri.isEmpty()) greg else "$hijri  ·  $greg",
            remaining = if (ui.remaining > 0) "${ui.remaining} حصص متبقية" else "",
            segments = segments,
            hasFocus = slot != null,
            emptyLabel = if (ui.isToday) "انتهى نصابك اليوم" else "إجازة",
            status = when {
                slot == null -> ""
                live -> "جارية الآن"
                ui.isAssembly -> "بعد الطابور"
                !ui.isToday -> ""
                ui.minutesUntilNext != null -> "تبدأ بعد ${ui.minutesUntilNext} دقيقة"
                else -> ""
            },
            periodLabel = if (slot != null) "الحصة ${slot.period}" else "",
            section = slot?.section ?: "انتظار",
            subject = slot?.section?.let { ui.config.progress[it]?.next }
                ?.takeIf { it.isNotBlank() }
                ?: if (slot == null || slot.isStandby) "لا يوجد فصل" else ui.config.subject,
            startTime = if (slot != null) t(slot.bell.start) else "",
            endTime = if (slot != null) "حتى " + t(slot.bell.end) else "",
            minutesLeft = (ui.minutesLeftInLive ?: 0L).toString(),
            startedAt = if (slot != null) "بدأت " + t(slot.bell.start) else "",
            endsAt = if (slot != null) "تنتهي " + t(slot.bell.end) else "",
            isStandby = slot?.isStandby ?: false,
            accent = Sections.color(slot?.section, glass.light),
            showProgress = live,
            progress = ui.progress,
            rows = rows,
            dayFraction = dayFraction,
            heroVeil = ColorUtils.setAlphaComponent(
                glass.heroVeil,
                (android.graphics.Color.alpha(glass.heroVeil) * (0.55f + 0.45f * nearness)).toInt()
                    .coerceIn(0x14, 0xFF),
            ),
            currentSegment = (ui.live ?: ui.next)?.period ?: 0,
            footer = ui.slots.lastOrNull()?.let { last ->
                "${ui.slots.size} حصص · ينتهي دوامك " + t(last.bell.end)
            } ?: "",
            glass = glass,
        )
    }

    private fun deviceLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    private fun ltr(text: String) = "\u2066" + text + "\u2069"

    private fun hijri(date: java.time.LocalDate, locale: Locale): String = try {
        DateTimeFormatter.ofPattern("d MMMM", locale).format(HijrahDate.from(date))
    } catch (t: Throwable) {
        ""
    }
}

/* ══════════════════════════════════════════════════════════════
 *  WIDGET
 * ══════════════════════════════════════════════════════════════ */

internal fun provider(argb: Int) = ColorProvider(ComposeColor(argb))
internal fun fade(color: Int, alpha: Int) =
    provider(ColorUtils.setAlphaComponent(color, alpha))

/** Dims a colour that already carries alpha, instead of replacing it. */
internal fun dim(color: Int, factor: Int) = provider(
    ColorUtils.setAlphaComponent(color, android.graphics.Color.alpha(color) * factor / 0xFF)
)

private val Compact = DpSize(260.dp, 120.dp)
private val Medium = DpSize(260.dp, 200.dp)
private val Large = DpSize(300.dp, 300.dp)

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = ScheduleEngine.build(context, LocalDateTime.now(ZoneId.systemDefault()))
        val vm = Vms.build(context, ui)
        provideContent {
            GlanceTheme { WidgetRoot(vm) }
        }
    }
}

@Composable
private fun WidgetRoot(vm: Vm) {
    val size = LocalSize.current
    val height = size.height
    val compact = height < Medium.height
    val barWidth = (size.width.value.toInt() - 32).coerceAtLeast(80)
    val g = vm.glass

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(provider(g.panel))
            .cornerRadius(28.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Header(vm)
        if (!compact) DayBar(vm, barWidth) else Spacer(GlanceModifier.height(10.dp))
        Hero(vm, compact)

        if (height >= Medium.height && vm.rows.isNotEmpty()) {
            Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                vm.rows.forEach { row ->
                    SlotRow(row, g)
                    Spacer(GlanceModifier.height(8.dp))
                }
            }
            if (height >= Large.height && vm.footer.isNotEmpty()) {
                Spacer(GlanceModifier.defaultWeight())
                Spacer(
                    GlanceModifier.fillMaxWidth().height(1.dp)
                        .background(fade(g.ink, 0x1A))
                )
                Text(
                    text = vm.footer,
                    style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
                    modifier = GlanceModifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(vm: Vm) {
    val g = vm.glass
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column {
            Text(
                text = vm.dayName,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = provider(g.ink)),
            )
            Text(
                text = vm.dateLine,
                style = TextStyle(fontSize = 11.sp, color = provider(g.inkMuted)),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = vm.remaining,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = provider(g.inkMuted)),
        )
    }
}

/** Seven segments, one per period: the whole day as a single bar. */
@Composable
private fun DayBar(vm: Vm, barWidth: Int) {
    val g = vm.glass
    Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            vm.segments.forEachIndexed { index, seg ->
                val current = index + 1 == vm.currentSegment
                Spacer(
                    GlanceModifier
                        .defaultWeight()
                        .height(if (current) 7.dp else 5.dp)
                        .padding(horizontal = 1.dp)
                        .background(fade(seg.color, seg.alpha))
                        .cornerRadius(4.dp)
                )
            }
        }
        // a hairline that walks the bar with the clock, not with the periods
        if (vm.dayFraction > 0f) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 3.dp)) {
                Spacer(GlanceModifier.width((barWidth * vm.dayFraction).toInt().dp))
                Spacer(
                    GlanceModifier
                        .width(2.dp)
                        .height(4.dp)
                        .background(provider(g.ink))
                        .cornerRadius(1.dp)
                )
            }
        }
    }
}

@Composable
private fun Hero(vm: Vm, compact: Boolean = false) {
    val g = vm.glass

    if (!vm.hasFocus) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(provider(g.row))
                .cornerRadius(24.dp)
                .padding(18.dp)
        ) {
            Text(
                text = vm.emptyLabel,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = provider(g.ink)),
            )
        }
        return
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(provider(vm.heroVeil))
            .cornerRadius(24.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = vm.periodLabel,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = provider(vm.accent)),
                modifier = GlanceModifier
                    .background(fade(vm.accent, 0x2E))
                    .cornerRadius(9.dp)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = vm.status,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = provider(g.inkMuted)),
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column {
                // the section is the picture, not a caption
                Text(
                    text = vm.section,
                    style = TextStyle(
                        fontSize = if (vm.isStandby) 30.sp else if (compact) 40.sp else 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = provider(vm.accent),
                    ),
                )
                Text(
                    text = vm.subject,
                    style = TextStyle(fontSize = 12.sp, color = provider(g.inkMuted)),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                // Only the section number is allowed to shout. The clock is
                // reference, so it stays quiet even while a period runs.
                Text(
                    text = if (vm.showProgress) vm.minutesLeft else vm.startTime,
                    style = TextStyle(
                        fontSize = if (vm.showProgress) 26.sp else 16.sp,
                        fontWeight = if (vm.showProgress) FontWeight.Medium else FontWeight.Normal,
                        color = provider(if (vm.showProgress) g.ink else g.inkMuted),
                    ),
                )
                Text(
                    text = if (vm.showProgress) "دقيقة متبقية" else vm.endTime,
                    style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
                )
            }
        }

        if (vm.showProgress) {
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = vm.startedAt,
                    style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = vm.endsAt,
                    style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
                )
            }
        }
    }
}

@Composable
private fun SlotRow(row: RowVm, g: Glass) {
    if (row.isBreak) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = row.label,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = provider(g.inkFaint)),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = row.time,
                style = TextStyle(fontSize = 11.sp, color = provider(g.inkFaint)),
            )
            Spacer(GlanceModifier.defaultWeight())
        }
        return
    }

    val railHeight = if (row.isNext) 30 else 24
    val filled = (railHeight * row.railFill).toInt().coerceAtLeast(3)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(dim(g.row, if (row.isNext) 0xFF else row.inkAlpha))
            .cornerRadius(18.dp)
            .padding(horizontal = 12.dp, vertical = if (row.isNext) 13.dp else 10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // the rail doubles as a countdown: it fills as the period approaches
        Column(modifier = GlanceModifier.width(3.dp)) {
            Spacer(
                GlanceModifier
                    .width(3.dp)
                    .height((railHeight - filled).dp)
                    .background(fade(row.color, 0x33))
                    .cornerRadius(2.dp)
            )
            Spacer(
                GlanceModifier
                    .width(3.dp)
                    .height(filled.dp)
                    .background(fade(row.color, row.inkAlpha))
                    .cornerRadius(2.dp)
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = row.period,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = fade(g.ink, (row.inkAlpha * 3 / 4).coerceAtLeast(0x30))),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.time,
            style = TextStyle(fontSize = 13.sp, color = fade(g.ink, (row.inkAlpha * 3 / 4).coerceAtLeast(0x30))),
        )
        Spacer(GlanceModifier.width(12.dp))
        // The section sits next to its time instead of across the row: the eye
        // should not have to travel to connect "11:40" with "2/1".
        Text(
            text = row.label,
            style = TextStyle(
                fontSize = if (row.isNext) 19.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                color = fade(row.color, row.inkAlpha),
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        if (row.note.isNotEmpty()) {
            Text(
                text = row.note,
                style = TextStyle(fontSize = 11.sp, color = fade(g.ink, (row.inkAlpha / 2).coerceAtLeast(0x40))),
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════
 *  RECEIVER + REFRESH
 * ══════════════════════════════════════════════════════════════ */

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_WALLPAPER_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED -> refresh(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleUpdater.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!DesignWidgets.anyInstalled(context)) ScheduleUpdater.cancel(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateEveryWidget(context)
                ScheduleUpdater.schedule(context)
                PeriodNotifier.sync(context)
            } catch (t: Throwable) {
                // never crash the launcher's broadcast
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.mrabah.oneuischedule.TICK"
    }
}

object ScheduleUpdater {

    private const val REQUEST_CODE = 8501

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val utilityInstalled = UtilityWidgets.receivers.values.any { manager.getAppWidgetIds(android.content.ComponentName(context,it)).isNotEmpty() }
        val today = if(utilityInstalled) ScheduleEngine.today(com.mrabah.oneuischedule.data.ScheduleStore.load(context),now) else null
        val at = if(today?.focus!=null) now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusMinutes(1) else ScheduleEngine.nextRefresh(context, now)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent(context))
            } else {
                alarms.setWindow(AlarmManager.RTC, triggerAt, 60_000L, pendingIntent(context))
            }
        } catch (t: Throwable) {
            // exact-alarm permission revoked; updatePeriodMillis covers it
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ScheduleWidgetReceiver::class.java).setAction(ScheduleWidgetReceiver.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
