package com.mrabah.oneuischedule.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.widget.*
import kotlinx.coroutines.launch
import java.time.*
import java.util.UUID

@Composable
internal fun ClassHub(config:Config) {
    val c=LocalContext.current
    var query by rememberSaveable{mutableStateOf("")}
    var school by rememberSaveable{mutableStateOf(false)}
    var adding by remember{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    val entries=if(school)(config.schoolSections+config.sections).distinct() else config.sections
    val load=ScheduleEngine.weeklyLoad(config)
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Column {Text("الفصول والمنهج",style=MaterialTheme.typography.headlineMedium);Text("${load.teaching} تدريس · ${load.standby} انتظار أسبوعيًا")}}
        item{OutlinedTextField(query,{query=it},label={Text("ابحث عن فصل")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!school,{school=false},label={Text("فصولي")});FilterChip(school,{school=true},label={Text("كل المدرسة")});TextButton(onClick={adding=true}){Text("إضافة")}}}
        entries.filter{it.contains(query.trim(),true)}.forEach {section->item {
            val progress=config.progress[section] ?: SectionProgress()
            val color=Color(android.graphics.Color.parseColor(SchoolTools.color(config,section)))
            Card(onClick={openStudio(c,"class",section)},modifier=Modifier.fillMaxWidth()) {Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(5.dp).height(60.dp).background(color));Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text("الفصل $section",style=MaterialTheme.typography.titleLarge);Text("${load.perSection[section] ?: 0} حصص أسبوعية · ${progress.taught} درسًا");if(progress.last.isNotBlank())Text("آخر درس: ${progress.last}",style=MaterialTheme.typography.bodySmall)}}}
        }}
        if(entries.none{it.contains(query.trim(),true)})item{Text("لا يوجد فصل مطابق")}
        item{Text("مقارنة التقدم",style=MaterialTheme.typography.titleLarge)}
        val lead=config.progress.values.maxOfOrNull{it.taught} ?: 0
        config.sections.forEach {section->item{val taught=config.progress[section]?.taught ?: 0;Text("$section · $taught درسًا${if(lead>taught) " · الفارق ${lead-taught}" else ""}");LinearProgressIndicator(progress={if(lead==0)0f else taught.toFloat()/lead},modifier=Modifier.fillMaxWidth())}}
    }
    if(adding)AlertDialog(onDismissRequest={adding=false},title={Text("إضافة فصل للمدرسة")},text={OutlinedTextField(name,{name=it.take(40)},label={Text("اسم الفصل")})},confirmButton={Button(enabled=name.isNotBlank(),onClick={val latest=ScheduleStore.load(c);ScheduleStore.save(c,latest.copy(schoolSections=(latest.schoolSections+name.trim()).distinct()));adding=false;name=""}){Text("حفظ")}},dismissButton={TextButton(onClick={adding=false}){Text("إلغاء")}})
}

