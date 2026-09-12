package com.mrabah.oneuischedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
internal fun <T> Choice(label:String,value:T,values:List<T>,name:(T)->String,onChange:(T)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Box {OutlinedButton(onClick={expanded=true}){Text("$label: ${name(value)}")};DropdownMenu(expanded,{expanded=false}){values.forEach {item->DropdownMenuItem(text={Text(name(item))},onClick={onChange(item);expanded=false})}}}
}
@Composable internal fun DateField(label:String,date:LocalDate,onChange:(LocalDate)->Unit) {
    val c=LocalContext.current
    OutlinedButton(onClick={DatePickerDialog(c,{_,y,m,d->onChange(LocalDate.of(y,m+1,d))},date.year,date.monthValue-1,date.dayOfMonth).show()}){Text("$label: $date")}
}
@Composable private fun TimeField(time:LocalTime,onChange:(LocalTime)->Unit) {
    val c=LocalContext.current;OutlinedButton(onClick={TimePickerDialog(c,{_,h,m->onChange(LocalTime.of(h,m))},time.hour,time.minute,true).show()}){Text(time.toString())}
}
private fun dayName(d:DayOfWeek)=d.getDisplayName(TextStyle.FULL,Locale("ar"))
@Composable
internal fun WeekTools(config:Config,commit:(Config)->Unit) {
    var source by remember{mutableStateOf(DayOfWeek.SUNDAY)};var target by remember{mutableStateOf(DayOfWeek.MONDAY)}
    var p by remember{mutableStateOf(1)};var q by remember{mutableStateOf(2)}
    var pending by remember{mutableStateOf<Pair<String,Config>?>(null)}
    var message by remember{mutableStateOf("")}
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("الأسبوع والنسخ والتبديل",style=MaterialTheme.typography.headlineMedium);Text("عرض أسبوع كامل؛ اسحب أفقيًا عند الحاجة.")}
        item{Column(Modifier.horizontalScroll(rememberScrollState())){
            Row{Text("اليوم",Modifier.width(85.dp));(1..7).forEach{Text("الحصة $it",Modifier.width(85.dp))}}
            ScheduleStore.DAYS.forEach{day->Row(Modifier.padding(vertical=8.dp)){Text(dayName(day),Modifier.width(85.dp));(1..7).forEach{period->val duty=config.templateOn(day)[period];Text(when(duty){is Duty.Teach->duty.section;Duty.Standby->"انتظار";null->"—"},Modifier.width(85.dp))}}}}
        }
        item{Choice("من",source,ScheduleStore.DAYS,::dayName){source=it};Choice("إلى",target,ScheduleStore.DAYS,::dayName){target=it}}
        item{Button(enabled=source!=target,onClick={pending="نسخ حصص ${dayName(source)} إلى ${dayName(target)} واستبدال حصص اليوم المستهدف" to SchoolTools.copyDay(config,source,target)}){Text("معاينة نسخ اليوم")}}
        item{Text("تبديل حصتين",style=MaterialTheme.typography.titleLarge);Choice("حصة اليوم الأول",p,(1..7).toList(),{it.toString()}){p=it};Choice("حصة اليوم الثاني",q,(1..7).toList(),{it.toString()}){q=it}}
        item{Button(enabled=source!=target || p!=q,onClick={pending="تبديل الحصة $p يوم ${dayName(source)} مع الحصة $q يوم ${dayName(target)}" to SchoolTools.swap(config,source,p,target,q)}){Text("معاينة التبديل")}}
        if(message.isNotBlank())item{Text(message)}
    }
    pending?.let{(label,next)->AlertDialog(onDismissRequest={pending=null},title={Text("مراجعة التعديل الأسبوعي")},text={Text("$label.\nيتكرر أسبوعيًا. تبقى التعديلات المؤقتة لهذا اليوم مستقلة.")},confirmButton={Button(onClick={commit(next);pending=null;message="تم حفظ التعديل الأسبوعي"}){Text("حفظ")}},dismissButton={TextButton(onClick={pending=null}){Text("إلغاء")}})}
}

