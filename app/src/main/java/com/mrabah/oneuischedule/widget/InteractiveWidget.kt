package com.mrabah.oneuischedule.widget

import android.appwidget.AppWidgetManager
import android.content.*
import android.os.SystemClock
import kotlinx.coroutines.*

internal object LessonPeek {
    const val ACTION="com.mrabah.oneuischedule.PEEK"
    private fun prefs(c:Context)=c.getSharedPreferences("widget_lesson_peek",Context.MODE_PRIVATE)
    fun selected(c:Context,id:Int,date:String,now:Long=SystemClock.elapsedRealtime()):Int? {
        val p=prefs(c);val left=p.getLong("until:$id",0)-now
        return if(left in 1..5000 && p.getString("date:$id",null)==date) p.getInt("period:$id",-1).takeIf{it>0} else null
    }
    @Synchronized fun toggle(c:Context,id:Int,date:String,period:Int,now:Long=SystemClock.elapsedRealtime()):Long {
        val until=if(selected(c,id,date,now)==period)0 else now+5000
        prefs(c).edit().putString("date:$id",date).putInt("period:$id",period).putLong("until:$id",until).apply()
        return until
    }
    @Synchronized fun expire(c:Context,id:Int,until:Long,now:Long=SystemClock.elapsedRealtime()):Boolean {
        if(until==0L || prefs(c).getLong("until:$id",0)!=until || now<until)return false
        prefs(c).edit().remove("until:$id").apply();return true
    }
    fun tiles(day:DesignDay,selected:Int?):List<android.graphics.RectF> {
        val count=day.ui.slots.size;val columns=minOf(4,count.coerceAtLeast(1))
        val rows=(count+columns-1)/columns;val h=if(rows>1)25f else 43f
        val width=(336f-5f*(columns-1))/columns
        val selectedIndex=day.ui.slots.indexOfFirst{it.period==selected}
        return day.ui.slots.mapIndexed { i,slot ->
            val x=12f+(columns-1-i%columns)*(width+5f)
            val y=57f+(i/columns)*(h+4f)+if(selectedIndex>=0 && selectedIndex/columns<i/columns)28f else 0f
            android.graphics.RectF(x,y,x+width,y+h+if(slot.period==selected)28f else 0f)
        }
    }
    fun clear(c:Context,id:Int) {prefs(c).edit().remove("until:$id").remove("date:$id").remove("period:$id").apply()}
}

class InteractiveWidgetReceiver:DesignWidgetReceiver() {
    override val design=Design.INTERACTIVE
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action!=LessonPeek.ACTION)return
        val id=intent.getIntExtra("widget",-1)
        val manager=AppWidgetManager.getInstance(context)
        if(manager.getAppWidgetInfo(id)?.provider!=ComponentName(context,InteractiveWidgetReceiver::class.java))return
        val day=DesignDay.build(com.mrabah.oneuischedule.data.ScheduleStore.load(context))
        val date=intent.getStringExtra("date") ?: return
        val period=intent.getIntExtra("period",-1)
        if(day.ui.date.toString()!=date || day.ui.slots.none{it.period==period})return
        val until=LessonPeek.toggle(context,id,date,period)
        val pending=goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                DesignWidgets.update(context,manager,id,design)
                if(until>0) {
                    delay((until-SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    if(LessonPeek.expire(context,id,until) && manager.getAppWidgetInfo(id)!=null)
                        DesignWidgets.update(context,manager,id,design)
                }
            } finally {pending.finish()}
        }
    }
    override fun onDeleted(context:Context,appWidgetIds:IntArray) {
        appWidgetIds.forEach{LessonPeek.clear(context,it)}
        super.onDeleted(context,appWidgetIds)
    }
}
