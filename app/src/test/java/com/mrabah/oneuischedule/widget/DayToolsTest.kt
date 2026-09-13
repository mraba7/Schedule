package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class DayToolsTest {
    private val now=LocalDateTime.of(2026,9,14,6,0)
    @Test fun absenceCancelsTodayAndRestoresNextDayWithoutChangingTemplate() {
        val date=now.toLocalDate()
        val base=Defaults.config
        val absent=base.copy(absenceDate=date.toString(),absenceLabel="غائب")
        assertTrue(ScheduleEngine.dutiesOn(absent,date).isEmpty())
        val ui=ScheduleEngine.build(absent,now.plusHours(3))
        assertEquals(date,ui.date)
        assertTrue(ui.slots.isEmpty())
        assertNull(ui.live)
        assertEquals("غائب",ui.holiday?.label)
        assertEquals(ScheduleEngine.dutiesOn(base,date.plusDays(1)),ScheduleEngine.dutiesOn(absent,date.plusDays(1)))
        assertEquals(base,absent.copy(absenceDate="",absenceLabel=""))
        assertEquals(absent,ScheduleStore.importJson(ScheduleStore.exportJson(absent)))
        assertTrue(com.mrabah.oneuischedule.notify.PeriodNotifier.nextEvent(absent,now)!!.toLocalDate()>date)
        val section=(ScheduleEngine.dutiesOn(base,date).values.filterIsInstance<Duty.Teach>().first()).section
        assertTrue(ClassNotes.next(absent,ClassNote(section,"صفحة 20",now.minusDays(1)),now)!!.start.toLocalDate()>date)
    }
    @Test fun delayOnlyChangesTodayAndUndoRestoresExistingProfile() {
        val date=now.toLocalDate()
        val base=Defaults.config.copy(profiles=listOf(TimetableProfile("winter","شتاء",date,date.plusDays(10),Defaults.config.bells,Defaults.config.week)))
        val updated=EmergencyDay.apply(base,now,2,15,"اجتماع")
        assertEquals(SchoolTools.bells(base,date)[0],SchoolTools.bells(updated,date)[0])
        assertEquals(SchoolTools.bells(base,date)[1].start.plusMinutes(15),SchoolTools.bells(updated,date)[1].start)
        assertEquals(SchoolTools.bells(base,date.plusDays(1)),SchoolTools.bells(updated,date.plusDays(1)))
        assertEquals(ScheduleEngine.dutiesOn(base,date),ScheduleEngine.dutiesOn(updated,date))
        assertEquals(base,EmergencyDay.undo(updated,date))
        assertEquals(updated,ScheduleStore.importJson(ScheduleStore.exportJson(updated)))
        assertTrue(DesignDay(ScheduleEngine.today(updated,now),now).day.contains("معدّل"))
    }
    @Test fun startedLessonsAndMidnightOverflowAreRejected() {
        val base=Defaults.config
        val start=base.bells.first().start
        assertTrue(runCatching{EmergencyDay.apply(base,now.toLocalDate().atTime(start),1,15)}.isFailure)
        val late=base.copy(bells=listOf(Bell(1,LocalTime.of(23,0),LocalTime.of(23,50))))
        assertTrue(runCatching{EmergencyDay.apply(late,now,1,15)}.isFailure)
        assertTrue(runCatching{EmergencyDay.apply(base.copy(holidays=listOf(Holiday(now.toLocalDate(),now.toLocalDate(),"إجازة"))),now,1,15)}.isFailure)
    }
    @Test fun successiveAdjustmentsRemainOneDayAndAccumulate() {
        val a=EmergencyDay.apply(Defaults.config,now,1,10)
        val b=EmergencyDay.apply(a,now,1,20)
        assertEquals(1,b.profiles.count{it.id==EmergencyDay.id(now.toLocalDate())})
        assertEquals(Defaults.config.bells.first().start.plusMinutes(30),SchoolTools.bells(b,now.toLocalDate()).first().start)
        assertEquals(Defaults.config,EmergencyDay.undo(b,now.toLocalDate()))
    }
    @Test fun preparationIsDateScopedAndChangedDetailsRequireReview() {
        val c=RuntimeEnvironment.getApplication() as android.content.Context
        val date=now.toLocalDate()
        val slot=ScheduleEngine.today(Defaults.config,now).slots.first()
        TomorrowPrep.set(c,date,slot,true)
        assertTrue(TomorrowPrep.ready(c,date,slot))
        assertFalse(TomorrowPrep.ready(c,date.plusDays(1),slot))
        assertFalse(TomorrowPrep.ready(c,date,slot.copy(bell=slot.bell.copy(start=slot.bell.start.plusMinutes(5)))))
        ClassNotes.save(c,slot.displaySection!!,"صفحة جديدة",false)
        assertFalse(TomorrowPrep.ready(c,date,slot))
        TomorrowPrep.set(c,date,slot,true);TomorrowPrep.set(c,date,slot,false)
        assertFalse(TomorrowPrep.ready(c,date,slot))
    }
}
