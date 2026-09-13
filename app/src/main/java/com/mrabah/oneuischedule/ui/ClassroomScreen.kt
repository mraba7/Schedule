package com.mrabah.oneuischedule.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import java.util.UUID

@Composable
internal fun ClassroomScreen(config:Config,initialSection:String="",initialMode:String="student",startAdding:Boolean=false) {
    val c=LocalContext.current;val revision=workspaceRevision()
    val sections=(config.sections+config.schoolSections).distinct()
    var section by rememberSaveable{mutableStateOf(initialSection.ifBlank{sections.firstOrNull().orEmpty()})}
    var mode by rememberSaveable{mutableStateOf(initialMode.takeIf{it in listOf("student","exit","difficulty","lab","plan","question")} ?: "student")}
    var editing by remember{mutableStateOf<ClassroomRecord?>(null)}
    var removing by remember{mutableStateOf<ClassroomRecord?>(null)}
    var selected by remember{mutableStateOf<ClassroomRecord?>(null)}
    var groupCount by rememberSaveable{mutableStateOf(3)}
    var columns by rememberSaveable{mutableStateOf(2)}
    var showGroups by rememberSaveable{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    LaunchedEffect(Unit){if(startAdding && section.isNotBlank())editing=ClassroomRecord(kind=mode,section=section,title="",extra=if(mode=="student")((1..60).firstOrNull{n->ClassroomStore.all(c).none{it.kind=="student" && it.section==section && it.extra.toIntOrNull()==n}} ?: 60).toString() else "")}
    val records=remember(revision,section,mode){ClassroomStore.all(c).filter{it.section==section && it.kind==mode}.sortedByDescending{it.created}}
    val titles=linkedMapOf("student" to "المقاعد والمشاركة","exit" to "بطاقة الخروج","difficulty" to "صعوبات التعلم","lab" to "التجارب","plan" to "المخطط والمنفذ","question" to "بنك الأسئلة")
    LazyColumn(Modifier.fillMaxSize().testTag("classroom-list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{WorkspaceBanner("إدارة الحصة","متابعة طلابك وتجاربك وفهم الفصول")}
        if(sections.isEmpty())item{Text("أضف فصلًا من صفحة الفصول أولًا.")}
        else {
            item{Choice("الفصل",section,sections,{it}){section=it;selected=null;showGroups=false}}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){titles.forEach{(key,label)->FilterChip(mode==key,{mode=key;selected=null;showGroups=false},label={Text(label)})}}}
            item{Button(onClick={editing=ClassroomRecord(kind=mode,section=section,title="",extra=if(mode=="student")((1..60).firstOrNull{n->records.none{it.extra.toIntOrNull()==n}} ?: 60).toString() else "")},modifier=Modifier.fillMaxWidth()){Text(when(mode){"student"->"إضافة طالب ومقعد";"exit"->"تسجيل فهم الحصة";"difficulty"->"تسجيل صعوبة";"lab"->"إضافة تجربة";"plan"->"خطة حصة جديدة";else->"إضافة سؤال"})}}
            if(message.isNotBlank())item{Text(message)}
            if(mode=="student") {
                item{Text("اضغط المقعد للتسجيل. استبعد الغائب من الاختيار والمجموعات.");Button(enabled=records.any{!it.excluded},onClick={ClassroomStore.choose(records)?.let{ClassroomStore.participate(c,it);selected=it.copy(count=it.count+1)}}){Text("اختيار عادل وتسجيل مشاركة")};selected?.let{Text("الاختيار: ${it.title} · المقعد ${it.extra}",style=MaterialTheme.typography.titleLarge)}}
                item{Choice("عدد المجموعات",groupCount,(2..10).toList(),{it.toString()}){groupCount=it};TextButton(onClick={showGroups=!showGroups}){Text(if(showGroups)"طي المجموعات" else "تكوين المجموعات")};Text("التوزيع متوازن بالعدد وسجل المشاركة، وليس تقييمًا للتحصيل.",style=MaterialTheme.typography.bodySmall)}
                if(showGroups)ClassroomStore.groups(records,groupCount).forEachIndexed{i,list->item{Text("المجموعة ${i+1}: ${list.joinToString("، "){it.title}.ifBlank{"—"}}")}}
                item{Choice("مقاعد كل صف",columns,listOf(2,3,4),{it.toString()}){columns=it};Text("السبورة",style=MaterialTheme.typography.titleMedium)}
                val seats=(1..maxOf(columns,records.maxOfOrNull{it.extra.toIntOrNull() ?: 0} ?: 0)).toList().chunked(columns)
                seats.forEach{row->item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach{number->val student=records.find{it.extra.toIntOrNull()==number}
                        Card(onClick={if(student==null)editing=ClassroomRecord(kind="student",section=section,title="",extra=number.toString()) else {selected=student;editing=student}},modifier=Modifier.weight(1f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("المقعد $number",style=MaterialTheme.typography.labelLarge)
                                Text(student?.title ?: "خالٍ",style=MaterialTheme.typography.titleMedium)
                                if(student!=null){Text("${student.count} مشاركة${if(student.excluded)" · مستبعد" else ""}");TextButton(onClick={ClassroomStore.participate(c,student)}){Text("+ مشاركة")}}
                            }
                        }
                    }
                    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                }}}
            } else {
                if(mode=="exit")item{Text("واضح: ${records.count{it.status=="واضح"}} · مراجعة: ${records.count{it.status=="يحتاج مراجعة"}} · إعادة: ${records.count{it.status=="يحتاج إعادة"}}")}
                if(mode=="question")item{OutlinedButton(onClick={val m=android.appwidget.AppWidgetManager.getInstance(c);if(m.isRequestPinAppWidgetSupported)m.requestPinAppWidget(android.content.ComponentName(c,com.mrabah.oneuischedule.widget.QuestionWidgetReceiver::class.java),null,null) else message="أضف سؤال اليوم من أدوات الشاشة الرئيسية"}){Text("إضافة ودجت سؤال اليوم")}}
                records.forEach{r->item(key=r.id){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(r.title,style=MaterialTheme.typography.titleLarge)
                    Text(java.time.Instant.ofEpochMilli(r.created).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),style=MaterialTheme.typography.bodySmall)
                    if(r.detail.isNotBlank())Text(when(mode){"plan"->"المخطط: ${r.detail}";"question"->"الإجابة: ${r.detail}";else->r.detail})
                    if(mode=="lab") {
                        val checked=r.status.split(',').toSet()
                        r.extra.lines().filter{it.isNotBlank()}.forEachIndexed{i,supply->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(i.toString() in checked,{value->ClassroomStore.save(c,r.copy(status=(if(value)checked+i.toString() else checked-i.toString()).filter{it.isNotBlank()}.joinToString(",")))});Text(supply,Modifier.weight(1f))}}
                        Text("علامة الصح تعني أن الأداة والكمية متوفرتان؛ البقية تحتاج تجهيزًا.",style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={editing=r.copy(id=UUID.randomUUID().toString(),status="",created=System.currentTimeMillis())}){Text("نسخ التجربة لفصل آخر")}
                    } else if(r.extra.isNotBlank())Text(if(mode=="plan")"المنفذ: ${r.extra}" else if(mode=="question")"المفهوم أو المرحلة: ${r.extra}" else "المعالجة: ${r.extra}")
                    if(r.status.isNotBlank() && mode!="lab")Text(if(mode=="plan")"سبب الاختلاف: ${r.status}" else r.status,color=MaterialTheme.colorScheme.primary)
                    if(mode=="difficulty" && r.updated>0)Text("آخر معالجة: ${java.time.Instant.ofEpochMilli(r.updated).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick={editing=r}){Text("تعديل")}
                        TextButton(onClick={removing=r}){Text("حذف")}
                        if(mode=="difficulty")TextButton(onClick={ClassroomStore.save(c,r.copy(status=if(r.status=="عولجت")"تحتاج متابعة" else "عولجت",updated=System.currentTimeMillis()))}){Text(if(r.status=="عولجت")"إعادة فتح" else "تمت المعالجة")}
                        if(mode=="question" || mode=="lab")TextButton(onClick={c.startActivity(Intent(c,ClassroomDisplayActivity::class.java).putExtra("record",r.id))}){Text("عرض للطلاب")}
                    }
                }}}}
            }
            if(records.isEmpty())item{Text("لا توجد سجلات لهذا الفصل في هذا القسم بعد.")}
            if(mode=="student" && selected!=null)item{TextButton(onClick={removing=selected}){Text("حذف الطالب المحدد")}}
        }
    }
    editing?.let{r->ClassroomEditor(r,sections,{editing=null}){next->ClassroomStore.save(c,next);editing=null;selected=null}}
    removing?.let{r->AlertDialog(onDismissRequest={removing=null},title={Text("حذف ${r.title}؟")},text={Text("يمكن الاسترجاع من سجل التعديلات.")},confirmButton={TextButton(onClick={ClassroomStore.remove(c,r);removing=null;selected=null}){Text("حذف")}},dismissButton={TextButton(onClick={removing=null}){Text("إلغاء")}})}
}

