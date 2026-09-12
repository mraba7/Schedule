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

@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="ar-rSA-w360dp-h740dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceScreensTest {
    @get:Rule val compose=createEmptyComposeRule()
    @get:Rule val testName=org.junit.rules.TestName()
    private lateinit var host:ActivityController<ComponentActivity>
    @Before fun start(){host=Robolectric.buildActivity(ComponentActivity::class.java);host.get().setTheme(com.mrabah.oneuischedule.R.style.Theme_OneUISchedule);host.setup().visible()}
    @After fun stop(){
        capture(testName.methodName)
        compose.runOnUiThread {
            val container=host.get().findViewById<android.view.ViewGroup>(android.R.id.content)
            (container.getChildAt(0) as? androidx.compose.ui.platform.ComposeView)?.disposeComposition()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        host.pause().stop().destroy()
    }
    private fun content(body:@androidx.compose.runtime.Composable ()->Unit){
        compose.runOnUiThread{host.get().setContent{ScheduleTheme(dark=false){Surface(Modifier.fillMaxSize()){body()}}}}
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        capture("initial-${testName.methodName}")
    }
    private fun windows():List<android.view.View> {
        val type=Class.forName("android.view.WindowManagerGlobal")
        val instance=type.getDeclaredMethod("getInstance").invoke(null)
        val field=type.getDeclaredField("mViews").apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST")
        return (field.get(instance) as List<android.view.View>).toList()
    }
    // Robolectric has no automatic draw pass, including for separate dialog windows.
    private fun drawWindows() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {windows().filter{it.width>0 && it.height>0}.forEach {view->
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));bitmap.recycle()
        }}
    }
    private fun capture(name:String){compose.runOnUiThread{
        val view=windows().lastOrNull() ?: host.get().window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val dir=File("build/design-previews").apply{mkdirs()}
        File(dir,"workspace-$name-live.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }}
    @Test fun classPageCanCreateAndFindANoteAtLargeFont() {
        host.get().getSharedPreferences("app_preferences",0).edit().putFloat("font",1.3f).commit()
        content{ClassPage(Defaults.config,"2/1",{})}
        compose.onNodeWithText("ملاحظة جديدة").performScrollTo().performClick()
        drawWindows()
        compose.onNodeWithText("العنوان").performTextInput("تجربة الخلايا")
        drawWindows()
        compose.onNodeWithText("التفاصيل أو الصفحة").performTextInput("صفحة ٣٠")
        drawWindows()
        compose.onNodeWithText("حفظ").performClick()
        drawWindows()
        compose.onNodeWithText("تجربة الخلايا").performScrollTo().assertExists()
        capture("class-large-font")
    }
    @Test fun monthlyCalendarShowsHolidayEditorAndCancelsSafely() {
        var saved=false
        content{CalendarScreen(Defaults.config,{saved=true})}
        compose.onNodeWithText("إضافة إجازة").performClick()
        drawWindows()
        compose.onNodeWithText("تفاصيل الإجازة").assertExists()
        capture("holiday-editor")
        compose.onNodeWithText("إلغاء").performClick()
        Assert.assertFalse(saved)
    }
    @Test fun classShortcutsCanAddAndEditALink() {
        content{ClassShortcutsScreen("2/1")}
        compose.onNodeWithText("إضافة رابط").performClick()
        drawWindows()
        compose.onNodeWithText("اسم الاختصار").performTextInput("عرض الخلايا")
        compose.onNodeWithText("الرابط https://").performTextInput("https://example.com/cells")
        drawWindows()
        compose.onNodeWithText("حفظ").performClick()
        drawWindows()
        Assert.assertEquals("عرض الخلايا",ClassShortcuts.forClass(host.get(),"2/1").single().title)
        compose.onNodeWithText("تعديل").performScrollTo().performClick()
        drawWindows()
        compose.onNodeWithText("اسم الاختصار").performTextReplacement("عرض الدرس")
        drawWindows()
        compose.onNodeWithText("حفظ").performClick()
        drawWindows()
        compose.onNodeWithText("عرض الدرس").performScrollTo().assertExists()
        Assert.assertTrue(ClassShortcuts.forClass(host.get(),"2/2").isEmpty())
    }
    @Test fun copyDayRequiresReviewBeforeSaving() {
        var saved=false
        content{WeekTools(Defaults.config,{saved=true})}
        compose.onNodeWithText("معاينة نسخ اليوم").performScrollTo().performClick()
        compose.onNodeWithText("مراجعة التعديل الأسبوعي").assertExists()
        Assert.assertFalse(saved)
        capture("week-review")
        compose.onNodeWithText("حفظ").performClick()
        drawWindows()
        Assert.assertTrue(saved)
    }
}
