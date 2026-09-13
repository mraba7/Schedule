package com.mrabah.oneuischedule.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.widget.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun workspaceRevision():Int {
    val c=LocalContext.current;var revision by remember{mutableStateOf(0)}
    DisposableEffect(c){val prefs=listOf("class_journal","class_progress_notes","app_preferences","widget_lesson_details").map{c.getSharedPreferences(it,0)};val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};prefs.forEach{it.registerOnSharedPreferenceChangeListener(listener)};onDispose{prefs.forEach{it.unregisterOnSharedPreferenceChangeListener(listener)}}}
    return revision
}

@Composable
internal fun WorkspaceBanner(title:String,subtitle:String) {
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(colors.primaryContainer,colors.secondaryContainer)),RoundedCornerShape(26.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall,color=colors.onPrimaryContainer)
        Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=colors.onSurface)
    }
}

@Composable
internal fun ToolsHub() {
    val c=LocalContext.current
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{WorkspaceBanner("مساحة المعلم","التخطيط والمتابعة وأدوات الحصة، في مكان واحد")}
        item{Card(onClick={openStudio(c,"classroom")},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text("إدارة الحصة",style=MaterialTheme.typography.titleLarge);Text("المقاعد والمشاركة والفهم والتجارب وبنك الأسئلة")}}}
        val groups=listOf("أثناء اليوم" to listOf(Triple("focus","وضع الحصة","عد تنازلي واضح ومؤقت للنشاط"),Triple("agenda","الأجندة","استعرض أيامك وعدّل حصة محددة"),Triple("search","البحث الشامل","الفصول والملاحظات والملفات"),Triple("inbox","المتابعات","اجمع ملاحظاتك المفتوحة وأنجزها")),"التخطيط والاستعداد" to listOf(Triple("tomorrow","تجهيز بكرة","ملاحظات وملفات الحصص القادمة"),Triple("emergency","اليوم الطارئ","تأخير أو نشاط لهذا اليوم فقط"),Triple("calendar","التقويم","الإجازات والتواريخ المهمة"),Triple("summary","ملخص الأسبوع","الحصص والتقدم والمشاركة")),"الأدوات والتخصيص" to listOf(Triple("gallery","تصاميم الودجت","إضافة ومعاينة أدوات الشاشة"),Triple("week","تنظيم الأسبوع","نسخ وتبديل الحصص"),Triple("data","النسخ والاسترجاع","احفظ بياناتك وراجع سجل التعديلات")))
        groups.forEach{(title,tools)->item{Text(title,style=MaterialTheme.typography.titleMedium)};items(tools,key={it.first}){(page,label,detail)->Card(onClick={openStudio(c,page)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.titleMedium);Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text("‹",style=MaterialTheme.typography.headlineSmall)}}}}
    }
}

