package com.mrabah.oneuischedule

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrabah.oneuischedule.data.Bell
import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.Duty
import com.mrabah.oneuischedule.data.Holiday
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.ScheduleStore
import com.mrabah.oneuischedule.data.SectionProgress
import com.mrabah.oneuischedule.data.SlotState
import com.mrabah.oneuischedule.notify.PeriodNotifier
import com.mrabah.oneuischedule.notify.Speaker
import com.mrabah.oneuischedule.widget.ScheduleUpdater
import com.mrabah.oneuischedule.widget.updateEveryWidget
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * IBM Plex Sans Arabic: Arabic and Latin drawn as one family, so "الحصة 2"
 * and "8:05" share a baseline. The widget cannot use it — the launcher's
 * process only reaches system fonts.
 */
private val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private fun typographyOf(base: Typography) = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = PlexArabic),
    displayMedium = base.displayMedium.copy(fontFamily = PlexArabic),
    displaySmall = base.displaySmall.copy(fontFamily = PlexArabic),
    headlineLarge = base.headlineLarge.copy(fontFamily = PlexArabic),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlexArabic),
    headlineSmall = base.headlineSmall.copy(fontFamily = PlexArabic),
    titleLarge = base.titleLarge.copy(fontFamily = PlexArabic),
    titleMedium = base.titleMedium.copy(fontFamily = PlexArabic),
    titleSmall = base.titleSmall.copy(fontFamily = PlexArabic),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlexArabic),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexArabic),
    bodySmall = base.bodySmall.copy(fontFamily = PlexArabic),
    labelLarge = base.labelLarge.copy(fontFamily = PlexArabic),
    labelMedium = base.labelMedium.copy(fontFamily = PlexArabic),
    labelSmall = base.labelSmall.copy(fontFamily = PlexArabic),
)

private val CANCELLED = "CANCELLED"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = scheme, typography = typographyOf(MaterialTheme.typography)) {
                AppShell(intent.getIntExtra("open_tab", 0))
            }
        }
    }
}

/* ══════════════════════════════════════════════════════════════
 *  SHELL — the app opens on the day, not on a settings form
 * ══════════════════════════════════════════════════════════════ */

@Composable
private fun AppShell(initialTab: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    var config by remember { mutableStateOf(ScheduleStore.load(context)) }

    fun commit(next: Config) {
        config = next
        ScheduleStore.save(context, next)
        PeriodNotifier.sync(context)
        ScheduleUpdater.schedule(context)
        scope.launch { updateEveryWidget(context) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("اليوم", "الجدول", "المنهج", "التصاميم").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {},
                        label = { Text(label, fontSize = 13.sp) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> TodayScreen(config, ::commit)
                1 -> ScheduleScreen(config, ::commit)
                2 -> SyllabusScreen(config, ::commit)
                else -> com.mrabah.oneuischedule.widget.DesignGallery(config)
            }
        }
    }
}

/* ══════════════════════════════════════════════════════════════
 *  1. TODAY
 * ══════════════════════════════════════════════════════════════ */

