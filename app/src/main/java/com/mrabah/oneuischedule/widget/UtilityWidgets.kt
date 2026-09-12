package com.mrabah.oneuischedule.widget

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.util.SizeF
import android.widget.RemoteViews
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.ui.StudioActivity
import kotlinx.coroutines.*
import java.time.LocalDateTime

internal enum class UtilityKind(val title:String) { TWO_FACE("يومي بوجهين"), AVAILABLE("الوقت المتاح") }
internal object WidgetFaces {
    private fun prefs(c:Context)=c.getSharedPreferences("widget_preferences",0)
    fun isBack(c:Context,id:Int)=prefs(c).getBoolean("face:$id",false)
    @Synchronized fun toggle(c:Context,id:Int){prefs(c).edit().putBoolean("face:$id",!isBack(c,id)).apply()}
    fun remove(c:Context,id:Int){prefs(c).edit().remove("face:$id").apply()}
}

/** Shared by launcher, gallery and native screenshot tests. */
internal object UtilityRenderer {
    private val ink=Color.rgb(239,244,250)
    private val muted=Color.rgb(165,184,201)
    private val gold=Color.rgb(226,199,143)
    fun render(day:DesignDay,kind:UtilityKind,back:Boolean,width:Int=380,height:Int=420):Bitmap {
        val w=width.coerceIn(220,900).toFloat();val h=height.coerceIn(240,900).toFloat()
        val bitmap=Bitmap.createBitmap(w.toInt(),h.toInt(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val p=TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun box(x:Float,y:Float,r:Float,b:Float,color:Int,radius:Float=16f){p.color=color;canvas.drawRoundRect(x,y,r,b,radius,radius,p)}
        fun text(value:String,x:Float,y:Float,size:Float=14f,color:Int=ink,max:Float=w-32,bold:Boolean=false,align:Paint.Align=Paint.Align.RIGHT){p.color=color;p.textSize=size;p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);p.textAlign=align;canvas.drawText(TextUtils.ellipsize(value,p,max,TextUtils.TruncateAt.END).toString(),x,y,p)}
        box(0f,0f,w,h,Color.rgb(18,34,48),24f)
        text(kind.title,w-18,29f,17f,gold,bold=true)
        text("${day.day} · ${day.ui.slots.size} حصص",w-18,51f,12f,muted)
        val end=h-58;val ui=day.ui
        if(kind==UtilityKind.TWO_FACE && back && ui.slots.isNotEmpty()) {
            val columns=if(w>=340)2 else 1
            val rows=(ui.slots.size+columns-1)/columns
            val rowH=((end-65)/rows).coerceAtMost(65f)
            val cellW=(w-36-8*(columns-1))/columns
            ui.slots.forEachIndexed {index,slot->
                val right=w-18-(index%columns)*(cellW+8);val left=right-cellW;val top=65+(index/columns)*rowH
                val done=slot.state==SlotState.DONE;val live=slot.state==SlotState.LIVE
                box(left,top,right,top+rowH-5,if(live)Color.rgb(56,61,59) else Color.rgb(28,47,62),9f)
                val color=if(done)muted else Color.parseColor(SchoolTools.color(ui.config,slot.displaySection ?: ""))
                box(right-5,top+5,right-2,top+rowH-10,if(live)gold else color,2f)
                val label="${if(done)"✓ " else if(live)"● " else ""}${periodName(slot.period)} · ${DesignDay.section(slot)}"
                if(rowH>=40) {
                    text(label,right-10,top+18,12f,if(done)muted else ink,cellW-18,bold=live)
                    text("${DesignDay.clock(slot.bell.start)} – ${DesignDay.clock(slot.bell.end)}",right-10,top+35,11f,muted,cellW-18)
                } else text("${if(done)"✓ " else ""}${slot.period} · ${DesignDay.section(slot)} · ${DesignDay.clock(slot.bell.start)}–${DesignDay.clock(slot.bell.end)}",right-10,top+rowH*.65f,11f,if(done)muted else ink,cellW-18)
            }
        } else {
            val available=AvailableTime.from(day)
            val title=when(available.state){Availability.HOLIDAY->"اليوم إجازة";Availability.EMPTY->"يوم بلا حصص";Availability.FINISHED->"انتهت حصص اليوم";Availability.BUSY->if(kind==UtilityKind.AVAILABLE)"أنت في حصة الآن" else periodName(ui.live!!.period);Availability.FREE->if(kind==UtilityKind.AVAILABLE)"وقتك المتاح الآن" else "القادمة · ${periodName(ui.next!!.period)}"}
            box(14f,66f,w-14,end-4,Color.rgb(26,45,60),18f)
            val center=w/2;val roomy=h>=340
            text(title,center,if(roomy)101f else 91f,if(w>=340)22f else 17f,ink,w-50,true,Paint.Align.CENTER)
            if(available.minutes!=null) {
                text("${available.minutes}",center,if(roomy)163f else 129f,if(roomy)52f else 34f,gold,w-48,true,Paint.Align.CENTER)
                text(if(available.state==Availability.BUSY)"دقيقة حتى نهاية الحصة" else "دقيقة حتى بداية الحصة",center,if(roomy)187f else 149f,12f,muted,w-48,align=Paint.Align.CENTER)
                val focus=ui.focus!!
                if(roomy) {
                    text("${if(focus.isStandby)"انتظار · " else ""}الفصل ${DesignDay.section(focus)}",center,221f,20f,Color.parseColor(SchoolTools.color(ui.config,focus.displaySection ?: "")),w-48,true,Paint.Align.CENTER)
                    text("${DesignDay.clock(focus.bell.start)} – ${DesignDay.clock(focus.bell.end)}",center,247f,17f,ink,w-48,align=Paint.Align.CENTER)
                    val next=if(ui.live!=null)ui.next else null
                    if(next!=null && h>=390)text("التالي: ${periodName(next.period)} · ${DesignDay.section(next)} · ${DesignDay.clock(next.bell.start)}",center,280f,13f,muted,w-48,align=Paint.Align.CENTER)
                    val fraction=if(ui.live!=null)ui.progress else 0f
                    box(30f,end-23,w-30,end-19,Color.rgb(51,70,86),2f)
                    if(fraction>0)box(w-30-(w-60)*fraction,end-23,w-30,end-19,gold,2f)
                } else text("${DesignDay.section(focus)} · ${DesignDay.clock(focus.bell.start)} – ${DesignDay.clock(focus.bell.end)}",center,end-17,12f,ink,w-44,align=Paint.Align.CENTER)
            } else {
                text(ui.holiday?.label ?: if(available.state==Availability.FINISHED)"أنجزت ${ui.slots.size} حصص · وقتك لك" else "لا توجد التزامات في الجدول اليوم",center,if(roomy)154f else 125f,14f,muted,w-48,align=Paint.Align.CENTER)
                text(if(available.state==Availability.FINISHED)"✓" else "—",center,if(roomy)218f else 165f,36f,gold,w-48,align=Paint.Align.CENTER)
            }
        }
        box(12f,h-48,w/2-4,h-8,Color.rgb(34,53,69),12f);box(w/2+4,h-48,w-12,h-8,Color.rgb(34,53,69),12f)
        text(if(kind==UtilityKind.TWO_FACE)if(back)"الحصة الآن ↶" else "جدول اليوم ↶" else "فتح الجدول",w/4,h-23,12f,ink,w/2-28,align=Paint.Align.CENTER)
        text(if(ui.focus?.displaySection!=null)"اختصارات الفصل" else "الفصول",w*3/4,h-23,12f,gold,w/2-28,align=Paint.Align.CENTER)
        return bitmap
    }
}

internal object UtilityWidgets {
    val receivers=mapOf(UtilityKind.TWO_FACE to TwoFaceWidgetReceiver::class.java,UtilityKind.AVAILABLE to AvailableTimeWidgetReceiver::class.java)
    fun updateAll(c:Context){val m=AppWidgetManager.getInstance(c);receivers.forEach{(kind,receiver)->m.getAppWidgetIds(ComponentName(c,receiver)).forEach{update(c,m,it,kind)}}}
    fun update(c:Context,m:AppWidgetManager,id:Int,kind:UtilityKind) {
        val options=m.getAppWidgetOptions(id)
        @Suppress("DEPRECATION")
        val sizes=options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.filter{it.width>0 && it.height>0}?.distinct()?.take(4)
        val fallback=listOf(SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,380).toFloat(),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,420).toFloat()),SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,380).toFloat(),options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,420).toFloat())).distinct()
        val now=LocalDateTime.now();val day=DesignDay(ScheduleEngine.today(ScheduleStore.load(c),now),now)
        val back=WidgetFaces.isBack(c,id)
        val layouts=(sizes?.takeIf{it.isNotEmpty()} ?: fallback).associateWith {size->
            RemoteViews(c.packageName,R.layout.utility_widget).apply {
                setImageViewBitmap(R.id.utility_image,UtilityRenderer.render(day,kind,back,size.width.toInt(),size.height.toInt()))
                setContentDescription(R.id.utility_image,if(back && kind==UtilityKind.TWO_FACE)day.ui.slots.joinToString("، "){"${periodName(it.period)}، ${DesignDay.section(it)}، ${DesignDay.clock(it.bell.start)} إلى ${DesignDay.clock(it.bell.end)}"} else day.summary)
                val open=Intent(c,com.mrabah.oneuischedule.MainActivity::class.java).putExtra("tab",1).setData(Uri.parse("schedule://utility/$id/open"))
                val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                setOnClickPendingIntent(R.id.utility_image,PendingIntent.getActivity(c,0,open,flags))
                if(kind==UtilityKind.TWO_FACE) {
                    val toggle=Intent(c,TwoFaceWidgetReceiver::class.java).setAction(UtilityWidgetReceiver.FLIP).setData(Uri.parse("schedule://utility/$id/flip")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                    setOnClickPendingIntent(R.id.utility_flip,PendingIntent.getBroadcast(c,0,toggle,flags))
                } else setOnClickPendingIntent(R.id.utility_flip,PendingIntent.getActivity(c,0,open,flags))
                setContentDescription(R.id.utility_flip,if(kind==UtilityKind.AVAILABLE)"فتح الجدول" else if(back)"عرض الحصة الحالية" else "عرض جدول اليوم")
                val section=day.focus?.displaySection.orEmpty()
                val shortcuts=Intent(c,StudioActivity::class.java).setData(Uri.parse("schedule://utility/$id/shortcuts")).putExtra("page",if(section.isBlank())"classes" else "shortcuts").putExtra("section",section)
                setOnClickPendingIntent(R.id.utility_shortcuts,PendingIntent.getActivity(c,0,shortcuts,flags))
                setContentDescription(R.id.utility_shortcuts,if(section.isBlank())"الفصول" else "اختصارات الفصل $section")
            }
        }
        m.updateAppWidget(id,if(layouts.size==1)layouts.values.first() else RemoteViews(layouts))
    }
}

