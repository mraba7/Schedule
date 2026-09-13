package com.mrabah.oneuischedule.widget

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.ClassroomStore
import com.mrabah.oneuischedule.ui.StudioActivity
import java.time.LocalDate
import org.json.JSONObject

class QuestionWidgetReceiver:AppWidgetProvider() {
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){ids.forEach{update(c,m,it)};ScheduleUpdater.schedule(c)}
    override fun onAppWidgetOptionsChanged(c:Context,m:AppWidgetManager,id:Int,options:Bundle){update(c,m,id)}
    override fun onEnabled(c:Context){ScheduleUpdater.schedule(c)}
    override fun onDisabled(c:Context){if(!DesignWidgets.anyInstalled(c))ScheduleUpdater.cancel(c)}
    override fun onDeleted(c:Context,ids:IntArray){val edit=c.getSharedPreferences("widget_preferences",0).edit();ids.forEach{edit.remove("question:$it")};edit.apply()}
    override fun onReceive(c:Context,intent:Intent) {
        super.onReceive(c,intent)
        if(intent.action !in listOf(REVEAL,NEXT))return
        val id=intent.getIntExtra("widget",-1);val m=AppWidgetManager.getInstance(c)
        if(id !in m.getAppWidgetIds(ComponentName(c,QuestionWidgetReceiver::class.java)))return
        update(c,m,id,intent.action)
    }
    companion object {
        const val REVEAL="com.mrabah.oneuischedule.QUESTION_REVEAL"
        const val NEXT="com.mrabah.oneuischedule.QUESTION_NEXT"
        fun refresh(c:Context){val m=AppWidgetManager.getInstance(c);m.getAppWidgetIds(ComponentName(c,QuestionWidgetReceiver::class.java)).forEach{update(c,m,it)}}
        @Synchronized internal fun update(c:Context,m:AppWidgetManager,id:Int,action:String?=null) {
            val prefs=c.getSharedPreferences("widget_preferences",0)
            val today=LocalDate.now().toString()
            val state=runCatching{JSONObject(prefs.getString("question:$id","{}")!!)}.getOrDefault(JSONObject())
            val questions=ClassroomStore.all(c).filter{it.kind=="question"}.sortedBy{it.id}
            var offset=if(state.optString("date")==today)state.optInt("offset") else 0
            if(action==NEXT)offset=if(questions.isEmpty())0 else Math.floorMod(offset+1,questions.size)
            val question=questions.takeIf{it.isNotEmpty()}?.let{it[Math.floorMod(LocalDate.now().toEpochDay()+offset,it.size.toLong()).toInt()]}
            val key=question?.let{"${it.id}|${it.title}|${it.detail}"}.orEmpty()
            var reveal=state.optString("date")==today && state.optString("key")==key && state.optBoolean("reveal")
            if(action==NEXT)reveal=false
            if(action==REVEAL)reveal=!reveal
            prefs.edit().putString("question:$id",JSONObject().put("date",today).put("offset",offset).put("key",key).put("reveal",reveal).toString()).apply()
            val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val views=RemoteViews(c.packageName,R.layout.question_widget)
            views.setTextViewText(R.id.question_title,question?.let{"سؤال اليوم · ${it.section}"} ?: "سؤال اليوم")
            views.setTextViewText(R.id.question_body,question?.let{if(reveal)it.detail.ifBlank{"لم تُضف الإجابة بعد"} else it.title} ?: "أضف أسئلتك من إدارة الحصة ← بنك الأسئلة")
            views.setTextViewText(R.id.question_reveal,if(reveal)"السؤال" else "الإجابة")
            listOf(R.id.question_reveal to REVEAL,R.id.question_next to NEXT).forEach{(view,a)->views.setOnClickPendingIntent(view,PendingIntent.getBroadcast(c,0,Intent(c,QuestionWidgetReceiver::class.java).setAction(a).setData(Uri.parse("schedule://question/$id/$a")).putExtra("widget",id),flags))}
            val open=Intent(c,StudioActivity::class.java).setData(Uri.parse("schedule://question/$id/open")).putExtra("page","classroom").putExtra("section",question?.section.orEmpty()).putExtra("classroom_mode","question")
            views.setOnClickPendingIntent(R.id.question_open,PendingIntent.getActivity(c,0,open,flags))
            m.updateAppWidget(id,views)
        }
    }
}
