package com.mrabah.oneuischedule.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrabah.oneuischedule.MainActivity
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.ScheduleStore
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Three jobs on one alarm chain:
 *   1. a bell at every period start and end,
 *   2. a five-minute heads-up before each period,
 *   3. an ongoing progress notification while a period runs.
 *
 * Only one alarm exists at a time: it fires, does its work, and arms the next.
 */
object PeriodNotifier {

    private const val CH_BELL = "bells_school_v2"   // synthesised bell
    private const val CH_PLAIN = "bells_system_v2"  // device notification tone
    private const val CH_LIVE = "live_class_v1"     // silent, ongoing

    private const val REQUEST = 9110
    private const val ID_BELL = 4201
    private const val ID_LIVE = 4202

    private const val PRE_ALERT_MINUTES = 5L

    fun sync(context: Context) {
        val config = ScheduleStore.load(context)
        if (!config.notify) {
            cancel(context)
            NotificationManagerCompat.from(context).cancel(ID_LIVE)
            return
        }
        ensureChannels(context)
        tick(context, config, announce = false)
    }

    fun cancel(context: Context) {
        alarms(context)?.cancel(alarmIntent(context))
    }

    /** Called by the alarm. Works out what just happened, then arms the next. */
    fun onAlarm(context: Context) {
        val config = ScheduleStore.load(context)
        if (!config.notify) return
        ensureChannels(context)
        tick(context, config, announce = true)
    }

    private fun tick(context: Context, config: Config, announce: Boolean) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val ui = ScheduleEngine.build(config, now)
        val today = now.toLocalDate()
        val time = now.toLocalTime()

        if (announce && today.dayOfWeek in config.workdays) {
            config.dutiesOn(today.dayOfWeek).forEach { (period, duty) ->
                val bell = config.bells.firstOrNull { it.period == period } ?: return@forEach
                val section = (duty as? com.mrabah.oneuischedule.data.Duty.Teach)?.section ?: "انتظار"

                when {
                    near(time, bell.start) ->
                        ring(context, config, "بدأت الحصة $period", "$section · ${Defaults.SUBJECT}")

                    near(time, bell.end) ->
                        ring(context, config, "انتهت الحصة $period", section)

                    config.preAlert && near(time, bell.start.minusMinutes(PRE_ALERT_MINUTES)) ->
                        ring(
                            context, config,
                            "بعد $PRE_ALERT_MINUTES دقائق · الحصة $period",
                            lessonLine(config, section) ?: section,
                        )
                }
            }
        }

        // live progress notification
        val live = ui.live
        if (config.liveUpdate && live != null) {
            postLive(context, config, live.section ?: "انتظار", live.period,
                ui.progress, ui.minutesLeftInLive ?: 0L)
        } else {
            NotificationManagerCompat.from(context).cancel(ID_LIVE)
        }

        armNext(context, config, now)
    }

    /* ── scheduling ─────────────────────────────────────────── */

    private fun armNext(context: Context, config: Config, now: LocalDateTime) {
        val at = nextEvent(config, now) ?: return
        val trigger = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val manager = alarms(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context))
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, alarmIntent(context))
            }
        } catch (t: Throwable) {
            // exact alarms revoked; nothing worth crashing over
        }
    }

    private fun nextEvent(config: Config, now: LocalDateTime): LocalDateTime? {
        // while a period runs, tick every minute so the progress bar moves
        if (config.liveUpdate && ScheduleEngine.build(config, now).live != null) {
            return now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusMinutes(1)
        }
        var date = now.toLocalDate()
        repeat(8) {
            val marks = config.dutiesOn(date.dayOfWeek).keys
                .mapNotNull { p -> config.bells.firstOrNull { it.period == p } }
                .flatMap {
                    buildList {
                        add(date.atTime(it.start))
                        add(date.atTime(it.end))
                        if (config.preAlert) add(date.atTime(it.start).minusMinutes(PRE_ALERT_MINUTES))
                    }
                }
                .filter { it.isAfter(now) }
                .minOrNull()
            if (marks != null) return marks
            date = date.plusDays(1)
        }
        return null
    }

    private fun near(a: LocalTime, b: LocalTime): Boolean =
        kotlin.math.abs(a.toSecondOfDay() - b.toSecondOfDay()) <= 90

    private fun lessonLine(config: Config, section: String): String? =
        config.progress[section]?.next?.takeIf { it.isNotBlank() }?.let { "$section · $it" }

    /* ── notifications ──────────────────────────────────────── */

    private fun ring(context: Context, config: Config, title: String, body: String) {
        val channel = if (config.schoolBell) CH_BELL else CH_PLAIN
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        post(context, ID_BELL, notification)
    }

    private fun postLive(
        context: Context,
        config: Config,
        section: String,
        period: Int,
        progress: Float,
        minutesLeft: Long,
    ) {
        val lesson = config.progress[section]?.next?.takeIf { it.isNotBlank() }
        val builder = NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$section · الحصة $period")
            .setContentText(
                if (lesson != null) "$lesson · باقي $minutesLeft دقيقة"
                else "باقي $minutesLeft دقيقة"
            )
            .setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(context))
        post(context, ID_LIVE, builder.build())
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (t: Throwable) {
            // POST_NOTIFICATIONS not granted yet
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CH_BELL) == null) {
            val bellUri = Uri.parse(
                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.school_bell}"
            )
            manager.createNotificationChannel(
                NotificationChannel(CH_BELL, "جرس المدرسة", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "صوت جرس عند بداية الحصة ونهايتها"
                    enableVibration(true)
                    setSound(
                        bellUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
        }

        if (manager.getNotificationChannel(CH_PLAIN) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CH_PLAIN, "نغمة النظام", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "تنبيه الحصص بنغمة الإشعارات المعتادة"
                    enableVibration(true)
                }
            )
        }

        if (manager.getNotificationChannel(CH_LIVE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CH_LIVE, "الحصة الجارية", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "إشعار مستمر يعرض تقدّم الحصة الحالية"
                    setShowBadge(false)
                    setSound(null, null)
                }
            )
        }
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, PeriodAlarmReceiver::class.java).setAction(ACTION_BELL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_BELL = "com.mrabah.oneuischedule.BELL"
}

class PeriodAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            when (intent.action) {
                PeriodNotifier.ACTION_BELL -> PeriodNotifier.onAlarm(context)
                else -> PeriodNotifier.sync(context)
            }
        } finally {
            pending.finish()
        }
    }
}