@Composable
private fun TodayScreen(config: Config, commit: (Config) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = Locale.getDefault()
    val clock = DateTimeFormatter.ofPattern("h:mm", locale)
    val now = LocalDateTime.now()
    val ui = ScheduleEngine.build(config, now)

    var update by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var checking by remember { mutableStateOf(true) }
    var checkFailed by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    suspend fun runCheck() {
        checking = true
        checkFailed = false
        val result = UpdateChecker.check(context)
        checking = false
        if (result == null) checkFailed = true else update = result
    }

    LaunchedEffect(Unit) { runCheck() }

    var editing by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val found = update
            if (found == null || !found.newer) {
                // Silence is not an answer: say which state we are in.
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    checking -> "يبحث عن تحديث…"
                                    checkFailed -> UpdateChecker.lastError.ifBlank { "تعذّر الفحص" }
                                    else -> "أنت على أحدث نسخة"
                                },
                                fontSize = 14.sp,
                            )
                            Text(
                                if (checkFailed) "النسخة ${BuildConfig.VERSION_NAME} · جرّب بيانات الجوال"
                                else "النسخة ${BuildConfig.VERSION_NAME}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!checking) {
                            TextButton(onClick = { scope.launch { runCheck() } }) {
                                Text("تحقق الآن")
                            }
                        }
                    }
                }
            }
        }

        update?.takeIf { it.newer }?.let { found ->
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("توجد نسخة أحدث: ${found.version}", fontSize = 15.sp)
                                Text(
                                    "الحالية ${BuildConfig.VERSION_NAME}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            when {
                                downloading -> Text(
                                    "${(progress * 100).toInt()}%",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )

                                found.apkUrl == null -> Button(
                                    onClick = { uriHandler.openUri(found.page) },
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("فتح الصفحة") }

                                else -> Button(
                                    onClick = {
                                        if (!Updater.canInstall(context)) {
                                            Updater.requestPermission(context)
                                            return@Button
                                        }
                                        failed = false
                                        downloading = true
                                        progress = 0f
                                        scope.launch {
                                            val file = Updater.download(
                                                context, found.apkUrl, found.size
                                            ) { progress = it }
                                            downloading = false
                                            if (file != null) Updater.install(context, file)
                                            else failed = true
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("تحديث الآن") }
                            }
                        }
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp).padding(bottom = 14.dp),
                            )
                        }
                        if (failed) {
                            Text(
                                "تعذّر التنزيل — افتح الصفحة يدويًا",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    ui.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale) +
                        if (ui.isToday) "" else " · القادم",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    ui.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ui.holiday?.let { holiday ->
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(holiday.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${holiday.from} إلى ${holiday.to}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ui.focus?.let { slot ->
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (ui.live != null) "الآن · باقي ${ui.minutesLeftInLive} دقيقة"
                            else "القادمة · الحصة ${slot.period}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                slot.section ?: "انتظار",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(slot.bell.start.format(clock), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("حتى ${slot.bell.end.format(clock)}", fontSize = 12.sp)
                            }
                        }
                        if (slot.note.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(slot.note, fontSize = 14.sp)
                        }
                        if (ui.live != null) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { ui.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        items@ for (slot in ui.slots) {
            item(key = "slot-${slot.period}") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (slot.state == SlotState.DONE)
                            MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                ) {
                    Column(
                        Modifier.fillMaxWidth().clickable { editing = slot.period }.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${slot.period}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                "${slot.bell.start.format(clock)} – ${slot.bell.end.format(clock)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            if (slot.overridden) {
                                Text("معدّلة اليوم", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                slot.section ?: "انتظار",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (slot.note.isNotBlank()) {
                            Text(
                                slot.note,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "اضغط أي حصة لتعديلها اليوم فقط أو لإضافة ملاحظة",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { period ->
        DayEditDialog(
            config = config,
            date = ui.date,
            period = period,
            onDismiss = { editing = null },
            onApply = { next -> commit(next); editing = null },
        )
    }
}

/** One-day change plus a recurring note, in a single sheet. */
@Composable
private fun DayEditDialog(
    config: Config,
    date: LocalDate,
    period: Int,
    onDismiss: () -> Unit,
    onApply: (Config) -> Unit,
) {
    val key = "$date#$period"
    val noteKey = "${date.dayOfWeek.name}#$period"
    var note by remember { mutableStateOf(config.notes[noteKey].orEmpty()) }

    val options = buildList {
        add(null to "ملغاة اليوم")
        add(Duty.Standby as Duty? to "انتظار")
        config.sections.forEach { add(Duty.Teach(it) as Duty? to it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الحصة $period · ${date}") },
        text = {
            Column {
                Text("تبديل لهذا اليوم فقط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                options.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowItems.forEach { (duty, label) ->
                            OutlinedButton(
                                onClick = {
                                    onApply(config.copy(overrides = config.overrides + (key to duty)))
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text(label, fontSize = 11.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة تتكرر كل أسبوع") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(config.copy(notes = config.notes + (noteKey to note.trim())))
            }) { Text("حفظ الملاحظة") }
        },
        dismissButton = {
            TextButton(onClick = {
                onApply(config.copy(overrides = config.overrides - key))
            }) { Text("إلغاء التبديل") }
        },
    )
}

/* ══════════════════════════════════════════════════════════════
 *  2. SCHEDULE — bells, week, sections, timetables, holidays
 * ══════════════════════════════════════════════════════════════ */

@Composable
private fun ScheduleScreen(config: Config, commit: (Config) -> Unit) {
    val context = LocalContext.current
    var draft by remember(config) { mutableStateOf(config) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var addingSection by remember { mutableStateOf(false) }
    var addingHoliday by remember { mutableStateOf(false) }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val next = draft.copy(notify = granted)
        draft = next
        commit(next)
    }

    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(ScheduleStore.exportJson(draft).toByteArray())
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)
                ?.bufferedReader()?.use { r -> r.readText() }
            val loaded = text?.let { t -> ScheduleStore.importJson(t) }
            if (loaded != null) {
                draft = loaded
                commit(loaded)
            } else {
                problem = "الملف غير صالح"
            }
        }
    }

    val pickTone = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val next = draft.copy(bellUri = uri?.toString().orEmpty())
        draft = next
        commit(next)
    }

    fun edit(next: Config) {
        draft = next
        saved = false
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header("الجدول", "عدّل ثم احفظ — الودجتات تتحدّث فورًا") }

        item { SectionTitle("التوقيت النشط") }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("التوقيت ${draft.activeTimetable}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "الآخر محفوظ كما هو",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            edit(
                                draft.copy(
                                    bells = draft.altBells,
                                    altBells = draft.bells,
                                    activeTimetable = if (draft.activeTimetable == Defaults.SUMMER)
                                        Defaults.WINTER else Defaults.SUMMER,
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("تبديل") }
                }
            }
        }

        item { SectionTitle("أوقات الحصص") }
        for (bell in draft.bells) {
            item(key = "bell-${bell.period}") {
                BellRow(bell) { updated ->
                    edit(draft.copy(bells = draft.bells.map { if (it.period == updated.period) updated else it }))
                }
            }
        }

        item { SectionTitle("الشعب والمادة") }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = draft.subject,
                        onValueChange = { edit(draft.copy(subject = it)) },
                        label = { Text("اسم المادة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        draft.sections.take(5).forEach { section ->
                            OutlinedButton(
                                onClick = {
                                    edit(draft.copy(sections = draft.sections - section))
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                            ) { Text(section, fontSize = 11.sp) }
                        }
                    }
                    TextButton(onClick = { addingSection = true }) { Text("إضافة شعبة") }
                    Text(
                        "اضغط شعبة لحذفها",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle("الجدول الأسبوعي") }
        for (day in ScheduleStore.DAYS) {
            item(key = "day-${day.name}") {
                DayCard(day, draft) { period, duty ->
                    val duties = draft.templateOn(day).toMutableMap()
                    if (duty == null) duties.remove(period) else duties[period] = duty
                    edit(draft.copy(week = draft.week + (day to duties)))
                }
            }
        }

        item { SectionTitle("الإجازات") }
        draft.holidays.forEach { holiday ->
            item(key = "hol-${holiday.from}") {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(holiday.label, fontSize = 15.sp)
                            Text(
                                "${holiday.from} – ${holiday.to}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            edit(draft.copy(holidays = draft.holidays - holiday))
                        }) { Text("حذف") }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { addingHoliday = true },
                shape = RoundedCornerShape(16.dp),
            ) { Text("إضافة إجازة") }
        }

        item { SectionTitle("التنبيهات") }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // These save on tap. Waiting for the Save button meant a
                    // switch silently reverted whenever the app was closed.
                    OptionRow("تنبيه صوتي عند الحصص", "بداية كل حصة ونهايتها", draft.notify) { want ->
                        if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val next = draft.copy(notify = want)
                            draft = next
                            commit(next)
                        }
                    }
                    if (draft.notify) {
                        OptionRow("صوت جرس المدرسة", "بدل نغمة الإشعارات", draft.schoolBell) {
                            { val next = draft.copy(schoolBell = it); draft = next; commit(next) }()
                        }
                        OptionRow("تنبيه قبل الحصة بخمس دقائق", "مع الدرس القادم", draft.preAlert) {
                            { val next = draft.copy(preAlert = it); draft = next; commit(next) }()
                        }
                        OptionRow(
                            "تنبيه قبل نهاية الحصة بخمس دقائق",
                            "لتختم الدرس وتوزّع الواجب",
                            draft.endAlert,
                        ) { { val next = draft.copy(endAlert = it); draft = next; commit(next) }() }
                        OptionRow(
                            "نطق التنبيه بصوت",
                            "يقرأ التنبيه بالعربية بدل الاكتفاء بالجرس",
                            draft.speak,
                        ) { { val next = draft.copy(speak = it); draft = next; commit(next) }() }
                        OptionRow("إشعار الحصة الجارية", "شريط تقدّم مستمر", draft.liveUpdate) {
                            edit(draft.copy(liveUpdate = it))
                        }
                    }
                }
            }
        }

        if (draft.notify) {
            item { SectionTitle("نغمة الجرس") }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (draft.bellUri.isBlank()) "الجرس المدمج"
                                else "نغمة مختارة من جهازك",
                                fontSize = 15.sp,
                            )
                            Text(
                                "سجّل جرس مدرستك الحقيقي واخترْه من هنا",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة الجرس")
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                    .putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        draft.bellUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                                    )
                                pickTone.launch(intent)
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("اختيار") }
                    }
                }
            }
            if (draft.bellUri.isNotBlank()) {
                item {
                    TextButton(onClick = {
                        val next = draft.copy(bellUri = "")
                        draft = next
                        commit(next)
                    }) { Text("العودة للجرس المدمج") }
                }
            }

            if (draft.speak) {
                item { SectionTitle("صوت النطق") }
                item {
                    VoicePicker(draft.voiceId) { id ->
                        val next = draft.copy(voiceId = id)
                        draft = next
                        commit(next)
                    }
                }
            }

            item { SectionTitle("اختبار التنبيهات") }
            item { NotificationTestCard() }
        }

        item { SectionTitle("نسخ احتياطي") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportFile.launch("class-schedule-backup.json") },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("تصدير") }
                OutlinedButton(
                    onClick = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("استيراد") }
            }
        }

        item {
            Column {
                problem?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = {
                        val issue = validate(draft)
                        if (issue != null) {
                            problem = issue
                        } else {
                            problem = null
                            commit(draft)
                            saved = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) { Text(if (saved) "تم الحفظ ✓" else "حفظ", fontSize = 16.sp) }

                TextButton(
                    onClick = { ScheduleStore.reset(context); commit(Defaults.config) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("استعادة الجدول الأصلي") }
            }
        }
    }

    if (addingSection) {
        TextPrompt(
            title = "اسم الشعبة",
            initial = "",
            onDismiss = { addingSection = false },
        ) { value ->
            if (value.isNotBlank()) edit(draft.copy(sections = draft.sections + value.trim()))
            addingSection = false
        }
    }

    if (addingHoliday) {
        HolidayPrompt(
            onDismiss = { addingHoliday = false },
        ) { holiday ->
            edit(draft.copy(holidays = draft.holidays + holiday))
            addingHoliday = false
        }
    }
}

