package com.mrabah.oneuischedule.data

import android.content.Context
import org.json.JSONObject
import java.util.UUID

internal data class ClassroomRecord(
    val id:String=UUID.randomUUID().toString(),val kind:String,val section:String,
    val title:String,val detail:String="",val extra:String="",val status:String="",
    val count:Int=0,val excluded:Boolean=false,val created:Long=System.currentTimeMillis(),val updated:Long=0
)
internal object ClassroomStore {
    private const val PREFIX="classroom:"
    fun all(c:Context):List<ClassroomRecord> = c.getSharedPreferences("app_preferences",0).all.filterKeys{it.startsWith(PREFIX)}.values.mapNotNull{raw->runCatching {
        val j=JSONObject(raw as String)
        ClassroomRecord(j.getString("id"),j.getString("kind"),j.getString("section"),j.getString("title"),j.optString("detail"),j.optString("extra"),j.optString("status"),j.optInt("count"),j.optBoolean("excluded"),j.optLong("created"),j.optLong("updated"))
    }.getOrNull()}
    fun save(c:Context,r:ClassroomRecord) {
        require(r.kind in setOf("student","exit","difficulty","lab","plan","question"))
        require(r.title.isNotBlank() && r.title.length<=160 && r.detail.length<=4000 && r.extra.length<=4000 && r.section.isNotBlank())
        if(r.kind=="student") {
            val seat=r.extra.toIntOrNull();require(seat!=null && seat in 1..60){"المقعد من 1 إلى 60"}
            require(all(c).none{it.kind=="student" && it.section==r.section && it.id!=r.id && it.extra.toIntOrNull()==seat}){"المقعد مستخدم؛ اختر مقعدًا آخر"}
        }
        DataVault.checkpoint(c,"تعديل إدارة الحصة")
        val j=JSONObject().put("id",r.id).put("kind",r.kind).put("section",r.section).put("title",r.title).put("detail",r.detail).put("extra",r.extra).put("status",r.status).put("count",r.count).put("excluded",r.excluded).put("created",r.created).put("updated",r.updated)
        c.getSharedPreferences("app_preferences",0).edit().putString(PREFIX+r.id,j.toString()).apply()
        if(r.kind=="question")com.mrabah.oneuischedule.widget.QuestionWidgetReceiver.refresh(c)
    }
    fun remove(c:Context,r:ClassroomRecord){DataVault.checkpoint(c,"حذف من إدارة الحصة");c.getSharedPreferences("app_preferences",0).edit().remove(PREFIX+r.id).apply();if(r.kind=="question")com.mrabah.oneuischedule.widget.QuestionWidgetReceiver.refresh(c)}
    fun choose(students:List<ClassroomRecord>):ClassroomRecord? = students.filter{!it.excluded}.minWithOrNull(compareBy<ClassroomRecord>{it.count}.thenBy{it.updated}.thenBy{it.extra.toIntOrNull() ?: 0})
    fun groups(students:List<ClassroomRecord>,number:Int):List<List<ClassroomRecord>> {
        require(number in 2..10)
        val available=students.filter{!it.excluded}.sortedByDescending{it.count}
        val result=List(number){mutableListOf<ClassroomRecord>()}
        available.forEachIndexed{i,s->val round=i/number;val col=i%number;result[if(round%2==0)col else number-1-col].add(s)}
        return result
    }
    fun participate(c:Context,r:ClassroomRecord)=save(c,r.copy(count=r.count+1,updated=System.currentTimeMillis()))
    fun publicRecord(c:Context,id:String?)=all(c).find{it.id==id && it.kind in listOf("question","lab")}
}
