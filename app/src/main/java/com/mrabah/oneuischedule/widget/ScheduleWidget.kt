package com.mrabah.oneuischedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
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
 *  GLASS PALETTE
 *
 *  The widget is not tinted — it is translucent. Every surface is a
 *  white or black veil, so the wallpaper itself supplies the colour,
 *  exactly like One UI's own widgets.
 *
 *  The only thing read from the system is WallpaperColors.colorHints:
 *  HINT_SUPPORTS_DARK_TEXT tells us whether the wallpaper behind the
 *  widget is light. That decides ink colour and veil polarity — the
 *  one piece of information a translucent widget genuinely needs.
 * ══════════════════════════════════════════════════════════════ */

private data class Glass(
    val panel: Int,
    val hero: Int,
    val row: Int,
    val rowDone: Int,
    val chip: Int,
    val ink: Int,
    val inkMuted: Int,
    val inkFaint: Int,
)

private object GlassPalette {

    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000

    fun of(context: Context): Glass {
        val lightWallpaper = wallpaperIsLight(context)

        return if (lightWallpaper) {
            // dark ink on white veils
            val ink = AndroidColor.rgb(16, 19, 24)
            Glass(
                panel = veil(WHITE, 0x73),
                hero = veil(WHITE, 0xC4),
                row = veil(WHITE, 0x8A),
                rowDone = veil(WHITE, 0x40),
                chip = veil(BLACK, 0x14),
                ink = ink,
                inkMuted = ColorUtils.setAlphaComponent(ink, 0xB0),
                inkFaint = ColorUtils.setAlphaComponent(ink, 0x70),
            )
        } else {
            // white ink on white veils — the wallpaper shows through
            val ink = AndroidColor.WHITE
            Glass(
                panel = veil(WHITE, 0x2E),
                hero = veil(WHITE, 0x59),
                row = veil(WHITE, 0x1F),
                rowDone = veil(WHITE, 0x12),
                chip = veil(WHITE, 0x33),
                ink = ink,
                inkMuted = ColorUtils.setAlphaComponent(ink, 0xC0),
                inkFaint = ColorUtils.setAlphaComponent(ink, 0x80),
            )
        }
    }

    private fun veil(rgb: Int, alpha: Int) = ColorUtils.setAlphaComponent(rgb or 0xFF000000.toInt(), alpha)

    private fun wallpaperIsLight(context: Context): Boolean = try {
        val colors = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        colors != null &&
            (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    } catch (t: Throwable) {
        false // assume a dark wallpaper; white ink is the safer default
    }
}

/* ══════════════════════════════════════════════════════════════
 *  VIEW MODEL
 * ══════════════════════════════════════════════════════════════ */

private data class RowVm(
    val period: String,
    val time: String,
    val label: String,
    val done: Boolean,
    val isBreak: Boolean = false,
)

private data class Vm(
    val dayName: String,
    val dateLine: String,
    val remaining: String,
    val dayDots: String,
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
    val showProgress: Boolean,
    val progress: Float,
    val rows: List<RowVm>,
    val glass: Glass,
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

        // one-line map of the whole day: done / live / ahead / no duty
        val byPeriod = ui.slots.associateBy { it.period }
        val dots = (1..7).joinToString("  ") { p ->
            val s = byPeriod[p]
            when {
                s == null -> "·"
                s.state == SlotState.LIVE -> "◉"
                s.state == SlotState.DONE -> "●"
                else -> "○"
            }
        }

        return Vm(
            dayName = if (!ui.isToday && ui.date == java.time.LocalDate.now().plusDays(1))
                "غدًا · " + ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
            else ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
            dateLine = if (hijri.isEmpty()) greg else "$hijri  ·  $greg",
            remaining = if (ui.remaining > 0) "${ui.remaining} حصص متبقية" else "",
            dayDots = dots,
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
            subject = if (slot == null || slot.isStandby) "لا يوجد فصل" else Defaults.SUBJECT,
            startTime = if (slot != null) t(slot.bell.start) else "",
            endTime = if (slot != null) "حتى " + t(slot.bell.end) else "",
            minutesLeft = (ui.minutesLeftInLive ?: 0L).toString(),
            startedAt = if (slot != null) "بدأت " + t(slot.bell.start) else "",
            endsAt = if (slot != null) "تنتهي " + t(slot.bell.end) else "",
            isStandby = slot?.isStandby ?: false,
            showProgress = live,
            progress = ui.progress,
            rows = buildList {
                var crossedBreak = false
                ui.slots.filter { it.period != slot?.period }.forEach {
                    if (!crossedBreak && it.period >= 4) {
                        crossedBreak = true
                        add(
                            RowVm(
                                period = "",
                                time = t(ui.config.bell(3).end) + " – " + t(ui.config.bell(4).start),
                                label = "الفسحة",
                                done = ui.slots.any { s -> s.period >= 4 && s.state == SlotState.DONE },
                                isBreak = true,
                            )
                        )
                    }
                    add(
                        RowVm(
                            period = "${it.period}",
                            time = t(it.bell.start),
                            label = it.section ?: "انتظار",
                            done = it.state == SlotState.DONE,
                        )
                    )
                }
            },
            glass = GlassPalette.of(context),
        )
    }

