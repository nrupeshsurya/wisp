package com.alarmclockpro.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmclockpro.app
import com.alarmclockpro.data.Alarm
import com.alarmclockpro.quickadd.QuickAddParser
import com.alarmclockpro.quickadd.QuickAddResult
import com.alarmclockpro.quickadd.QuickAlarm
import com.alarmclockpro.ui.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The alarm list split the way the screen shows it. */
data class AlarmSections(
    val temporary: List<Alarm> = emptyList(),
    val regular: List<Alarm> = emptyList(),
    val ended: List<Alarm> = emptyList(),
    val recentLabels: List<String> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty get() = temporary.isEmpty() && regular.isEmpty() && ended.isEmpty()

    /** The soonest ring, counting snoozes. */
    val next: Pair<Alarm, Long>? get() = (temporary + regular)
        .mapNotNull { a -> listOfNotNull(a.snoozedUntil, a.nextTriggerAt).minOrNull()?.let { a to it } }
        .minByOrNull { it.second }
}

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.alarms

    val sections: StateFlow<AlarmSections> = repo.observe().map { all ->
        val active = all.filter { !it.isEnded }
        AlarmSections(
            temporary = active.filter { it.temporary }.sortedBy { it.nextTriggerAt ?: Long.MAX_VALUE },
            regular = active.filter { !it.temporary },
            ended = all.filter { it.isEnded }.sortedByDescending { it.endedAt },
            recentLabels = all.sortedByDescending { it.createdAt }.map { it.label.trim() }
                .filter { it.isNotEmpty() }.distinct().take(8),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmSections())

    init {
        viewModelScope.launch {
            repo.purgeExpired()
            // Also repairs alarms lost to a force-stop, which clears AlarmManager without any broadcast.
            repo.rescheduleAll(detectMissed = true)
        }
    }

    fun parse(text: String): QuickAddResult = QuickAddParser.parse(text, LocalDateTime.now())

    /** Saves a quick-add result and reports back when it will ring. */
    fun quickAdd(q: QuickAlarm, onSaved: (String) -> Unit) = viewModelScope.launch {
        val saved = repo.save(
            Alarm(
                hour = q.hour, minute = q.minute, days = q.days,
                dateEpochDay = q.date?.toEpochDay(), label = q.label, temporary = q.temporary,
            )
        )
        onSaved(ringsMessage(saved))
    }

    fun save(alarm: Alarm, onSaved: (String) -> Unit) = viewModelScope.launch {
        onSaved(ringsMessage(repo.save(alarm)))
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean, onSaved: (String) -> Unit) = viewModelScope.launch {
        val updated = repo.setEnabled(alarm, enabled)
        if (enabled) onSaved(ringsMessage(updated))
    }

    fun delete(alarm: Alarm) = viewModelScope.launch { repo.delete(alarm) }
    fun undoDelete(alarm: Alarm) = viewModelScope.launch { repo.undoDelete(alarm) }
    fun restore(alarm: Alarm, onSaved: (String) -> Unit) = viewModelScope.launch { onSaved(ringsMessage(repo.restore(alarm))) }
    fun clearEnded() = viewModelScope.launch { repo.clearEnded() }
    fun skipNext(alarm: Alarm) = viewModelScope.launch { repo.skipNext(alarm) }
    fun undoSkip(alarm: Alarm) = viewModelScope.launch { repo.undoSkip(alarm) }

    private fun ringsMessage(alarm: Alarm): String {
        val at = alarm.nextTriggerAt ?: return "Alarm saved"
        return "Alarm set for ${Format.until(at).removePrefix("in ")} from now"
    }
}
