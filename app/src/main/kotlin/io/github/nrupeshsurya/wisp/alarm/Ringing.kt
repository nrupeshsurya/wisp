package io.github.nrupeshsurya.wisp.alarm

import io.github.nrupeshsurya.wisp.data.Alarm
import io.github.nrupeshsurya.wisp.data.Timer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What is ringing right now. RingService writes it, RingActivity observes it (same process). */
sealed interface RingTarget {
    data class AlarmRing(val alarm: Alarm) : RingTarget
    data class TimerRing(val timer: Timer) : RingTarget
}

object Ringing {
    private val _current = MutableStateFlow<RingTarget?>(null)
    val current: StateFlow<RingTarget?> = _current

    internal fun set(target: RingTarget?) {
        _current.value = target
    }
}
