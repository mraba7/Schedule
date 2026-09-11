package com.mrabah.oneuischedule.widget

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject

internal data class WidgetOptions(val fontScale:Float=1f,val opacity:Float=1f,val showNote:Boolean=true,val showNext:Boolean=true,val peekSeconds:Int=5)
internal object WidgetPreferences {
    private fun prefs(c:Context)=c.getSharedPreferences("widget_preferences",0)
    fun get(c:Context,id:Int):WidgetOptions {
        val j=runCatching{JSONObject(prefs(c).getString("widget:$id",null) ?: prefs(c).getString("widget:0","{}")!!)}.getOrDefault(JSONObject())
        return WidgetOptions(j.optDouble("font",1.0).toFloat().coerceIn(.85f,1.25f),j.optDouble("opacity",1.0).toFloat().coerceIn(.25f,1f),j.optBoolean("note",true),j.optBoolean("next",true),j.optInt("peek",5).coerceIn(3,15))
    }
    fun save(c:Context,id:Int,o:WidgetOptions) {
        com.mrabah.oneuischedule.data.DataVault.checkpoint(c,"تخصيص الودجت $id")
        prefs(c).edit().putString("widget:$id",JSONObject().put("font",o.fontScale).put("opacity",o.opacity).put("note",o.showNote).put("next",o.showNext).put("peek",o.peekSeconds).toString()).apply()
        WidgetBitmapCache.clear()
    }
    fun clear(c:Context,id:Int){prefs(c).edit().remove("widget:$id").apply();WidgetBitmapCache.clear()}
}
internal object WidgetBitmapCache {
    private val cache=object:android.util.LruCache<String,Bitmap>(4*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.byteCount}
    @Synchronized fun get(key:String,render:()->Bitmap):Bitmap=cache.get(key) ?: render().also{cache.put(key,it)}
    @Synchronized fun clear(){cache.evictAll()}
}
