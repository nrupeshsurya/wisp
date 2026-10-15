package com.alarmclockpro.quickadd

import com.alarmclockpro.data.Days
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the user typed, understood as an alarm. */
data class QuickAlarm(
    val hour: Int,
    val minute: Int,
    /** Repeat bitmask (see [Days]); 0 = one-time. */
    val days: Int = 0,
    /** Pinned date for a one-time alarm; null = next occurrence of the time. */
    val date: LocalDate? = null,
    val label: String = "",
    val temporary: Boolean = days == 0,
)

sealed interface QuickAddResult {
    data class Success(val alarm: QuickAlarm) : QuickAddResult
    data class Failure(val reason: String) : QuickAddResult
}

/**
 * Rule-based parser for quick-add text such as "nap 20m", "7:15 gym tomorrow",
 * "in 45 min laundry", "weekdays 6:45 work" or "every mon and wed 7am yoga".
 *
 * Ambiguous 12-hour times without am/pm ("7:15", "at 6") resolve to the next upcoming occurrence;
 * when a day is given they resolve to 6–11 → morning, 12 → noon, 1–5 → afternoon.
 */
object QuickAddParser {

    private const val WEEKDAY = "(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)(?:day|nesday|sday|urday|rsday)?s?"
    private const val UNIT_H = "(?:h|hrs?|hours?)"
    private const val UNIT_M = "(?:m|mins?|minutes?)"

    private val relative = Regex("""\b(?:in\s+)?(?:(\d+(?:\.\d+)?)\s*$UNIT_H(?:\s*(?:and\s+)?(\d+)\s*$UNIT_M?)?|(\d+)\s*$UNIT_M)\b""")
    private val clock = Regex("""\b(?:at\s+)?(\d{1,2})[:.](\d{2})(?:\s*(am|pm))?\b""")
    private val compact = Regex("""\b(?:at\s+)?(\d{3,4})\s*(am|pm)\b|\bat\s+(\d{3,4})\b""")
    private val hourOnly = Regex("""\b(?:at\s+)?(\d{1,2})\s*(am|pm)\b|\bat\s+(\d{1,2})\b""")
    private val noonMidnight = Regex("""\b(?:at\s+)?(noon|midday|midnight)\b""")

    private val everyDay = Regex("""\b(?:every\s*day|daily)\b""")
    private val weekdays = Regex("""\b(?:every\s+)?week\s?days\b|\bevery\s+weekday\b""")
    private val weekends = Regex("""\b(?:every\s+)?weekends?\b""")
    private val everyList = Regex("""\bevery\s+$WEEKDAY(?:\s*(?:,|and|&)\s*$WEEKDAY)*\b""")
    private val pluralDay = Regex("""\b(?:on\s+)?(mon|tues|wednes|thurs|fri|satur|sun)days\b""")
    private val singleDay = Regex("""\b(?:on\s+|next\s+|this\s+)?$WEEKDAY\b""")
    private val today = Regex("""\btoday\b""")
    private val tonight = Regex("""\btonight\b""")
    private val tomorrow = Regex("""\b(?:tomorrow|tmrw?|tmr)\b""")
    private val keep = Regex("""\b(?:keep|permanent)\b""")
    private val once = Regex("""\b(?:just\s+)?once\b|\bone\s+time\b|\btemp(?:orary)?\b""")
    /** Command words around the label: "set an alarm for …", "remind me to …", a dangling "at". */
    private val fillers = listOf(
        Regex("""^(?:(?:set|create|add)\s+(?:an?\s+)?)?alarm\b""", RegexOption.IGNORE_CASE),
        Regex("""^remind\s+me\b""", RegexOption.IGNORE_CASE),
        Regex("""^wake\s+me(?:\s+up)?\b""", RegexOption.IGNORE_CASE),
        Regex("""^(?:for|to|at|on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:for|to|at|on)$""", RegexOption.IGNORE_CASE),
    )

