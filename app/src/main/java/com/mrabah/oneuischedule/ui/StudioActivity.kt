package com.mrabah.oneuischedule.ui

import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.mrabah.oneuischedule.data.*
import com.mrabah.oneuischedule.widget.*

internal fun openStudio(c:Context,page:String,section:String="") {c.startActivity(Intent(c,StudioActivity::class.java).putExtra("page",page).putExtra("section",section))}
class StudioActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {ScheduleTheme {
            var config by remember {mutableStateOf(ScheduleStore.load(this))}
            var hasChanges by remember{mutableStateOf(false)}
            var leaving by remember{mutableStateOf(false)}
            androidx.activity.compose.BackHandler(enabled=hasChanges){leaving=true}
            if(leaving)AlertDialog(onDismissRequest={leaving=false},title={Text("تعديلات لم تُحفظ")},text={Text("العودة للتعديل أو الخروج وتجاهل التعديلات؟")},confirmButton={TextButton(onClick={finish()}){Text("تجاهل واخرج")}},dismissButton={TextButton(onClick={leaving=false}){Text("متابعة التعديل")}})
            DisposableEffect(Unit) {
                val prefs=getSharedPreferences("schedule_config",0)
                val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,_->config=ScheduleStore.load(this@StudioActivity)}
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
            }
            fun commit(next:Config) {ScheduleStore.save(this,next);config=ScheduleStore.load(this);com.mrabah.oneuischedule.notify.PeriodNotifier.sync(this);sendBroadcast(Intent(this,ScheduleWidgetReceiver::class.java).setAction(ScheduleWidgetReceiver.ACTION_TICK))}
            Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {Column {
                TextButton(onClick={if(hasChanges)leaving=true else finish()}) {Text("رجوع")}
                Box(Modifier.weight(1f)) {when(intent.getStringExtra("page")) {
                    "data"->DataScreen()
                    "agenda"->AgendaWorkspace(config,::commit){hasChanges=it}
                    "focus"->LessonFocusScreen(config)
                    "search"->SearchScreen(config)
                    "inbox"->FollowupsScreen(config)
                    "gallery"->DesignGallery(config)
                    "tools"->ToolsHub()
                    "classroom"->ClassroomScreen(config,intent.getStringExtra("section").orEmpty(),intent.getStringExtra("classroom_mode") ?: "student")
                    "tomorrow"->TomorrowScreen(config)
                    "emergency"->EmergencyScreen(config,::commit)
                    "absence"->AbsenceScreen(config,::commit)
                    "class"->ClassPage(config,intent.getStringExtra("section").orEmpty(),::commit)
                    "shortcuts"->ClassShortcutsScreen(intent.getStringExtra("section").orEmpty())
                    "week"->WeekTools(config,::commit)
                    "calendar"->CalendarScreen(config,::commit)
                    "profiles"->ProfilesScreen(config,::commit)
                    "alerts"->AlertsScreen(config,::commit)
                    "widgets"->WidgetSettingsScreen()
                    "summary"->WeeklySummary(config)
                    "appearance"->AppearanceScreen()
                    else->ClassHub(config)
                }}
            }}
        }}
    }
}

@Composable
internal fun SettingsHome() {
    val c=androidx.compose.ui.platform.LocalContext.current
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Text("الإعدادات",style=MaterialTheme.typography.headlineMedium)}
        item {com.mrabah.oneuischedule.UpdatePanel()}
        listOf("summary" to ("ملخص الأسبوع" to "التقدم والملاحظات والانتظار ومشاركة جاهزة"),"appearance" to ("المظهر والقراءة" to "الوضع الليلي وحجم النص"),"alerts" to ("التنبيهات" to "أنواع الحصص والأوقات والأصوات"),"widgets" to ("تخصيص الودجت" to "إعدادات مستقلة لكل نسخة"),"data" to ("البيانات والاسترجاع" to "نسخة شاملة، نسخ محلية تلقائية وسجل التعديلات"),"profiles" to ("الأوقات الخاصة" to "الصيف والشتاء ورمضان والاختبارات")).forEach {(page,labels)->item {
            Card(onClick={openStudio(c,page)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text(labels.first,style=MaterialTheme.typography.titleMedium);Text(labels.second,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        }}
    }
}

/** Full-page forms keep their actions visible when text is enlarged or the keyboard opens. */
@Composable
internal fun EditorPage(
    onDismissRequest:()->Unit,
    title:@Composable ()->Unit,
    text:@Composable ()->Unit,
    confirmButton:@Composable ()->Unit,
    dismissButton:@Composable ()->Unit
) {
    androidx.activity.compose.BackHandler(onBack=onDismissRequest)
    Surface(Modifier.fillMaxSize().then(Modifier.zIndex(1f)),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {title()}
            Box(Modifier.weight(1f).fillMaxWidth()) {text()}
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                confirmButton();dismissButton()
            }
        }
    }
}
