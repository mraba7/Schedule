package com.mrabah.oneuischedule.data

internal object AppearancePreferences {
    fun reset(c:android.content.Context) {
        DataVault.checkpoint(c,"إعادة المظهر الافتراضي")
        c.getSharedPreferences("app_preferences",0).edit().remove("theme").remove("font").remove("accent").apply()
    }
}
