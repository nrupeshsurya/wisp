package com.alarmclockpro

import com.alarmclockpro.data.Days
import com.alarmclockpro.quickadd.QuickAddParser
import com.alarmclockpro.quickadd.QuickAddResult
import com.alarmclockpro.quickadd.QuickAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class QuickAddParserTest {
    // Thursday 15 Oct 2026, 10:00
    private val now = LocalDateTime.of(2026, 10, 15, 10, 0)
    private val today = now.toLocalDate()
    private val tomorrow = today.plusDays(1)

    private fun ok(input: String, at: LocalDateTime = now): QuickAlarm {
        val r = QuickAddParser.parse(input, at)
        assertTrue("Expected success for '$input' but got $r", r is QuickAddResult.Success)
        return (r as QuickAddResult.Success).alarm
    }

    private fun fails(input: String) {
        val r = QuickAddParser.parse(input, now)
        assertTrue("Expected failure for '$input' but got $r", r is QuickAddResult.Failure)
    }

    @Test fun relativeMinutes() = assertEquals(QuickAlarm(10, 20, date = today, label = "Nap", temporary = true), ok("nap 20m"))
    @Test fun relativeWithIn() = assertEquals(QuickAlarm(10, 45, date = today, label = "Laundry", temporary = true), ok("in 45 min laundry"))
    @Test fun relativeHoursAndMinutes() = assertEquals(QuickAlarm(11, 30, date = today, label = "Tea", temporary = true), ok("1h30m tea"))
    @Test fun relativeSpelledOut() = assertEquals(QuickAlarm(12, 15, date = today, label = "", temporary = true), ok("in 2 hours and 15 minutes"))
    @Test fun relativeDecimalHours() = assertEquals(11 to 30, ok("in 1.5 hours").let { it.hour to it.minute })
    @Test fun relativeCrossesMidnight() =
        assertEquals(QuickAlarm(1, 0, date = tomorrow, temporary = true), ok("in 2h", LocalDateTime.of(2026, 10, 15, 23, 0)))
    @Test fun relativeRoundsToNearestMinute() =
        assertEquals(10 to 21, ok("nap 20m", now.withSecond(40)).let { it.hour to it.minute })

    @Test fun clockWithTomorrow() = assertEquals(QuickAlarm(7, 15, date = tomorrow, label = "Gym", temporary = true), ok("7:15 gym tomorrow"))
    @Test fun ambiguousPicksNextUpcoming() = assertEquals(19 to 15, ok("7:15 gym").let { it.hour to it.minute })
    @Test fun ambiguousBothPassedPicksMorning() = assertEquals(7, ok("7:15", LocalDateTime.of(2026, 10, 15, 21, 0)).hour)
    @Test fun amExplicit() = assertEquals(QuickAlarm(7, 0, label = "Gym", temporary = true), ok("7am gym"))
    @Test fun twentyFourHour() = assertEquals(QuickAlarm(19, 30, label = "Dinner", temporary = true), ok("19:30 dinner"))
    @Test fun leadingZeroIs24h() = assertEquals(7, ok("07:30").hour)
    @Test fun compactPm() = assertEquals(QuickAlarm(19, 30, label = "Call mom", temporary = true), ok("730pm call mom"))
    @Test fun dottedPm() = assertEquals(19 to 30, ok("7.30 p.m. pickup").let { it.hour to it.minute })
    @Test fun noon() = assertEquals(QuickAlarm(12, 0, label = "Lunch", temporary = true), ok("noon lunch"))
    @Test fun remindMeAtTo() = assertEquals(QuickAlarm(18, 0, label = "Call mom", temporary = true), ok("remind me at 6 to call mom"))
    @Test fun setAnAlarmFor() = assertEquals(QuickAlarm(6, 30, label = "", temporary = true), ok("set an alarm for 6:30 am"))
    @Test fun keepsAddInLabel() = assertEquals("Add milk to list", ok("8pm add milk to list").label)
    @Test fun dayGivenUsesMorningHeuristic() = assertEquals(QuickAlarm(15, 0, date = tomorrow, temporary = true), ok("tomorrow at 3"))
    @Test fun tonight() = assertEquals(QuickAlarm(21, 0, date = today, label = "Meds", temporary = true), ok("meds tonight at 9"))

    @Test fun weekdays() = assertEquals(QuickAlarm(6, 45, days = Days.WEEKDAYS, label = "Work", temporary = false), ok("weekdays 6:45 work"))
    @Test fun everyListOfDays() {
        val a = ok("every mon and wed 7 am yoga")
        assertEquals(Days.bit(DayOfWeek.MONDAY) or Days.bit(DayOfWeek.WEDNESDAY), a.days)
        assertEquals(QuickAlarm(7, 0, days = a.days, label = "Yoga", temporary = false), a)
    }
    @Test fun pluralDays() = assertEquals(Days.bit(DayOfWeek.SATURDAY), ok("saturdays 9am football").days)
    @Test fun daily() = assertEquals(QuickAlarm(22, 0, days = Days.EVERY_DAY, label = "Meds", temporary = false), ok("daily 22:00 meds"))
    @Test fun keepMakesPermanent() = assertEquals(false, ok("8am dentist keep").temporary)

    @Test fun singleWeekday() = assertEquals(QuickAlarm(9, 0, date = LocalDate.of(2026, 10, 16), label = "Dentist", temporary = true), ok("fri 9am dentist"))
    @Test fun todaysWeekdayLaterToday() = assertEquals(today, ok("thursday 11am").date)
    @Test fun todaysWeekdayAlreadyPassedMeansNextWeek() = assertEquals(today.plusDays(7), ok("thu 9am").date)

    @Test fun noTime() = fails("gym")
    @Test fun blank() = fails("   ")
    @Test fun todayInThePast() = fails("today 9am")
    @Test fun invalidHour() = fails("25:00 nope")
    @Test fun invalidMeridiem() = fails("15pm")
}
