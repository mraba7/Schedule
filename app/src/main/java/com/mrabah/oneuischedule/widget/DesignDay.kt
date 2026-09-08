package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.Slot
import com.mrabah.oneuischedule.data.SlotState
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Pure data shared by the seven renderers. No hard-coded sample lessons. */
internal data class DesignDay(val ui: com.mrabah.oneuischedule.data.ScheduleUi, val now: LocalDateTime) {
    val focus get() = ui.focus
    val upcoming get() = ui.slots.filter { it.state == SlotState.AHEAD && it.period != focus?.period }
    val key get() = focus?.let { "${ui.date}#${it.period}" }
    val section get() = focus?.section ?: if (focus == null) "—" else "انتظار"
    val subject get() = if (focus==null) "لا توجد حصص" else if (focus?.section != null) ui.config.subject else "حصة انتظار"
    val day: String get() {
        val name = ui.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale("ar"))
        return when {
            ui.isToday -> name
            ui.date == now.toLocalDate().plusDays(1) -> "غدًا · $name"
            else -> name
        }
    }
    val date get() = ui.date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ar")))
    val period get() = focus?.let { "الحصة ${it.period}" } ?: "لا توجد حصص"
    val status get() = when {
        focus == null -> "لا توجد حصص"
        ui.live != null -> "الحصة الحالية"
        else -> "الحصة القادمة"
    }
    val range get() = focus?.let { "${clock(it.bell.start)} – ${clock(it.bell.end)}" } ?: "—"
    val minutes: Long? get() = when {
        ui.live != null -> ceilMinutes(ui.live!!.bell.end)
        ui.isToday && focus != null -> ceilMinutes(focus!!.bell.start)
        else -> null
    }
    private fun ceilMinutes(end: LocalTime) = ((ChronoUnit.SECONDS.between(now.toLocalTime(), end).coerceAtLeast(0) + 59) / 60)
    val countText get() = minutes?.toString() ?: focus?.let { clock(it.bell.start) } ?: "—"
    val countLabel get() = when {
        ui.live != null -> "دقيقة متبقية"
        minutes != null -> "دقيقة حتى البداية"
        else -> "تبدأ الساعة"
    }
    val finish get() = ui.slots.lastOrNull()?.let { clock(it.bell.end) } ?: "—"
    val breakRange: Pair<LocalTime, LocalTime>? get() {
        val third = ui.config.bells.firstOrNull { it.period == 3 } ?: return null
        val fourth = ui.config.bells.firstOrNull { it.period == 4 } ?: return null
        return if (fourth.start > third.end) third.end to fourth.start else null
    }
    val breakTime get() = breakRange?.first?.let(::clock) ?: "—"
    val freeLabel: String get() {
        if (!ui.isToday || ui.live != null || minutes == null) return "الفسحة"
        val t = now.toLocalTime()
        val b = breakRange
        return if (b != null && t >= b.first && t < b.second) "الفسحة الآن" else "وقت متاح"
    }
    val freeValue get() = if (freeLabel == "وقت متاح") "$minutes دقيقة" else breakTime
    val urgent get() = ui.isToday && minutes != null && minutes!! <= 5
    val summary get() = "$day، $status، $period، الفصل $section، $range، $countText $countLabel"

    companion object {
        fun build(config: Config, now: LocalDateTime = LocalDateTime.now()) = DesignDay(ScheduleEngine.build(config, now), now)
        fun clock(time: LocalTime) = time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        fun section(slot: Slot) = slot.section ?: "انتظار"
    }
}

internal enum class Design(val title: String, val number: String) {
    TICKET("بطاقة يومك", "00"), ORBIT("المدار التقني", "01"), EDITORIAL("المجلة الهادئة", "02"),
    BENTO("الوحدات الملونة", "03"), ROUTE("المسار الليلي", "04"), SPORT("وقت التركيز", "05"), BLUEPRINT("المخطط الهندسي", "06"), GLASS("الجدول الزجاجي", "07"), FOCUS("الحصة بوضوح", "08")
}
