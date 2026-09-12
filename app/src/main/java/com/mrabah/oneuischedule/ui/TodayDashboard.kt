package com.mrabah.oneuischedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.widget.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NavSymbol(index:Int) {
    val tint=LocalContentColor.current
    Canvas(Modifier.size(23.dp)) {
        val w=size.width;val h=size.height;val stroke=2.dp.toPx()
        when(index) {
            0 -> {drawCircle(tint,w*.39f,style=Stroke(stroke));drawLine(tint,center,Offset(w*.5f,h*.25f),stroke);drawLine(tint,center,Offset(w*.7f,h*.6f),stroke)}
            1 -> {drawRoundRect(tint,Offset(w*.1f,h*.18f),Size(w*.8f,h*.72f),style=Stroke(stroke));drawLine(tint,Offset(w*.1f,h*.4f),Offset(w*.9f,h*.4f),stroke)}
            2 -> {drawLine(tint,Offset(w*.5f,h*.15f),Offset(w*.5f,h*.85f),stroke);drawRoundRect(tint,Offset(w*.1f,h*.2f),Size(w*.8f,h*.6f),style=Stroke(stroke))}
            3 -> for(x in 0..1)for(y in 0..1)drawRoundRect(tint,Offset(w*(.08f+x*.48f),h*(.08f+y*.48f)),Size(w*.35f,h*.35f),style=Stroke(stroke))
            else -> {for(i in 0..2){val y=h*(.22f+i*.28f);drawLine(tint,Offset(w*.1f,y),Offset(w*.9f,y),stroke);drawCircle(tint,w*.07f,Offset(w*(if(i==1).7f else .3f),y))}}
        }
    }
}

