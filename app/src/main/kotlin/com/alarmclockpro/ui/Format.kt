package com.alarmclockpro.ui

import android.content.Context
import android.text.format.DateFormat
import com.alarmclockpro.data.Days
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {
    fun is24h(context: Context) = DateFormat.is24HourFormat(context)

    /** "7:15" + "AM" (or "19:15" + "" in 24-hour mode), so the UI can style the suffix separately. */
    fun timeParts(context: Context, hour: Int, minute: Int): Pair<String, String> {
        val t = LocalTime.of(hour, minute)
        return if (is24h(context)) t.format(DateTimeFormatter.ofPattern("H:mm")) to ""
        else t.format(DateTimeFormatter.ofPattern("h:mm")) to t.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    }

    fun time(context: Context, hour: Int, minute: Int): String =
        timeParts(context, hour, minute).let { (t, suffix) -> if (suffix.isEmpty()) t else "$t $suffix" }

    fun time(context: Context, epochMs: Long): String {
        val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
        return time(context, t.hour, t.minute)
    }

    /** "Today", "Tomorrow", or "Fri, 17 Oct". */
    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }

    fun day(epochMs: Long): String = day(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate())

    /** Days of the week in the user's locale order (Monday or Sunday first). */
    fun orderedWeek(): List<DayOfWeek> {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return (0L until 7L).map { first.plus(it) }
    }

    fun repeatSummary(days: Int): String = when (days) {
        0 -> "Once"
        Days.EVERY_DAY -> "Every day"
        Days.WEEKDAYS -> "Weekdays"
        Days.WEEKEND -> "Weekends"
        else -> orderedWeek().filter { Days.has(days, it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    /** "in 7 h 12 min", "in 45 min", "in under a minute". */
    fun until(targetMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val totalMin = TimeUnit.MILLISECONDS.toMinutes(targetMs - nowMs + 59_999)
        if (totalMin < 1) return "in under a minute"
        val d = totalMin / (24 * 60)
        val h = (totalMin / 60) % 24
        val m = totalMin % 60
        return "in " + listOfNotNull(
            d.takeIf { it > 0 }?.let { "$it d" },
            h.takeIf { it > 0 }?.let { "$it h" },
            m.takeIf { it > 0 && d == 0L }?.let { "$it min" },
        ).joinToString(" ")
    }

    /** "1:05:09" or "5:09" (timers); centiseconds optional (stopwatch). */
    fun duration(ms: Long, centis: Boolean = false): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec / 60) % 60
        val s = totalSec % 60
        val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        return if (centis) base + ".%02d".format((ms / 10) % 100) else base
    }
}
