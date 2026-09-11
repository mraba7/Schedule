package com.mrabah.oneuischedule.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.UUID
import java.util.zip.*

/** User data only: alarms and elapsed-time widget peek state must never be restored. */
internal object DataVault {
    val stores=listOf("schedule_config","class_progress_notes","widget_lesson_details","class_journal","widget_preferences","app_preferences")
    private var restoring=false
    private fun folder(c:Context,name:String)=File(c.filesDir,name).apply{mkdirs()}
    fun attachments(c:Context)=folder(c,"attachments")
    fun snapshots(c:Context)=folder(c,"history").listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.name}.orEmpty()
    fun snapshot(c:Context):JSONObject=JSONObject().put("version",1).put("savedAt",System.currentTimeMillis()).put("stores",JSONObject().apply {
        stores.forEach {name-> put(name,JSONObject().apply {
            c.getSharedPreferences(name,0).all.forEach {(key,value)->
                put(key,JSONObject().put("type",when(value){is Boolean->"boolean";is Int->"int";is Long->"long";is Float->"float";is Set<*>->"set";else->"string"})
                    .put("value",if(value is Set<*>)JSONArray(value.toList()) else value))
            }
        }) }
    })
    @Synchronized fun checkpoint(c:Context,label:String) {
        if(restoring)return
        val data=snapshot(c).put("label",label)
        val previous=snapshots(c).firstOrNull()?.let {runCatching{JSONObject(it.readText()).getJSONObject("stores").toString()}.getOrNull()}
        if(previous==data.getJSONObject("stores").toString())return
        val file=File(folder(c,"history"),"${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        file.writeText(data.toString())
        snapshots(c).drop(30).forEach{it.delete()}
    }
    fun summary(data:JSONObject):String {
        val s=data.getJSONObject("stores")
        val config=s.optJSONObject("schedule_config")?.optJSONObject("config_json")?.optString("value")?.let {ScheduleStore.importJson(it)}
        return "${config?.sections?.size ?: 0} فصول · ${config?.holidays?.size ?: 0} إجازات · ${s.optJSONObject("class_progress_notes")?.length() ?: 0} ملاحظات تذكير\n${data.optString("label","نسخة كاملة للبيانات")}" 
    }
    private fun validate(data:JSONObject) {
        require(data.getInt("version")==1){"إصدار النسخة غير مدعوم"}
        val s=data.getJSONObject("stores")
        require(s.keys().asSequence().toSet()==stores.toSet()){"محتوى غير مدعوم"}
        val raw=s.optJSONObject("schedule_config")?.optJSONObject("config_json")?.optString("value")
        if(raw!=null)require(ScheduleStore.importJson(raw)?.let{ScheduleValidation.problem(it)==null}==true){"الجدول في النسخة غير صالح"}
        s.keys().forEach {name->s.getJSONObject(name).let {p->p.keys().forEach {key->
            val item=p.getJSONObject(key)
            when(item.getString("type")){"boolean"->item.getBoolean("value");"int"->item.getInt("value");"long"->item.getLong("value");"float"->item.getDouble("value");"set"->item.getJSONArray("value");"string"->item.getString("value");else->error("نوع بيانات غير مدعوم")}
        }}}
    }
    private fun write(c:Context,data:JSONObject) {
        val s=data.getJSONObject("stores")
        stores.forEach {name->
            val p=s.optJSONObject(name) ?: JSONObject()
            val edit=c.getSharedPreferences(name,0).edit().clear()
            p.keys().forEach {key->val item=p.getJSONObject(key);when(item.getString("type")) {
                "boolean"->edit.putBoolean(key,item.getBoolean("value"));"int"->edit.putInt(key,item.getInt("value"));"long"->edit.putLong(key,item.getLong("value"));"float"->edit.putFloat(key,item.getDouble("value").toFloat());"string"->edit.putString(key,item.getString("value"));"set"->{val a=item.getJSONArray("value");edit.putStringSet(key,(0 until a.length()).map{a.getString(it)}.toSet())}
            }}
            check(edit.commit()){"تعذّر حفظ البيانات"}
        }
    }
    @Synchronized fun restore(c:Context,data:JSONObject) {
        validate(data)
        checkpoint(c,"قبل استعادة البيانات")
        val original=snapshot(c)
        restoring=true
        try {write(c,data)}catch(t:Throwable){write(c,original);throw t}finally{restoring=false}
        com.mrabah.oneuischedule.notify.PeriodNotifier.sync(c)
        c.sendBroadcast(android.content.Intent(c,com.mrabah.oneuischedule.widget.ScheduleWidgetReceiver::class.java).setAction(com.mrabah.oneuischedule.widget.ScheduleWidgetReceiver.ACTION_TICK))
    }
    fun export(c:Context,out:OutputStream) {
        ZipOutputStream(out).use {zip->
            zip.putNextEntry(ZipEntry("data.json"));zip.write(snapshot(c).toString().toByteArray());zip.closeEntry()
            attachments(c).listFiles()?.filter{it.isFile}?.forEach {file->zip.putNextEntry(ZipEntry("attachments/${file.name}"));file.inputStream().use{it.copyTo(zip)};zip.closeEntry()}
        }
    }
    data class Import(val data:JSONObject,val directory:File) {fun discard(){directory.deleteRecursively()}}
    /** Stage and validate before changing any live data; restrict paths and expanded size. */
    fun preview(c:Context,input:InputStream):Import {
        val stage=File(c.cacheDir,"restore-${UUID.randomUUID()}").apply{mkdirs()}
        try {
            val buffered=input.buffered()
            buffered.mark(4)
            val magic=ByteArray(4);val read=buffered.read(magic);buffered.reset()
            if(read<2 || magic[0]!=80.toByte() || magic[1]!=75.toByte()) {
                val text=buffered.use { source ->
                    val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val n=source.read(buffer);if(n<0)break;require(out.size()+n<=4*1024*1024){"ملف الجدول كبير جدًا"};out.write(buffer,0,n)}
                    out.toByteArray()
                }
                require(text.size<=4*1024*1024){"ملف الجدول أكبر من الحد المسموح"}
                val config=ScheduleStore.importJson(String(text,Charsets.UTF_8)) ?: error("ملف الجدول غير صالح")
                val data=snapshot(c).put("label","نسخة جدول قديمة؛ تبقى ملاحظاتك وتجهيزاتك الحالية")
                data.getJSONObject("stores").put("schedule_config",JSONObject().put("config_json",JSONObject().put("type","string").put("value",ScheduleStore.exportJson(config))))
                validate(data)
                return Import(data,stage)
            }
            var total=0L;var count=0;val seen=mutableSetOf<String>()
            ZipInputStream(buffered).use {zip->while(true){val entry=zip.nextEntry ?: break
                require(++count<=1000 && seen.add(entry.name)){"عدد ملفات أو أسماء غير صالحة"}
                require(entry.name=="data.json" || Regex("attachments/[a-zA-Z0-9._-]+").matches(entry.name)){"مسار ملف غير صالح"}
                val target=File(stage,entry.name);target.parentFile!!.mkdirs()
                target.outputStream().use {out->val buffer=ByteArray(8192);while(true){val n=zip.read(buffer);if(n<0)break;total+=n;require(total<=100L*1024*1024){"النسخة أكبر من 100 ميجابايت"};out.write(buffer,0,n)}}
            }}
            require(File(stage,"data.json").length()<=4*1024*1024){"ملف البيانات أكبر من الحد المسموح"}
            val data=JSONObject(File(stage,"data.json").readText());validate(data)
            return Import(data,stage)
        }catch(t:Throwable){stage.deleteRecursively();throw t}
    }
    fun apply(c:Context,import:Import) {
        val files=File(import.directory,"attachments").listFiles().orEmpty()
        files.forEach {source->val target=File(attachments(c),source.name);require(!target.exists() || target.readBytes().contentEquals(source.readBytes())){"تعارض في ملف مرفق"}}
        files.forEach {source->val target=File(attachments(c),source.name);if(!target.exists())source.copyTo(target)}
        restore(c,import.data);import.discard()
    }
    fun daily(c:Context) {
        if(!c.getSharedPreferences("app_preferences",0).getBoolean("daily_backup",true))return
        val file=File(folder(c,"daily-backups"),"${java.time.LocalDate.now()}.zip")
        if(file.exists())return
        val temporary=File(file.parentFile,"pending.tmp")
        temporary.outputStream().use{export(c,it)}
        check(temporary.renameTo(file))
        file.parentFile!!.listFiles()?.filter{it.extension=="zip"}?.sortedByDescending{it.name}?.drop(7)?.forEach{it.delete()}
    }
    fun dailyFiles(c:Context)=folder(c,"daily-backups").listFiles()?.filter{it.extension=="zip"}?.sortedByDescending{it.name}.orEmpty()
}

internal object ScheduleValidation {
    fun problem(c:Config):String? {
        if(c.sections.isEmpty())return "أضف فصلًا واحدًا على الأقل"
        val bells=c.bells.sortedBy{it.period}
        if(bells.map{it.period}.distinct().size!=bells.size)return "رقم حصة مكرر"
        if(bells.any{it.period !in 1..7 || java.time.Duration.between(it.start,it.end).toMinutes()<5})return "تأكد من مدة كل حصة"
        if(bells.zipWithNext().any{(a,b)->b.start<a.end})return "أوقات الحصص متداخلة"
        if(c.holidays.any{it.to<it.from})return "نهاية الإجازة تسبق بدايتها"
        for(profile in c.profiles) {
            if(profile.to<profile.from || profile.name.isBlank())return "تواريخ التوقيت الخاص غير صالحة"
            problem(c.copy(bells=profile.bells,profiles=emptyList()))?.let{return it}
        }
        return null
    }
}
