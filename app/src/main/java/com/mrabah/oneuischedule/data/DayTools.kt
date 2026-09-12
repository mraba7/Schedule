package com.mrabah.oneuischedule.data

import java.time.*

internal object EmergencyDay {
    fun id(date:LocalDate)="emergency:$date"
    fun active(c:Config,date:LocalDate)=SchoolTools.profile(c,date)?.id==id(date)
    fun apply(c:Config,now:LocalDateTime,period:Int,minutes:Int,activity:String=""):Config {
        require(minutes in 5..120){"المدة من 5 إلى 120 دقيقة"}
        val date=now.toLocalDate()
        require(c.holidayOn(date)==null){"اليوم إجازة"}
        val bells=SchoolTools.bells(c,date)
        val selected=bells.firstOrNull{it.period==period} ?: error("اختر حصة")
        require(selected.start>now.toLocalTime()){"بدأت الحصة؛ اختر حصة لم تبدأ"}
        val shifted=bells.map {bell->if(bell.period<period)bell else {
            require(bell.end.toSecondOfDay()+minutes*60<86400){"التعديل يتجاوز نهاية اليوم"}
            bell.copy(start=bell.start.plusMinutes(minutes.toLong()),end=bell.end.plusMinutes(minutes.toLong()))
        }}
        val label=if(activity.isBlank())"يوم طارئ · تأخير $minutes دقيقة" else "يوم طارئ · ${activity.trim().take(60)} · ${selected.start}–${selected.start.plusMinutes(minutes.toLong())}"
        val profile=TimetableProfile(id(date),label,date,date,shifted,SchoolTools.week(c,date))
        val next=c.copy(profiles=c.profiles.filterNot{it.id==id(date)}+profile)
        require(DataVault.problem(next)==null){DataVault.problem(next).orEmpty()}
        return next
    }
    fun undo(c:Config,date:LocalDate)=c.copy(profiles=c.profiles.filterNot{it.id==id(date)})
}

internal object TomorrowPrep {
    fun signature(c:android.content.Context,date:LocalDate,slot:Slot):String {
        val section=slot.displaySection.orEmpty()
        return listOf(date,slot.period,section,slot.isStandby,slot.bell.start,slot.bell.end,slot.note,
            com.mrabah.oneuischedule.widget.ClassNotes.get(c,section)?.text.orEmpty(),
            ClassJournal.forClass(c,section).filter{!it.done}.map{ClassJournal.encode(it).toString()},
            ClassShortcuts.forClass(c,section),
            com.mrabah.oneuischedule.widget.PreparationStore.task(c,"$date#${slot.period}"),
            com.mrabah.oneuischedule.widget.PreparationStore.note(c,"$date#${slot.period}")).joinToString("|")
    }
    fun ready(c:android.content.Context,date:LocalDate,slot:Slot)=c.getSharedPreferences("app_preferences",0).getString("ready:$date#${slot.period}",null)==signature(c,date,slot)
    fun set(c:android.content.Context,date:LocalDate,slot:Slot,value:Boolean) {
        DataVault.checkpoint(c,"تجهيز حصة الغد")
        val edit=c.getSharedPreferences("app_preferences",0).edit();val key="ready:$date#${slot.period}"
        if(value)edit.putString(key,signature(c,date,slot)) else edit.remove(key)
        edit.apply()
    }
}
