package com.mrabah.oneuischedule.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.widget.*
import java.time.LocalDate

@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(value,onChange)}}
@Composable
internal fun AppearanceScreen() {
    val c=LocalContext.current;val prefs=remember{c.getSharedPreferences("app_preferences",0)}
    var mode by remember{mutableStateOf(prefs.getString("theme","system")!!)};var scale by remember{mutableStateOf(prefs.getFloat("font",1f))}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{Text("المظهر والقراءة",style=MaterialTheme.typography.headlineMedium)}
        item{Choice("الوضع",mode,listOf("system","light","dark"),{when(it){"light"->"فاتح";"dark"->"داكن";else->"حسب الجهاز"}}){mode=it;DataVault.checkpoint(c,"تغيير المظهر");prefs.edit().putString("theme",mode).apply()}}
        item{Text("حجم النص ${(scale*100).toInt()}٪");Slider(scale,{scale=it},valueRange=.85f..1.4f,onValueChangeFinished={DataVault.checkpoint(c,"تغيير حجم النص");prefs.edit().putFloat("font",scale).apply()});Text("يتكامل هذا الحجم مع حجم الخط في إعدادات جهازك.")}
        item{OutlinedButton(onClick={scale=1f;mode="system";DataVault.checkpoint(c,"إعادة المظهر الافتراضي");prefs.edit().clear().apply()}){Text("المظهر الافتراضي")}}
    }
}
@Composable
internal fun AlertsScreen(config:Config,commit:(Config)->Unit) {
    val c=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->commit(ScheduleStore.load(c).copy(notify=granted))}
    val tone=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->if(result.resultCode==android.app.Activity.RESULT_OK){@Suppress("DEPRECATION") val uri:android.net.Uri?=result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);commit(ScheduleStore.load(c).copy(bellUri=uri?.toString().orEmpty()))}}
    var pre by remember(config.preMinutes){mutableStateOf(config.preMinutes.toFloat())};var end by remember(config.endMinutes){mutableStateOf(config.endMinutes.toFloat())}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("تنبيهات الحصص",style=MaterialTheme.typography.headlineMedium)}
        item{Toggle("تشغيل تنبيهات الحصص",config.notify){if(it && android.os.Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else commit(config.copy(notify=it))}}
        item{Toggle("حصص التدريس",config.notifyTeaching){commit(config.copy(notifyTeaching=it))};Toggle("حصص الانتظار",config.notifyStandby){commit(config.copy(notifyStandby=it))}}
        item{Toggle("تنبيه قبل البداية",config.preAlert){commit(config.copy(preAlert=it))};Text("قبل الحصة بـ ${pre.toInt()} دقيقة");Slider(pre,{pre=it},valueRange=1f..30f,steps=28,onValueChangeFinished={commit(config.copy(preMinutes=pre.toInt()))})}
        item{Toggle("تنبيه قبل النهاية",config.endAlert){commit(config.copy(endAlert=it))};Text("قبل النهاية بـ ${end.toInt()} دقيقة");Slider(end,{end=it},valueRange=1f..30f,steps=28,onValueChangeFinished={commit(config.copy(endMinutes=end.toInt()))})}
        item{Toggle("إشعار الحصة الجارية",config.liveUpdate){commit(config.copy(liveUpdate=it))};Toggle("جرس المدرسة",config.schoolBell){commit(config.copy(schoolBell=it))};Toggle("نطق التنبيه",config.speak){commit(config.copy(speak=it))}}
        item{Button(onClick={commit(config.copy(mutedDate=if(config.mutedDate==LocalDate.now().toString())"" else LocalDate.now().toString()))}){Text(if(config.mutedDate==LocalDate.now().toString())"إلغاء كتم اليوم" else "كتم تنبيهات اليوم فقط")};Text("يشمل كتم اليوم تنبيهات الحصص وملاحظات الفصول؛ يعود التنبيه في الأيام التالية.")}
        item{OutlinedButton(onClick={tone.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALL).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,false))}){Text("اختيار نغمة الجرس")};TextButton(onClick={commit(config.copy(bellUri=""))}){Text("الجرس المدمج")}}
        if(config.speak)item{com.mrabah.oneuischedule.VoicePicker(config.voiceId){commit(config.copy(voiceId=it))}}
        item{com.mrabah.oneuischedule.NotificationTestCard()}
        item{TextButton(onClick={c.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,c.packageName))}){Text("أذونات الإشعارات في الجهاز")}}
    }
}
@Composable
internal fun WidgetSettingsScreen() {
    val c=LocalContext.current
    val manager=AppWidgetManager.getInstance(c)
    val installed=remember{DesignWidgets.receivers.flatMap{(design,receiver)->manager.getAppWidgetIds(ComponentName(c,receiver)).map{Triple(it,design.title,design)}}}
    val labels=mapOf(0 to "الإعدادات الافتراضية للنسخ الجديدة")+installed.associate{it.first to "${it.second} · ${it.first}"}
    var id by remember{mutableStateOf(0)};var options by remember(id){mutableStateOf(WidgetPreferences.get(c,id))};var saved by remember{mutableStateOf(false)}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("تخصيص الودجت",style=MaterialTheme.typography.headlineMedium);Choice("النسخة",id,labels.keys.toList(),{labels[it]!!}){id=it;saved=false}}
        if(installed.isEmpty())item{Text("أضف أحد تصاميم الودجت إلى الشاشة الرئيسية لتظهر نسخته هنا.")}
        item{Text("حجم النص ${(options.fontScale*100).toInt()}٪");Slider(options.fontScale,{options=options.copy(fontScale=it);saved=false},valueRange=.85f..1.25f);Text("يُضبط النص داخل المساحة المتاحة حتى لا يتداخل.")}
        item{Text("وضوح الخلفية ${(options.opacity*100).toInt()}٪");Slider(options.opacity,{options=options.copy(opacity=it);saved=false},valueRange=.25f..1f)}
        item{Toggle("إظهار الملاحظة والتجهيز",options.showNote){options=options.copy(showNote=it);saved=false};Toggle("إظهار ملخص الحصة التالية",options.showNext){options=options.copy(showNext=it);saved=false}}
        item{Text("ظهور الوقت بعد الضغط: ${options.peekSeconds} ثوانٍ");Slider(options.peekSeconds.toFloat(),{options=options.copy(peekSeconds=it.toInt());saved=false},valueRange=3f..15f,steps=11)}
        item{Text("مدة الضغط تخص الودجت التفاعلي. يتكيف إظهار التفاصيل مع نوع التصميم ومساحته.")}
        item{Button(onClick={WidgetPreferences.save(c,id,options);c.sendBroadcast(Intent(c,ScheduleWidgetReceiver::class.java).setAction(ScheduleWidgetReceiver.ACTION_TICK));saved=true},modifier=Modifier.fillMaxWidth()){Text(if(saved)"تم الحفظ ✓" else "حفظ تخصيص النسخة")}}
    }
}
