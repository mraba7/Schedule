package com.mrabah.oneuischedule.widget

import android.appwidget.AppWidgetManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.content.*
import android.os.SystemClock
import kotlinx.coroutines.*

internal object LessonPeek {
    fun lessons(day:DesignDay)=day.ui.slots.filter{it.state!=com.mrabah.oneuischedule.data.SlotState.DONE}
    const val ACTION="com.mrabah.oneuischedule.PEEK"
    private fun prefs(c:Context)=c.getSharedPreferences("widget_lesson_peek",Context.MODE_PRIVATE)
    fun selected(c:Context,id:Int,date:String,now:Long=SystemClock.elapsedRealtime()):Int? {
        val p=prefs(c);val left=p.getLong("until:$id",0)-now
        return if(left in 1..15000 && now>=p.getLong("started:$id",Long.MAX_VALUE) && p.getString("date:$id",null)==date) p.getInt("period:$id",-1).takeIf{it>0} else null
    }
    @Synchronized fun toggle(c:Context,id:Int,date:String,period:Int,now:Long=SystemClock.elapsedRealtime()):Long {
        val until=if(selected(c,id,date,now)==period)0 else now+WidgetPreferences.get(c,id).peekSeconds*1000L
        prefs(c).edit().putString("date:$id",date).putInt("period:$id",period).putLong("until:$id",until).putLong("started:$id",now).apply()
        return until
    }
    @Synchronized fun expire(c:Context,id:Int,until:Long,now:Long=SystemClock.elapsedRealtime()):Boolean {
        if(until==0L || prefs(c).getLong("until:$id",0)!=until || now<until)return false
        prefs(c).edit().remove("until:$id").apply();return true
    }
    fun tiles(day:DesignDay,selected:Int?):List<android.graphics.RectF> {
        val visible=lessons(day)
        val count=visible.size;val columns=minOf(4,count.coerceAtLeast(1))
        val rows=(count+columns-1)/columns;val h=if(rows>1)25f else 43f
        val width=(336f-5f*(columns-1))/columns
        val selectedIndex=visible.indexOfFirst{it.period==selected}
        val expansion=if(visible.getOrNull(selectedIndex)?.isStandby==true)54f else 28f
        return visible.mapIndexed { i,slot ->
            val x=12f+(columns-1-i%columns)*(width+5f)
            val y=57f+(i/columns)*(h+4f)+if(selectedIndex>=0 && selectedIndex/columns<i/columns)expansion else 0f
            android.graphics.RectF(x,y,x+width,y+h+if(slot.period==selected)expansion else 0f)
        }
    }
    fun clear(c:Context,id:Int) {WidgetPreferences.clear(c,id);prefs(c).edit().remove("until:$id").remove("date:$id").remove("period:$id").remove("started:$id").apply()}
}

/** The UI timer must not keep a broadcast pending: Android may queue later taps. */
internal object PeekCollapseScheduler {
    const val ACTION="com.mrabah.oneuischedule.PEEK_COLLAPSE"
    private val handler=Handler(Looper.getMainLooper())
    private val tasks=mutableMapOf<Int,Runnable>()
    private fun intent(c:Context,id:Int,until:Long)=Intent(c,InteractiveWidgetReceiver::class.java)
        .setAction(ACTION).setData(Uri.parse("schedule-peek://collapse/$id"))
        .putExtra("widget",id).putExtra("until",until)
    private fun alarm(c:Context,id:Int,until:Long)=PendingIntent.getBroadcast(c,0,intent(c,id,until),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun schedule(context:Context,id:Int,until:Long) {
        val c=context.applicationContext
        tasks.remove(id)?.let{handler.removeCallbacks(it)}
        val manager=c.getSystemService(AlarmManager::class.java)
        manager?.cancel(alarm(c,id,until))
        if(until<=0)return
        val task=Runnable {
            synchronized(this) {tasks.remove(id)}
            c.sendBroadcast(intent(c,id,until))
        }
        tasks[id]=task
        handler.postDelayed(task,(until-SystemClock.elapsedRealtime()).coerceAtLeast(0))
        // Backup if the process goes away. The handler gives prompt foreground
        // collapse without requiring exact-alarm access or holding a broadcast.
        if(manager?.canScheduleExactAlarms()==true) {
            try {manager.setExact(AlarmManager.ELAPSED_REALTIME,until,alarm(c,id,until))}
            catch (_:SecurityException) { /* foreground handler remains armed */ }
        }
    }
}

class InteractiveWidgetReceiver:DesignWidgetReceiver() {
    override val design=Design.INTERACTIVE
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action=="com.mrabah.oneuischedule.NOTE_PEEK") {
            val id=intent.getIntExtra("widget",-1);val m=AppWidgetManager.getInstance(context)
            if(m.getAppWidgetInfo(id)?.provider!=ComponentName(context,InteractiveWidgetReceiver::class.java))return
            val key=intent.getStringExtra("key") ?: return
            val day=DesignDay.build(com.mrabah.oneuischedule.data.ScheduleStore.load(context))
            if(day.key!=key)return
            val prefs=context.getSharedPreferences("widget_lesson_peek",0)
            prefs.edit().putString("note:$id",if(prefs.getString("note:$id",null)==key)"" else key).apply()
            DesignWidgets.update(context,m,id,design)
            return
        }
        if(intent.action!=LessonPeek.ACTION && intent.action!=PeekCollapseScheduler.ACTION)return
        val id=intent.getIntExtra("widget",-1)
        val manager=AppWidgetManager.getInstance(context)
        if(manager.getAppWidgetInfo(id)?.provider!=ComponentName(context,InteractiveWidgetReceiver::class.java))return
        if(intent.action==PeekCollapseScheduler.ACTION) {
            if(!LessonPeek.expire(context,id,intent.getLongExtra("until",0)))return
        } else {
            val day=DesignDay.build(com.mrabah.oneuischedule.data.ScheduleStore.load(context))
            val date=intent.getStringExtra("date") ?: return
            val period=intent.getIntExtra("period",-1)
            if(day.ui.date.toString()!=date || day.ui.slots.none{it.period==period})return
            val until=LessonPeek.toggle(context,id,date,period)
            PeekCollapseScheduler.schedule(context,id,until)
        }
        val pending=goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {DesignWidgets.update(context,manager,id,design)}
            finally {pending.finish()}
        }
    }
    override fun onDeleted(context:Context,appWidgetIds:IntArray) {
        appWidgetIds.forEach{PeekCollapseScheduler.schedule(context,it,0);LessonPeek.clear(context,it)}
        super.onDeleted(context,appWidgetIds)
    }
}
