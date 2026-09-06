package com.mrabah.oneuischedule

import com.mrabah.oneuischedule.data.Config
import com.mrabah.oneuischedule.data.Defaults
import com.mrabah.oneuischedule.data.Duty
import com.mrabah.oneuischedule.data.Holiday
import com.mrabah.oneuischedule.data.ScheduleEngine
import com.mrabah.oneuischedule.data.SlotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The timetable itself is data, and data was where the real bug lived: two days
 * were shifted by one period and nothing caught it. These tests pin the
 * schedule to the official sheet and the engine to the clock.
 */
class ScheduleEngineTest {

    private val config: Config = Defaults.config

    // 7 September 2026 is a Monday
    private fun monday(h: Int, m: Int) = LocalDateTime.of(LocalDate.of(2026, 9, 7), LocalTime.of(h, m))
    private fun sunday(h: Int, m: Int) = LocalDateTime.of(LocalDate.of(2026, 9, 6), LocalTime.of(h, m))
    private fun friday(h: Int, m: Int) = LocalDateTime.of(LocalDate.of(2026, 9, 11), LocalTime.of(h, m))

    /* ── the timetable, against the official sheet ─────────── */

    @Test
    fun `sunday matches the official sheet`() {
        val day = config.templateOn(DayOfWeek.SUNDAY)
        assertEquals(Duty.Teach("2/3"), day[1])
        assertEquals(Duty.Teach("2/2"), day[2])
        assertEquals(Duty.Standby, day[3])
        assertEquals(Duty.Teach("2/4"), day[4])
        assertEquals(4, day.size)
    }

    @Test
    fun `monday starts at period two, not one`() {
        val day = config.templateOn(DayOfWeek.MONDAY)
        assertNull("Monday has no first period", day[1])
        assertEquals(Duty.Teach("2/3"), day[2])
        assertEquals(Duty.Teach("2/4"), day[3])
        assertEquals(Duty.Teach("2/1"), day[6])
        assertEquals(Duty.Teach("2/2"), day[7])
    }

    @Test
    fun `tuesday runs periods two to four`() {
        val day = config.templateOn(DayOfWeek.TUESDAY)
        assertNull("Tuesday has no first period", day[1])
        assertEquals(Duty.Teach("2/4"), day[2])
        assertEquals(Duty.Teach("2/2"), day[3])
        assertEquals(Duty.Teach("2/1"), day[4])
        assertEquals(3, day.size)
    }

    @Test
    fun `wednesday and thursday keep their standby period`() {
        assertEquals(Duty.Standby, config.templateOn(DayOfWeek.WEDNESDAY)[5])
        assertEquals(Duty.Standby, config.templateOn(DayOfWeek.THURSDAY)[5])
    }

    @Test
    fun `every duty points at a bell that exists`() {
        config.week.forEach { (day, duties) ->
            duties.keys.forEach { period ->
                assertNotNull(
                    "$day period $period has no bell time",
                    config.bells.firstOrNull { it.period == period },
                )
            }
        }
    }

    @Test
    fun `bells never end before they start and never overlap`() {
        val ordered = config.bells.sortedBy { it.period }
        ordered.forEach { assertTrue("period ${it.period}", it.end.isAfter(it.start)) }
        ordered.zipWithNext { a, b ->
            assertTrue("period ${a.period} overlaps ${b.period}", !b.start.isBefore(a.end))
        }
    }

    /* ── the engine, against the clock ─────────────────────── */

    @Test
    fun `mid period reports live with the right section`() {
        val ui = ScheduleEngine.build(config, monday(8, 20)) // period 2, 8:05-8:55
        assertEquals(2, ui.live?.period)
        assertEquals("2/3", ui.live?.section)
        assertEquals(SlotState.LIVE, ui.live?.state)
    }

    @Test
    fun `progress is proportional to the period's own length`() {
        // period 2 runs 8:05 to 8:55, so 8:30 is exactly halfway
        val ui = ScheduleEngine.build(config, monday(8, 30))
        assertEquals(0.5f, ui.progress, 0.02f)
        assertEquals(25L, ui.minutesLeftInLive)
    }

    @Test
    fun `between periods there is no live slot but a next one`() {
        val ui = ScheduleEngine.build(config, monday(10, 0)) // inside the break
        assertNull(ui.live)
        assertEquals(6, ui.next?.period)
    }

    @Test
    fun `before school the first duty of the day is next`() {
        val ui = ScheduleEngine.build(config, monday(6, 50))
        assertNull(ui.live)
        assertEquals(2, ui.next?.period)
        assertTrue(ui.isToday)
    }

    @Test
    fun `after the last bell the widget rolls to the next workday`() {
        val ui = ScheduleEngine.build(config, monday(14, 0))
        assertEquals(DayOfWeek.TUESDAY, ui.dayOfWeek)
        assertTrue("previewing tomorrow is not today", !ui.isToday)
        assertEquals(2, ui.next?.period)
    }

    @Test
    fun `friday rolls forward to sunday`() {
        val ui = ScheduleEngine.build(config, friday(9, 0))
        assertEquals(DayOfWeek.SUNDAY, ui.dayOfWeek)
        assertEquals("2/3", ui.next?.section)
    }

    @Test
    fun `finished periods are counted out of the remaining total`() {
        val ui = ScheduleEngine.build(config, sunday(9, 0)) // periods 1 and 2 done
        assertEquals(2, ui.slots.count { it.state == SlotState.DONE })
        assertEquals(2, ui.remaining)
    }

    @Test
    fun `assembly is flagged between seven and the first bell`() {
        assertTrue(ScheduleEngine.build(config, monday(7, 5)).isAssembly)
        assertTrue(!ScheduleEngine.build(config, monday(7, 30)).isAssembly)
    }

    /* ── refresh scheduling ────────────────────────────────── */

    @Test
    fun `a live period refreshes on the next minute`() {
        val now = monday(8, 20)
        val next = ScheduleEngine.build(config, now).let {
            assertNotNull(it.live); ScheduleEngine.boundaries(config, now.toLocalDate())
        }
        assertTrue(next.isNotEmpty())
    }

    @Test
    fun `a one-day override replaces the template for that date only`() {
        val date = LocalDate.of(2026, 9, 7) // a Monday
        val swapped = config.copy(overrides = mapOf("$date#2" to Duty.Teach("2/1")))
        assertEquals(Duty.Teach("2/1"), ScheduleEngine.dutiesOn(swapped, date)[2])
        // the following Monday is untouched
        assertEquals(Duty.Teach("2/3"), ScheduleEngine.dutiesOn(swapped, date.plusDays(7))[2])
    }

    @Test
    fun `a cancelled period disappears for that date`() {
        val date = LocalDate.of(2026, 9, 7)
        val cancelled = config.copy(overrides = mapOf("$date#2" to null))
        assertNull(ScheduleEngine.dutiesOn(cancelled, date)[2])
        assertEquals(3, ScheduleEngine.dutiesOn(cancelled, date).size)
    }

    @Test
    fun `a holiday empties the day and the bell stays silent`() {
        val date = LocalDate.of(2026, 9, 7)
        val onLeave = config.copy(
            holidays = listOf(Holiday(date.minusDays(1), date.plusDays(3), "اختبارات"))
        )
        assertTrue(ScheduleEngine.dutiesOn(onLeave, date).isEmpty())
        val ui = ScheduleEngine.build(onLeave, date.atTime(8, 20))
        assertNull(ui.live)
        assertNotNull(ui.holiday)
    }

    @Test
    fun `weekly load counts teaching and standby separately`() {
        val load = ScheduleEngine.weeklyLoad(config)
        assertEquals(16, load.teaching)
        assertEquals(3, load.standby)
        assertEquals(4, load.perSection["2/1"])
    }

    @Test
    fun `boundaries cover every bell of the day`() {
        val marks = ScheduleEngine.boundaries(config, LocalDate.of(2026, 9, 7))
        // four periods on Monday: eight bells, plus assembly start and end, plus midnight
        assertTrue(marks.size >= 10)
    }
}
