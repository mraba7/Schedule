package com.mrabah.oneuischedule.widget

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mrabah.oneuischedule.data.*
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

internal data class ClassNote(val section:String, val text:String, val savedAt:LocalDateTime,
    val reminder:Boolean=true, val delivered:Boolean=false)
internal data class ClassVisit(val start:LocalDateTime,val end:LocalDateTime,val period:Int)

internal object ClassNotes {
    private fun prefs(c:Context)=c.getSharedPreferences("class_progress_notes",Context.MODE_PRIVATE)
    fun get(c:Context,section:String?):ClassNote? {
        if(section==null) return null
        return runCatching {
            val raw=prefs(c).getString(section,null) ?: return null
            val j=JSONObject(raw)
            ClassNote(section,j.getString("text"),LocalDateTime.parse(j.getString("savedAt")),
                j.optBoolean("reminder",true),j.optBoolean("delivered",false))
        }.getOrNull()
    }
    fun all(c:Context)=prefs(c).all.keys.mapNotNull { get(c,it) }
    fun save(c:Context,section:String,text:String,reminder:Boolean,now:LocalDateTime=LocalDateTime.now()) {
        com.mrabah.oneuischedule.data.DataVault.checkpoint(c,"تعديل ملاحظة الفصل")
        if(text.isBlank()) { complete(c,section);return }
        put(c,ClassNote(section,text.trim().take(1000),now,reminder))
        NotificationManagerCompat.from(c).cancel(section,7401)
    }
    private fun put(c:Context,n:ClassNote) {
        prefs(c).edit().putString(n.section,JSONObject().put("text",n.text).put("savedAt",n.savedAt.toString())
            .put("reminder",n.reminder).put("delivered",n.delivered).toString()).apply()
    }
    fun delivered(c:Context,n:ClassNote) {
        if(get(c,n.section)==n) put(c,n.copy(delivered=true))
    }
    fun complete(c:Context,section:String) {
        DataVault.checkpoint(c,"إنهاء ملاحظة الفصل")
        prefs(c).edit().remove(section).apply()
        NotificationManagerCompat.from(c).cancel(section,7401)
    }
    /** Re-evaluates real duties, overrides and holidays, never weekday guesses. */
    fun next(config:Config,n:ClassNote,now:LocalDateTime):ClassVisit? {
        for(offset in 0..366) {
            val date=now.toLocalDate().plusDays(offset.toLong())
            val duties=ScheduleEngine.dutiesOn(config,date)
            SchoolTools.bells(config,date).sortedBy { it.start }.forEach { bell ->
                val section=(duties[bell.period] as? Duty.Teach)?.section
                val start=date.atTime(bell.start);val end=date.atTime(bell.end)
                if(section==n.section && start>n.savedAt && end>now) return ClassVisit(start,end,bell.period)
            }
        }
        return null
    }
    fun intent(c:Context,section:String)=Intent(c,ClassNoteActivity::class.java)
        .setData(Uri.parse("schedule-class://note/${Uri.encode(section)}")).putExtra("section",section)
}

internal object ClassNoteReminders {
    private const val CHANNEL="class_progress_v1"
    private fun alarm(c:Context)=PendingIntent.getBroadcast(c,7400,Intent(c,ClassNoteReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun sync(c:Context,now:LocalDateTime=LocalDateTime.now()) {
        val manager=c.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(alarm(c))
        val config=ScheduleStore.load(c)
        val nm=c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,"آخر نقطة للفصل",NotificationManager.IMPORTANCE_DEFAULT))
        val enabled=NotificationManagerCompat.from(c).areNotificationsEnabled() &&
            nm.getNotificationChannel(CHANNEL)?.importance!=NotificationManager.IMPORTANCE_NONE
        var nextAt:LocalDateTime?=null
        ClassNotes.all(c).filter { it.reminder && !it.delivered }.forEach { note ->
            val visit=ClassNotes.next(config,note,now) ?: return@forEach
            if(visit.start<=now) {
                if(enabled && config.mutedDate!=now.toLocalDate().toString()) {
                    val open=PendingIntent.getActivity(c,0,ClassNotes.intent(c,note.section),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification=NotificationCompat.Builder(c,CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("الفصل ${note.section} · ${periodName(visit.period)}")
                        .setContentText(note.text).setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
                        .setContentIntent(open).setAutoCancel(true).build()
                    try { NotificationManagerCompat.from(c).notify(note.section,7401,notification);ClassNotes.delivered(c,note) }
                    catch (_:SecurityException) { /* note remains available in the widget */ }
                }
            } else if(nextAt==null || visit.start<nextAt!!) nextAt=visit.start
        }
        val journal=ClassJournal.all(c).filter {it.remind && !it.delivered && !it.done}
        journal.forEach { entry ->
            val visit=ClassNotes.next(config,ClassNote(entry.section,entry.text,entry.savedAt),now) ?: return@forEach
            if(visit.start<=now && enabled && config.mutedDate!=now.toLocalDate().toString()) {
                val target=Intent(c,com.mrabah.oneuischedule.ui.StudioActivity::class.java).setData(Uri.parse("schedule-journal://${entry.id}")).putExtra("page","class").putExtra("section",entry.section)
                val open=PendingIntent.getActivity(c,0,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    NotificationManagerCompat.from(c).notify(entry.id,7402,NotificationCompat.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("الفصل ${entry.section} · ${entry.title}").setContentText(entry.text).setStyle(NotificationCompat.BigTextStyle().bigText(entry.text)).setContentIntent(open).setAutoCancel(true).build())
                    ClassJournal.delivered(c,entry)
                }catch(_:SecurityException){}
            } else if(visit.start>now && (nextAt==null || visit.start<nextAt!!))nextAt=visit.start
        }
        // A daily check also handles notes whose next class is not yet scheduled.
        val at=nextAt ?: if(ClassNotes.all(c).any { it.reminder && !it.delivered } || journal.isNotEmpty()) now.toLocalDate().plusDays(1).atStartOfDay().plusHours(5) else return
        val millis=at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if(manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,alarm(c))
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,alarm(c))
        } catch (_:SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,alarm(c)) }
    }
}

class ClassNoteReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        Thread {
            try { ClassNoteReminders.sync(context);DesignWidgets.updateAll(context) }
            finally { pending.finish() }
        }.start()
    }
}

internal fun periodName(period:Int)=when(period) {
    1->"الحصة الأولى";2->"الحصة الثانية";3->"الحصة الثالثة";4->"الحصة الرابعة"
    5->"الحصة الخامسة";6->"الحصة السادسة";7->"الحصة السابعة";else->"الحصة $period"
}
