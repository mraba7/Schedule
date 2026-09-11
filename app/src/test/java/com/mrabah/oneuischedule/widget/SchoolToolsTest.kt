package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.*
import java.io.*
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class SchoolToolsTest {
    private val c:android.content.Context get()=RuntimeEnvironment.getApplication()
    @Test fun profilesReplaceTimesAndDutiesOnlyInsideTheirDates() {
        val date=LocalDate.of(2026,9,13)
        val profile=TimetableProfile("exam","اختبارات",date,date.plusDays(2),Defaults.summerBells.map{it.copy(start=it.start.plusHours(2),end=it.end.plusHours(2))},mapOf(DayOfWeek.SUNDAY to mapOf(1 to Duty.Teach("2/3"))))
        val config=Defaults.config.copy(profiles=listOf(profile))
        assertEquals(profile.bells,SchoolTools.bells(config,date))
        assertEquals(config.bells,SchoolTools.bells(config,date.minusDays(1)))
        assertEquals(mapOf(1 to Duty.Teach("2/3")),ScheduleEngine.dutiesOn(config,date))
        assertEquals(profile.bells.first().start,ScheduleEngine.today(config,date.atStartOfDay()).slots.first().bell.start)
        assertEquals(config,ScheduleStore.importJson(ScheduleStore.exportJson(config)))
        assertTrue(ScheduleEngine.dutiesOn(config.copy(holidays=listOf(Holiday(date,date,"إجازة"))),date).isEmpty())
    }
    @Test fun copyingAndSwappingDoNotMutateTheSource() {
        val base=Defaults.config.copy(week=mapOf(DayOfWeek.SUNDAY to mapOf(1 to Duty.Teach("2/1"),2 to Duty.Standby)))
        val copy=SchoolTools.copyDay(base,DayOfWeek.SUNDAY,DayOfWeek.MONDAY)
        assertEquals(base.week[DayOfWeek.SUNDAY],copy.week[DayOfWeek.MONDAY])
        assertFalse(base.week.containsKey(DayOfWeek.MONDAY))
        val swapped=SchoolTools.swap(base,DayOfWeek.SUNDAY,1,DayOfWeek.SUNDAY,2)
        assertEquals(Duty.Standby,swapped.week[DayOfWeek.SUNDAY]!![1])
        assertEquals(Duty.Teach("2/1"),swapped.week[DayOfWeek.SUNDAY]!![2])
    }
    @Test fun fullBackupRestoresNotesTasksAttachmentsAndSupportsUndo() {
        ScheduleStore.save(c,Defaults.config.copy(subject="أحياء الاختبار"))
        ClassNotes.save(c,"2/1","صفحة ٤٠",false)
        PreparationStore.save(c,"2026-09-13#1","أوراق عمل","تجربة")
        val file=File(DataVault.attachments(c),"test-image");file.writeText("test attachment")
        val entry=JournalEntry(section="2/1",title="درس",file=file.name,fileName="صورة",mime="image/png")
        ClassJournal.save(c,entry)
        val bytes=ByteArrayOutputStream().also{DataVault.export(c,it)}.toByteArray()
        ClassNotes.complete(c,"2/1")
        val staged=DataVault.preview(c,ByteArrayInputStream(bytes))
        assertNull(ClassNotes.get(c,"2/1"))
        DataVault.apply(c,staged)
        assertEquals("صفحة ٤٠",ClassNotes.get(c,"2/1")!!.text)
        assertEquals("أوراق عمل",PreparationStore.task(c,"2026-09-13#1"))
        assertEquals(entry.file,ClassJournal.forClass(c,"2/1").single().file)
        assertEquals("test attachment",file.readText())
        assertTrue(DataVault.snapshots(c).isNotEmpty())
    }
    @Test fun invalidArchiveCannotChangeLiveDataOrEscapeItsFolder() {
        ScheduleStore.save(c,Defaults.config.copy(subject="محفوظ"))
        val bytes=ByteArrayOutputStream().also{out->ZipOutputStream(out).use{it.putNextEntry(ZipEntry("../escape"));it.write(byteArrayOf(1));it.closeEntry()}}.toByteArray()
        assertThrows(IllegalArgumentException::class.java){DataVault.preview(c,ByteArrayInputStream(bytes))}
        assertEquals("محفوظ",ScheduleStore.load(c).subject)
        assertFalse(File(c.cacheDir,"escape").exists())
    }
    @Test fun widgetInstancesAndPeekDurationsAreIndependent() {
        WidgetPreferences.save(c,101,WidgetOptions(peekSeconds=12,fontScale=1.2f))
        WidgetPreferences.save(c,102,WidgetOptions(peekSeconds=3))
        LessonPeek.toggle(c,101,"2026-09-13",1,1000)
        assertEquals(1,LessonPeek.selected(c,101,"2026-09-13",11000))
        assertNull(LessonPeek.selected(c,101,"2026-09-13",13000))
        assertEquals(3,WidgetPreferences.get(c,102).peekSeconds)
        assertEquals(1.2f,WidgetPreferences.get(c,101).fontScale,.001f)
    }
    @Test fun completingALessonUpdatesProgressOnce() {
        val lesson=JournalEntry(section="2/1",title="الخلايا",unit="الوحدة الأولى",lesson=true)
        ClassJournal.save(c,lesson)
        ClassJournal.save(c,lesson.copy(done=true))
        val first=ScheduleStore.load(c).progress["2/1"]!!.taught
        ClassJournal.save(c,lesson.copy(done=true))
        assertEquals(first,ScheduleStore.load(c).progress["2/1"]!!.taught)
        ClassJournal.save(c,lesson.copy(done=false))
        assertEquals(first-1,ScheduleStore.load(c).progress["2/1"]!!.taught)
    }
    @Test fun oldJsonBackupPreservesNewerNotesWhenRestoringOnlySchedule() {
        ClassNotes.save(c,"2/1","احتفظ بهذه الملاحظة",false)
        val old=ScheduleStore.exportJson(Defaults.config.copy(subject="جدول قديم"))
        val preview=DataVault.preview(c,ByteArrayInputStream(old.toByteArray()))
        DataVault.apply(c,preview)
        assertEquals("جدول قديم",ScheduleStore.load(c).subject)
        assertEquals("احتفظ بهذه الملاحظة",ClassNotes.get(c,"2/1")!!.text)
    }
    @Test fun muteAndPeriodTypesControlTheNextBellWithoutStoppingFutureDays() {
        val now=LocalDateTime.of(2026,9,13,6,0)
        val base=Defaults.config.copy(liveUpdate=false,week=mapOf(DayOfWeek.SUNDAY to mapOf(1 to Duty.Standby),DayOfWeek.MONDAY to mapOf(1 to Duty.Teach("2/1"))))
        val muted=base.copy(mutedDate=now.toLocalDate().toString())
        assertEquals(now.toLocalDate().plusDays(1),com.mrabah.oneuischedule.notify.PeriodNotifier.nextEvent(muted,now)!!.toLocalDate())
        assertEquals(now.toLocalDate().plusDays(1),com.mrabah.oneuischedule.notify.PeriodNotifier.nextEvent(base.copy(notifyStandby=false),now)!!.toLocalDate())
        assertNull(com.mrabah.oneuischedule.notify.PeriodNotifier.nextEvent(base.copy(notifyStandby=false,notifyTeaching=false),now))
    }

}