/* ══════════════════════════════════════════════════════════════
 *  3. SYLLABUS + LOAD
 * ══════════════════════════════════════════════════════════════ */

@Composable
private fun SyllabusScreen(config: Config, commit: (Config) -> Unit) {
    val load = ScheduleEngine.weeklyLoad(config)
    val lead = config.progress.values.maxOfOrNull { it.taught } ?: 0

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header("المنهج والنصاب", "سجّل الدرس بعد كل حصة") }

        item { SectionTitle("نصابك الأسبوعي") }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp)) {
                    Stat("${load.teaching}", "حصة تدريس", Modifier.weight(1f))
                    Stat("${load.standby}", "حصة انتظار", Modifier.weight(1f))
                    Stat("${load.teaching + load.standby}", "المجموع", Modifier.weight(1f))
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("توزيع الحصص على الشعب", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    load.perSection.entries.sortedBy { it.key }.forEach { (section, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(section, fontSize = 14.sp, modifier = Modifier.width(50.dp))
                            Text("$count حصص أسبوعيًا", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { SectionTitle("تقدّم المنهج") }
        for (section in config.sections) {
            item(key = "prog-$section") {
                ProgressCard(
                    section = section,
                    progress = config.progress[section] ?: SectionProgress(),
                    lead = lead,
                ) { updated ->
                    commit(config.copy(progress = config.progress + (section to updated)))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ══════════════════════════════════════════════════════════════
 *  SHARED PIECES
 * ══════════════════════════════════════════════════════════════ */

@Composable
private fun Header(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun OptionRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BellRow(bell: Bell, onChange: (Bell) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("الحصة ${bell.period}", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            TimeButton(bell.start) { onChange(bell.copy(start = it)) }
            Text(" – ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeButton(bell.end) { onChange(bell.copy(end = it)) }
        }
    }
}

@Composable
private fun TimeButton(time: LocalTime, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
                time.hour, time.minute, false,
            ).show()
        },
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(time.format(DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())), fontSize = 14.sp)
    }
}

@Composable
private fun DayCard(day: DayOfWeek, config: Config, onCell: (Int, Duty?) -> Unit) {
    val duties = config.templateOn(day)
    val cycle: List<Duty?> = buildList {
        add(null)
        config.sections.forEach { add(Duty.Teach(it)) }
        add(Duty.Standby)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(day.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()),
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (period in 1..7) {
                    val duty = duties[period]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$period", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(
                                    if (duty != null) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    val index = cycle.indexOfFirst { it == duty }
                                    onCell(period, cycle[(index + 1) % cycle.size])
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (duty) {
                                    is Duty.Teach -> duty.section
                                    Duty.Standby -> "انتظار"
                                    null -> "—"
                                },
                                fontSize = if (duty is Duty.Standby) 9.sp else 12.sp,
                                fontWeight = if (duty != null) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    section: String,
    progress: SectionProgress,
    lead: Int,
    onChange: (SectionProgress) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val behind = lead - progress.taught

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("${progress.taught} درسًا", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (behind > 0) {
                    Text("متأخرة $behind", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (progress.next.isBlank()) "لم تحدّد الدرس القادم" else "القادم: ${progress.next}",
                fontSize = 14.sp,
            )
            if (progress.last.isNotBlank()) {
                Text("الأخير: ${progress.last}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onChange(
                            progress.copy(
                                taught = progress.taught + 1,
                                last = progress.next.ifBlank { "درس ${progress.taught + 1}" },
                                next = "",
                            )
                        )
                        editing = true
                    },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("سجّل الدرس") }
                OutlinedButton(onClick = { editing = true }, shape = RoundedCornerShape(16.dp)) {
                    Text("الدرس القادم")
                }
                if (progress.taught > 0) {
                    TextButton(onClick = { onChange(progress.copy(taught = progress.taught - 1)) }) {
                        Text("تراجع")
                    }
                }
            }
        }
    }

    if (editing) {
        TextPrompt("الدرس القادم لـ $section", progress.next, { editing = false }) { value ->
            onChange(progress.copy(next = value.trim()))
            editing = false
        }
    }
}

@Composable
private fun TextPrompt(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun HolidayPrompt(onDismiss: () -> Unit, onConfirm: (Holiday) -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("إجازة") }
    var from by remember { mutableStateOf(LocalDate.now()) }
    var to by remember { mutableStateOf(LocalDate.now().plusDays(6)) }

    fun pick(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة إجازة") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("الاسم") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick(from) { from = it } }, shape = RoundedCornerShape(14.dp)) {
                        Text("من $from", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { pick(to) { to = it } }, shape = RoundedCornerShape(14.dp)) {
                        Text("إلى $to", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!to.isBefore(from)) onConfirm(Holiday(from, to, label.ifBlank { "إجازة" }))
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Refuses a timetable that cannot exist, with the reason in plain words. */
private fun validate(config: Config): String? {
    val ordered = config.bells.sortedBy { it.period }
    ordered.forEach { bell ->
        if (!bell.end.isAfter(bell.start)) {
            return "الحصة ${bell.period}: النهاية يجب أن تكون بعد البداية"
        }
        val minutes = ChronoUnit.MINUTES.between(bell.start, bell.end)
        if (minutes < 5) return "الحصة ${bell.period}: المدة $minutes دقيقة فقط"
    }
    ordered.zipWithNext { a, b ->
        if (b.start.isBefore(a.end)) return "الحصة ${a.period} والحصة ${b.period} متداخلتان"
    }
    if (config.sections.isEmpty()) return "أضف شعبة واحدة على الأقل"
    return null
}


/**
 * Notifications are the one feature you cannot verify by looking at the app —
 * they happen hours later, once. This fires each one on demand and states
 * plainly whether the system is even letting them through.
 */
@Composable
private fun NotificationTestCard() {
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf(PeriodNotifier.diagnostics(context)) }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            DiagnosticRow("إذن الإشعارات", diagnostics.notificationsAllowed)
            DiagnosticRow("المنبّهات الدقيقة", diagnostics.exactAlarmsAllowed)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("التنبيه القادم", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    diagnostics.nextEvent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("جرّب الآن", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            val samples = listOf(
                "بداية حصة" to PeriodNotifier.Preview.START,
                "باقي 5 دقائق" to PeriodNotifier.Preview.END_SOON,
                "نهاية حصة" to PeriodNotifier.Preview.END,
                "قبل الحصة" to PeriodNotifier.Preview.PRE,
                "الحصة الجارية" to PeriodNotifier.Preview.LIVE,
                "نطق تجريبي" to PeriodNotifier.Preview.SPEAK,
            )
            samples.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { (label, kind) ->
                        OutlinedButton(
                            onClick = {
                                PeriodNotifier.preview(context, kind)
                                diagnostics = PeriodNotifier.diagnostics(context)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Text(
                "التنبيهات تصل صامتة إن كان الجوال على الوضع الصامت أو عدم الإزعاج",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            if (ok) "مسموح" else "ممنوع",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}


/**
 * Android ships several Arabic voices, not one, and they differ enormously:
 * the network ones are neural and close to human, the local ones older and
 * flatter. Listing them with a preview is the only honest way to choose —
 * quality cannot be judged from a name.
 */
@Composable
private fun VoicePicker(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var voices by remember { mutableStateOf<List<Speaker.VoiceOption>?>(null) }

    LaunchedEffect(Unit) {
        Speaker.voices(context) { voices = it }
    }

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            when {
                voices == null -> Text("يبحث عن الأصوات المتاحة…", fontSize = 13.sp)

                voices!!.isEmpty() -> Column {
                    Text("لا يوجد صوت عربي مثبّت", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error)
                    Text(
                        "الإعدادات ← الإدارة العامة ← تحويل النص إلى كلام ← " +
                            "ثبّت محرك Google ونزّل اللغة العربية",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> Column {
                    voices!!.take(8).forEach { voice ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(voice.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = voice.id == selected,
                                onClick = { onSelect(voice.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(voice.label, fontSize = 13.sp)
                                Text(
                                    voice.id,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                Speaker.say(
                                    context,
                                    "باقي خمس دقائق على نهاية الحصة الثانية",
                                    voice.id,
                                )
                            }) { Text("استمع", fontSize = 12.sp) }
                        }
                    }
                    Text(
                        "أصوات الشبكة أقرب للصوت البشري لكنها تحتاج إنترنت لحظة النطق",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
