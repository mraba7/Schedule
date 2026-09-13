package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ClassroomTest {
    private val c get()=RuntimeEnvironment.getApplication() as android.content.Context
    private fun student(n:Int,count:Int=0)=ClassroomRecord(kind="student",section="2/1",title="طالب $n",extra=n.toString(),count=count)
    @Test fun fairSelectionSkipsExcludedAndRotatesAfterParticipation() {
        val a=student(1);val b=student(2)
        assertEquals(a,ClassroomStore.choose(listOf(a,b)))
        assertEquals(b,ClassroomStore.choose(listOf(a.copy(count=1),b)))
        assertEquals(a,ClassroomStore.choose(listOf(a,b.copy(excluded=true))))
        assertNull(ClassroomStore.choose(listOf(a.copy(excluded=true))))
        assertEquals(b,ClassroomStore.choose(listOf(a.copy(updated=20),b.copy(updated=10))))
    }
    @Test fun groupsHaveEveryPresentStudentOnceAndBalancedSizes() {
        val students=(1..11).map{student(it,it)}+student(12).copy(excluded=true)
        val groups=ClassroomStore.groups(students,3)
        assertEquals(11,groups.flatten().map{it.id}.distinct().size)
        assertEquals(1,groups.maxOf{it.size}-groups.minOf{it.size})
        assertFalse(groups.flatten().any{it.excluded})
    }
    @Test fun seatsAreUniqueWithinAClassAndPrivateRecordsCannotBeDisplayed() {
        val a=student(1);ClassroomStore.save(c,a)
        assertTrue(runCatching{ClassroomStore.save(c,student(1))}.isFailure)
        ClassroomStore.save(c,student(1).copy(section="2/2"))
        assertNull(ClassroomStore.publicRecord(c,a.id))
        val q=ClassroomRecord(kind="question",section="2/1",title="ما الخلية؟",detail="وحدة بناء الكائن الحي",extra="أحياء")
        ClassroomStore.save(c,q)
        assertEquals(q,ClassroomStore.publicRecord(c,q.id))
    }
    @Test fun classroomRecordsSurviveComprehensiveBackup() {
        val r=ClassroomRecord(kind="lab",section="2/1",title="تجربة",detail="الخطوات",extra="عدسات | 5",status="0")
        ClassroomStore.save(c,r)
        val bytes=ByteArrayOutputStream().also{DataVault.export(c,it)}.toByteArray()
        ClassroomStore.remove(c,r)
        DataVault.apply(c,DataVault.preview(c,ByteArrayInputStream(bytes)))
        assertEquals(r,ClassroomStore.all(c).single())
    }
    @Test fun questionProviderSupportsAnEmptyAndPopulatedBank() {
        val m=android.appwidget.AppWidgetManager.getInstance(c)
        val component=android.content.ComponentName(c,QuestionWidgetReceiver::class.java)
        assertNotNull(c.packageManager.getReceiverInfo(component,android.content.pm.PackageManager.GET_META_DATA).metaData)
        org.robolectric.Shadows.shadowOf(m).bindAppWidgetId(951,component)
        QuestionWidgetReceiver.update(c,m,951)
        ClassroomStore.save(c,ClassroomRecord(kind="question",section="2/1",title="سؤال",detail="جواب"))
        QuestionWidgetReceiver.update(c,m,951,QuestionWidgetReceiver.REVEAL)
        QuestionWidgetReceiver.update(c,m,951,QuestionWidgetReceiver.NEXT)
    }
}