@Composable
internal fun TodayDashboard(config:Config, fixedNow:LocalDateTime?=null,onEdit:(LocalDate,Int)->Unit) {
    val context=LocalContext.current
    var now by remember { mutableStateOf(fixedNow ?: LocalDateTime.now()) }
    val activity=context as? androidx.activity.ComponentActivity
    DisposableEffect(activity) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) now=fixedNow ?: LocalDateTime.now()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {activity?.lifecycle?.removeObserver(observer)}
    }
    LaunchedEffect(fixedNow) {if(fixedNow==null)while(true){delay(1000);if(activity==null || activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))now=LocalDateTime.now()}}
    var tomorrow by rememberSaveable {mutableStateOf(false)}
    val at=if(tomorrow) now.toLocalDate().plusDays(1).atStartOfDay() else now
    val ui=remember(config,at){ScheduleEngine.today(config,at)}
    val fg=MaterialTheme.colorScheme.onSurfaceVariant
    val clock=DateTimeFormatter.ofPattern("HH:mm",Locale.ENGLISH)
    var expanded by rememberSaveable {mutableStateOf(true)}
    val focus=ui.focus
    val done=ui.slots.count {it.state==SlotState.DONE}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(tomorrow)"استعد للغد" else "يومك الدراسي",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text(ui.date.format(DateTimeFormatter.ofPattern("EEEE، d MMMM",Locale("ar"))),color=fg,style=MaterialTheme.typography.bodyMedium)
                }
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(14.dp)) {Text("${ui.slots.size} حصص",Modifier.padding(12.dp),fontWeight=FontWeight.Bold)}
            }
        }
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!tomorrow,onClick={tomorrow=false},label={Text("اليوم")})
            FilterChip(selected=tomorrow,onClick={tomorrow=true},label={Text("غدًا")})
        }}
        if(EmergencyDay.active(config,ui.date))item{Text(SchoolTools.profile(config,ui.date)!!.name,color=MaterialTheme.colorScheme.primary)}
        item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical=18.dp)) {
                Metric("$done","انتهت",Modifier.weight(1f))
                Metric("${ui.slots.count{it.state!=SlotState.DONE}}","متبقية",Modifier.weight(1f))
                Metric("${ui.slots.count{it.isStandby}}","انتظار",Modifier.weight(1f))
            }
        }}
        item {
            ui.slots.lastOrNull()?.let {Text("ينتهي دوامك ${it.bell.end.format(clock)}",style=MaterialTheme.typography.titleMedium)}
            if(!tomorrow && ui.live==null && focus!=null && now.toLocalTime()>=SchoolTools.bells(config,ui.date).first().start)Text("فترة فراغ حتى ${focus.bell.start.format(clock)}")
            if(ui.live!=null)ui.next?.let {Text("التالي: ${periodName(it.period)} · ${it.displaySection ?: "انتظار"} · ${it.bell.start.format(clock)}")}
            config.holidays.filter{it.from>now.toLocalDate()}.minByOrNull{it.from}?.let{Text("${it.label} بعد ${java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(),it.from)} يومًا",style=MaterialTheme.typography.bodySmall)}
        }
        if(focus!=null) item {
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(if(ui.live!=null)"جارية الآن" else "الحصة القادمة",style=MaterialTheme.typography.labelLarge)
                    Text(periodName(focus.period),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text(if(focus.isStandby)"انتظار · ${focus.displaySection ?: "لم يُحدّد الفصل"}" else "الفصل ${focus.displaySection}",style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"طي تفاصيل الحصة" else "عرض تفاصيل الحصة")}
                    if(expanded) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("البداية ${focus.bell.start.format(clock)}")
                        Text("النهاية ${focus.bell.end.format(clock)}")
                    }
                    if(!tomorrow) {
                        val until=if(ui.live!=null)focus.bell.end else focus.bell.start
                        val seconds=Duration.between(now,ui.date.atTime(until)).seconds.coerceAtLeast(0)
                        Text(String.format(Locale.ENGLISH,"%02d:%02d",seconds/60,seconds%60),fontSize=36.sp,fontWeight=FontWeight.Bold)
                        Text(if(ui.live!=null)"حتى نهاية الحصة" else "حتى بداية الحصة",style=MaterialTheme.typography.bodySmall)
                    }
                    if(ui.live!=null)LinearProgressIndicator(progress={ui.progress},modifier=Modifier.fillMaxWidth())
                    if(focus.isStandby)OutlinedButton(onClick={context.startActivity(StandbyAssignments.intent(context,ui.date,focus.period))}) {Text(if(focus.standbySection==null)"تحديد فصل الانتظار" else "تغيير فصل الانتظار")}
                    else TextButton(onClick={openStudio(context,"class",focus.section!!)}){Text("صفحة الفصل وآخر نقطة")}
                    }
                }
            }
        }
        if(focus==null) item {
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(ui.slots.isNotEmpty())"اكتمل يومك الدراسي" else ui.holiday?.label ?: "لا توجد حصص",style=MaterialTheme.typography.titleLarge)
                Text(if(ui.slots.isNotEmpty())"أنجزت حصص اليوم. يمكنك مراجعتها أدناه أو الاطلاع على الغد." else "استعرض اليوم الآخر أو أضف حصصك من تبويب الجدول.",color=fg)
            }}
        }
        item {
            Text("وصول سريع",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("focus" to "وضع الحصة","search" to "بحث","inbox" to "المتابعات","tomorrow" to "تجهيز بكرة","emergency" to "اليوم الطارئ").forEach{(page,label)->
                    OutlinedButton(onClick={openStudio(context,page)}){Text(label)}
                }
            }
        }
        item {Text("${if(tomorrow)"حصص الغد" else "حصص اليوم"} · ${ui.slots.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
        items(ui.slots,key={it.period}) {slot ->
            Card(onClick={onEdit(ui.date,slot.period)},colors=CardDefaults.cardColors(containerColor=if(slot.state==SlotState.LIVE)MaterialTheme.colorScheme.secondaryContainer else Color(android.graphics.Color.parseColor(SchoolTools.color(config,slot.displaySection))).copy(alpha=.12f)),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(periodName(slot.period),fontWeight=FontWeight.Bold)
                            Text(if(slot.isStandby)"انتظار · ${slot.displaySection ?: "اختر الفصل"}" else "الفصل ${slot.displaySection}",color=fg)
                        }
                        Text(when(slot.state){SlotState.DONE->"✓ انتهت";SlotState.LIVE->"الآن";else->"قادمة"},color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                    }
                    Text("من ${slot.bell.start.format(clock)} إلى ${slot.bell.end.format(clock)}",color=fg)
                    if(slot.note.isNotBlank())Text(slot.note,style=MaterialTheme.typography.bodySmall)
                    if(slot.isStandby)TextButton(onClick={context.startActivity(StandbyAssignments.intent(context,ui.date,slot.period))}){Text("فصل الانتظار")}
                    if(slot.overridden)Text("تعديل لهذا اليوم فقط",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(ui.slots.isNotEmpty())item {Text("اضغط الحصة لتعديلها لهذا اليوم أو لإضافة ملاحظة أسبوعية.",style=MaterialTheme.typography.bodySmall,color=fg)}
    }
}

@Composable
private fun Metric(value:String,label:String,modifier:Modifier) {Column(modifier,horizontalAlignment=Alignment.CenterHorizontally){Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
