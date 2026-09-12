package com.mrabah.oneuischedule.widget

import com.mrabah.oneuischedule.data.*
import java.time.*
import java.time.temporal.ChronoUnit

internal enum class PreviewState(val title:String) {
    ACTUAL("جدولي الآن"), BEFORE("قبل الدوام"), LIVE("حصة حالية"), FREE("وقت متاح"), HOLIDAY("إجازة"), FINISHED("انتهى اليوم")
}

/** Isolated fixture: previewing never writes to the teacher's schedule. */
internal object WidgetScenarios {
    fun day(config:Config,state:PreviewState,now:LocalDateTime):DesignDay {
        if(state==PreviewState.ACTUAL) return DesignDay(ScheduleEngine.today(config,now),now)
        val date=now.toLocalDate()
        val sections=config.sections.takeIf{it.isNotEmpty()} ?: listOf("2/1","2/2")
        val bells=listOf(Bell(1,LocalTime.of(7,15),LocalTime.of(8,5)),Bell(2,LocalTime.of(8,5),LocalTime.of(8,55)),Bell(4,LocalTime.of(9,40),LocalTime.of(10,30)),Bell(6,LocalTime.of(11,20),LocalTime.of(12,10)))
        val sample=config.copy(bells=bells,profiles=emptyList(),week=mapOf(date.dayOfWeek to mapOf(1 to Duty.Teach(sections.first()),2 to Duty.Teach(sections.last()),4 to Duty.Standby,6 to Duty.Teach(sections.first()))),overrides=emptyMap(),standbySections=emptyMap(),notes=emptyMap(),holidays=if(state==PreviewState.HOLIDAY)listOf(Holiday(date,date,"إجازة مدرسية")) else emptyList())
        val time=when(state){PreviewState.BEFORE->LocalTime.of(6,50);PreviewState.LIVE->LocalTime.of(7,43);PreviewState.FREE->LocalTime.of(9,10);PreviewState.FINISHED->LocalTime.of(12,15);else->LocalTime.of(8,0)}
        val at=date.atTime(time)
        return DesignDay(ScheduleEngine.today(sample,at),at)
    }
}

internal enum class Availability { HOLIDAY, EMPTY, BUSY, FREE, FINISHED }
internal data class AvailableTime(val state:Availability,val minutes:Long?,val until:LocalTime?,val next:Slot?) {
    companion object {
        fun from(day:DesignDay):AvailableTime {
            val ui=day.ui
            fun minutes(t:LocalTime)=((ChronoUnit.SECONDS.between(day.now.toLocalTime(),t).coerceAtLeast(0)+59)/60)
            return when {
                ui.holiday!=null->AvailableTime(Availability.HOLIDAY,null,null,null)
                ui.slots.isEmpty()->AvailableTime(Availability.EMPTY,null,null,null)
                ui.live!=null->AvailableTime(Availability.BUSY,minutes(ui.live.bell.end),ui.live.bell.end,ui.next)
                ui.next!=null->AvailableTime(Availability.FREE,minutes(ui.next.bell.start),ui.next.bell.start,ui.next)
                else->AvailableTime(Availability.FINISHED,null,null,null)
            }
        }
    }
}