@Composable
internal fun SearchScreen(config:Config) {
    val c=LocalContext.current;val revision=workspaceRevision()
    var query by rememberSaveable{mutableStateOf("")}
    val index=remember(config,revision){WorkspaceSearch.all(c,config)}
    val results=remember(index,query){WorkspaceSearch.filter(index,query)}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("البحث الشامل",style=MaterialTheme.typography.headlineMedium)}
        item{OutlinedTextField(query,{query=it.take(200)},label={Text("فصل، درس، صفحة أو ملف")},singleLine=true,trailingIcon={if(query.isNotEmpty())TextButton(onClick={query=""}){Text("مسح")}},modifier=Modifier.fillMaxWidth())}
        item{Text(if(query.isBlank())"جرّب اسم فصل أو كلمة من ملاحظاتك. يعمل البحث دون إنترنت." else if(results.isEmpty())"لا توجد نتائج؛ جرّب كلمة أقصر." else "${results.size} نتيجة${if(results.size==80)" · حدّد بحثك لمزيد من الدقة" else ""}")}
        items(results,key={it.id}){r->Card(onClick={openStudio(c,r.page,r.section)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${r.title} · ${r.section}",style=MaterialTheme.typography.titleMedium);Text(r.detail,maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
    }
}

@Composable
internal fun AgendaWorkspace(config:Config,commit:(Config)->Unit,onDirty:(Boolean)->Unit={}) {
    val c=LocalContext.current
    var offset by rememberSaveable{mutableStateOf(0L)}
    var editingSchedule by rememberSaveable{mutableStateOf(false)}
    var dirty by remember{mutableStateOf(false)}
    var lesson by remember{mutableStateOf<Pair<LocalDate,Int>?>(null)}
    var now by remember{mutableStateOf(LocalDateTime.now())}
    LaunchedEffect(Unit){while(true){delay(30_000);now=LocalDateTime.now()}}
    if(editingSchedule) {
        Column {TextButton(enabled=!dirty,onClick={editingSchedule=false}){Text(if(dirty)"احفظ التعديلات للعودة" else "العودة للأجندة")};Box(Modifier.weight(1f)){com.mrabah.oneuischedule.ScheduleScreen(config,commit,onDirty={dirty=it;onDirty(it)})}}
    } else {
        val date=now.toLocalDate().plusDays(offset)
        val ui=ScheduleEngine.today(config,if(offset==0L)now else date.atStartOfDay())
        LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{WorkspaceBanner("أجندتك الدراسية",date.format(DateTimeFormatter.ofPattern("EEEE، d MMMM",Locale("ar"))))}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(enabled=offset> -365,onClick={offset--}){Text("السابق")};TextButton(onClick={offset=0}){Text("اليوم")};TextButton(enabled=offset<365,onClick={offset++}){Text("التالي")}}}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(0..6).forEach{d->val day=now.toLocalDate().plusDays(d.toLong());FilterChip(offset==d.toLong(),{offset=d.toLong()},label={Text(day.format(DateTimeFormatter.ofPattern("EEE d",Locale("ar"))))})}}}
            item{Row(Modifier.horizontalScroll(rememberScrollState())){OutlinedButton(onClick={editingSchedule=true}){Text("تحرير الجدول الأساسي")};TextButton(onClick={openStudio(c,"calendar")}){Text("التقويم")};TextButton(onClick={c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,buildString{append("جدولي $date\n");if(ui.slots.isEmpty())append(ui.holiday?.label ?: "لا توجد حصص");ui.slots.forEach{append("${periodName(it.period)} · ${it.displaySection ?: "انتظار"} · ${it.bell.start}–${it.bell.end}\n")}}),"مشاركة اليوم"))}){Text("مشاركة")}}}
            item{Text("${ui.slots.size} حصص · ${ui.slots.count{it.isStandby}} انتظار",style=MaterialTheme.typography.titleMedium)}
            if(ui.slots.isEmpty())item{Card(Modifier.fillMaxWidth()){Text(ui.holiday?.label ?: "يوم بلا حصص؛ يمكنك تعديل الجدول الأساسي أو اختيار يوم آخر.",Modifier.padding(22.dp))}}
            items(ui.slots,key={it.period}){slot->
                Card(onClick={lesson=date to slot.period},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(slot.state==SlotState.LIVE)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("${periodName(slot.period)} · ${slot.displaySection ?: "انتظار"}",style=MaterialTheme.typography.titleLarge)
                        Text("${slot.bell.start} – ${slot.bell.end}")
                        if(slot.note.isNotBlank())Text(slot.note)
                        if(offset==0L)Text(when(slot.state){SlotState.LIVE->"جارية الآن";SlotState.DONE->"✓ انتهت";else->"قادمة"},color=MaterialTheme.colorScheme.primary)
                        slot.displaySection?.let{section->TextButton(onClick={openStudio(c,"class",section)}){Text("صفحة الفصل")}}
                    }
                }
            }
        }
    }
    lesson?.let{(date,period)->com.mrabah.oneuischedule.DayEditDialog(config,date,period,{lesson=null}){commit(it);lesson=null}}
}

