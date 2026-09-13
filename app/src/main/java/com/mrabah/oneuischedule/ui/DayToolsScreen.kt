package com.mrabah.oneuischedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.widget.*
import java.time.*

@Composable
internal fun QuickLessonScreen(config:Config,commit:(Config)->Unit) {
    var date by remember{mutableStateOf(LocalDate.now())}
    var period by remember{mutableStateOf<Int?>(null)}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{WorkspaceBanner("إضافة أو تعديل حصة","تعديل مؤقت لليوم المختار")}
        item{DateField("اليوم",date){date=it}}
        SchoolTools.bells(config,date).forEach{bell->item{OutlinedButton(onClick={period=bell.period},modifier=Modifier.fillMaxWidth()){Text("${periodName(bell.period)} · ${bell.start}–${bell.end}")}}}
    }
    period?.let{p->com.mrabah.oneuischedule.DayEditDialog(config,date,p,{period=null}){commit(it);period=null}}
}

@Composable
internal fun AbsenceScreen(config:Config,commit:(Config)->Unit) {
    val c=LocalContext.current
    var reason by rememberSaveable{mutableStateOf("غائب")}
    var custom by rememberSaveable{mutableStateOf("")}
    val date=LocalDate.now()
    val active=config.absenceDate==date.toString()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{WorkspaceBanner("غياب أو إجازة اليوم","$date · لهذا اليوم فقط")}
        item{Text("تُلغى حصص اليوم وتُوقف تنبيهاتها وتذكيرات الفصول، وتظهر الحالة في الودجت. يعود جدولك تلقائيًا في اليوم التالي دون تغيير الجدول الأسبوعي.")}
        if(active)item{Text("الحالة الحالية: ${config.absenceLabel}",style=MaterialTheme.typography.titleLarge)}
        item{Choice("السبب",reason,listOf("غائب","إجازة مرضية","إجازة","سبب آخر"),{it}){reason=it}}
        if(reason=="سبب آخر")item{OutlinedTextField(custom,{custom=it.take(60)},label={Text("اكتب الحالة التي تظهر في الودجت")},modifier=Modifier.fillMaxWidth())}
        item{Button(enabled=reason!="سبب آخر" || custom.isNotBlank(),onClick={
            val latest=ScheduleStore.load(c)
            commit(latest.copy(absenceDate=LocalDate.now().toString(),absenceLabel=if(reason=="سبب آخر")custom.trim() else reason))
        },modifier=Modifier.fillMaxWidth()){Text(if(active)"تحديث سبب الغياب" else "اعتماد وإيقاف حصص اليوم")}}
        if(active)item{OutlinedButton(onClick={val latest=ScheduleStore.load(c);commit(latest.copy(absenceDate="",absenceLabel=""))},modifier=Modifier.fillMaxWidth()){Text("التراجع وإعادة حصص اليوم")}}
    }
}

@Composable
internal fun TomorrowScreen(config:Config) {
    val c=LocalContext.current
    var revision by remember{mutableStateOf(0)}
    var date by remember{mutableStateOf(LocalDate.now().plusDays(1))}
    DisposableEffect(Unit) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
        val prefs=listOf("app_preferences","class_journal","class_progress_notes","widget_lesson_details").map{c.getSharedPreferences(it,0)}
        prefs.forEach{it.registerOnSharedPreferenceChangeListener(listener)}
        val owner=c as? androidx.lifecycle.LifecycleOwner
        val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME){date=LocalDate.now().plusDays(1);revision++}}
        owner?.lifecycle?.addObserver(observer)
        onDispose{prefs.forEach{it.unregisterOnSharedPreferenceChangeListener(listener)};owner?.lifecycle?.removeObserver(observer)}
    }
    val ui=remember(config,date,revision){ScheduleEngine.today(config,date.atStartOfDay())}
    val ready=remember(config,date,revision){ui.slots.count{TomorrowPrep.ready(c,date,it)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("تجهيز بكرة بنقرة",style=MaterialTheme.typography.headlineSmall);Text("$date · $ready من ${ui.slots.size} حصص جاهزة")}
        if(ui.slots.isEmpty())item{Text(ui.holiday?.label ?: "غدًا بلا حصص؛ استمتع بوقتك")}
        if(ui.slots.isNotEmpty())item{Text("ملاحظات الفصول وملفاتها مجمّعة هنا. حدّد الحصة جاهزة بعد مراجعتها؛ أي تغيير في تفاصيلها يعيدها للمراجعة.")}
        ui.slots.forEach {slot->item(key=slot.period) {
            val section=slot.displaySection.orEmpty()
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row {Column(Modifier.weight(1f)){Text("${periodName(slot.period)} · ${section.ifBlank{"انتظار"}}",style=MaterialTheme.typography.titleMedium);Text("${slot.bell.start} – ${slot.bell.end}")};Checkbox(checked=TomorrowPrep.ready(c,date,slot),onCheckedChange={TomorrowPrep.set(c,date,slot,it)})}
                Text(if(TomorrowPrep.ready(c,date,slot))"جاهزة ✓" else "تحتاج مراجعة")
                if(slot.note.isNotBlank())Text(slot.note)
                ClassNotes.get(c,section)?.let{Text("آخر نقطة: ${it.text}")}
                val key="$date#${slot.period}"
                PreparationStore.task(c,key).takeIf{it.isNotBlank()}?.let{Text("التجهيز: $it")}
                PreparationStore.note(c,key).takeIf{it.isNotBlank()}?.let{Text(it)}
                ClassJournal.forClass(c,section).filter{!it.done}.forEach{entry->Text("• ${entry.title}${if(entry.text.isBlank())"" else " — ${entry.text}"}${if(entry.fileName.isBlank())"" else " · ${entry.fileName}"}")}
                val shortcuts=ClassShortcuts.forClass(c,section)
                if(shortcuts.isNotEmpty())Text("الملفات والروابط: ${shortcuts.joinToString("، "){it.title}}")
                if(section.isNotBlank()) {
                    OutlinedButton(onClick={openStudio(c,"shortcuts",section)},modifier=Modifier.fillMaxWidth()){Text("فتح ملفات وروابط الفصل")}
                    TextButton(onClick={openStudio(c,"class",section)}){Text("سجل الفصل والمرفقات")}
                }
                if(slot.isStandby)TextButton(onClick={c.startActivity(StandbyAssignments.intent(c,date,slot.period))}){Text("تحديد فصل الانتظار")}
                TextButton(onClick={c.startActivity(android.content.Intent(c,DesignLessonActivity::class.java).putExtra("key",key).putExtra("title","تجهيز ${periodName(slot.period)}"))}){Text("تعديل تجهيز الحصة")}
            }}
        }}
    }
}

