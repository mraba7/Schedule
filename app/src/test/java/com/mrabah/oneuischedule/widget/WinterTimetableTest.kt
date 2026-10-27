package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class WinterTimetableTest {
    @Test fun winterMatchesTheTeachersSheet() {
        assertEquals(listOf("07:45–08:35","08:35–09:20","09:20–10:05","10:35–11:20","11:20–12:05","12:05–12:50","12:50–13:35"),
            Defaults.winterBells.map { "${it.start}–${it.end}" })
        assertEquals((1..7).toList(), Defaults.winterBells.map { it.period })
    }

    @Test fun winterDatesPreserveClassesAndRestoreNormalHours() {
        val from=LocalDate.of(2026,10,25)
        val to=from.plusDays(4)
        val profile=TimetableProfile("winter","شتوي",from,to,Defaults.winterBells)
        val c=Defaults.config.copy(profiles=listOf(profile))
        assertEquals(Defaults.winterBells,SchoolTools.bells(c,from))
        assertEquals(Defaults.winterBells,SchoolTools.bells(c,to))
        assertEquals(c.bells,SchoolTools.bells(c,from.minusDays(1)))
        assertEquals(c.bells,SchoolTools.bells(c,to.plusDays(1)))
        assertEquals(c.week[from.dayOfWeek],ScheduleEngine.dutiesOn(c,from))
        assertEquals(c,ScheduleStore.importJson(ScheduleStore.exportJson(c)))
        assertTrue(ScheduleEngine.today(c,from.atTime(7,30)).isAssembly)
        assertFalse(ScheduleEngine.today(c,from.atTime(7,15)).isAssembly)
        val live=ScheduleEngine.today(c,from.atTime(7,45))
        assertFalse(live.isAssembly)
        assertEquals(1,live.live!!.period)
        assertEquals(50L,live.minutesLeftInLive)
        assertNull(ScheduleEngine.today(c,from.atTime(10,10)).live)
        assertEquals(4,ScheduleEngine.today(c,from.atTime(10,35)).live!!.period)
        assertTrue(ScheduleEngine.boundaries(c,from).contains(from.atTime(7,30)))
        assertTrue(ScheduleEngine.dutiesOn(c.copy(holidays=listOf(Holiday(from,from,"إجازة"))),from).isEmpty())
    }
}
