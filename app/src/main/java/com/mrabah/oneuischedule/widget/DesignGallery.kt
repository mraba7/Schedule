package com.mrabah.oneuischedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.ScheduleStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Actual rendered widget previews, rather than the AI concept pictures. */
@Composable
internal fun DesignGallery(config:Config) {
    val c=LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit){while(true){delay(60_000);now=LocalDateTime.now()}}
    val day=remember(config,now){DesignDay.build(config,now)}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Text("تصاميم الودجت",style=MaterialTheme.typography.headlineMedium)
            Text("هذه معاينة فعلية من نفس محرك الودجت. أفضل تطابق في مساحة مربعة؛ اضغط على الودجت لفتح ملاحظة الحصة وتجهيزها.")
        }
        items(listOf(Design.GLASS) + Design.entries.filter { it != Design.GLASS }){design ->
            val bitmap=remember(design,day){DesignRenderer(c).render(design,day,
                task=PreparationStore.task(c,day.key).ifBlank{"تحديد التجهيز"},done=PreparationStore.done(c,day.key))}
            Column {
                Text(design.title,style=MaterialTheme.typography.titleLarge)
                Image(bitmap.asImageBitmap(),contentDescription=day.summary,
                    modifier=Modifier.fillMaxWidth().aspectRatio(1f))
                OutlinedButton(onClick={
                    val manager=AppWidgetManager.getInstance(c)
                    if(manager.isRequestPinAppWidgetSupported) {
                        val accepted=manager.requestPinAppWidget(ComponentName(c,DesignWidgets.receivers.getValue(design)),null,null)
                        if(!accepted) Toast.makeText(c,"أضف التصميم من قائمة أدوات الشاشة الرئيسية",Toast.LENGTH_LONG).show()
                    } else Toast.makeText(c,"اضغط مطولًا على الشاشة الرئيسية ثم الأدوات ← جدول الحصص",Toast.LENGTH_LONG).show()
                },modifier=Modifier.fillMaxWidth()) {Text("إضافة إلى الشاشة الرئيسية")}
            }
        }
    }
}

class DesignLessonActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val key=intent.getStringExtra("key")
        setContent {
            MaterialTheme {
                var task by remember {mutableStateOf(PreparationStore.task(this,key))}
                var note by remember {mutableStateOf(PreparationStore.note(this,key))}
                val day=remember {DesignDay.build(ScheduleStore.load(this))}
                Surface(modifier=Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        item {Text(intent.getStringExtra("title") ?: "تفاصيل الحصة",style=MaterialTheme.typography.headlineSmall)}
                        if(key==null) {
                            item {Text("لا توجد حصة محددة حاليًا. يمكنك تعديل جدولك من التطبيق.")}
                        } else {
                            item {OutlinedTextField(value=task,onValueChange={task=it.take(160)},label={Text("تجهيز الحصة")},placeholder={Text("مثل: أوراق العمل أو أدوات التجربة")},modifier=Modifier.fillMaxWidth())}
                            item {OutlinedTextField(value=note,onValueChange={note=it.take(2000)},label={Text("ملاحظة الحصة")},minLines=4,modifier=Modifier.fillMaxWidth())}
                            if(day.key==key && !day.focus?.note.isNullOrBlank()) {
                                item {Text("ملاحظة الجدول: ${day.focus?.note}")}
                            }
                            item {Button(onClick={
                                PreparationStore.save(this@DesignLessonActivity,key,task.trim(),note.trim())
                                sendBroadcast(android.content.Intent(this@DesignLessonActivity,ScheduleWidgetReceiver::class.java)
                                    .setAction(ScheduleWidgetReceiver.ACTION_TICK))
                                Toast.makeText(this@DesignLessonActivity,"تم الحفظ لهذه الحصة",Toast.LENGTH_SHORT).show()
                                finish()
                            },modifier=Modifier.fillMaxWidth()){Text("حفظ")}}
                            item {Text("التجهيز والملاحظة محفوظان لهذه الحصة في هذا التاريخ فقط. اضغط دائرة التجهيز في الودجت لتحديد الإنجاز.")}
                        }
                        item {TextButton(onClick={finish()}){Text("رجوع")}}
                    }
                }
            }
        }
    }
}
