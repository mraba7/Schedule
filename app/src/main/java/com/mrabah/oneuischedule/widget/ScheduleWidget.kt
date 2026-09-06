package com.mrabah.oneuischedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.appwidget.unit.ColorProvider
import com.mrabah.oneuischedule.MainActivity
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.ScheduleUi
import com.mrabah.oneuischedule.data.Slot
import com.mrabah.oneuischedule.data.SlotState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/* ══════════════════════════════════════════════════════════════
 *  THEME — Monet first, biology-teal fallback
 * ══════════════════════════════════════════════════════════════ */

private object Fallback {
    private val lightScheme = lightColorScheme(
        primary = Color(0xFF146B5C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFEDE4),
        onPrimaryContainer = Color(0xFF03251E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF16181C),
        onSurfaceVariant = Color(0xFF5C636B),
    )
    private val darkScheme = darkColorScheme(
        primary = Color(0xFF7FD9C2),
        onPrimary = Color(0xFF00382E),
        primaryContainer = Color(0xFF123A33),
        onPrimaryContainer = Color(0xFFB9F2E2),
        surface = Color(0xFF1A1D21),
        onSurface = Color(0xFFE7EAEE),
        onSurfaceVariant = Color(0xFF9AA2AC),
    )
    val providers = ColorProviders(light = lightScheme, dark = darkScheme)
}

/** Muted ink that survives both Monet palettes without fighting the accent. */
private val Muted = ColorProvider(day = Color(0xFF5C636B), night = Color(0xFF9AA2AC))
private val Ink = ColorProvider(day = Color(0xFF16181C), night = Color(0xFFE7EAEE))

@Composable
private fun ScheduleTheme(content: @Composable () -> Unit) {
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors
        else Fallback.providers
    GlanceTheme(colors = colors, content = content)
}

/* ══════════════════════════════════════════════════════════════
 *  WIDGET
 * ══════════════════════════════════════════════════════════════ */

private val Compact = DpSize(260.dp, 120.dp)  // 4 × 2
private val Medium = DpSize(260.dp, 200.dp)   // 4 × 3
private val Large = DpSize(300.dp, 300.dp)    // 4 × 4

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ui = ScheduleEngine.build(LocalDateTime.now(ZoneId.systemDefault()))
            ScheduleTheme { WidgetRoot(ui) }
        }
    }
}

@Composable
private fun WidgetRoot(ui: ScheduleUi) {
    val height = LocalSize.current.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_widget_glass))
            .cornerRadius(28.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Header(ui)
        Spacer(GlanceModifier.height(10.dp))
        Hero(ui)

        if (height >= Medium.height) {
            Spacer(GlanceModifier.height(10.dp))
            DayList(ui)
        }
    }
}

/* ─────────────────────────── header ─────────────────────────── */

@Composable
private fun Header(ui: ScheduleUi) {
    val locale = Locale.getDefault()
    val dayName = ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
    val dateLine = ui.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = dayName,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink),
            )
            Text(
                text = if (ui.isToday) dateLine else "$dateLine · next school day",
                style = TextStyle(fontSize = 11.sp, color = Muted),
            )
        }

        if (ui.remaining > 0) {
            Chip(text = "${ui.remaining} left")
        }
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.bg_chip))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant,
            ),
        )
    }
}

/* ──────────────────────────── hero ──────────────────────────── */

@Composable
private fun Hero(ui: ScheduleUi) {
    val slot = ui.focus

    if (slot == null) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.bg_card))
                .padding(14.dp),
        ) {
            Text(
                text = "Done for today",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink),
            )
        }
        return
    }

    val live = ui.live != null
    val statusLabel = when {
        live -> "Now · ${ui.minutesLeftInLive} min left"
        ui.isAssembly -> "Assembly · then"
        !ui.isToday -> "First period"
        ui.minutesUntilNext != null && ui.minutesUntilNext!! <= 60 -> "Next in ${ui.minutesUntilNext} min"
        else -> "Next"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                ImageProvider(
                    if (slot.isStandby) R.drawable.bg_hero_standby else R.drawable.bg_hero
                )
            )
            .padding(14.dp)
            .semantics { contentDescription = slot.spoken(statusLabel) },
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "P${slot.period} · $statusLabel",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onPrimaryContainer,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = slot.bell.range(),
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = slot.section ?: "Standby",
                style = TextStyle(
                    fontSize = if (slot.isStandby) 22.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onPrimaryContainer,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = slot.subject ?: "No class assigned",
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
            )
        }

        if (live) {
            Spacer(GlanceModifier.height(10.dp))
            LinearProgressIndicator(
                progress = ui.progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = ColorProvider(
                    day = Color(0x33000000),
                    night = Color(0x33FFFFFF),
                ),
            )
        }
    }
}

/* ──────────────────────────── list ──────────────────────────── */

@Composable
private fun DayList(ui: ScheduleUi) {
    val rest = ui.slots.filter { it.period != ui.focus?.period }
    if (rest.isEmpty()) return

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(rest) { slot -> SlotRow(slot) }
    }
}

@Composable
private fun SlotRow(slot: Slot) {
    val done = slot.state == SlotState.DONE
    val ink = if (done) Muted else Ink

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(
                ImageProvider(
                    when {
                        done -> R.drawable.bg_row_done
                        slot.isStandby -> R.drawable.bg_row_standby
                        else -> R.drawable.bg_card
                    }
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = slot.spoken(if (done) "finished" else "upcoming") },
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = "P${slot.period}",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted),
            modifier = GlanceModifier.width(30.dp),
        )
        Text(
            text = slot.bell.start.hhmm(),
            style = TextStyle(fontSize = 13.sp, color = Muted),
            modifier = GlanceModifier.width(62.dp),
        )
        Text(
            text = slot.section ?: "Standby",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ink),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (!done) {
            Box(
                modifier = GlanceModifier
                    .size(6.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(3.dp),
                content = {},
            )
        }
    }
}

/* ─────────────────────────── helpers ────────────────────────── */

private val hourMinute: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())

private fun LocalTime.hhmm(): String = format(hourMinute)

private fun com.mrabah.oneuischedule.data.Bell.range(): String =
    "${start.hhmm()} – ${end.hhmm()}"

private fun Slot.spoken(status: String): String = buildString {
    append("Period $period, ${bell.start.hhmm()}, ")
    append(if (isStandby) "standby duty" else "class $section, $subject")
    append(", $status")
}

/* ══════════════════════════════════════════════════════════════
 *  RECEIVER + REFRESH SCHEDULING
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
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> refresh(context)
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
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.mrabah.oneuischedule.TICK"
    }
}

/**
 * Wakes the widget exactly at the next bell — and once a minute while a period
 * is running, so the countdown stays honest. Nothing is scheduled outside
 * school hours beyond the next day-start.
 */
object ScheduleUpdater {

    private const val REQUEST_CODE = 8501

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = ScheduleEngine.nextRefresh(LocalDateTime.now())
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarms.canScheduleExactAlarms()

        if (canBeExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent(context))
        } else {
            alarms.setWindow(
                AlarmManager.RTC,
                triggerAt,
                60_000L,
                pendingIntent(context),
            )
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