abstract class UtilityWidgetReceiver:AppWidgetProvider() {
    internal abstract val kind:UtilityKind
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){refresh(c){ids.forEach{UtilityWidgets.update(c,m,it,kind)}}}
    override fun onAppWidgetOptionsChanged(c:Context,m:AppWidgetManager,id:Int,options:Bundle){refresh(c){UtilityWidgets.update(c,m,id,kind)}}
    override fun onEnabled(c:Context){ScheduleUpdater.schedule(c)}
    override fun onDisabled(c:Context){if(!DesignWidgets.anyInstalled(c))ScheduleUpdater.cancel(c)}
    override fun onDeleted(c:Context,ids:IntArray){ids.forEach{WidgetFaces.remove(c,it)}}
    override fun onReceive(c:Context,intent:Intent) {
        super.onReceive(c,intent)
        if(intent.action==FLIP && kind==UtilityKind.TWO_FACE) {
            val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1);val m=AppWidgetManager.getInstance(c)
            if(id !in m.getAppWidgetIds(ComponentName(c,TwoFaceWidgetReceiver::class.java)))return
            refresh(c){WidgetFaces.toggle(c,id);UtilityWidgets.update(c,m,id,kind)}
        }
    }
    private fun refresh(c:Context,block:()->Unit){val pending=goAsync();CoroutineScope(Dispatchers.Default).launch{try{block();ScheduleUpdater.schedule(c)}finally{pending.finish()}}}
    companion object {const val FLIP="com.mrabah.oneuischedule.FLIP_WIDGET"}
}
class TwoFaceWidgetReceiver:UtilityWidgetReceiver(){override val kind=UtilityKind.TWO_FACE}
class AvailableTimeWidgetReceiver:UtilityWidgetReceiver(){override val kind=UtilityKind.AVAILABLE}
