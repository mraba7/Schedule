package com.mrabah.oneuischedule.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.mrabah.oneuischedule.ScheduleScreen
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.Duty
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.io.File
import android.graphics.Bitmap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ar-rSA-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreensTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var controller:org.robolectric.android.controller.ActivityController<androidx.activity.ComponentActivity>
    @org.junit.Before fun createHost() {controller=org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java);controller.get().setTheme(com.mrabah.oneuischedule.R.style.Theme_OneUISchedule);controller.setup()}
    @org.junit.After fun closeHost() {
        compose.runOnUiThread{val root=controller.get().findViewById<android.view.ViewGroup>(android.R.id.content);(root.getChildAt(0) as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()}
        controller.pause().stop().destroy()
    }
    private fun drawWindows() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {
            val type=Class.forName("android.view.WindowManagerGlobal")
            val instance=type.getDeclaredMethod("getInstance").invoke(null)
            val field=type.getDeclaredField("mViews").apply{isAccessible=true}
            @Suppress("UNCHECKED_CAST") val views=(field.get(instance) as List<android.view.View>).toList()
            views.filter{it.width>0 && it.height>0}.forEach{view->val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b));b.recycle()}
        }
    }
    private fun content(body:@Composable ()->Unit) {compose.runOnUiThread {controller.get().setContent(content=body)};drawWindows()}
    private fun capture(name:String) {
        val folder=File("build/design-previews").apply{mkdirs()}
        compose.runOnIdle {
            val view=controller.get().window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(folder,"app-$name-live.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun todayShowsLiveCountdownAndCanOpenTomorrow() {
        content {ScheduleTheme(dark=false){Surface(Modifier.fillMaxSize()){TodayDashboard(Defaults.config,LocalDateTime.of(2026,9,7,8,30)){_,_->}}}}
        compose.onNodeWithText("جارية الآن").assertExists()
        compose.onNodeWithText("25:00").assertExists()
        capture("today-light")
        compose.onNodeWithText("غدًا").performClick()
        compose.onNodeWithText("استعد للغد").assertExists()
        compose.onNodeWithText("جارية الآن").assertDoesNotExist()
    }
    @Test fun finishedDayKeepsItsCompletedLessons() {
        content {ScheduleTheme(dark=true){Surface(Modifier.fillMaxSize()){TodayDashboard(Defaults.config,LocalDateTime.of(2026,9,7,16,0)){_,_->}}}}
        compose.onNodeWithText("اكتمل يومك الدراسي").assertExists()
        capture("completed-dark")
    }
    @Test fun scheduleUsesExplicitChoiceAndSavesOnlyOnConfirmation() {
        var saved=com.mrabah.oneuischedule.data.Defaults.config
        content {ScheduleTheme(dark=false){Surface(Modifier.fillMaxSize()){ScheduleScreen(Defaults.config,{saved=it})}}}
        compose.onNodeWithContentDescription("SUNDAY الحصة 1").performClick()
        drawWindows()
        compose.onNodeWithText("حصة انتظار").performClick()
        assertEquals(Defaults.config,saved)
        compose.onNodeWithText("حفظ التعديلات").performClick()
        assertEquals(Duty.Standby,saved.templateOn(java.time.DayOfWeek.SUNDAY)[1])
        capture("schedule-light")
    }
}
