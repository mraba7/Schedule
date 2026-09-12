package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class WorkspaceDataTest {
    @Test fun arabicSearchHandlesDigitsHamzaAndDiacriticsAndKeepsAllTerms() {
        val item=WorkspaceResult("1","أَحياء الفصل","توقفنا عند صفحة 35","2/1")
        assertEquals(listOf(item),WorkspaceSearch.filter(listOf(item),"احياء ٢/١ ٣٥"))
        assertTrue(WorkspaceSearch.filter(listOf(item),"احياء 40").isEmpty())
        assertTrue(WorkspaceSearch.filter(listOf(item),"  ").isEmpty())
    }
    @Test fun resettingAppearancePreservesTeacherDataAndBackupPreferences() {
        val c=RuntimeEnvironment.getApplication() as android.content.Context
        val prefs=c.getSharedPreferences("app_preferences",0)
        prefs.edit().putString("theme","dark").putFloat("font",1.3f).putString("accent","blue").putString("class-shortcut:test","saved-link").putString("ready:2026-09-15#1","prepared").putBoolean("daily_backup",false).putStringSet("pinned_classes",setOf("2/1")).commit()
        AppearancePreferences.reset(c)
        assertFalse(prefs.contains("theme"));assertFalse(prefs.contains("font"));assertFalse(prefs.contains("accent"))
        assertEquals("saved-link",prefs.getString("class-shortcut:test",null))
        assertEquals("prepared",prefs.getString("ready:2026-09-15#1",null))
        assertFalse(prefs.getBoolean("daily_backup",true))
        assertEquals(setOf("2/1"),prefs.getStringSet("pinned_classes",null))
    }
}
