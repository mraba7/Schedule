package com.mrabah.oneuischedule.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.*

/** A dated mode replaces bells and optionally the whole weekly assignment. Last matching mode wins. */
data class TimetableProfile(val id:String,val name:String,val from:LocalDate,val to:LocalDate,val bells:List<Bell>,val week:Map<DayOfWeek,Map<Int,Duty>>?=null)
internal object SchoolTools {
    fun profile(c:Config,date:LocalDate)=c.profiles.lastOrNull{date>=it.from && date<=it.to}
    fun bells(c:Config,date:LocalDate)=profile(c,date)?.bells ?: c.bells
    fun week(c:Config,date:LocalDate)=profile(c,date)?.week ?: c.week
    fun copyDay(c:Config,source:DayOfWeek,target:DayOfWeek)=c.copy(week=c.week+(target to c.templateOn(source).toMap()))
    fun swap(c:Config,a:DayOfWeek,p:Int,b:DayOfWeek,q:Int):Config {
        val first=c.templateOn(a)[p];val second=c.templateOn(b)[q]
        val week=c.week.mapValues{it.value.toMutableMap()}.toMutableMap()
        val left=week.getOrPut(a){mutableMapOf()};val right=week.getOrPut(b){mutableMapOf()}
        if(second==null)left.remove(p) else left[p]=second
        if(first==null)right.remove(q) else right[q]=first
        return c.copy(week=week)
    }
    fun encode(profiles:List<TimetableProfile>)=JSONArray().apply {profiles.forEach{profile->put(JSONObject()
        .put("id",profile.id).put("name",profile.name).put("from",profile.from).put("to",profile.to)
        .put("bells",JSONArray().apply{profile.bells.forEach{put(JSONObject().put("p",it.period).put("s",it.start).put("e",it.end))}})
        .put("week",profile.week?.let{week->JSONObject().apply{week.forEach{(day,duties)->put(day.name,JSONObject().apply{duties.forEach{(p,d)->put(p.toString(),if(d is Duty.Teach)d.section else "WAIT")}})}}))}}
    fun decode(array:JSONArray?):List<TimetableProfile> = if(array==null)emptyList() else (0 until array.length()).map {i->
        val j=array.getJSONObject(i);val bells=j.getJSONArray("bells");val week=j.optJSONObject("week")
        TimetableProfile(j.getString("id"),j.getString("name"),LocalDate.parse(j.getString("from")),LocalDate.parse(j.getString("to")),
            (0 until bells.length()).map{val b=bells.getJSONObject(it);Bell(b.getInt("p"),LocalTime.parse(b.getString("s")),LocalTime.parse(b.getString("e")))},
            week?.keys()?.asSequence()?.associate {key->DayOfWeek.valueOf(key) to week.getJSONObject(key).let{d->d.keys().asSequence().associate{p->p.toInt() to if(d.getString(p)=="WAIT") Duty.Standby else Duty.Teach(d.getString(p))}}})
    }
    fun color(c:Config,section:String?):String=c.classColors[section] ?: com.mrabah.oneuischedule.widget.GlassAgenda.color(section)
}
