package com.mrabah.oneuischedule.widget

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import com.mrabah.oneuischedule.R
import com.mrabah.oneuischedule.data.Defaults
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ar-rSA")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LessonPeekTest {
    private val c:Context get()=RuntimeEnvironment.getApplication()
    private val date="2026-09-07"
    @Test fun tappingAgainClosesAndExpiryDoesNotCloseANewerSelection() {
        val first=LessonPeek.toggle(c,12,date,2,1000)
        assertEquals(2,LessonPeek.selected(c,12,date,1001))
        val second=LessonPeek.toggle(c,12,date,3,2000)
        assertEquals(3,LessonPeek.selected(c,12,date,2001))
        assertFalse(LessonPeek.expire(c,12,first,6000))
        assertEquals(3,LessonPeek.selected(c,12,date,6999))
        assertTrue(LessonPeek.expire(c,12,second,7000))
        assertNull(LessonPeek.selected(c,12,date,7000))
        LessonPeek.toggle(c,12,date,2,8000)
        assertEquals(0L,LessonPeek.toggle(c,12,date,2,8100))
        assertNull(LessonPeek.selected(c,12,date,8101))
    }
    @Test fun instancesDatesAndExpiredStateAreIsolated() {
        LessonPeek.toggle(c,1,date,2,1000)
        assertNull(LessonPeek.selected(c,2,date,1001))
        assertNull(LessonPeek.selected(c,1,"2026-09-08",1001))
        assertNull(LessonPeek.selected(c,1,date,6000))
        assertNull(LessonPeek.selected(c,1,date,0))
    }
    @Test fun collapseTimerResetsWithoutBlockingSubsequentSelections() {
        val app=RuntimeEnvironment.getApplication()
        val shadow=org.robolectric.Shadows.shadowOf(app)
        val looper=org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        fun collapses()=shadow.broadcastIntents.filter { it.action==PeekCollapseScheduler.ACTION }
        val first=android.os.SystemClock.elapsedRealtime()+5000
        PeekCollapseScheduler.schedule(c,91,first)
        assertTrue(collapses().isEmpty())
        looper.idleFor(java.time.Duration.ofSeconds(1))
        val second=android.os.SystemClock.elapsedRealtime()+5000
        PeekCollapseScheduler.schedule(c,91,second)
        looper.idleFor(java.time.Duration.ofSeconds(4))
        assertTrue(collapses().isEmpty())
        looper.idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(second,collapses().single().getLongExtra("until",0))
        PeekCollapseScheduler.schedule(c,91,android.os.SystemClock.elapsedRealtime()+5000)
        PeekCollapseScheduler.schedule(c,91,0)
        looper.idleFor(java.time.Duration.ofSeconds(6))
        assertEquals(1,collapses().size)
    }
    @Test fun realRemoteViewsHaveOneAccessibleTargetPerLesson() {
        val day=DesignDay.build(Defaults.config,LocalDateTime.of(2026,9,7,8,30))
        val views=DesignWidgets.makeViews(c,Design.INTERACTIVE,day,DesignRenderer(c),320f,320f,45)
        val root=views.apply(c,FrameLayout(c))
        val hits=root.findViewById<FrameLayout>(R.id.design_peeks)
        assertEquals(day.ui.slots.size,hits.childCount)
        assertTrue(hits.getChildAt(0).isClickable)
        assertTrue(hits.getChildAt(0).contentDescription.toString().contains("08:05"))
    }
    @Test fun standbyTimeAndClassHaveSeparateClickTargets() {
        val now=LocalDateTime.of(2026,9,10,7,0)
        val config=Defaults.config.copy(week=mapOf(java.time.DayOfWeek.THURSDAY to (1..7).associateWith { com.mrabah.oneuischedule.data.Duty.Standby }))
        val day=DesignDay.build(config,now)
        val shadow=org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
        val collapsed=DesignWidgets.makeViews(c,Design.INTERACTIVE,day,DesignRenderer(c),360f,360f,97).apply(c,FrameLayout(c))
        collapsed.findViewById<FrameLayout>(R.id.design_peeks).getChildAt(0).performClick()
        assertEquals(LessonPeek.ACTION,shadow.broadcastIntents.last().action)
        LessonPeek.toggle(c,97,day.ui.date.toString(),1)
        val expanded=DesignWidgets.makeViews(c,Design.INTERACTIVE,day,DesignRenderer(c),360f,360f,97).apply(c,FrameLayout(c))
        val hits=expanded.findViewById<FrameLayout>(R.id.design_peeks)
        assertEquals(8,hits.childCount)
        hits.getChildAt(1).performClick()
        val launched=shadow.nextStartedActivity
        assertEquals(StandbyClassActivity::class.java.name,launched.component!!.className)
        assertEquals(1,launched.getIntExtra("period",-1))
        for(period in listOf(1,7)) {
            val tiles=LessonPeek.tiles(day,period)
            tiles.indices.forEach { a -> (a+1 until tiles.size).forEach { b -> assertFalse(android.graphics.RectF.intersects(tiles[a],tiles[b])) } }
            val bitmap=DesignRenderer(c).render(Design.INTERACTIVE,day,peekPeriod=period)
            val folder=File("build/design-previews").apply{mkdirs()}
            File(folder,"standby-time-and-class-$period.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun expandedCardsDoNotOverlapAndRenderOnBothRows() {
        val now=LocalDateTime.of(2026,9,7,8,30)
        val all=Defaults.config.copy(week=mapOf(java.time.DayOfWeek.MONDAY to (1..7).associateWith { com.mrabah.oneuischedule.data.Duty.Teach("2/1") }))
        val folder=File("build/design-previews").apply{mkdirs()}
        for((label,config) in listOf("four" to Defaults.config,"seven" to all)) {
            val day=DesignDay.build(config,now)
            for(period in listOf(day.ui.slots.first().period,day.ui.slots.last().period)) {
                val tiles=LessonPeek.tiles(day,period)
                tiles.indices.forEach { a -> (a+1 until tiles.size).forEach { b -> assertFalse(android.graphics.RectF.intersects(tiles[a],tiles[b])) } }
                val bitmap=DesignRenderer(c).render(Design.INTERACTIVE,day,peekPeriod=period)
                File(folder,"interactive-$label-expanded-$period.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }
}