@Composable
internal fun CalendarScreen(config:Config,commit:(Config)->Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<Holiday?>(null) }
    var original by remember { mutableStateOf<Holiday?>(null) }
    val cells=List(month.atDay(1).dayOfWeek.value%7){0}+(1..month.lengthOfMonth()).toList()
    val next=config.holidays.filter {it.to>=LocalDate.now()}.minByOrNull {it.from}
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Column {
            Text("التقويم الدراسي",style=MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
                TextButton(onClick={month=month.minusMonths(1)}) {Text("السابق")}
                Text("${month.month.getDisplayName(TextStyle.FULL,Locale("ar"))} ${month.year}")
                TextButton(onClick={month=month.plusMonths(1)}) {Text("التالي")}
            }
        }}
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("أحد","اثن","ثلا","أرب","خمي","جمع","سبت").forEach {label ->
                    Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        cells.chunked(7).forEach {week ->
            item {
                Row(Modifier.fillMaxWidth()) {
                    week.forEach {day ->
                        if(day==0) Spacer(Modifier.weight(1f)) else {
                            val date=month.atDay(day)
                            val holiday=config.holidayOn(date)
                            val color=if(selected==date)MaterialTheme.colorScheme.primaryContainer else if(holiday!=null)MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
                            TextButton(onClick={selected=date},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.textButtonColors(containerColor=color)) {Text(day.toString())}
                        }
                    }
                    repeat(7-week.size) {Spacer(Modifier.weight(1f))}
                }
            }
        }
        item {Column {
            Text(selected.toString(),style=MaterialTheme.typography.titleLarge)
            Text(config.holidayOn(selected)?.label ?: "${ScheduleEngine.dutiesOn(config,selected).size} حصص في هذا اليوم")
            SchoolTools.profile(config,selected)?.let {Text("التوقيت: ${it.name}")}
            OutlinedButton(onClick={original=null;editing=Holiday(selected,selected,"إجازة")}) {Text("إضافة إجازة")}
        }}
        if(next!=null) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${next.label} · ${next.from} إلى ${next.to}")
                    Text("العودة بعد الإجازة: ${ScheduleEngine.nextWorkday(config,next.to)}")
                }
            }
        }
        config.holidays.sortedBy {it.from}.forEach {holiday ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(holiday.label)
                        Text("${holiday.from} إلى ${holiday.to}")
                        TextButton(onClick={original=holiday;editing=holiday}) {Text("تعديل الإجازة")}
                    }
                }
            }
        }
    }
    editing?.let {holiday ->
        val remove:(()->Unit)?=original?.let {old -> {commit(config.copy(holidays=config.holidays-old));editing=null}}
        HolidayEditor(holiday,{editing=null},remove) {updated ->
            val retained=config.holidays.filter {it!=original}
            commit(config.copy(holidays=(retained+updated).sortedBy {it.from}))
            editing=null
        }
    }
}
@Composable
private fun HolidayEditor(initial:Holiday,onDismiss:()->Unit,onDelete:(()->Unit)?,onSave:(Holiday)->Unit) {
    var name by remember{mutableStateOf(initial.label)};var from by remember{mutableStateOf(initial.from)};var to by remember{mutableStateOf(initial.to)}
    EditorPage(onDismissRequest=onDismiss,title={Text("تفاصيل الإجازة")},text={Column(Modifier.verticalScroll(rememberScrollState())){OutlinedTextField(name,{name=it},label={Text("الاسم")});DateField("من",from){from=it};DateField("إلى",to){to=it};if(to<from)Text("النهاية يجب ألا تسبق البداية",color=MaterialTheme.colorScheme.error);if(onDelete!=null)TextButton(onClick=onDelete){Text("حذف الإجازة · يمكن التراجع من السجل")}}},confirmButton={Button(enabled=name.isNotBlank() && to>=from,onClick={onSave(Holiday(from,to,name.trim()))}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
internal fun ProfilesScreen(config:Config,commit:(Config)->Unit) {
    var editing by remember{mutableStateOf<TimetableProfile?>(null)}
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Text("الأوقات الخاصة",style=MaterialTheme.typography.headlineMedium);Text("يُفعّل التوقيت تلقائيًا خلال تاريخه فقط. تبقى الإجازات أولوية فوق أي توقيت.")}
        item{Row(Modifier.horizontalScroll(rememberScrollState())){listOf("صيفي","شتوي","رمضان","اختبارات").forEach{name->OutlinedButton(onClick={editing=TimetableProfile(UUID.randomUUID().toString(),name,LocalDate.now(),LocalDate.now().plusDays(6),if(name=="شتوي")Defaults.winterBells else config.bells,if(name=="اختبارات")emptyMap() else null)}){Text("إضافة $name")}}}}
        config.profiles.forEach{profile->item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(profile.name,style=MaterialTheme.typography.titleLarge);Text("${profile.from} إلى ${profile.to}");Text(if(profile.week==null)"نفس الحصص، بأوقات مختلفة" else "حصص مستقلة لهذه الفترة");Row{TextButton(onClick={editing=profile}){Text("تعديل")};TextButton(onClick={commit(config.copy(profiles=config.profiles-profile))}){Text("حذف · قابل للاسترجاع")}}}}}}
    }
    editing?.let{profile->ProfileEditor(profile,config,{editing=null}){updated->commit(config.copy(profiles=config.profiles.filter{it.id!=updated.id}+updated));editing=null}}
}
@Composable
private fun ProfileEditor(initial:TimetableProfile,config:Config,onDismiss:()->Unit,onSave:(TimetableProfile)->Unit) {
    var name by remember{mutableStateOf(initial.name)};var from by remember{mutableStateOf(initial.from)};var to by remember{mutableStateOf(initial.to)}
    var bells by remember{mutableStateOf(initial.bells)};var independent by remember{mutableStateOf(initial.week!=null)};var week by remember{mutableStateOf(initial.week ?: config.week)};var day by remember{mutableStateOf(DayOfWeek.SUNDAY)}
    val issue=ScheduleValidation.problem(config.copy(bells=bells))
    val overlaps=config.profiles.any{it.id!=initial.id && from<=it.to && to>=it.from}
    EditorPage(onDismissRequest=onDismiss,title={Text("توقيت ${initial.name}")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        OutlinedTextField(name,{name=it},label={Text("اسم التوقيت")});DateField("من",from){from=it};DateField("إلى",to){to=it}
        bells.forEach {bell->Text("الحصة ${bell.period}");Row{TimeField(bell.start){time->bells=bells.map{if(it.period==bell.period)it.copy(start=time) else it}};TimeField(bell.end){time->bells=bells.map{if(it.period==bell.period)it.copy(end=time) else it}}}}
        Row{Checkbox(independent,{independent=it});Text("جدول حصص مستقل")}
        if(independent){Choice("اليوم",day,ScheduleStore.DAYS,::dayName){day=it};(1..7).forEach{p->val duty=week[day]?.get(p);val selected=when(duty){is Duty.Teach->duty.section;Duty.Standby->"انتظار";else->"بدون"};Choice("الحصة $p",selected,listOf("بدون","انتظار")+config.sections,{it}){v->val duties=week[day].orEmpty().toMutableMap();if(v=="بدون")duties.remove(p) else duties[p]=if(v=="انتظار")Duty.Standby else Duty.Teach(v);week=week+(day to duties)}}}
        if(issue!=null)Text(issue,color=MaterialTheme.colorScheme.error)
        if(overlaps)Text("الفترة تتداخل مع توقيت آخر؛ عدّل التاريخ أولًا",color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(enabled=name.isNotBlank() && to>=from && issue==null && !overlaps,onClick={onSave(initial.copy(name=name.trim(),from=from,to=to,bells=bells,week=if(independent)week else null))}){Text("حفظ وتفعيل حسب التاريخ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}
