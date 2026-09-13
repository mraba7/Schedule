package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.ui.RecentAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*
import java.io.File
import android.graphics.Bitmap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PolishTest {
    private val c get()=RuntimeEnvironment.getApplication() as android.content.Context
    @Test fun automaticDeliveryAndQuestionStateDoNotBecomeUndoActions() {
        val p=c.getSharedPreferences("class_progress_notes",0)
        p.edit().putString("2/1",org.json.JSONObject().put("text","before").put("delivered",false).toString()).commit()
        val before=DataVault.snapshot(c)
        p.edit().putString("2/1",org.json.JSONObject().put("text","before").put("delivered",true).toString()).commit()
        c.getSharedPreferences("widget_preferences",0).edit().putString("question:1","automatic").commit()
        assertFalse(RecentAction.hasUserChange(before,DataVault.snapshot(c)))
        p.edit().putString("2/1",org.json.JSONObject().put("text","after").put("delivered",true).toString()).commit()
        val after=DataVault.snapshot(c)
        assertTrue(RecentAction.hasUserChange(before,after))
        val undo=RecentAction.mergeUndo(before,after,after).getJSONObject("stores")
        val note=org.json.JSONObject(undo.getJSONObject("class_progress_notes").getJSONObject("2/1").getString("value"))
        assertTrue(note.getBoolean("delivered"));assertEquals("before",note.getString("text"))
        assertTrue(undo.getJSONObject("widget_preferences").has("question:1"))
    }
    @Test fun previewIgnoresAbsenceButActualViewKeepsIt() {
        val now=LocalDateTime.of(2026,9,14,8,30)
        val config=Defaults.config.copy(absenceDate=now.toLocalDate().toString(),absenceLabel="غائب")
        assertNotNull(WidgetScenarios.day(config,PreviewState.LIVE,now).ui.live)
        assertEquals("غائب",WidgetScenarios.day(config,PreviewState.ACTUAL,now).ui.holiday?.label)
    }
    @Test fun completedWidgetOpensTodayInsteadOfAnEmptyEditor() {
        val now=LocalDateTime.of(2026,9,14,18,0)
        val day=DesignDay(ScheduleEngine.today(Defaults.config,now),now)
        val view=DesignWidgets.makeViews(c,Design.INTERACTIVE,day,DesignRenderer(c),360f,360f,100).apply(c,android.widget.FrameLayout(c))
        view.findViewById<android.view.View>(com.mrabah.oneuischedule.R.id.design_image).performClick()
        val launched=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertEquals(com.mrabah.oneuischedule.MainActivity::class.java.name,launched.component!!.className)
        assertEquals(0,launched.getIntExtra("open_tab",-1))
    }
    @Test fun undoPreservesUnrelatedChangesAndRejectsChangedTarget() {
        val p=c.getSharedPreferences("app_preferences",0)
        p.edit().putString("note","before").commit()
        val before=DataVault.snapshot(c)
        p.edit().putString("note","after").commit()
        val after=DataVault.snapshot(c)
        p.edit().putBoolean("unrelated",true).commit()
        val merged=RecentAction.mergeUndo(before,after,DataVault.snapshot(c)).getJSONObject("stores").getJSONObject("app_preferences")
        assertEquals("before",merged.getJSONObject("note").getString("value"))
        assertTrue(merged.getJSONObject("unrelated").getBoolean("value"))
        p.edit().putString("note","newer").commit()
        assertTrue(runCatching{RecentAction.mergeUndo(before,after,DataVault.snapshot(c))}.isFailure)
    }
    @Test fun completedPeriodsLeaveTilesAndCurrentPeriodTakesFocus() {
        val config=Defaults.config.copy(week=mapOf(DayOfWeek.MONDAY to (1..7).associateWith{Duty.Teach("2/1")}))
        val first=config.bells[0];val second=config.bells[1]
        val now=LocalDate.of(2026,9,14).atTime(second.start.plusMinutes(1))
        val day=DesignDay(ScheduleEngine.today(config,now),now)
        assertFalse(LessonPeek.lessons(day).any{it.period==first.period})
        assertEquals(second.period,day.focus!!.period)
        assertEquals(LessonPeek.lessons(day).size,LessonPeek.tiles(day,second.period).size)
    }
    @Test fun polishedWidgetStatesRenderWithNotesAndDayCompletion() {
        val date=LocalDate.of(2026,9,14)
        val config=Defaults.config.copy(week=mapOf(DayOfWeek.MONDAY to (1..7).associateWith{Duty.Teach("2/1")}))
        ClassNotes.save(c,"2/1","توقفنا عند الصفحة 73",false)
        val folder=File("build/design-previews").apply{mkdirs()}
        listOf("last-five" to config.bells[1].end.minusMinutes(3),"done" to config.bells.last().end.plusMinutes(1)).forEach{(label,time)->
            val now=date.atTime(time);val day=DesignDay(ScheduleEngine.today(config,now),now)
            val bitmap=DesignRenderer(c).render(Design.INTERACTIVE,day,noteOpen=true)
            File(folder,"polish-$label.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            assertEquals(720,bitmap.width)
        }
    }
}
