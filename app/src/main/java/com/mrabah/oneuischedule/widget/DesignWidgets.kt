package com.mrabah.oneuischedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.ScheduleStore
import kotlinx.coroutines.*

internal object PreparationStore {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_lesson_details", Context.MODE_PRIVATE)
    fun task(c: Context,key:String?) = key?.let { prefs(c).getString("task:$it", "") }.orEmpty()
    fun note(c: Context,key:String?) = key?.let { prefs(c).getString("note:$it", "") }.orEmpty()
    fun done(c: Context,key:String?) = key!=null && prefs(c).getBoolean("done:$key",false)
    fun save(c:Context,key:String,task:String,note:String) {
        val previous=PreparationStore.task(c,key)
        prefs(c).edit().putString("task:$key",task).putString("note:$key",note).apply {
            if(previous!=task) putBoolean("done:$key",false)
        }.apply()
    }
    fun toggle(c:Context,key:String) {
        if(task(c,key).isNotBlank()) prefs(c).edit().putBoolean("done:$key",!done(c,key)).apply()
    }
}

/** One shared alarm keeps old Glance widgets and these native widgets alive. */
internal object DesignWidgets {
    val receivers = linkedMapOf(
        Design.TICKET to TicketWidgetReceiver::class.java,
        Design.ORBIT to OrbitWidgetReceiver::class.java,
        Design.EDITORIAL to EditorialWidgetReceiver::class.java,
        Design.BENTO to BentoWidgetReceiver::class.java,
        Design.ROUTE to RouteWidgetReceiver::class.java,
        Design.SPORT to SportWidgetReceiver::class.java,
        Design.BLUEPRINT to BlueprintWidgetReceiver::class.java,
    )
    fun updateAll(context: Context) {
        val manager=AppWidgetManager.getInstance(context)
        val day=DesignDay.build(ScheduleStore.load(context))
        val renderer=DesignRenderer(context)
        receivers.forEach { (style,receiver) ->
            manager.getAppWidgetIds(ComponentName(context,receiver)).forEach { id ->
                update(context,manager,id,style,day,renderer)
            }
        }
    }
    fun update(context:Context,manager:AppWidgetManager,id:Int,style:Design,
               day:DesignDay=DesignDay.build(ScheduleStore.load(context)),renderer:DesignRenderer=DesignRenderer(context)) {
        val opts=manager.getAppWidgetOptions(id)
        // Android 12 provides the full portrait/landscape size list. Supplying
        // both layouts lets the launcher choose the matching orientation.
        @Suppress("DEPRECATION")
        val sizes=opts.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.filter { it.width>0 && it.height>0 }?.distinct()?.take(4)
        val fallback=android.util.SizeF(
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,320).coerceAtLeast(180).toFloat(),
            opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,320).coerceAtLeast(180).toFloat())
        val layouts=(sizes?.takeIf { it.isNotEmpty() } ?: listOf(fallback)).associateWith { size ->
            makeViews(context,style,day,renderer,size.width,size.height)
        }
        manager.updateAppWidget(id,if(layouts.size==1) layouts.values.first() else RemoteViews(layouts))
    }
    private fun makeViews(c:Context,style:Design,day:DesignDay,renderer:DesignRenderer,w:Float,h:Float):RemoteViews {
        val width=w.coerceIn(120f,900f);val height=h.coerceIn(120f,900f)
        // Conservative bitmap budget: max 640px long edge per orientation.
        val density=minOf(2f,640f/maxOf(width,height))
        val bitmap=renderer.render(style,day,(width*density).toInt(),(height*density).toInt(),
            PreparationStore.task(c,day.key).ifBlank { "تحديد التجهيز" },PreparationStore.done(c,day.key))
        val views=RemoteViews(c.packageName,R.layout.design_widget)
        views.setImageViewBitmap(R.id.design_image,bitmap)
        views.setContentDescription(R.id.design_image,day.summary)
        val intent=Intent(c,DesignLessonActivity::class.java)
            .setData(Uri.parse("schedule-design://lesson/${day.key ?: "empty"}"))
            .putExtra("key",day.key).putExtra("title","${day.day} · ${day.period} · ${day.section}")
        val open=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.design_image,open)
        // A real, accessible touch target laid over the painted task checkbox.
        val taskY=when(style){Design.TICKET->296f;Design.BENTO->320f;Design.BLUEPRINT->286f;else->null}
        views.setViewVisibility(R.id.design_task,if(taskY!=null && day.key!=null) View.VISIBLE else View.GONE)
        if(taskY!=null && day.key!=null) {
            val scale=minOf(width,height)/360f
            views.setViewLayoutWidth(R.id.design_task,48f*scale,TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.design_task,40f*scale,TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutMargin(R.id.design_task,RemoteViews.MARGIN_LEFT,(width-360*scale)/2+16*scale,TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutMargin(R.id.design_task,RemoteViews.MARGIN_TOP,(height-360*scale)/2+taskY*scale,TypedValue.COMPLEX_UNIT_DIP)
            val label=if(PreparationStore.done(c,day.key)) "إلغاء إنجاز التجهيز" else "تم تجهيز الحصة"
            views.setContentDescription(R.id.design_task,label)
            val toggle=Intent(c,receivers.getValue(style)).setAction("com.mrabah.oneuischedule.PREPARED")
                .setData(Uri.parse("schedule-design://toggle/${day.key}"))
                .putExtra("key",day.key)
            val action=if(PreparationStore.task(c,day.key).isBlank()) open else PendingIntent.getBroadcast(c,0,toggle,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.design_task,action)
        }
        return views
    }
    fun anyInstalled(c:Context):Boolean {
        val manager=AppWidgetManager.getInstance(c)
        return manager.installedProviders.filter{it.provider.packageName==c.packageName}
            .any{manager.getAppWidgetIds(it.provider).isNotEmpty()}
    }
}

abstract class DesignWidgetReceiver : AppWidgetProvider() {
    internal abstract val design: Design
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        refresh(context) { ids.forEach{DesignWidgets.update(context,manager,it,design)} }
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle) {
        refresh(context){DesignWidgets.update(context,manager,id,design)}
    }
    override fun onEnabled(context:Context) { ScheduleUpdater.schedule(context) }
    override fun onDisabled(context:Context) {
        if(!DesignWidgets.anyInstalled(context)) ScheduleUpdater.cancel(context)
    }
    override fun onReceive(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action=="com.mrabah.oneuischedule.PREPARED") {
            val key=intent.getStringExtra("key") ?: return
            if(!Regex("\\d{4}-\\d{2}-\\d{2}#\\d{1,2}").matches(key)) return
            refresh(context){PreparationStore.toggle(context,key);DesignWidgets.updateAll(context)}
        }
    }
    private fun refresh(context:Context,block:()->Unit) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { block();ScheduleUpdater.schedule(context) }
            finally { pending.finish() }
        }
    }
}
class TicketWidgetReceiver:DesignWidgetReceiver(){override val design=Design.TICKET}
class OrbitWidgetReceiver:DesignWidgetReceiver(){override val design=Design.ORBIT}
class EditorialWidgetReceiver:DesignWidgetReceiver(){override val design=Design.EDITORIAL}
class BentoWidgetReceiver:DesignWidgetReceiver(){override val design=Design.BENTO}
class RouteWidgetReceiver:DesignWidgetReceiver(){override val design=Design.ROUTE}
class SportWidgetReceiver:DesignWidgetReceiver(){override val design=Design.SPORT}
class BlueprintWidgetReceiver:DesignWidgetReceiver(){override val design=Design.BLUEPRINT}
