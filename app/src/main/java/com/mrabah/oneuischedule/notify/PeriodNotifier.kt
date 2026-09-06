package com.mrabah.oneuischedule.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrabah.oneuischedule.MainActivity
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.ScheduleStore
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm at a time: it fires at the next bell, announces it with sound,
 * then arms the following one. Nothing is scheduled outside school hours.
 */
object PeriodNotifier {

    private const val CHANNEL = "period_bells"
    private const val REQUEST = 9110
    private const val NOTIFICATION_ID = 4201

    fun sync(context: Context) {
        val config = ScheduleStore.load(context)
        if (!config.notify) {
            cancel(context)
            return
        }
        ensureChannel(context)
        armNext(context, config, LocalDateTime.now(ZoneId.systemDefault()))
    }

    fun cancel(context: Context) {
        alarms(context)?.cancel(intent(context))
    }

    private fun armNext(context: Context, config: Config, now: LocalDateTime) {
        val at = nextBell(config, now) ?: return
        val trigger = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val manager = alarms(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent(context))
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, intent(context))
            }
        } catch (t: Throwable) {
            // permission revoked; silently skip rather than crash
        }
    }

    /** Looks up to a week ahead so Thursday evening arms Sunday morning. */
    private fun nextBell(config: Config, now: LocalDateTime): LocalDateTime? {
        var date = now.toLocalDate()
        repeat(8) {
            val hit = config.dutiesOn(date.dayOfWeek).keys
                .mapNotNull { p -> config.bells.firstOrNull { it.period == p } }
                .flatMap { listOf(date.atTime(it.start), date.atTime(it.end)) }
                .filter { it.isAfter(now) }
                .minOrNull()
            if (hit != null) return hit
            date = date.plusDays(1)
        }
        return null
    }

    fun announce(context: Context) {
        val config = ScheduleStore.load(context)
        if (!config.notify) return

        val now = LocalDateTime.now(ZoneId.systemDefault())
        val today = now.toLocalDate()
        val time = now.toLocalTime()

        // which bell just rang, within a minute of now
        val duties = config.dutiesOn(today.dayOfWeek)
        var title: String? = null
        var body: String? = null

        duties.forEach { (period, duty) ->
            val bell = config.bells.firstOrNull { it.period == period } ?: return@forEach
            val section = com.mrabah.oneuischedule.data.Duty.Standby.let {
                (duty as? com.mrabah.oneuischedule.data.Duty.Teach)?.section ?: "انتظار"
            }
            if (within(time, bell.start)) {
                title = "بدأت الحصة $period"
                body = "$section · ${Defaults.SUBJECT}"
            } else if (within(time, bell.end)) {
                title = "انتهت الحصة $period"
                body = "$section"
            }
        }

        val t = title
        if (t != null) notify(context, t, body ?: "")
        armNext(context, config, now.plusSeconds(30))
    }

    private fun within(a: java.time.LocalTime, b: java.time.LocalTime): Boolean {
        val diff = kotlin.math.abs(a.toSecondOfDay() - b.toSecondOfDay())
        return diff <= 90
    }

    private fun notify(context: Context, title: String, body: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            // POST_NOTIFICATIONS not granted
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL, "أجراس الحصص", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تنبيه صوتي عند بداية كل حصة ونهايتها"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, PeriodAlarmReceiver::class.java).setAction(ACTION_BELL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_BELL = "com.mrabah.oneuischedule.BELL"
}

class PeriodAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PeriodNotifier.ACTION_BELL -> PeriodNotifier.announce(context)
            else -> PeriodNotifier.sync(context)
        }
    }
}
