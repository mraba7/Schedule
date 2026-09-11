package com.mrabah.oneuischedule.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.DataVault
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun DataScreen() {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var revision by remember{mutableStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var preview by remember{mutableStateOf<DataVault.Import?>(null)}
    var history by remember{mutableStateOf<JSONObject?>(null)}
    fun work(task:suspend ()->Unit){scope.launch {busy=true;runCatching{task()}.onFailure{message=it.message ?: "تعذّر إتمام العملية"};busy=false;revision++}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)work{withContext(Dispatchers.IO){c.contentResolver.openOutputStream(uri)!!.use{DataVault.export(c,it)}};message="تم تصدير البيانات والمرفقات"}}
    val load=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)work{preview=withContext(Dispatchers.IO){c.contentResolver.openInputStream(uri)!!.use{DataVault.preview(c,it)}}}}
    DisposableEffect(Unit){onDispose{preview?.discard()}}
    val daily=remember(revision){DataVault.dailyFiles(c)}
    val snapshots=remember(revision){DataVault.snapshots(c)}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("حماية بياناتك",style=MaterialTheme.typography.headlineMedium);Text("النسخة الشاملة تشمل الجدول والفصول والملاحظات والتجهيزات والمرفقات والتخصيص.")}
        item {Button(enabled=!busy,onClick={export.launch("schedule-${java.time.LocalDate.now()}.zip")},modifier=Modifier.fillMaxWidth()){Text("تصدير نسخة شاملة")}}
        item {OutlinedButton(enabled=!busy,onClick={load.launch(arrayOf("application/zip","application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("استيراد ومعاينة نسخة")}}
        if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(message.isNotBlank())item{Text(message)}
        item {Text("نسخ محلية تلقائية",style=MaterialTheme.typography.titleLarge);Text("عند أول فتح للتطبيق يوميًا تُحفظ نسخة محلية، مع الاحتفاظ بآخر 7 نسخ. صدّر نسخة خارج الجهاز قبل حذفه أو تبديله.")}
        if(daily.isEmpty())item{Text("لم تُنشأ نسخة يومية بعد")}
        daily.forEach {file->item{OutlinedButton(enabled=!busy,onClick={work{preview=withContext(Dispatchers.IO){file.inputStream().use{DataVault.preview(c,it)}}}}){Text("معاينة ${file.nameWithoutExtension}")}}}
        item {Text("سجل التعديلات",style=MaterialTheme.typography.titleLarge);Text("استرجاع نقطة سابقة يعيد البيانات المحفوظة في تلك النقطة. تُحفظ نقطة أخرى قبل الاسترجاع.")}
        if(snapshots.isEmpty())item{Text("لا توجد تعديلات محفوظة بعد")}
        snapshots.forEach {file->item{val data=remember(file){runCatching{JSONObject(file.readText())}.getOrNull()};if(data!=null)Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)){Text(data.optString("label"));Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(data.optLong("savedAt"))));TextButton(enabled=!busy,onClick={history=data}){Text("معاينة واسترجاع")}}}}}
    }
    val data=preview?.data ?: history
    if(data!=null)AlertDialog(onDismissRequest={if(!busy){preview?.discard();preview=null;history=null}},title={Text("معاينة الاستعادة")},text={Text(DataVault.summary(data)+"\nستُستبدل البيانات الحالية بهذه النسخة. تبقى نقطة رجوع في السجل.")},
        confirmButton={Button(enabled=!busy,onClick={work{val staged=preview;withContext(Dispatchers.IO){if(staged!=null)DataVault.apply(c,staged) else DataVault.restore(c,data)};preview=null;history=null;message="تم استرجاع البيانات وتحديث الجدول"}}){Text("استعادة")}},dismissButton={TextButton(enabled=!busy,onClick={preview?.discard();preview=null;history=null}){Text("إلغاء")}})
}