@Composable
internal fun FollowupsScreen(config:Config) {
    val c=LocalContext.current;val revision=workspaceRevision()
    var completed by rememberSaveable{mutableStateOf(false)}
    var adding by rememberSaveable{mutableStateOf(false)}
    var title by rememberSaveable{mutableStateOf("")}
    var section by rememberSaveable{mutableStateOf(config.sections.firstOrNull().orEmpty())}
    var undo by remember{mutableStateOf<JournalEntry?>(null)}
    val entries=remember(revision,completed){ClassJournal.all(c).filter{it.done==completed}}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{WorkspaceBanner("متابعاتك","ملاحظات ودروس الفصول التي تحتاج متابعة")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!completed,{completed=false},label={Text("المفتوحة")});FilterChip(completed,{completed=true},label={Text("المنجزة")})}}
        item{Button(onClick={adding=true},enabled=config.sections.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("متابعة جديدة")}}
        undo?.let{old->item{OutlinedButton(onClick={ClassJournal.all(c).find{it.id==old.id}?.let{latest->ClassJournal.save(c,latest.copy(done=old.done))};undo=null}){Text("تراجع عن آخر تغيير")}}}
        if(entries.isEmpty())item{Text(if(completed)"لا توجد متابعات منجزة بعد." else "كل شيء مرتب؛ لا توجد متابعات مفتوحة.")}
        items(entries,key={it.id}){entry->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(entry.title,style=MaterialTheme.typography.titleMedium);Text("الفصل ${entry.section} · ${if(entry.lesson)"درس" else "ملاحظة"}",color=MaterialTheme.colorScheme.primary);if(entry.text.isNotBlank())Text(entry.text,maxLines=3,overflow=TextOverflow.Ellipsis);Row{TextButton(onClick={openStudio(c,"class",entry.section)}){Text("التفاصيل")};TextButton(onClick={undo=entry;ClassJournal.save(c,entry.copy(done=!entry.done))}){Text(if(entry.done)"إعادة فتح" else "تم الإنجاز")}}}}}
    }
    if(adding)EditorPage(onDismissRequest={adding=false},title={Text("متابعة جديدة")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(title,{title=it.take(160)},label={Text("ما الذي تريد متابعته؟")},modifier=Modifier.fillMaxWidth());Choice("الفصل",section,config.sections,{it}){section=it}}},confirmButton={Button(enabled=title.isNotBlank() && section in config.sections,onClick={ClassJournal.save(c,JournalEntry(section=section,title=title.trim()));title="";adding=false}){Text("حفظ")}},dismissButton={TextButton(onClick={adding=false}){Text("إلغاء")}})
}

@Composable
internal fun LessonFocusScreen(config:Config,fixedNow:LocalDateTime?=null) {
    val c=LocalContext.current
    var now by remember{mutableStateOf(fixedNow ?: LocalDateTime.now())}
    var deadline by rememberSaveable{mutableStateOf(0L)}
    var remaining by remember{mutableStateOf(0L)}
    var keepAwake by rememberSaveable{mutableStateOf(false)}
    val view=androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view,keepAwake){val old=view.keepScreenOn;view.keepScreenOn=keepAwake;onDispose{view.keepScreenOn=old}}
    LaunchedEffect(deadline){while(true){now=fixedNow ?: LocalDateTime.now();remaining=((deadline-android.os.SystemClock.elapsedRealtime()+999)/1000).coerceAtLeast(0);delay(1000)}}
    val ui=ScheduleEngine.today(config,now);val slot=ui.focus
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item{WorkspaceBanner("وضع الحصة",if(ui.live!=null)"تركيز على الحصة الحالية" else "استعداد للحصة القادمة")}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("إبقاء الشاشة مضاءة",Modifier.weight(1f));Switch(keepAwake,{keepAwake=it})}}
        if(slot==null)item{Text(ui.holiday?.label ?: "لا توجد حصة متبقية اليوم",style=MaterialTheme.typography.headlineMedium)}
        else item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(periodName(slot.period),style=MaterialTheme.typography.headlineLarge);Text("الفصل ${slot.displaySection ?: "انتظار"}",style=MaterialTheme.typography.titleLarge);val seconds=Duration.between(now,ui.date.atTime(if(ui.live!=null)slot.bell.end else slot.bell.start)).seconds.coerceAtLeast(0);Text(String.format(Locale.ENGLISH,"%02d:%02d",seconds/60,seconds%60),style=MaterialTheme.typography.displayLarge);Text(if(ui.live!=null)"حتى نهاية الحصة" else "حتى البداية");Text("${slot.bell.start} – ${slot.bell.end}");if(ui.live!=null)LinearProgressIndicator(progress={ui.progress},modifier=Modifier.fillMaxWidth());slot.displaySection?.let{section->ClassNotes.get(c,section)?.let{Text("آخر نقطة: ${it.text}")};Button(onClick={openStudio(c,"shortcuts",section)}){Text("ملفات الفصل")};TextButton(onClick={c.startActivity(ClassNotes.intent(c,section))}){Text("تسجيل أين توقفت")}}}}}
        item {
            Text("مؤقت النشاط",style=MaterialTheme.typography.titleLarge)
            Text(if(deadline>0 && remaining==0L)"انتهى وقت النشاط ✓" else String.format(Locale.ENGLISH,"%02d:%02d",remaining/60,remaining%60),style=MaterialTheme.typography.headlineLarge)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(1,3,5,10).forEach{minutes->
                    TextButton(onClick={deadline=android.os.SystemClock.elapsedRealtime()+minutes*60_000L}){Text("$minutes د")}
                }
            }
            if(deadline>0)TextButton(onClick={deadline=0;remaining=0}){Text("إيقاف المؤقت")}
            Text("مؤقت مرئي أثناء استخدام هذه الشاشة.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
