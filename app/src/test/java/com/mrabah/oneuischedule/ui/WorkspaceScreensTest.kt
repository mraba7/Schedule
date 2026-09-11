package com.mrabah.oneuischedule.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.mrabah.oneuischedule.data.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.android.controller.ActivityController
import java.io.File
import android.graphics.Bitmap

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ar-rSA-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceScreensTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var host:ActivityController<ComponentActivity>
    @Before fun start(){host=Robolectric.buildActivity(ComponentActivity::class.java);host.get().setTheme(com.mrabah.oneuischedule.R.style.Theme_OneUISchedule);host.setup()}
    @After fun stop(){host.pause().stop().destroy()}
    private fun content(body:@androidx.compose.runtime.Composable ()->Unit){compose.runOnUiThread{host.get().setContent{ScheduleTheme(dark=false){Surface(Modifier.fillMaxSize()){body()}}}}}
    private fun capture(name:String){compose.runOnIdle{val view=host.get().window.decorView;val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(image));val dir=File("build/design-previews").apply{mkdirs()};File(dir,"workspace-$name-live.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
    @Test fun classPageCanCreateAndFindANoteAtLargeFont() {
        host.get().getSharedPreferences("app_preferences",0).edit().putFloat("font",1.3f).commit()
        content{ClassPage(Defaults.config,"2/1",{})}
        compose.onNodeWithText("ملاحظة جديدة").performScrollTo()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("ملاحظة جديدة").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("العنوان").performTextInput("تجربة الخلايا")
        compose.onNodeWithText("التفاصيل أو الصفحة").performTextInput("صفحة ٣٠")
        compose.onNodeWithText("حفظ").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("تجربة الخلايا").performScrollTo().assertExists()
        capture("class-large-font")
    }
    @Test fun monthlyCalendarShowsHolidayEditorAndCancelsSafely() {
        var saved=false
        content{CalendarScreen(Defaults.config,{saved=true})}
        compose.onNodeWithText("إضافة إجازة").performScrollTo()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("إضافة إجازة").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("تفاصيل الإجازة").assertExists()
        capture("holiday-editor")
        compose.onNodeWithText("إلغاء").performClick()
        Assert.assertFalse(saved)
    }
    @Test fun copyDayRequiresReviewBeforeSaving() {
        var saved=false
        content{WeekTools(Defaults.config,{saved=true})}
        compose.onNodeWithText("معاينة نسخ اليوم").performScrollTo().performClick()
        compose.onNodeWithText("مراجعة التعديل الأسبوعي").assertExists()
        Assert.assertFalse(saved)
        capture("week-review")
        compose.onNodeWithText("حفظ").performClick()
        Assert.assertTrue(saved)
    }
}
