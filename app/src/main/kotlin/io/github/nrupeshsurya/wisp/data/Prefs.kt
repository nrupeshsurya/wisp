package io.github.nrupeshsurya.wisp.data

import android.content.Context
import androidx.core.content.edit

/** Small key-value settings. Kept in SharedPreferences to avoid pulling in DataStore. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var snoozeMinutes: Int
        get() = sp.getInt("snooze_minutes", 10)
        set(v) = sp.edit { putInt("snooze_minutes", v) }

    var autoSilenceMinutes: Int
        get() = sp.getInt("auto_silence_minutes", 10)
        set(v) = sp.edit { putInt("auto_silence_minutes", v) }

    var gradualVolume: Boolean
        get() = sp.getBoolean("gradual_volume", true)
        set(v) = sp.edit { putBoolean("gradual_volume", v) }

    var worldClockZones: List<String>
        get() = sp.getString("world_zones", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit { putString("world_zones", v.joinToString(",")) }

    // Stopwatch: elapsed time is accumulatedMs + (now - runningSince) while running.
    var stopwatchRunningSince: Long
        get() = sp.getLong("sw_running_since", 0L)
        set(v) = sp.edit { putLong("sw_running_since", v) }

    var stopwatchAccumulatedMs: Long
        get() = sp.getLong("sw_accumulated", 0L)
        set(v) = sp.edit { putLong("sw_accumulated", v) }

    /** Lap split totals (ms since start), comma separated. */
    var stopwatchLaps: List<Long>
        get() = sp.getString("sw_laps", null)?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        set(v) = sp.edit { putString("sw_laps", v.joinToString(",")) }
}
