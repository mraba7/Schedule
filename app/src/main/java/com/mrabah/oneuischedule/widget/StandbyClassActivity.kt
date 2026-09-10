package com.mrabah.oneuischedule.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import java.time.LocalDate

internal object StandbyAssignments {
    fun choices(config:Config)=(config.schoolSections+config.sections).distinct().sorted()
    fun assign(config:Config,date:LocalDate,period:Int,section:String?):Config? {
        if(ScheduleEngine.dutiesOn(config,date)[period] !is Duty.Standby)return null
        val name=section?.trim()?.take(40)?.takeIf { it.isNotEmpty() }
        val key="$date#$period"
        return config.copy(
            schoolSections=if(name==null)config.schoolSections else (config.schoolSections+name).distinct(),
            standbySections=if(name==null)config.standbySections-key else config.standbySections+(key to name))
    }
    fun intent(c:Context,date:LocalDate,period:Int)=Intent(c,StandbyClassActivity::class.java)
        .setData(Uri.parse("schedule-standby://assignment/$date/$period"))
        .putExtra("date",date.toString()).putExtra("period",period)
}

class StandbyClassActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val date=runCatching {LocalDate.parse(intent.getStringExtra("date"))}.getOrNull() ?: run {finish();return}
        val period=intent.getIntExtra("period",-1)
        val config=ScheduleStore.load(this)
        if(ScheduleEngine.dutiesOn(config,date)[period] !is Duty.Standby) {finish();return}
        fun save(name:String?) {
            val updated=StandbyAssignments.assign(ScheduleStore.load(this),date,period,name)
            if(updated==null) {
                android.widget.Toast.makeText(this,"تغيّر الجدول؛ هذه الحصة لم تعد انتظارًا",android.widget.Toast.LENGTH_LONG).show()
                finish();return
            }
            ScheduleStore.save(this,updated)
            sendBroadcast(Intent(this,ScheduleWidgetReceiver::class.java).setAction(ScheduleWidgetReceiver.ACTION_TICK))
            finish()
        }
        setContent {MaterialTheme {Surface(Modifier.fillMaxSize()) {
            var selected by rememberSaveable {mutableStateOf(config.standbySections["$date#$period"].orEmpty())}
            var newClass by rememberSaveable {mutableStateOf("")}
            LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text("تحديد فصل الانتظار",style=MaterialTheme.typography.headlineSmall)}
                item {Text("${periodName(period)} · $date")}
                item {Text("اختر من فصول المدرسة أو أضف فصلًا جديدًا. التكليف لهذه الحصة في هذا التاريخ فقط.")}
                items(StandbyAssignments.choices(config)) {section ->
                    OutlinedButton(onClick={selected=section;newClass=""},modifier=Modifier.fillMaxWidth()) {
                        Text(if(selected==section && newClass.isBlank())"✓ $section" else section)
                    }
                }
                item {OutlinedTextField(value=newClass,onValueChange={newClass=it.take(40)},
                    label={Text("فصل آخر من المدرسة")},placeholder={Text("مثل: 1/8 أو ثالث ثانوي ٤")},modifier=Modifier.fillMaxWidth())}
                item {Text("الفصول الجديدة تُحفظ في قائمة المدرسة للاختيار منها لاحقًا، ولا تُضاف إلى حصصك الأساسية.")}
                item {Button(onClick={save(newClass.ifBlank{selected})},enabled=newClass.isNotBlank() || selected.isNotBlank(),modifier=Modifier.fillMaxWidth()) {Text("حفظ فصل الانتظار")}}
                if(config.standbySections.containsKey("$date#$period")) item {TextButton(onClick={save(null)}) {Text("إلغاء تحديد الفصل")}}
                item {TextButton(onClick={finish()}) {Text("رجوع")}}
            }
        }}}
    }
}
