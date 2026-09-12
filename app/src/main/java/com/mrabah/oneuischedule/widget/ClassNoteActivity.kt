package com.mrabah.oneuischedule.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.mrabah.oneuischedule.data.ScheduleStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ClassNoteActivity:ComponentActivity() {
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(!granted) android.widget.Toast.makeText(this,"حُفظت الملاحظة؛ فعّل إذن الإشعارات لاستقبال التنبيه",android.widget.Toast.LENGTH_LONG).show()
        refresh();finish()
    }
    private fun refresh() {
        ClassNoteReminders.sync(this)
        sendBroadcast(Intent(this,ScheduleWidgetReceiver::class.java).setAction(ScheduleWidgetReceiver.ACTION_TICK))
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val section=intent.getStringExtra("section")?.takeIf { it.isNotBlank() } ?: run { finish();return }
        setContent { com.mrabah.oneuischedule.ui.ScheduleTheme {
            var note by remember { mutableStateOf(ClassNotes.get(this,section)?.text.orEmpty()) }
            var reminder by remember { mutableStateOf(ClassNotes.get(this,section)?.reminder ?: true) }
            val existing=remember { ClassNotes.get(this,section) }
            Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    item { Text("آخر نقطة للفصل $section",style=MaterialTheme.typography.headlineSmall) }
                    item { OutlinedButton(onClick={com.mrabah.oneuischedule.ui.openStudio(this@ClassNoteActivity,"shortcuts",section)},modifier=Modifier.fillMaxWidth()){Text("اختصارات الفصل · ملفات وروابط")}}
                    item { Text("اكتب أين توقفت. تظهر الملاحظة لهذا الفصل في حصصه القادمة حتى تحديثها أو إنهائها.") }
                    item { OutlinedTextField(value=note,onValueChange={note=it.take(1000)},label={Text("ملاحظة الفصل")},
                        placeholder={Text("توقفنا عند صفحة ٣٥، السؤال ٤")},minLines=3,modifier=Modifier.fillMaxWidth()) }
                    item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("تنبيه في الحصة القادمة لنفس الفصل",modifier=Modifier.weight(1f))
                        Switch(checked=reminder,onCheckedChange={reminder=it})
                    } }
                    item { Text("يصل التنبيه مرة واحدة بعد كل حفظ، عند بداية الحصة القادمة. الملاحظة لا تنتقل إلى فصل آخر.") }
                    if(existing?.delivered==true) item { Text("تم إرسال تنبيه هذه الملاحظة. الحفظ من جديد يفعّل تذكيرًا للحصة التالية.") }
                    if(!NotificationManagerCompat.from(this@ClassNoteActivity).areNotificationsEnabled()) item {
                        TextButton(onClick={startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}) { Text("تفعيل إشعارات التطبيق") }
                    }
                    val alarms=getSystemService(android.app.AlarmManager::class.java)
                    if(!alarms.canScheduleExactAlarms()) item {
                        Text("للتنبيه بدقة عند بداية الحصة، فعّل إذن المنبّهات والتذكيرات. بدونه قد يتأخر التنبيه.")
                        TextButton(onClick={startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}) { Text("ضبط توقيت التنبيه") }
                    }
                    item { Button(onClick={
                        ClassNotes.save(this@ClassNoteActivity,section,note,reminder)
                        if(reminder && note.isNotBlank() && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else {refresh();finish()}
                    },modifier=Modifier.fillMaxWidth(),enabled=note.isNotBlank()) {Text("حفظ للفصل") } }
                    if(existing!=null) item { OutlinedButton(onClick={ClassNotes.complete(this@ClassNoteActivity,section);refresh();finish()},modifier=Modifier.fillMaxWidth()) {Text("تم · إنهاء الملاحظة") } }
                    item { TextButton(onClick={finish()}) {Text("رجوع") } }
                }
            }
        } }
    }
}