@Composable
private fun ClassroomEditor(record:ClassroomRecord,sections:List<String>,dismiss:()->Unit,save:(ClassroomRecord)->Unit) {
    var title by rememberSaveable(record.id){mutableStateOf(record.title)}
    var detail by rememberSaveable(record.id){mutableStateOf(record.detail)}
    var extra by rememberSaveable(record.id){mutableStateOf(record.extra)}
    var status by rememberSaveable(record.id){mutableStateOf(record.status)}
    var section by rememberSaveable(record.id){mutableStateOf(record.section)}
    var excluded by rememberSaveable(record.id){mutableStateOf(record.excluded)}
    var error by remember{mutableStateOf("")}
    EditorPage(onDismissRequest=dismiss,title={Text("تفاصيل السجل")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Choice("الفصل",section,sections,{it}){section=it}
        OutlinedTextField(title,{title=it.take(160)},label={Text(if(record.kind=="student")"اسم الطالب" else "الدرس أو المفهوم أو السؤال")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(detail,{detail=it.take(4000)},label={Text(when(record.kind){"student"->"ملاحظة خاصة";"plan"->"ما خُطّط تنفيذه";"question"->"الإجابة";"lab"->"خطوات التجربة";else->"التفاصيل"})},modifier=Modifier.fillMaxWidth(),minLines=2)
        if(record.kind!="exit")OutlinedTextField(extra,{extra=it.take(4000)},label={Text(when(record.kind){"student"->"رقم المقعد · 1 إلى 60";"plan"->"ما نُفّذ فعليًا";"question"->"المفهوم أو المرحلة";"lab"->"الأدوات والكميات · كل أداة في سطر";else->"إجراء المعالجة"})},modifier=Modifier.fillMaxWidth())
        if(record.kind=="student")Row{Checkbox(excluded,{excluded=it});Text("استبعاد من الاختيار والمجموعات · غائب")}
        if(record.kind=="exit")Choice("فهم الفصل",status.ifBlank{"واضح"},listOf("واضح","يحتاج مراجعة","يحتاج إعادة"),{it}){status=it}
        if(record.kind=="plan")OutlinedTextField(status,{status=it.take(1000)},label={Text("سبب الاختلاف أو ما يحتاج استكمالًا")},modifier=Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(enabled=title.isNotBlank(),onClick={runCatching{save(record.copy(title=title.trim(),detail=detail,extra=extra,section=section,status=if(record.kind=="exit")status.ifBlank{"واضح"} else if(record.kind=="lab" && extra!=record.extra)"" else status,excluded=excluded,updated=if(record.kind=="difficulty")System.currentTimeMillis() else record.updated))}.onFailure{error=it.message ?: "تعذر الحفظ"}}){Text("حفظ")}},dismissButton={TextButton(onClick=dismiss){Text("إلغاء")}})
}
