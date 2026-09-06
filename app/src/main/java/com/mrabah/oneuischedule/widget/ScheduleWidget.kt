package com.mrabah.oneuischedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
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

/*
 * Built from core Glance constructs only — colour backgrounds, cornerRadius,
 * Column/Row/Text. Drawable-image backgrounds and LazyColumn were the likely
 * source of the composition failure, so they are out of the render path.
 */

private val Muted = ColorProvider(R.color.widget_muted)
private val Ink = ColorProvider(R.color.widget_ink)
private val Panel = ColorProvider(R.color.widget_panel)
private val RowBg = ColorProvider(R.color.widget_row)
private val Track = ColorProvider(R.color.widget_progress_track)

private val Compact = DpSize(260.dp, 120.dp)
private val Medium = DpSize(260.dp, 200.dp)
private val Large = DpSize(300.dp, 300.dp)

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Medium, Large))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // State is built outside composition so a data error can't kill the UI.
        val ui = ScheduleEngine.build(LocalDateTime.now(ZoneId.systemDefault()))
        provideContent {
            GlanceTheme { WidgetRoot(ui) }
        }
    }
}

@Composable
private fun WidgetRoot(ui: ScheduleUi) {
    val height = LocalSize.current.height

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Panel)
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

@Composable
private fun Header(ui: ScheduleUi) {
    val locale = Locale.getDefault()

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Ink),
            )
            Text(
                text = ui.date.format(DateTimeFormatter.ofPattern("d MMMM", locale)),
                style = TextStyle(fontSize = 11.sp, color = Muted),
            )
        }
        Text(
            text = if (ui.remaining > 0) "${ui.remaining} متبقية" else "",
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Muted),
        )
    }
}

@Composable
private fun Hero(ui: ScheduleUi) {
    val slot = ui.focus

    if (slot == null) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(RowBg)
                .cornerRadius(24.dp)
                .padding(14.dp)
        ) {
            Text(
                text = "ما عندك حصص باقية اليوم",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink),
            )
        }
        return
    }

    val live = ui.live != null
    val status = when {
        live -> "الآن · باقي ${ui.minutesLeftInLive} د"
        ui.isAssembly -> "الطابور · بعدها"
        !ui.isToday -> "أول حصة"
        ui.minutesUntilNext != null -> "بعد ${ui.minutesUntilNext} د"
        else -> "القادمة"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                if (slot.isStandby) GlanceTheme.colors.surfaceVariant
                else GlanceTheme.colors.primaryContainer
            )
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "الحصة ${slot.period} · $status",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onPrimaryContainer,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = slot.bell.start.hhmm() + " - " + slot.bell.end.hhmm(),
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = slot.section ?: "انتظار",
                style = TextStyle(
                    fontSize = if (slot.isStandby) 22.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onPrimaryContainer,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = slot.subject ?: "لا يوجد فصل",
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onPrimaryContainer),
            )
        }

        if (live) {
            Spacer(GlanceModifier.height(10.dp))
            LinearProgressIndicator(
                progress = ui.progress,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = Track,
            )
        }
    }
}

@Composable
private fun DayList(ui: ScheduleUi) {
    val rest = ui.slots.filter { it.period != ui.focus?.period }
    if (rest.isEmpty()) return

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        rest.forEach { slot -> SlotRow(slot) }
    }
}

@Composable
private fun SlotRow(slot: Slot) {
    val done = slot.state == SlotState.DONE

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(if (done) Track else RowBg)
            .cornerRadius(18.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = "${slot.period}",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted),
            modifier = GlanceModifier.width(24.dp),
        )
        Text(
            text = slot.bell.start.hhmm(),
            style = TextStyle(fontSize = 13.sp, color = Muted),
            modifier = GlanceModifier.width(56.dp),
        )
        Text(
            text = slot.section ?: "انتظار",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (done) Muted else Ink,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

private val hourMinute: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())

private fun LocalTime.hhmm(): String = format(hourMinute)

/* ══════════════════════════════════════════════════════════════ */

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> refresh(context)
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
                // never let a refresh crash the launcher's broadcast
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
        val at = ScheduleEngine.nextRefresh(LocalDateTime.now())
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
