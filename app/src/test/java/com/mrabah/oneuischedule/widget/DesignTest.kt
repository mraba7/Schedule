package com.mrabah.oneuischedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import com.mrabah.oneuischedule.data.Defaults
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="ar-rSA")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    private fun at(h:Int,m:Int)=DesignDay.build(Defaults.config,LocalDateTime.of(2026,9,7,h,m))

    @Test fun liveDataIsSharedByEveryDesign() {
        val d=at(8,30)
        assertEquals("2/3",d.section)
        assertEquals(25L,d.minutes)
        assertEquals("08:05 – 08:55",d.range)
        assertEquals("09:40",d.breakTime)
        assertEquals("13:05",d.finish)
        assertEquals(listOf("2/4","2/1","2/2"),d.upcoming.map{it.section})
        assertEquals("2026-09-07#2",d.key)
    }
    @Test fun previewNeverPretendsToBeLive() {
        val d=DesignDay.build(Defaults.config,LocalDateTime.of(2026,9,6,15,0))
        assertNull(d.minutes)
        assertEquals("08:05",d.countText)
        assertTrue(d.day.startsWith("غدًا"))
        assertEquals("الحصة القادمة",d.status)
    }
    @Test fun breakAndFreePeriodAreDifferent() {
        assertEquals("الفسحة الآن",at(10,0).freeLabel)
        assertEquals("وقت متاح",at(10,30).freeLabel)
        assertEquals(70L,at(10,30).minutes)
    }
    @Test fun tasksAreIsolatedAndEditingResetsCompletion() {
        val key="2026-09-07#2"
        PreparationStore.save(context,key,"أوراق العمل","ملاحظة")
        PreparationStore.toggle(context,key)
        assertTrue(PreparationStore.done(context,key))
        assertFalse(PreparationStore.done(context,"2026-09-14#2"))
        PreparationStore.save(context,key,"أدوات التجربة","ملاحظة")
        assertFalse(PreparationStore.done(context,key))
    }
    @Test fun renderEveryDesignAndState() {
        val states=mapOf("live" to at(8,30),"before" to at(7,55),"break" to at(10,0),
            "last" to at(12,40),"tomorrow" to at(15,0),
            "empty" to DesignDay.build(Defaults.config.copy(week=emptyMap()),LocalDateTime.of(2026,9,7,8,30)))
        val folder=File("build/design-previews").apply{mkdirs()}
        Design.entries.forEach{style ->
            states.forEach{(state,day) ->
                val bitmap=DesignRenderer(context).render(style,day,720,720,"أوراق العمل")
                assertEquals(720,bitmap.width)
                assertNotEquals(0,bitmap.getPixel(360,360))
                File(folder,"${style.name.lowercase()}-$state.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
                }
                bitmap.recycle()
            }
            val wide=DesignRenderer(context).render(style,at(8,30),720,480)
            assertEquals(0,wide.getPixel(0,0))
            wide.recycle()
        }
    }
    @Test fun providersAndRemoteViewsAreRegistered() {
        val manager=AppWidgetManager.getInstance(context)
        DesignWidgets.receivers.forEach{(style,receiver) ->
            val info=context.packageManager.getReceiverInfo(ComponentName(context,receiver),android.content.pm.PackageManager.GET_META_DATA)
            assertTrue(info.metaData.containsKey("android.appwidget.provider"))
            val id=100+style.ordinal
            manager.updateAppWidgetOptions(id,Bundle().apply{
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,320)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,320)
            })
            DesignWidgets.update(context,manager,id,style,at(8,30))
        }
    }
}
