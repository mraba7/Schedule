package com.mrabah.oneuischedule.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.ClassroomStore

/** Only explicitly selected teaching content is passed to this screen. No roster or private notes. */
class ClassroomDisplayActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val record=ClassroomStore.publicRecord(this,intent.getStringExtra("record"))
        setContent{ScheduleTheme {
            var answer by rememberSaveable{mutableStateOf(false)}
            var exit by remember{mutableStateOf(false)}
            androidx.activity.compose.BackHandler{exit=true}
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                LazyColumn(contentPadding=PaddingValues(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    item{Text("مساحة الطلاب",style=MaterialTheme.typography.titleMedium)}
                    item{Text(record?.title ?: "اختر سؤالًا أو تجربة من إدارة الحصة",style=MaterialTheme.typography.headlineLarge)}
                    if(record?.kind=="question") {
                        item{Button(onClick={answer=!answer}){Text(if(answer)"إخفاء الإجابة" else "كشف الإجابة")}}
                        if(answer)item{Text(record.detail,style=MaterialTheme.typography.headlineSmall)}
                    }
                    if(record?.kind=="lab")item{Text(record.detail,style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(20.dp));Text(record.extra,style=MaterialTheme.typography.titleLarge)}
                    item{TextButton(onClick={exit=true}){Text("إنهاء العرض")}}
                }
            }
            if(exit)AlertDialog(onDismissRequest={exit=false},title={Text("العودة لمساحة المعلم؟")},text={Text("أوقف عرض الشاشة للطلاب قبل العودة إلى بياناتك الخاصة.")},confirmButton={TextButton(onClick={finish()}){Text("إنهاء والعودة")}},dismissButton={TextButton(onClick={exit=false}){Text("متابعة العرض")}})
        }}
    }
}
