package com.mrabah.oneuischedule.data

import android.content.Context
import android.net.Uri
import org.json.*
import java.time.LocalDateTime
import java.util.UUID

internal data class JournalEntry(val id:String=UUID.randomUUID().toString(),val section:String,val title:String,val unit:String="",val text:String="",val lesson:Boolean=false,val done:Boolean=false,val file:String="",val fileName:String="",val mime:String="",val savedAt:LocalDateTime=LocalDateTime.now(),val remind:Boolean=false,val delivered:Boolean=false,val period:Int?=null)
internal object ClassJournal {
    private fun prefs(c:Context)=c.getSharedPreferences("class_journal",0)
    fun all(c:Context)=prefs(c).all.keys.mapNotNull {key->runCatching{decode(JSONObject(prefs(c).getString(key,"{}")!!))}.getOrNull()}.sortedByDescending{it.savedAt}
    fun forClass(c:Context,section:String)=all(c).filter{it.section==section}
    fun encode(e:JournalEntry)=JSONObject().put("id",e.id).put("section",e.section).put("title",e.title).put("unit",e.unit).put("text",e.text).put("lesson",e.lesson).put("done",e.done).put("file",e.file).put("fileName",e.fileName).put("mime",e.mime).put("savedAt",e.savedAt).put("remind",e.remind).put("delivered",e.delivered).put("period",e.period)
    private fun decode(j:JSONObject)=JournalEntry(j.getString("id"),j.getString("section"),j.getString("title"),j.optString("unit"),j.optString("text"),j.optBoolean("lesson"),j.optBoolean("done"),j.optString("file"),j.optString("fileName"),j.optString("mime"),LocalDateTime.parse(j.getString("savedAt")),j.optBoolean("remind"),j.optBoolean("delivered"),if(j.has("period"))j.getInt("period") else null)
    private fun put(c:Context,e:JournalEntry){prefs(c).edit().putString(e.id,encode(e).toString()).apply()}
    fun save(c:Context,e:JournalEntry) {
        require(e.title.isNotBlank())
        DataVault.checkpoint(c,"تعديل سجل الفصل ${e.section}")
        val old=all(c).find{it.id==e.id}
        put(c,e)
        if(e.done)androidx.core.app.NotificationManagerCompat.from(c).cancel(e.id,7402)
        if(e.lesson && old?.done!=e.done && (old!=null || e.done)) {
            val config=ScheduleStore.load(c);val progress=config.progress[e.section] ?: SectionProgress()
            ScheduleStore.save(c,config.copy(progress=config.progress+(e.section to progress.copy(taught=(progress.taught+if(e.done)1 else -1).coerceAtLeast(0),last=if(e.done)e.title else progress.last))))
        }
        c.sendBroadcast(android.content.Intent(c,com.mrabah.oneuischedule.widget.ScheduleWidgetReceiver::class.java).setAction(com.mrabah.oneuischedule.widget.ScheduleWidgetReceiver.ACTION_TICK))
        com.mrabah.oneuischedule.widget.ClassNoteReminders.sync(c)
    }
    fun delivered(c:Context,e:JournalEntry){put(c,e.copy(delivered=true))}
    fun remove(c:Context,e:JournalEntry){DataVault.checkpoint(c,"حذف من سجل الفصل");prefs(c).edit().remove(e.id).apply();androidx.core.app.NotificationManagerCompat.from(c).cancel(e.id,7402);com.mrabah.oneuischedule.widget.ClassNoteReminders.sync(c)}
    fun attach(c:Context,uri:Uri):Triple<String,String,String> {
        var name="مرفق"
        c.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=it.getString(0)}
        val files=DataVault.attachments(c)
        require(files.listFiles().orEmpty().sumOf{it.length()}<80L*1024*1024){"وصلت المرفقات إلى 80 ميجابايت"}
        val file=java.io.File(files,UUID.randomUUID().toString())
        try {c.contentResolver.openInputStream(uri)!!.use{input->file.outputStream().use{out->val bytes=ByteArray(8192);var total=0;while(true){val n=input.read(bytes);if(n<0)break;total+=n;require(total<=10*1024*1024){"الحد الأقصى للمرفق 10 ميجابايت"};out.write(bytes,0,n)}}}}
        catch(t:Throwable){file.delete();throw t}
        return Triple(file.name,name,c.contentResolver.getType(uri) ?: "application/octet-stream")
    }
}
