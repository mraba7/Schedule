package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class StandbyAssignmentsTest {
    private val date=LocalDate.of(2026,9,10)
    private val base=Defaults.config.copy(overrides=mapOf("$date#5" to Duty.Standby))
    @Test fun assignmentIsForOneDateAndPreservesStandbyDuty() {
        val assigned=StandbyAssignments.assign(base,date,5," 1/8 ")!!
        val slot=ScheduleEngine.today(assigned,date.atTime(7,0)).slots.first {it.period==5}
        assertTrue(slot.isStandby)
        assertNull(slot.section)
        assertEquals("1/8",slot.displaySection)
        assertEquals(base.sections,assigned.sections)
        assertTrue(StandbyAssignments.choices(assigned).contains("1/8"))
        assertTrue(ScheduleEngine.today(assigned,date.plusWeeks(1).atTime(7,0)).slots.all {it.standbySection==null})
        val removed=StandbyAssignments.assign(assigned,date,5,null)!!
        assertTrue(removed.standbySections.isEmpty())
        assertTrue(removed.schoolSections.contains("1/8"))
    }
    @Test fun rejectsTeachingCancelledAndHolidayPeriods() {
        assertNull(StandbyAssignments.assign(base.copy(overrides=mapOf("$date#5" to Duty.Teach("2/2"))),date,5,"1/8"))
        assertNull(StandbyAssignments.assign(base.copy(overrides=mapOf("$date#5" to null)),date,5,"1/8"))
        assertNull(StandbyAssignments.assign(base.copy(holidays=listOf(Holiday(date,date,"إجازة"))),date,5,"1/8"))
    }
    @Test fun backupRoundTripAndOldBackups() {
        val assigned=StandbyAssignments.assign(base,date,5,"1/8")!!
        val restored=ScheduleStore.importJson(ScheduleStore.exportJson(assigned))!!
        assertEquals(assigned.schoolSections,restored.schoolSections)
        assertEquals(assigned.standbySections,restored.standbySections)
        val old=org.json.JSONObject(ScheduleStore.exportJson(base))
        old.remove("schoolSections");old.remove("standbySections")
        assertTrue(ScheduleStore.importJson(old.toString())!!.standbySections.isEmpty())
    }
}
