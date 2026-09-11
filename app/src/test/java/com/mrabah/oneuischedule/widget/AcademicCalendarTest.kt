package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class AcademicCalendarTest {
    @Test fun inclusiveHolidayBoundariesSuspendEvenOverridesAndResumeAfterward() {
        val base=Defaults.config.copy(week=java.time.DayOfWeek.entries.associateWith {mapOf(1 to Duty.Teach("2/3"))})
        val added=AcademicCalendar.apply(base)
        assertEquals(3,added.holidays.size)
        for(holiday in AcademicCalendar.holidays) {
            val override=added.copy(overrides=mapOf("${holiday.from}#1" to Duty.Teach("2/1")))
            assertTrue(ScheduleEngine.dutiesOn(override,holiday.from).isEmpty())
            assertTrue(ScheduleEngine.dutiesOn(override,holiday.to).isEmpty())
            assertEquals(1,ScheduleEngine.dutiesOn(override,holiday.from.minusDays(1)).size)
            assertEquals(1,ScheduleEngine.dutiesOn(override,holiday.to.plusDays(1)).size)
        }
        assertEquals(1,ScheduleEngine.dutiesOn(added,LocalDate.of(2027,1,3)).size) // exam start is not a holiday
    }
    @Test fun migrationKeepsPersonalDataDoesNotDuplicateAndRespectsRemoval() {
        val own=Holiday(LocalDate.of(2026,10,4),LocalDate.of(2026,10,5),"إجازتي")
        val existing=Defaults.config.copy(holidays=listOf(own,AcademicCalendar.holidays.first()),standbySections=mapOf("2026-09-10#5" to "1/8"))
        val added=AcademicCalendar.apply(existing)
        assertEquals(4,added.holidays.size)
        assertEquals(existing.standbySections,added.standbySections)
        assertEquals(existing.week,added.week)
        assertEquals(added,AcademicCalendar.apply(added))
        val removed=added.copy(holidays=listOf(own))
        assertEquals(removed,AcademicCalendar.apply(removed))
        val backup=ScheduleStore.importJson(ScheduleStore.exportJson(removed))!!
        assertEquals(removed,AcademicCalendar.apply(backup))
    }
    @Test fun upgradingStoredConfigurationAddsDatesOnceAndKeepsEdits() {
        val c=RuntimeEnvironment.getApplication()
        val prefs=c.getSharedPreferences("schedule_config",0)
        prefs.edit().clear().putString("config_json",ScheduleStore.exportJson(Defaults.config)).commit()
        val loaded=ScheduleStore.load(c)
        assertEquals(3,loaded.holidays.size)
        ScheduleStore.save(c,loaded.copy(holidays=emptyList()))
        assertTrue(ScheduleStore.load(c).holidays.isEmpty())
        assertTrue(ScheduleStore.load(c).appliedCalendars.contains(AcademicCalendar.ID))
    }
    @Test fun classReminderSkipsTheEntireBreak() {
        val holiday=AcademicCalendar.holidays.first()
        val config=AcademicCalendar.apply(Defaults.config.copy(week=java.time.DayOfWeek.entries.associateWith {mapOf(1 to Duty.Teach("2/3"))}))
        val saved=holiday.from.minusDays(1).atTime(18,0)
        val note=ClassNote("2/3","صفحة ٣٥",saved)
        assertEquals(holiday.to.plusDays(1),ClassNotes.next(config,note,saved)!!.start.toLocalDate())
    }
}
