package com.mrabah.oneuischedule.widget

import android.graphics.Bitmap
import com.mrabah.oneuischedule.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ar-rSA")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UtilityWidgetsTest {
    private val c get()=RuntimeEnvironment.getApplication() as android.content.Context
    private val now=LocalDateTime.of(2026,9,13,7,43)
    private fun sample(state:PreviewState)=WidgetScenarios.day(Defaults.config,state,now)
    @Test fun previewStatesAreIsolatedAndUseActualTodayWhenRequested() {
        ScheduleStore.save(c,Defaults.config)
        val saved=ScheduleStore.load(c)
        PreviewState.entries.forEach{sample(it)}
        assertEquals(saved,ScheduleStore.load(c))
        assertEquals(Availability.BUSY,AvailableTime.from(sample(PreviewState.LIVE)).state)
        assertEquals(22L,AvailableTime.from(sample(PreviewState.LIVE)).minutes)
        assertEquals(Availability.HOLIDAY,AvailableTime.from(sample(PreviewState.HOLIDAY)).state)
        assertEquals(Availability.FINISHED,AvailableTime.from(sample(PreviewState.FINISHED)).state)
        val actual=WidgetScenarios.day(Defaults.config,PreviewState.ACTUAL,now)
        assertEquals(ScheduleEngine.today(Defaults.config,now),actual.ui)
    }
    @Test fun availableTimeTracksBoundariesAndStandbyAssignments() {
        val day=sample(PreviewState.FREE)
        val result=AvailableTime.from(day)
        assertEquals(Availability.FREE,result.state)
        assertEquals(30L,result.minutes)
        assertEquals(4,result.next!!.period)
        val config=day.ui.config.copy(standbySections=mapOf("${now.toLocalDate()}#4" to "3/5"))
        fun at(time:String):AvailableTime {val t=now.toLocalDate().atTime(LocalTime.parse(time));return AvailableTime.from(DesignDay(ScheduleEngine.today(config,t),t))}
        assertEquals(1L,at("09:39:59").minutes)
        assertEquals("3/5",at("09:39:59").next!!.displaySection)
        assertEquals(Availability.BUSY,at("09:40").state)
        assertEquals(Availability.FREE,at("10:30").state)
        assertEquals(Availability.FINISHED,at("12:10").state)
        val empty=config.copy(week=emptyMap())
        assertEquals(Availability.EMPTY,AvailableTime.from(DesignDay(ScheduleEngine.today(empty,now),now)).state)
    }
    @Test fun facesAreIndependentPersistentAndRemovedIndividually() {
        WidgetFaces.toggle(c,41)
        assertTrue(WidgetFaces.isBack(c,41));assertFalse(WidgetFaces.isBack(c,42))
        WidgetFaces.toggle(c,42);WidgetFaces.remove(c,41)
        assertFalse(WidgetFaces.isBack(c,41));assertTrue(WidgetFaces.isBack(c,42))
        WidgetFaces.toggle(c,42);assertFalse(WidgetFaces.isBack(c,42))
    }
    @Test fun classShortcutsAreIsolatedValidatedAndIncludedInBackup() {
        assertFalse(ClassShortcuts.validLink("javascript:alert(1)"))
        assertFalse(ClassShortcuts.validLink("https://"))
        assertFalse(ClassShortcuts.validLink("https://person:password@example.com"))
        assertTrue(ClassShortcuts.validLink("https://example.com/lesson?unit=2"))
        val s=ClassShortcut(section="2/1",title="عرض الدرس",kind="link",target="https://example.com/lesson")
        ClassShortcuts.save(c,s)
        assertEquals(listOf(s),ClassShortcuts.forClass(c,"2/1"));assertTrue(ClassShortcuts.forClass(c,"2/2").isEmpty())
        val bytes=ByteArrayOutputStream().also{DataVault.export(c,it)}.toByteArray()
        ClassShortcuts.remove(c,s);assertTrue(ClassShortcuts.forClass(c,"2/1").isEmpty())
        DataVault.apply(c,DataVault.preview(c,ByteArrayInputStream(bytes)))
        assertEquals(listOf(s),ClassShortcuts.forClass(c,"2/1"))
        assertEquals(s.target,ClassShortcuts.intent(c,s).data.toString())
    }
    @Test fun renderNewWidgetsInEveryStateAndSize() {
        val folder=File("build/design-previews").apply{mkdirs()}
        for(kind in UtilityKind.entries)for(state in PreviewState.entries.filter{it!=PreviewState.ACTUAL})for(size in listOf(240 to 260,380 to 260,380 to 420))for(back in listOf(false,true)) {
            if(kind==UtilityKind.AVAILABLE && back)continue
            val bitmap=UtilityRenderer.render(sample(state),kind,back,size.first,size.second)
            assertEquals(size.first.toDouble()/size.second,bitmap.width.toDouble()/bitmap.height,0.01)
            assertTrue(bitmap.width<=720 && bitmap.height<=720)
            File(folder,"utility-${kind.name.lowercase()}-${state.name.lowercase()}-${size.first}x${size.second}-${if(back)"back" else "front"}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        val seven=Defaults.config.copy(week=mapOf(now.dayOfWeek to (1..7).associateWith{Duty.Teach("2/$it")}))
        for(size in listOf(220 to 240,380 to 420)) {
            val day=DesignDay(ScheduleEngine.today(seven,now),now)
            UtilityRenderer.render(day,UtilityKind.TWO_FACE,true,size.first,size.second).let{bitmap->File(folder,"utility-seven-${size.first}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        }
        for(kind in UtilityKind.entries)for(back in listOf(false,true)) {
            val bitmap=UtilityRenderer.render(sample(PreviewState.LIVE),kind,back,220,240,WidgetOptions(fontScale=1.25f))
            File(folder,"utility-large-font-${kind.name.lowercase()}-$back.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun newProvidersRegisterAndAcceptResponsiveLayouts() {
        val manager=android.appwidget.AppWidgetManager.getInstance(c)
        UtilityWidgets.receivers.forEach{(kind,receiver)->
            val component=android.content.ComponentName(c,receiver)
            val info=c.packageManager.getReceiverInfo(component,android.content.pm.PackageManager.GET_META_DATA)
            assertTrue(info.metaData.containsKey("android.appwidget.provider"))
            val id=800+kind.ordinal
            org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(id,component)
            manager.updateAppWidgetOptions(id,android.os.Bundle().apply{putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(android.util.SizeF(240f,260f),android.util.SizeF(380f,420f)))})
            UtilityWidgets.update(c,manager,id,kind)
            // Robolectric binds IDs but does not populate the launcher's provider catalogue.
            assertArrayEquals(intArrayOf(id),manager.getAppWidgetIds(component))
        }
    }
}
