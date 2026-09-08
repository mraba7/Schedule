package com.mrabah.oneuischedule.widget

import android.app.NotificationManager
import android.content.Context
import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[32])
class ClassNotesTest {
    private val c:Context get()=RuntimeEnvironment.getApplication()
    private val saved=LocalDateTime.of(2026,9,7,8,30)
    @Test fun remindersFollowTheClassAndSkipTheSavingLesson() {
        val n=ClassNote("2/3","صفحة ٣٥",saved)
        val visit=ClassNotes.next(Defaults.config,n,saved)!!
        assertEquals(LocalDateTime.of(2026,9,9,8,5),visit.start)
        val moved=Defaults.config.copy(overrides=mapOf("2026-09-08#4" to Duty.Teach("2/3")))
        assertEquals(LocalDateTime.of(2026,9,8,10,10),ClassNotes.next(moved,n,saved)!!.start)
        val holiday=moved.copy(holidays=listOf(Holiday(saved.toLocalDate().plusDays(1),saved.toLocalDate().plusDays(1),"إجازة")))
        assertEquals(visit.start,ClassNotes.next(holiday,n,saved)!!.start)
    }
    @Test fun notesPersistPerClassAndCompletionClearsOnlyThatClass() {
        ClassNotes.save(c,"2/3","صفحة ٣٥",true,saved)
        ClassNotes.save(c,"2/4","صفحة ١٢",true,saved)
        val n=ClassNotes.get(c,"2/3")!!
        ClassNotes.delivered(c,n)
        assertTrue(ClassNotes.get(c,"2/3")!!.delivered)
        ClassNotes.save(c,"2/3","صفحة ٤٠",true,saved.plusDays(2))
        assertFalse(ClassNotes.get(c,"2/3")!!.delivered)
        ClassNotes.complete(c,"2/3")
        assertNull(ClassNotes.get(c,"2/3"))
        assertEquals("صفحة ١٢",ClassNotes.get(c,"2/4")!!.text)
    }
    @Test fun notificationPostsOnceAtTheNextMatchingClassEvenWithBellAlertsOff() {
        ClassNotes.save(c,"2/3","توقفنا عند صفحة ٣٥",true,saved)
        ClassNoteReminders.sync(c,saved)
        assertFalse(ClassNotes.get(c,"2/3")!!.delivered)
        val at=LocalDateTime.of(2026,9,9,8,5)
        ClassNoteReminders.sync(c,at)
        assertTrue(ClassNotes.get(c,"2/3")!!.delivered)
        val manager=c.getSystemService(NotificationManager::class.java)
        assertEquals(1,manager.activeNotifications.size)
        assertEquals("توقفنا عند صفحة ٣٥",manager.activeNotifications.first().notification.extras.getCharSequence("android.text").toString())
        manager.cancelAll()
        ClassNoteReminders.sync(c,at.plusMinutes(1))
        assertEquals(0,manager.activeNotifications.size)
    }
    @Test fun disabledReminderKeepsNoteWithoutPosting() {
        ClassNotes.save(c,"2/3","صفحة ٣٥",false,saved)
        ClassNoteReminders.sync(c,LocalDateTime.of(2026,9,9,8,5))
        assertEquals(0,c.getSystemService(NotificationManager::class.java).activeNotifications.size)
        assertNotNull(ClassNotes.get(c,"2/3"))
    }
}