@Composable
internal fun EmergencyScreen(config:Config,commit:(Config)->Unit) {
    val c=LocalContext.current
    var minutes by rememberSaveable{mutableStateOf("15")}
    var activity by rememberSaveable{mutableStateOf("")}
    var period by rememberSaveable{mutableStateOf(0)}
    var message by remember{mutableStateOf("")}
    var proposed by remember{mutableStateOf<Config?>(null)}
    var original by remember{mutableStateOf<Config?>(null)}
    var reviewedAt by remember{mutableStateOf<LocalDateTime?>(null)}
    val now=LocalDateTime.now();val date=now.toLocalDate()
    val future=SchoolTools.bells(config,date).filter{it.start>now.toLocalTime()}
    val selected=period.takeIf{p->future.any{it.period==p}} ?: future.firstOrNull()?.period
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("اليوم الطارئ",style=MaterialTheme.typography.headlineSmall);Text("تأخير أو نشاط لهذا اليوم فقط. تبقى الحصص التي بدأت كما هي، وتتحرك الحصة المختارة وما بعدها.")}
        if(EmergencyDay.active(config,date))item{Text(SchoolTools.profile(config,date)!!.name);OutlinedButton(onClick={original=config;reviewedAt=LocalDateTime.now();proposed=EmergencyDay.undo(config,date)}){Text("معاينة التراجع عن تعديلات اليوم")}}
        if(future.isEmpty() || config.holidayOn(date)!=null)item{Text("لا توجد أوقات متبقية قابلة للتعديل اليوم")}
        else {
            item{Text("ابدأ من الحصة")}
            future.forEach{bell->item{FilterChip(selected=selected==bell.period,onClick={period=bell.period;proposed=null},label={Text("${periodName(bell.period)} · ${bell.start}")})}}
            item{OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3);proposed=null},label={Text("المدة بالدقائق · 5 إلى 120")},singleLine=true,modifier=Modifier.fillMaxWidth())}
            item{OutlinedTextField(activity,{activity=it.take(60);proposed=null},label={Text("اسم النشاط أو الاجتماع · اختياري")},singleLine=true,modifier=Modifier.fillMaxWidth())}
            item{Button(enabled=selected!=null,onClick={runCatching{val at=LocalDateTime.now();val next=EmergencyDay.apply(config,at,selected!!,minutes.toIntOrNull() ?: 0,activity);original=config;reviewedAt=at;proposed=next;message=""}.onFailure{message=it.message ?: "تعذر التعديل"}},modifier=Modifier.fillMaxWidth()){Text("معاينة التغيير")}}
        }
        if(message.isNotBlank())item{Text(message,color=MaterialTheme.colorScheme.error)}
        proposed?.let{next->
            item{Text("قبل ← بعد",style=MaterialTheme.typography.titleLarge)}
            SchoolTools.bells(next,date).forEach{bell->item{val old=SchoolTools.bells(config,date).first{it.period==bell.period};Text("${periodName(bell.period)}\n${old.start}–${old.end} ← ${bell.start}–${bell.end}")}}
            item{Button(onClick={
                val at=LocalDateTime.now();val baseline=original
                val changed=SchoolTools.bells(next,date).filter{it!=SchoolTools.bells(config,date).firstOrNull{old->old.period==it.period}}
                if(baseline!=ScheduleStore.load(c) || at.toLocalDate()!=reviewedAt?.toLocalDate() || changed.any{bell->minOf(bell.start,SchoolTools.bells(config,date).first{it.period==bell.period}.start)<=at.toLocalTime()}) {message="تغيّر الجدول أو بدأ أحد الأوقات؛ أعد المعاينة";proposed=null}
                else {commit(next);proposed=null;message="تم الحفظ وتحديث الودجت والتنبيهات"}
            },modifier=Modifier.fillMaxWidth()){Text("اعتماد لهذا اليوم")}}
            item{TextButton(onClick={proposed=null}){Text("إلغاء المعاينة")}}
        }
    }
}
