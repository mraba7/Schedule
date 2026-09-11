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
    fun encode(profiles:List<TimetableProfile>):JSONArray = JSONArray().apply {
        profiles.forEach { profile ->
            val bells=JSONArray()
            profile.bells.forEach { bell -> bells.put(JSONObject().put("p",bell.period).put("s",bell.start.toString()).put("e",bell.end.toString())) }
            val item=JSONObject().put("id",profile.id).put("name",profile.name)
                .put("from",profile.from.toString()).put("to",profile.to.toString()).put("bells",bells)
            profile.week?.let { week ->
                val days=JSONObject()
                week.forEach { (day,duties) ->
                    val values=JSONObject()
                    duties.forEach { (period,duty) -> values.put(period.toString(),if(duty is Duty.Teach)duty.section else "WAIT") }
                    days.put(day.name,values)
                }
                item.put("week",days)
            }
            put(item)
        }
    }
    fun decode(array:JSONArray?):List<TimetableProfile> {
        if(array==null)return emptyList()
        return (0 until array.length()).map { index ->
            val item=array.getJSONObject(index)
            val rawBells=item.getJSONArray("bells")
            val bells=(0 until rawBells.length()).map { i ->
                val bell=rawBells.getJSONObject(i)
                Bell(bell.getInt("p"),LocalTime.parse(bell.getString("s")),LocalTime.parse(bell.getString("e")))
            }
            val days=item.optJSONObject("week")
            val week=days?.keys()?.asSequence()?.associate { key ->
                val values=days.getJSONObject(key)
                val duties=values.keys().asSequence().associate { p ->
                    val value=values.getString(p)
                    p.toInt() to if(value=="WAIT") Duty.Standby else Duty.Teach(value)
                }
                DayOfWeek.valueOf(key) to duties
            }
            TimetableProfile(item.getString("id"),item.getString("name"),LocalDate.parse(item.getString("from")),LocalDate.parse(item.getString("to")),bells,week)
        }
    }
    fun color(c:Config,section:String?):String=c.classColors[section] ?: com.mrabah.oneuischedule.widget.GlassAgenda.color(section)
}
