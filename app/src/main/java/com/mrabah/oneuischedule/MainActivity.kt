package com.mrabah.oneuischedule

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
import androidx.glance.appwidget.updateAll
import com.mrabah.oneuischedule.data.Bell
import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.Duty
import com.mrabah.oneuischedule.data.ScheduleStore
import com.mrabah.oneuischedule.data.SectionProgress
import com.mrabah.oneuischedule.notify.PeriodNotifier
import com.mrabah.oneuischedule.widget.ScheduleUpdater
import com.mrabah.oneuischedule.widget.ScheduleWidget
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * IBM Plex Sans Arabic: open licence, four weights, and — the reason it was
 * chosen over the geometric alternatives — Arabic and Latin drawn as one
 * family, so "الحصة 2" and "8:05" share a baseline and a colour of text.
 *
 * The widget cannot use this. App widgets are drawn by the launcher's process,
 * which only reaches system fonts, so it keeps inheriting One UI Sans.
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

private val DAYS = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
)

/** Tapping a cell walks this list, so no dropdown is needed on a phone. */
private val CYCLE = listOf<Duty?>(
    null,
    Duty.Teach("2/1"), Duty.Teach("2/2"), Duty.Teach("2/3"), Duty.Teach("2/4"),
    Duty.Standby,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
            MaterialTheme(
                colorScheme = scheme,
                typography = typographyOf(MaterialTheme.typography),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EditorScreen()
                }
            }
        }
    }
}

