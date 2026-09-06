package com.mrabah.oneuischedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.ScheduleUi
import com.mrabah.oneuischedule.data.SlotState
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
 *  PALETTE — read from the actual wallpaper
 *
 *  Material You is not used. The seed comes from Samsung's own
 *  wallpaper colour extraction (WallpaperManager.getWallpaperColors),
 *  the same source One UI's Colour palette feature reads, so the card
 *  tracks the wallpaper even when the system palette is switched off.
 * ══════════════════════════════════════════════════════════════ */

private data class Palette(
    val heroBg: Int,
    val heroInk: Int,
    val heroChip: Int,
    val accent: Int,
    val standbyBg: Int,
    val standbyInk: Int,
)

private object Palettes {

    fun of(context: Context): Palette {
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val hsl = FloatArray(3)
        val seed = seedColor(context)
        if (seed != null) {
            ColorUtils.colorToHSL(seed, hsl)
        } else {
            hsl[0] = 168f; hsl[1] = 0.40f; hsl[2] = 0.45f
        }

        val hue = hsl[0]
        val sat = hsl[1].coerceIn(0.18f, 0.60f)

        fun tone(s: Float, l: Float) = ColorUtils.HSLToColor(floatArrayOf(hue, s, l))

        val heroInk = tone(sat * 0.9f, if (dark) 0.93f else 0.16f)
        return Palette(
            heroBg = tone(sat * 0.75f, if (dark) 0.20f else 0.88f),
            heroInk = heroInk,
            heroChip = ColorUtils.setAlphaComponent(heroInk, 0x24),
            accent = tone(sat, if (dark) 0.70f else 0.40f),
            standbyBg = tone(sat * 0.20f, if (dark) 0.18f else 0.90f),
            standbyInk = tone(sat * 0.20f, if (dark) 0.85f else 0.28f),
        )
    }

    private fun seedColor(context: Context): Int? = try {
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    } catch (t: Throwable) {
        null // no permission, live wallpaper, or nothing set
    }
}

/* ══════════════════════════════════════════════════════════════
 *  VIEW MODEL — all formatting happens here, using the device's
 *  timezone, locale and 12/24-hour setting.
 * ══════════════════════════════════════════════════════════════ */

private data class RowVm(
    val period: String,
    val time: String,
    val label: String,
    val done: Boolean,
)

private data class Vm(
    val dayName: String,
    val dateLine: String,
    val remaining: String,
    val hasFocus: Boolean,
    val emptyLabel: String,
    val status: String,
    val periodLabel: String,
    val section: String,
    val subject: String,
    val startTime: String,
    val endTime: String,
    val isStandby: Boolean,
    val showProgress: Boolean,
    val progress: Float,
    val rows: List<RowVm>,
    val palette: Palette,
)

private object Vms {

    fun build(context: Context, ui: ScheduleUi): Vm {
        val locale = deviceLocale(context)
        val clock = DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale
        )

        fun t(time: LocalTime) = ltr(time.format(clock))

        val slot = ui.focus
        val live = ui.live != null
        val hijri = hijri(ui.date, locale)
        val greg = ui.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))

        return Vm(
            dayName = ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
            dateLine = if (hijri.isEmpty()) greg else "$hijri  ·  $greg",
            remaining = if (ui.remaining > 0) "${ui.remaining} حصص متبقية" else "",
            hasFocus = slot != null,
            emptyLabel = if (ui.isToday) "انتهى نصابك اليوم" else "إجازة",
            status = when {
                slot == null -> ""
                live -> "باقي ${ui.minutesLeftInLive} دقيقة"
                ui.isAssembly -> "بعد الطابور"
                !ui.isToday -> "أول حصة"
                ui.minutesUntilNext != null -> "تبدأ بعد ${ui.minutesUntilNext} دقيقة"
                else -> "القادمة"
            },
            periodLabel = if (slot != null) "الحصة ${slot.period}" else "",
            section = slot?.section ?: "انتظار",
            subject = slot?.subject ?: "لا يوجد فصل",
            startTime = if (slot != null) t(slot.bell.start) else "",
            endTime = if (slot != null) "حتى " + t(slot.bell.end) else "",
            isStandby = slot?.isStandby ?: false,
            showProgress = live,
            progress = ui.progress,
            rows = ui.slots
                .filter { it.period != slot?.period }
                .map {
                    RowVm(
                        period = "${it.period}",
                        time = t(it.bell.start),
                        label = it.section ?: "انتظار",
                        done = it.state == SlotState.DONE,
                    )
                },
            palette = Palettes.of(context),
        )
    }

    private fun deviceLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    /** Bidi isolate: stops RTL layout reordering digits around a dash. */
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

