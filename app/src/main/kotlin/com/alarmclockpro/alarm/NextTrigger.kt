package com.alarmclockpro.alarm

import com.alarmclockpro.data.Alarm
import com.alarmclockpro.data.Days
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure next-occurrence math, kept free of Android types so it is unit-testable. */
object NextTrigger {

    /**
     * When [alarm] should next ring after [now], or null if it never will
     * (disabled, ended, or a one-time alarm pinned to a date that has passed).
     * Snoozes are tracked separately and not considered here.
     */
    fun compute(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled || alarm.isEnded) return null
        val time = LocalTime.of(alarm.hour, alarm.minute)
        val zone = now.zone

        if (!alarm.isRepeating) {
            alarm.dateEpochDay?.let { epochDay ->
                val at = at(LocalDate.ofEpochDay(epochDay), time, zone)
                return if (at.isAfter(now)) at else null
            }
            val today = at(now.toLocalDate(), time, zone)
            return if (today.isAfter(now)) today else at(now.toLocalDate().plusDays(1), time, zone)
        }

        val skipUntil = alarm.skipUntil ?: Long.MIN_VALUE
        // 15 days: today's time may have passed (next week) and that occurrence may also be skipped.
        for (offset in 0L..14L) {
            val date = now.toLocalDate().plusDays(offset)
            if (!Days.has(alarm.days, date.dayOfWeek)) continue
            val at = at(date, time, zone)
            if (at.isAfter(now) && at.toInstant().toEpochMilli() > skipUntil) return at
        }
        return null
    }

    /** The next occurrence of [hour]:[minute] after [now] (today or tomorrow). */
    fun nextOccurrence(hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime =
        compute(Alarm(hour = hour, minute = minute), now)!!

    // A time inside a DST gap (e.g. 2:30 on spring-forward night) is shifted forward by the gap length.
    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId) = ZonedDateTime.of(date, time, zone)
}
