package com.alarmclockpro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timers")
data class Timer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val durationMs: Long,
    val state: TimerState = TimerState.IDLE,
    /** Wall-clock end time while [TimerState.RUNNING]. */
    val endsAt: Long? = null,
    /** Remaining time while [TimerState.PAUSED]. */
    val remainingMs: Long? = null,
) {
    fun remainingAt(now: Long): Long = when (state) {
        TimerState.IDLE -> durationMs
        TimerState.RUNNING -> ((endsAt ?: now) - now).coerceAtLeast(0)
        TimerState.PAUSED -> remainingMs ?: durationMs
        TimerState.FINISHED -> 0
    }
}

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }
