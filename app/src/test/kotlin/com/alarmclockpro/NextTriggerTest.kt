package com.alarmclockpro

import com.alarmclockpro.alarm.NextTrigger
import com.alarmclockpro.data.Alarm
import com.alarmclockpro.data.Days
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerTest {
    private val zone = ZoneId.of("Europe/London")
    // Thursday 15 Oct 2026, 10:00
    private val now = ZonedDateTime.of(2026, 10, 15, 10, 0, 0, 0, zone)

    private fun at(day: Int, h: Int, m: Int, month: Int = 10) = ZonedDateTime.of(2026, month, day, h, m, 0, 0, zone)

    @Test fun oneTimeLaterToday() = assertEquals(at(15, 11, 0), NextTrigger.compute(Alarm(hour = 11, minute = 0), now))
    @Test fun oneTimePassedGoesTomorrow() = assertEquals(at(16, 9, 0), NextTrigger.compute(Alarm(hour = 9, minute = 0), now))
    @Test fun sameMinuteCountsAsPassed() = assertEquals(at(16, 10, 0), NextTrigger.compute(Alarm(hour = 10, minute = 0), now))

    @Test fun pinnedDate() {
        val a = Alarm(hour = 8, minute = 30, dateEpochDay = LocalDate.of(2026, 10, 20).toEpochDay())
        assertEquals(at(20, 8, 30), NextTrigger.compute(a, now))
    }

    @Test fun pinnedDateInPastNeverRings() {
        val a = Alarm(hour = 8, minute = 30, dateEpochDay = LocalDate.of(2026, 10, 14).toEpochDay())
        assertNull(NextTrigger.compute(a, now))
    }

    @Test fun disabledOrEndedNeverRings() {
        assertNull(NextTrigger.compute(Alarm(hour = 11, minute = 0, enabled = false), now))
        assertNull(NextTrigger.compute(Alarm(hour = 11, minute = 0, endedAt = 1), now))
    }

    @Test fun weekdaysOnThursdayAfterTime() =
        assertEquals(at(16, 7, 0), NextTrigger.compute(Alarm(hour = 7, minute = 0, days = Days.WEEKDAYS), now))

    @Test fun weekdaysOnFridayEveningSkipsWeekend() {
        val fridayEvening = at(16, 20, 0)
        assertEquals(at(19, 7, 0), NextTrigger.compute(Alarm(hour = 7, minute = 0, days = Days.WEEKDAYS), fridayEvening))
    }

    @Test fun onlyTodaysWeekdayAlreadyPassedGoesNextWeek() {
        val a = Alarm(hour = 7, minute = 0, days = Days.bit(DayOfWeek.THURSDAY))
        assertEquals(at(22, 7, 0), NextTrigger.compute(a, now))
    }

    @Test fun skipNextOccurrence() {
        val a = Alarm(hour = 7, minute = 0, days = Days.WEEKDAYS, skipUntil = at(16, 7, 0).toInstant().toEpochMilli())
        assertEquals(at(19, 7, 0), NextTrigger.compute(a, now))
    }

    @Test fun skipOnlyWeekdayJumpsTwoWeeks() {
        val a = Alarm(hour = 7, minute = 0, days = Days.bit(DayOfWeek.THURSDAY), skipUntil = at(22, 7, 0).toInstant().toEpochMilli())
        assertEquals(at(29, 7, 0), NextTrigger.compute(a, now))
    }

    @Test fun dstGapShiftsForward() {
        // UK clocks go forward at 01:00 on 29 Mar 2026; 01:30 doesn't exist that day.
        val before = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone)
        val result = NextTrigger.compute(Alarm(hour = 1, minute = 30), before)!!
        assertEquals(2, result.hour)
        assertEquals(30, result.minute)
    }
}
