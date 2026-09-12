package com.mrabah.oneuischedule.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class ClassShortcut(val id:String=UUID.randomUUID().toString(),val section:String,val title:String,val kind:String,val target:String,val mime:String="")

/** Included in the existing comprehensive backup, along with private attachments. */
internal object ClassShortcuts {
    private const val PREFIX="class-shortcut:"
    private fun prefs(c:Context)=c.getSharedPreferences("app_preferences",0)
    fun forClass(c:Context,section:String):List<ClassShortcut> = prefs(c).all.filterKeys{it.startsWith(PREFIX)}.values.mapNotNull {raw->runCatching {
        val j=JSONObject(raw as String)
        ClassShortcut(j.getString("id"),j.getString("section"),j.getString("title"),j.getString("kind"),j.getString("target"),j.optString("mime"))
    }.getOrNull()}.filter{it.section==section}.sortedBy{it.title}
    fun validLink(value:String):Boolean = runCatching {val uri=java.net.URI(value.trim());uri.scheme?.lowercase() in listOf("https","http") && !uri.host.isNullOrBlank() && uri.userInfo==null}.getOrDefault(false)
    fun save(c:Context,s:ClassShortcut) {
        require(s.section.isNotBlank() && s.title.isNotBlank() && s.title.length<=80)
        require(when(s.kind){"link"->validLink(s.target);"file"->Regex("[a-zA-Z0-9-]+").matches(s.target);"folder"->Uri.parse(s.target).scheme=="content";else->false})
        DataVault.checkpoint(c,"تعديل اختصارات الفصل ${s.section}")
        val j=JSONObject().put("id",s.id).put("section",s.section).put("title",s.title).put("kind",s.kind).put("target",s.target).put("mime",s.mime)
        prefs(c).edit().putString(PREFIX+s.id,j.toString()).apply()
    }
    fun remove(c:Context,s:ClassShortcut) {DataVault.checkpoint(c,"حذف اختصار الفصل");prefs(c).edit().remove(PREFIX+s.id).apply()}
    fun intent(c:Context,s:ClassShortcut):Intent {
        val uri=when(s.kind) {
            "link"->{require(validLink(s.target));Uri.parse(s.target)}
            "file"->{require(Regex("[a-zA-Z0-9-]+").matches(s.target));val file=File(DataVault.attachments(c),s.target);require(file.isFile){"الملف غير موجود؛ أعد إضافته"};FileProvider.getUriForFile(c,c.packageName+".updates",file)}
            "folder"->{val tree=Uri.parse(s.target);require(c.contentResolver.persistedUriPermissions.any{it.uri==tree && it.isReadPermission}){"أعد اختيار المجلد للسماح بالوصول إليه"};android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree))}
            else->error("اختصار غير صالح")
        }
        return Intent(Intent.ACTION_VIEW).apply {
            if(s.kind=="link")data=uri else setDataAndType(uri,if(s.kind=="folder")"vnd.android.document/directory" else s.mime.ifBlank{"application/octet-stream"})
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
