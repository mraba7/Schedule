package com.mrabah.oneuischedule.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import java.time.*

@Composable internal fun WeeklySummary(config: Config) {
    val c=LocalContext.current; val today=LocalDate.now(); val start=today.minusDays((today.dayOfWeek.value%7).toLong()); val end=start.plusDays(6)
    val duties=(0..6).flatMap { d->ScheduleEngine.dutiesOn(config,start.plusDays(d.toLong())).values }
    val teaching=duties.count{it is Duty.Teach}; val pending=ClassJournal.all(c).filter{!it.done}; val standby=duties.count{it is Duty.Standby}
    val lead=config.sections.maxOfOrNull{config.progress[it]?.taught ?: 0} ?: 0
    val upcoming=config.holidays.filter{it.to>=start && it.from<=end.plusDays(7)}.sortedBy{it.from}
    val text=buildString { append("ملخص الأسبوع الدراسي ${start} إلى ${end}\n"); append("حصص التدريس المجدولة: $teaching · الانتظار: $standby\n"); append("التقدم التراكمي للفصول:\n"); config.sections.forEach{sec->val p=config.progress[sec]?:SectionProgress();append("$sec: ${p.taught} درسًا\n")}; if(pending.isNotEmpty())append("عناصر مفتوحة: ${pending.size}\n"); upcoming.forEach{append("${it.label}: ${it.from} إلى ${it.to}\n")} }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("ملخصك الأسبوعي",style=MaterialTheme.typography.headlineMedium); Text("${start} — ${end}") }
        item { Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("$teaching حصة تدريس مجدولة",style=MaterialTheme.typography.titleLarge);Text("$standby حصة انتظار · ${pending.size} ملاحظة أو درس مفتوح")}} }
        item { Text("تقدم الفصول التراكمي",style=MaterialTheme.typography.titleLarge) }
        config.sections.forEach {sec-> item { val p=config.progress[sec]?:SectionProgress(); Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("الفصل $sec · ${p.taught} درسًا"); if(lead>p.taught)Text("يحتاج متابعة: الفارق ${lead-p.taught}",color=MaterialTheme.colorScheme.error); if(p.last.isNotBlank())Text("آخر درس: ${p.last}")}}}}
        if(pending.isNotEmpty()) item { Text("عناصر تحتاج إغلاقًا",style=MaterialTheme.typography.titleLarge); Text(pending.take(5).joinToString("\n"){"${it.section} · ${it.title}"}) }
        if(upcoming.isNotEmpty()) item { Text("القادم",style=MaterialTheme.typography.titleLarge); Text(upcoming.joinToString("\n"){"${it.label} · ${it.from}"}) }
        item { Button(onClick={c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text).putExtra(Intent.EXTRA_TITLE,"ملخص أسبوعي"),"مشاركة الملخص"))},modifier=Modifier.fillMaxWidth()){Text("مشاركة الملخص")}}
    }
}