    private fun deviceLocale(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    /** Bidi isolate: stops RTL layout reordering digits around a colon or dash. */
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

private fun provider(argb: Int) = ColorProvider(ComposeColor(argb))

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
    val height = LocalSize.current.height
    val g = vm.glass

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(provider(g.panel))
            .cornerRadius(28.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Header(vm)
        Spacer(GlanceModifier.height(10.dp))
        Hero(vm)

        if (height >= Medium.height && vm.rows.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            // own container: a Glance layout node accepts only ~10 children,
            // and the root already spends four on the header and hero
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                vm.rows.forEach { row ->
                    SlotRow(row, g)
                    Spacer(GlanceModifier.height(6.dp))
                }
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
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = provider(g.ink),
                ),
            )
            Text(
                text = vm.dateLine,
                style = TextStyle(fontSize = 11.sp, color = provider(g.inkMuted)),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(horizontalAlignment = Alignment.Horizontal.End) {
            Text(
                text = vm.remaining,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = provider(g.inkMuted),
                ),
            )
            // the whole day in one line: filled = done, ring = ahead, dot = free
            Text(
                text = vm.dayDots,
                style = TextStyle(fontSize = 10.sp, color = provider(g.inkFaint)),
            )
        }
    }
}

@Composable
private fun Hero(vm: Vm) {
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
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = provider(g.ink),
                ),
            )
        }
        return
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(provider(if (vm.isStandby) g.row else g.hero))
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = vm.periodLabel,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = provider(g.ink),
                ),
                modifier = GlanceModifier
                    .background(provider(g.chip))
                    .cornerRadius(9.dp)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = vm.status,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = provider(g.inkMuted),
                ),
            )
        }

        Spacer(GlanceModifier.height(12.dp))

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
                        color = provider(g.ink),
                    ),
                )
                Text(
                    text = vm.subject,
                    style = TextStyle(fontSize = 12.sp, color = provider(g.inkMuted)),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(
                    text = if (vm.showProgress) vm.minutesLeft else vm.startTime,
                    style = TextStyle(
                        fontSize = if (vm.showProgress) 30.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = provider(g.ink),
                    ),
                )
                Text(
                    text = if (vm.showProgress) "دقيقة متبقية" else vm.endTime,
                    style = TextStyle(fontSize = 12.sp, color = provider(g.inkMuted)),
                )
            }
        }

        if (vm.showProgress) {
            Spacer(GlanceModifier.height(14.dp))
            LinearProgressIndicator(
                progress = vm.progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = provider(g.ink),
                backgroundColor = provider(g.chip),
            )
            Spacer(GlanceModifier.height(6.dp))
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
    val ink = if (row.done) g.inkFaint else g.ink

    if (row.isBreak) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
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

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(provider(if (row.done) g.rowDone else g.row))
            .cornerRadius(18.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = row.period,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = provider(ink)),
            modifier = GlanceModifier
                .background(provider(g.chip))
                .cornerRadius(8.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = row.time,
            style = TextStyle(fontSize = 13.sp, color = provider(g.inkMuted)),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = row.label,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = provider(ink)),
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
                PeriodNotifier.sync(context)
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
        val at = ScheduleEngine.nextRefresh(context, LocalDateTime.now(ZoneId.systemDefault()))
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