@Composable
private fun EditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(ScheduleStore.load(context)) }
    var saved by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        update = UpdateChecker.check(context)?.takeIf { it.newer }
    }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        config = config.copy(notify = granted)
    }

    fun apply(next: Config) {
        config = next
        saved = false
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "جدول الحصص",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "عدّل الأوقات والفصول، ثم احفظ — الودجت يتحدّث فورًا",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        update?.let { found ->
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
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
                        Button(
                            onClick = { uriHandler.openUri(found.page) },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("تحميل") }
                    }
                }
            }
        }

        item { SectionTitle("أوقات الحصص") }

        items@ for (bell in config.bells) {
            item(key = "bell-${bell.period}") {
                BellRow(bell) { updated ->
                    apply(config.copy(bells = config.bells.map { if (it.period == updated.period) updated else it }))
                }
            }
        }

        item { SectionTitle("الجدول الأسبوعي") }

        for (day in DAYS) {
            item(key = "day-${day.name}") {
                DayCard(day, config) { period, duty ->
                    val duties = config.dutiesOn(day).toMutableMap()
                    if (duty == null) duties.remove(period) else duties[period] = duty
                    apply(config.copy(week = config.week + (day to duties)))
                }
            }
        }

        item { SectionTitle("التنبيهات") }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("تنبيه صوتي عند بداية الحصة ونهايتها", fontSize = 15.sp)
                        Text(
                            "يعتمد على نغمة الإشعارات في جهازك",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = config.notify,
                        onCheckedChange = { want ->
                            if (want && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                apply(config.copy(notify = want))
                            }
                        },
                    )
                }
            }
        }

        if (config.notify) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        OptionRow(
                            title = "صوت جرس المدرسة",
                            subtitle = "بدل نغمة الإشعارات المعتادة",
                            checked = config.schoolBell,
                        ) { apply(config.copy(schoolBell = it)) }
                        OptionRow(
                            title = "تنبيه قبل الحصة بخمس دقائق",
                            subtitle = "مع اسم الدرس القادم لتلك الشعبة",
                            checked = config.preAlert,
                        ) { apply(config.copy(preAlert = it)) }
                        OptionRow(
                            title = "إشعار الحصة الجارية",
                            subtitle = "شريط تقدّم مستمر يتحدّث كل دقيقة",
                            checked = config.liveUpdate,
                        ) { apply(config.copy(liveUpdate = it)) }
                    }
                }
            }
        }

        item { SectionTitle("تقدّم المنهج") }

        item {
            Text(
                "سجّل الدرس بعد كل حصة، والتطبيق يوضّح أي شعبة تأخّرت",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        for (section in Defaults.SECTIONS) {
            item(key = "prog-$section") {
                ProgressCard(
                    section = section,
                    progress = config.progress[section] ?: SectionProgress(),
                    lead = config.progress.values.maxOfOrNull { it.taught } ?: 0,
                ) { updated ->
                    apply(config.copy(progress = config.progress + (section to updated)))
                }
            }
        }

        item {
            Column {
                problem?.let { message ->
                    Text(
                        message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = {
                        val issue = validate(config)
                        if (issue != null) {
                            problem = issue
                            return@Button
                        }
                        problem = null
                        ScheduleStore.save(context, config)
                        PeriodNotifier.sync(context)
                        ScheduleUpdater.schedule(context)
                        scope.launch { ScheduleWidget().updateAll(context) }
                        saved = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Text(if (saved) "تم الحفظ ✓" else "حفظ وتحديث الودجت", fontSize = 16.sp)
                }
                TextButton(
                    onClick = {
                        ScheduleStore.reset(context)
                        config = Defaults.config
                        saved = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("استعادة الجدول الأصلي")
                }
            }
        }
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
private fun BellRow(bell: Bell, onChange: (Bell) -> Unit) {
    val context = LocalContext.current

    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "الحصة ${bell.period}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TimeButton(bell.start) { picked -> onChange(bell.copy(start = picked)) }
            Text(" – ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeButton(bell.end) { picked -> onChange(bell.copy(end = picked)) }
        }
    }
}

@Composable
private fun TimeButton(time: LocalTime, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val label = time.format(DateTimeFormatter.ofPattern("h:mm", Locale.getDefault()))

    OutlinedButton(
        onClick = { pickTime(context, time, onPicked) },
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(label, fontSize = 14.sp)
    }
}

private fun pickTime(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        false,
    ).show()
}

@Composable
private fun DayCard(day: DayOfWeek, config: Config, onCell: (Int, Duty?) -> Unit) {
    val duties = config.dutiesOn(day)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                day.getDisplayName(JavaTextStyle.FULL, Locale.getDefault()),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (period in 1..7) {
                    Cell(
                        period = period,
                        duty = duties[period],
                        modifier = Modifier.weight(1f),
                    ) { current ->
                        val index = CYCLE.indexOfFirst { it == current }
                        onCell(period, CYCLE[(index + 1 + CYCLE.size) % CYCLE.size])
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "اضغط على أي خانة لتبديلها",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Cell(
    period: Int,
    duty: Duty?,
    modifier: Modifier = Modifier,
    onTap: (Duty?) -> Unit,
) {
    val label = when (duty) {
        is Duty.Teach -> duty.section
        Duty.Standby -> "انتظار"
        null -> "—"
    }
    val filled = duty != null

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$period",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(
                    if (filled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .clickable { onTap(duty) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = if (duty is Duty.Standby) 9.sp else 12.sp,
                fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
                color = if (filled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
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
    var draft by remember(progress.next) { mutableStateOf(progress.next) }

    val lag = lead - progress.taught

    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${progress.taught} درسًا",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (lag > 0) {
                    Text(
                        "متأخرة $lag",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (progress.next.isBlank()) "لم تحدّد الدرس القادم"
                else "القادم: ${progress.next}",
                fontSize = 14.sp,
            )
            if (progress.last.isNotBlank()) {
                Text(
                    "الأخير: ${progress.last}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    enabled = true,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("سجّل الدرس")
                }
                OutlinedButton(
                    onClick = { editing = true },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("الدرس القادم")
                }
                if (progress.taught > 0) {
                    TextButton(onClick = {
                        onChange(progress.copy(taught = progress.taught - 1))
                    }) {
                        Text("تراجع")
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("الدرس القادم لـ $section") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(progress.copy(next = draft.trim()))
                    editing = false
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("إلغاء") }
            },
        )
    }
}


/** Refuses a timetable that cannot exist, with the reason in plain words. */
private fun validate(config: Config): String? {
    val ordered = config.bells.sortedBy { it.period }

    ordered.forEach { bell ->
        if (!bell.end.isAfter(bell.start)) {
            return "الحصة ${bell.period}: وقت النهاية يجب أن يكون بعد البداية"
        }
        val minutes = ChronoUnit.MINUTES.between(bell.start, bell.end)
        if (minutes < 5) {
            return "الحصة ${bell.period}: المدة $minutes دقيقة فقط"
        }
    }

    ordered.zipWithNext { a, b ->
        if (b.start.isBefore(a.end)) {
            return "الحصة ${a.period} والحصة ${b.period} متداخلتان"
        }
    }

    return null
}