private val Muted = ColorProvider(R.color.widget_muted)
private val Ink = ColorProvider(R.color.widget_ink)
private val RowBg = ColorProvider(R.color.widget_row)
private val Track = ColorProvider(R.color.widget_progress_track)

private fun provider(argb: Int) = ColorProvider(ComposeColor(argb))

private val Compact = DpSize(260.dp, 120.dp)
private val Medium = DpSize(260.dp, 200.dp)
private val Large = DpSize(300.dp, 300.dp)

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ui = ScheduleEngine.build(LocalDateTime.now(ZoneId.systemDefault()))
        val vm = Vms.build(context, ui)
        provideContent {
            GlanceTheme { WidgetRoot(vm) }
        }
    }
}

@Composable
private fun WidgetRoot(vm: Vm) {
    val height = LocalSize.current.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.glass_panel))
            .cornerRadius(28.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Header(vm)
        Spacer(GlanceModifier.height(12.dp))
        Hero(vm)

        if (height >= Medium.height && vm.rows.isNotEmpty()) {
            Spacer(GlanceModifier.height(10.dp))
            vm.rows.forEach { row ->
                SlotRow(row, vm.palette)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun Header(vm: Vm) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column {
            Text(
                text = vm.dayName,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink),
            )
            Text(
                text = vm.dateLine,
                style = TextStyle(fontSize = 11.sp, color = Muted),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = vm.remaining,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Muted),
        )
    }
}

@Composable
private fun Hero(vm: Vm) {
    val p = vm.palette

    if (!vm.hasFocus) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(RowBg)
                .cornerRadius(24.dp)
                .padding(18.dp)
        ) {
            Text(
                text = vm.emptyLabel,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink),
            )
        }
        return
    }

    val ink = provider(if (vm.isStandby) p.standbyInk else p.heroInk)
    val bg = provider(if (vm.isStandby) p.standbyBg else p.heroBg)

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(bg)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // period badge on one edge, live status on the other
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = vm.periodLabel,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink),
                modifier = GlanceModifier
                    .background(provider(p.heroChip))
                    .cornerRadius(9.dp)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = vm.status,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ink),
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        // section anchors one edge, the time block the other — the row is full width
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column {
                Text(
                    text = vm.section,
                    style = TextStyle(
                        fontSize = if (vm.isStandby) 26.sp else 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                    ),
                )
                Text(
                    text = vm.subject,
                    style = TextStyle(fontSize = 12.sp, color = ink),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(
                    text = vm.startTime,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink),
                )
                Text(
                    text = vm.endTime,
                    style = TextStyle(fontSize = 12.sp, color = ink),
                )
            }
        }

        if (vm.showProgress) {
            Spacer(GlanceModifier.height(14.dp))
            LinearProgressIndicator(
                progress = vm.progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = provider(p.accent),
                backgroundColor = provider(p.heroChip),
            )
        }
    }
}

@Composable
private fun SlotRow(row: RowVm, p: Palette) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(if (row.done) Track else RowBg)
            .cornerRadius(18.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = row.period,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (row.done) Muted else provider(p.accent),
            ),
            modifier = GlanceModifier
                .background(if (row.done) Track else provider(p.heroChip))
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = row.time,
            style = TextStyle(fontSize = 13.sp, color = Muted),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = row.label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (row.done) Muted else Ink,
            ),
        )
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
        ScheduleUpdater.cancel(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ScheduleWidget().updateAll(context)
                ScheduleUpdater.schedule(context)
            } catch (t: Throwable) {
                // a failed refresh must never crash the launcher's broadcast
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
        val at = ScheduleEngine.nextRefresh(LocalDateTime.now(ZoneId.systemDefault()))
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent(context))
            } else {
                alarms.setWindow(AlarmManager.RTC, triggerAt, 60_000L, pendingIntent(context))
            }
        } catch (t: Throwable) {
            // exact-alarm permission revoked mid-flight; updatePeriodMillis covers it
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleWidgetReceiver::class.java)
            .setAction(ScheduleWidgetReceiver.ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
