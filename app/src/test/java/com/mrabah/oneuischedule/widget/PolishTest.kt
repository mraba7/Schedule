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