@Composable
internal fun ClassPage(config:Config,section:String,commit:(Config)->Unit) {
    val c=LocalContext.current
    var revision by remember{mutableStateOf(0)}
    var filter by rememberSaveable{mutableStateOf(0)}
    var editing by remember{mutableStateOf<JournalEntry?>(null)}
    var removing by remember{mutableStateOf<JournalEntry?>(null)}
    var message by remember{mutableStateOf("")}
    DisposableEffect(Unit){val p=c.getSharedPreferences("class_journal",0);val l=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};p.registerOnSharedPreferenceChangeListener(l);onDispose{p.unregisterOnSharedPreferenceChangeListener(l)}}
    val entries=remember(revision,section){ClassJournal.forClass(c,section)}
    val next=ClassNotes.next(config,ClassNote(section,"",LocalDateTime.now().minusNanos(1)),LocalDateTime.now())
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Column {Text("الفصل $section",style=MaterialTheme.typography.headlineMedium);Text(next?.let{"القادمة: ${it.start.toLocalDate()} · ${periodName(it.period)} · ${it.start.toLocalTime()}"} ?: "لا توجد حصة تدريس قادمة")}}
        item {Column {Text("لون الفصل",style=MaterialTheme.typography.labelLarge);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("#D6B879","#96BFE0","#C2A0DB","#88CBBF","#E3A18F","#B8C68B").forEach {hex->OutlinedButton(onClick={commit(config.copy(classColors=config.classColors+(section to hex)))},colors=ButtonDefaults.outlinedButtonColors(containerColor=Color(android.graphics.Color.parseColor(hex)),contentColor=Color(0xFF142332))){Text(if(SchoolTools.color(config,section)==hex)"✓" else "لون")}}}}}
        item {Column {val progress=config.progress[section] ?: SectionProgress();Text("${progress.taught} درسًا منجزًا",style=MaterialTheme.typography.titleMedium);if(progress.last.isNotBlank())Text("آخر درس: ${progress.last}");if(progress.next.isNotBlank())Text("الدرس القادم: ${progress.next}")}}
        item {Column {TextButton(onClick={c.startActivity(ClassNotes.intent(c,section))}){Text("آخر نقطة محفوظة وتنبيه الفصل")};ClassNotes.get(c,section)?.let{Text(it.text)}}}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={editing=JournalEntry(section=section,title="")}){Text("ملاحظة جديدة")};OutlinedButton(onClick={editing=JournalEntry(section=section,title="",lesson=true)}){Text("إضافة درس")}}}
        item {Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("الكل","المنهج","الملاحظات","المنجز").forEachIndexed{i,label->FilterChip(filter==i,{filter=i},label={Text(label)})}}}
        val visible=entries.filter{when(filter){1->it.lesson;2->!it.lesson;3->it.done;else->true}}
        if(visible.isEmpty())item{Text("لا توجد عناصر في هذا القسم. أضف درسًا أو ملاحظة.")}
        visible.groupBy{it.unit.ifBlank{"عام"}}.forEach {(unit,list)->
            item{Text(unit,style=MaterialTheme.typography.titleLarge)}
            list.forEach {entry->item(key=entry.id){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(entry.title,style=MaterialTheme.typography.titleMedium);if(entry.text.isNotBlank())Text(entry.text)
                if(entry.remind)Text(if(entry.delivered)"تم إرسال التذكير" else "تذكير عند الحصة القادمة",style=MaterialTheme.typography.labelSmall)
                if(entry.period!=null)Text("${entry.savedAt.toLocalDate()} · ${periodName(entry.period)}",style=MaterialTheme.typography.bodySmall)
                if(entry.file.isNotBlank())TextButton(onClick={runCatching{val uri=FileProvider.getUriForFile(c,"${c.packageName}.updates",java.io.File(DataVault.attachments(c),entry.file));c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,entry.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{message="لا يوجد تطبيق مناسب لفتح المرفق"}}){Text("فتح ${entry.fileName}")}
                Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={ClassJournal.save(c,entry.copy(done=!entry.done,period=if(!entry.done)ScheduleEngine.today(config,LocalDateTime.now()).live?.takeIf{it.section==section}?.period else entry.period));revision++}){Text(if(entry.done)"إلغاء الإنجاز" else "تم الإنجاز")};TextButton(onClick={editing=entry}){Text("تعديل")};TextButton(onClick={removing=entry}){Text("حذف")}}
            }}}}
        }
        item{Text("حصص الانتظار لهذا الفصل",style=MaterialTheme.typography.titleMedium)}
        val assignments=config.standbySections.filterValues{it==section}.entries.sortedByDescending{it.key}
        if(assignments.isEmpty())item{Text("لا يوجد تكليف انتظار مسجّل")}
        assignments.forEach{(key,_)->item{Text("${key.substringBefore('#')} · الحصة ${key.substringAfter('#')}")}}
        if(message.isNotBlank())item{Text(message)}
    }
    editing?.let {entry->JournalEditor(entry,config.sections,onDismiss={editing=null}) {saved,copyToAll->
        ClassJournal.save(c,saved)
        if(copyToAll && saved.lesson)config.sections.filter{it!=section}.forEach{ClassJournal.save(c,saved.copy(id=UUID.randomUUID().toString(),section=it,done=false,delivered=false,period=null))}
        revision++;editing=null
    }}
    removing?.let{entry->AlertDialog(onDismissRequest={removing=null},title={Text("حذف ${entry.title}؟")},text={Text("يمكن استرجاعه من سجل التعديلات.")},confirmButton={TextButton(onClick={ClassJournal.remove(c,entry);removing=null;revision++}){Text("حذف")}},dismissButton={TextButton(onClick={removing=null}){Text("إلغاء")}})}
}

@Composable
private fun JournalEditor(entry:JournalEntry,sections:List<String>,onDismiss:()->Unit,onSave:(JournalEntry,Boolean)->Unit) {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var title by rememberSaveable{mutableStateOf(entry.title)};var unit by rememberSaveable{mutableStateOf(entry.unit)};var text by rememberSaveable{mutableStateOf(entry.text)}
    var reminder by rememberSaveable{mutableStateOf(entry.remind)};var all by rememberSaveable{mutableStateOf(false)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(!granted)reminder=false}
    var attachment by remember{mutableStateOf(Triple(entry.file,entry.fileName,entry.mime))};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{busy=true;runCatching{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){ClassJournal.attach(c,uri)}}.onSuccess{attachment=it}.onFailure{error=it.message.orEmpty()};busy=false}}
    EditorPage(onDismissRequest=onDismiss,title={Text(if(entry.lesson)"الوحدة والدرس" else "ملاحظة الفصل")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        OutlinedTextField(title,{title=it.take(120)},label={Text("العنوان")});OutlinedTextField(unit,{unit=it.take(120)},label={Text(if(entry.lesson)"الوحدة" else "التصنيف")});OutlinedTextField(text,{text=it.take(4000)},label={Text("التفاصيل أو الصفحة")},minLines=3)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(reminder,{enabled->reminder=enabled;if(enabled && android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)});Text("ذكّرني في الحصة القادمة")}
        if(entry.lesson && sections.size>1)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(all,{all=it});Text("نسخ خطة الدرس إلى بقية فصولي")}
        OutlinedButton(enabled=!busy,onClick={picker.launch(arrayOf("image/*","application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))}){Text(if(attachment.first.isBlank())"إرفاق صورة أو ملف" else attachment.second)}
        if(attachment.first.isNotBlank())TextButton(onClick={attachment=Triple("","","")}){Text("إزالة المرفق من العنصر")}
        if(error.isNotBlank())Text(error)
    }},confirmButton={Button(enabled=title.isNotBlank() && !busy,onClick={onSave(entry.copy(title=title.trim(),unit=unit.trim(),text=text.trim(),remind=reminder,delivered=false,savedAt=LocalDateTime.now(),file=attachment.first,fileName=attachment.second,mime=attachment.third),all)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}
