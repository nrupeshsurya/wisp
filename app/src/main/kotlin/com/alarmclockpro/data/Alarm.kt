package com.alarmclockpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

/**
 * An alarm.
 *
 * - [days] is a bitmask of repeat days (see [Days]); 0 means a one-time alarm.
 * - A one-time alarm may be pinned to [dateEpochDay]; otherwise it rings at the next occurrence of its time.
 * - A [temporary] alarm is one-time only and moves to "Recently ended" ([endedAt] set) once it has rung,
 *   so stale one-off alarms never pile up in the main list.
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val dateEpochDay: Long? = null,
    val enabled: Boolean = true,
    val label: String = "",
    val temporary: Boolean = false,
    val vibrate: Boolean = true,
    /** null = system default alarm sound, "" = silent. */
    val soundUri: String? = null,
    val soundTitle: String? = null,
    /** Occurrences at or before this instant are skipped (repeating alarms only). */
    val skipUntil: Long? = null,
    val snoozedUntil: Long? = null,
    /** The trigger time currently registered with AlarmManager, if any. */
    val nextTriggerAt: Long? = null,
    /** Set when the alarm has finished for good; it then lives in "Recently ended" until purged. */
    val endedAt: Long? = null,
    val endReason: EndReason? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isRepeating get() = days != 0
    val isEnded get() = endedAt != null
}

enum class EndReason { DISMISSED, MISSED }

object Days {
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val EVERY_DAY = 0b1111111

    /** Monday = bit 0 ... Sunday = bit 6. */
    fun bit(day: DayOfWeek) = 1 shl (day.value - 1)

    fun has(mask: Int, day: DayOfWeek) = mask and bit(day) != 0
}
