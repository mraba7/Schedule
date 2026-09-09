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
    @Test fun realRemoteViewsHaveOneAccessibleTargetPerLesson() {
        val day=DesignDay.build(Defaults.config,LocalDateTime.of(2026,9,7,8,30))
        val views=DesignWidgets.makeViews(c,Design.INTERACTIVE,day,DesignRenderer(c),320f,320f,45)
        val root=views.apply(c,FrameLayout(c))
        val hits=root.findViewById<FrameLayout>(R.id.design_peeks)
        assertEquals(day.ui.slots.size,hits.childCount)
        assertTrue(hits.getChildAt(0).isClickable)
        assertTrue(hits.getChildAt(0).contentDescription.toString().contains("08:05"))
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