    fun parse(input: String, now: LocalDateTime): QuickAddResult {
        // Same-length normalisation, so match ranges also index into the original text for the label.
        val text = input
            .replace(Regex("(?i)a\\.m\\."), "am  ")
            .replace(Regex("(?i)p\\.m\\."), "pm  ")
            .lowercase()
        if (text.isBlank()) return QuickAddResult.Failure("Type a time, e.g. “7:15 gym” or “nap 20m”")

        val used = BooleanArray(text.length)
        fun MatchResult.consume(): MatchResult = also { r -> r.range.forEach { used[it] = true } }
        fun Regex.take(): MatchResult? = findAll(text).firstOrNull { m -> m.range.none { used[it] } }?.consume()

        // --- Repeat days ---
        var days = 0
        everyDay.take()?.let { days = Days.EVERY_DAY }
        weekdays.take()?.let { days = days or Days.WEEKDAYS }
        weekends.take()?.let { days = days or Days.WEEKEND }
        everyList.take()?.let { m -> Regex(WEEKDAY).findAll(m.value).forEach { days = days or dayBit(it.groupValues[1]) } }
        while (true) {
            val m = pluralDay.take() ?: break
            days = days or dayBit(m.groupValues[1])
        }

        // --- Absolute time ---
        var hour: Int? = null
        var minute = 0
        var meridiem: String? = null
        var explicit24h = false
        clock.take()?.let { m ->
            hour = m.groupValues[1].toInt(); minute = m.groupValues[2].toInt()
            meridiem = m.groupValues[3].ifEmpty { null }
            explicit24h = m.groupValues[1].startsWith("0") && m.groupValues[1].length == 2
        } ?: compact.take()?.let { m ->
            val digits = m.groupValues[1].ifEmpty { m.groupValues[3] }
            hour = digits.dropLast(2).toInt(); minute = digits.takeLast(2).toInt()
            meridiem = m.groupValues[2].ifEmpty { null }
            explicit24h = meridiem == null
        } ?: noonMidnight.take()?.let { m ->
            hour = if (m.groupValues[1] == "midnight") 0 else 12
            explicit24h = true
        } ?: hourOnly.take()?.let { m ->
            hour = m.groupValues[1].ifEmpty { m.groupValues[3] }.toInt()
            meridiem = m.groupValues[2].ifEmpty { null }
        }

        // --- Relative time ("in 20m") — only if no clock time was given ---
        var relativeTarget: LocalDateTime? = null
        if (hour == null) {
            relative.take()?.let { m ->
                val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val min = (m.groupValues[2].ifEmpty { m.groupValues[3] }).toLongOrNull() ?: 0L
                val total = Duration.ofSeconds((h * 3600).toLong()).plusMinutes(min)
                if (total.isZero || total.toDays() >= 1) return QuickAddResult.Failure("Relative times must be under 24 hours")
                // Round to the nearest minute so "nap 20m" at 10:00:40 rings at 10:21, not 19m20s later.
                relativeTarget = now.plus(total).plusSeconds(30).withSecond(0).withNano(0)
            }
        }

        // --- Single day (one-time) ---
        var date: LocalDate? = null
        var dateFromWeekday = false
        var preferEvening = false
        when {
            tomorrow.take() != null -> date = now.toLocalDate().plusDays(1)
            today.take() != null -> date = now.toLocalDate()
            tonight.take() != null -> { date = now.toLocalDate(); preferEvening = true }
            days == 0 -> singleDay.take()?.let { m ->
                date = nextDate(now.toLocalDate(), dayOfWeek(m.groupValues[1]))
                dateFromWeekday = true
            }
        }

        val temporaryOverride = when {
            keep.take() != null -> false
            once.take() != null -> true
            else -> null
        }

        // Lowercasing can change the length of some characters; fall back to the lowercased text then.
        val label = buildLabel(if (input.length == text.length) input else text, used)

        relativeTarget?.let { t ->
            if (days != 0 || date != null) return QuickAddResult.Failure("Use a clock time with days, e.g. “mon 7:00”")
            return QuickAddResult.Success(
                QuickAlarm(t.hour, t.minute, date = t.toLocalDate(), label = label, temporary = temporaryOverride ?: true)
            )
        }

        var h = hour ?: return QuickAddResult.Failure("Couldn't find a time — try “7:15 gym”, “at 6 call mom” or “nap 20m”")
        if (minute > 59) return QuickAddResult.Failure("Minutes must be 00–59")
        when (meridiem) {
            "am" -> { if (h !in 1..12) return invalidHour(); if (h == 12) h = 0 }
            "pm" -> { if (h !in 1..12) return invalidHour(); if (h != 12) h += 12 }
            else -> if (h > 23) return invalidHour()
        }

        val ambiguous = meridiem == null && !explicit24h && h in 1..12
        if (ambiguous) {
            h = when {
                preferEvening -> if (h == 12) 0 else (h % 12) + 12
                date != null || days != 0 -> when (h) { in 6..11 -> h; 12 -> 12; else -> h + 12 }
                else -> nextOfAmPm(h, minute, now)
            }
            if (preferEvening && h == 0) date = date?.plusDays(1)
        }

        date?.let { d ->
            if (!LocalDateTime.of(d, LocalTime.of(h, minute)).isAfter(now)) {
                // "fri 9am" said on Friday at 10am means next Friday; "today 9am" is a mistake.
                if (dateFromWeekday) date = d.plusDays(7) else return QuickAddResult.Failure("That time has already passed")
            }
        }

        val isRepeating = days != 0
        return QuickAddResult.Success(
            QuickAlarm(
                hour = h, minute = minute, days = days,
                date = date.takeIf { !isRepeating },
                label = label,
                temporary = !isRepeating && (temporaryOverride ?: true),
            )
        )
    }

    private fun invalidHour() = QuickAddResult.Failure("That isn't a valid time")

    /** For "7:15" with no am/pm, pick whichever of 7:15 / 19:15 comes next. */
    private fun nextOfAmPm(h12: Int, minute: Int, now: LocalDateTime): Int {
        val am = if (h12 == 12) 0 else h12
        val pm = am + 12
        val nowT = now.toLocalTime()
        val amT = LocalTime.of(am, minute)
        val pmT = LocalTime.of(pm, minute)
        return when {
            amT.isAfter(nowT) -> am
            pmT.isAfter(nowT) -> pm
            else -> am // both passed today -> tomorrow morning
        }
    }

    private fun nextDate(from: LocalDate, day: DayOfWeek): LocalDate {
        var d = from
        while (d.dayOfWeek != day) d = d.plusDays(1)
        return d // may be today; parse() moves it a week ahead if the time has already passed
    }

    private fun dayOfWeek(prefix: String): DayOfWeek = when (prefix.take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun dayBit(prefix: String) = Days.bit(dayOfWeek(prefix))

    private fun buildLabel(source: String, used: BooleanArray): String {
        var label = String(CharArray(source.length) { i -> if (used[i]) ' ' else source[i] })
            .replace(Regex("\\s+"), " ").trim()
        repeat(4) {
            for (f in fillers) label = label.replace(f, "").trim(' ', ',', '-', '·', ':', '.')
        }
        return label.replace(Regex("\\s+"), " ").replaceFirstChar { it.uppercaseChar() }
    }
}
