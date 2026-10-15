package io.github.nrupeshsurya.wisp.alarm

import io.github.nrupeshsurya.wisp.data.Alarm
import io.github.nrupeshsurya.wisp.data.AlarmDao
import io.github.nrupeshsurya.wisp.data.EndReason
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * All alarm state changes go through here so the database and AlarmManager never disagree.
 *
 * Lifecycle of a one-time alarm after it rings and is dismissed:
 * - temporary -> moved to "Recently ended" (endedAt set), purged after [ENDED_RETENTION_MS]
 * - permanent -> simply disabled, like a stock clock app
 */
class AlarmRepository(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduling,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(): Flow<List<Alarm>> = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    /** Inserts or updates [alarm] from the editor / quick add. Saving always (re-)arms it. */
    suspend fun save(alarm: Alarm): Alarm {
        val armed = alarm.copy(
            enabled = true,
            endedAt = null,
            endReason = null,
            snoozedUntil = null,
            temporary = alarm.temporary && !alarm.isRepeating,
            dateEpochDay = alarm.dateEpochDay.takeIf { !alarm.isRepeating },
        )
        val id = if (armed.id == 0L) dao.insert(armed) else armed.id.also { dao.update(armed) }
        return reschedule(armed.copy(id = id))
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean): Alarm {
        val updated = alarm.copy(
            enabled = enabled,
            snoozedUntil = null,
            skipUntil = null,
            // A one-time alarm pinned to a past date would never ring again; fall back to "next occurrence".
            dateEpochDay = alarm.dateEpochDay.takeIf { !enabled || pinnedDateStillAhead(alarm) },
        )
        dao.update(updated)
        return reschedule(updated)
    }

    suspend fun delete(alarm: Alarm) {
        scheduler.cancelAll(alarm.id)
        dao.delete(alarm)
    }

    /** Re-inserts an alarm removed by [delete] (snackbar undo). */
    suspend fun undoDelete(alarm: Alarm) {
        dao.insert(alarm.copy(nextTriggerAt = null, snoozedUntil = null))
        reschedule(alarm)
    }

    suspend fun restore(alarm: Alarm): Alarm = save(alarm.copy(dateEpochDay = alarm.dateEpochDay.takeIf { pinnedDateStillAhead(alarm) }))

    suspend fun clearEnded() = dao.clearEnded()

    suspend fun purgeExpired() = dao.purgeEndedBefore(clock() - ENDED_RETENTION_MS)

    /** Skips the next occurrence of a repeating alarm. */
    suspend fun skipNext(alarm: Alarm): Alarm {
        val next = alarm.nextTriggerAt ?: return alarm
        val updated = alarm.copy(skipUntil = next)
        dao.update(updated)
        return reschedule(updated)
    }

    suspend fun undoSkip(alarm: Alarm): Alarm {
        val updated = alarm.copy(skipUntil = null)
        dao.update(updated)
        return reschedule(updated)
    }

    /**
     * Called when AlarmManager fires. Returns the alarm to ring, or null if the trigger is stale
     * (alarm deleted/disabled in the meantime). Repeating alarms are immediately armed for their
     * next occurrence so a crash while ringing can't lose them.
     */
    suspend fun onFired(id: Long, snooze: Boolean): Alarm? {
        val alarm = dao.get(id) ?: return null
        if (!alarm.enabled || alarm.isEnded) return null
        if (snooze && alarm.snoozedUntil == null) return null

        val updated = if (alarm.isRepeating) {
            // Keep a skip that still points at a future occurrence (e.g. set while this one was snoozed).
            reschedule(alarm.copy(snoozedUntil = null, skipUntil = alarm.skipUntil?.takeIf { it > clock() }))
        } else {
            alarm.copy(snoozedUntil = null, nextTriggerAt = null).also { dao.update(it) }
        }
        return updated
    }

    /** [missed] = the alarm rang until auto-silence without anyone responding. */
    suspend fun dismiss(id: Long, missed: Boolean = false) {
        val alarm = dao.get(id) ?: return
        scheduler.cancel(id, snooze = true)
        val updated = when {
            alarm.isRepeating -> alarm.copy(snoozedUntil = null)
            alarm.temporary -> alarm.copy(
                enabled = false, snoozedUntil = null, nextTriggerAt = null,
                endedAt = clock(), endReason = if (missed) EndReason.MISSED else EndReason.DISMISSED,
            )
            else -> alarm.copy(enabled = false, snoozedUntil = null, nextTriggerAt = null)
        }
        dao.update(updated)
    }

    suspend fun snooze(id: Long, minutes: Int): Long? {
        val alarm = dao.get(id) ?: return null
        val until = clock() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        dao.update(alarm.copy(snoozedUntil = until))
        scheduler.schedule(id, until, snooze = true)
        return until
    }

    /**
     * Re-registers everything with AlarmManager (after boot, app update, or a clock/time-zone change).
     *
     * With [detectMissed] (AlarmManager may have lost our alarms: reboot, update, force-stop) an alarm whose
     * time has passed rings right away if it is at most [LATE_RING_WINDOW_MS] late — a short reboot should
     * not cost you a wake-up — and is reported as missed otherwise. Returns the missed alarms.
     */
    suspend fun rescheduleAll(detectMissed: Boolean): List<Alarm> {
        val now = clock()
        val missed = mutableListOf<Alarm>()
        for (alarm in dao.getAll()) {
            if (!alarm.enabled || alarm.isEnded) continue

            val snoozed = alarm.snoozedUntil
            if (snoozed != null && snoozed > now) {
                scheduler.schedule(alarm.id, snoozed, snooze = true)
                // For a one-time alarm the snooze is its only pending ring.
                if (!alarm.isRepeating) continue
            }

            val overdue = (snoozed?.takeIf { it <= now } ?: alarm.nextTriggerAt)?.takeIf { it <= now }
            if (overdue != null) {
                // Without lost alarms, AlarmManager still holds the past-due one and fires it right away.
                if (!detectMissed) continue
                if (overdue >= now - LATE_RING_WINDOW_MS) {
                    // Ring now; onFired() then arms the next occurrence of a repeating alarm.
                    scheduler.schedule(alarm.id, now, snooze = overdue == snoozed)
                    continue
                }
                missed += alarm
                if (!alarm.isRepeating) {
                    dao.update(
                        if (alarm.temporary) alarm.copy(
                            enabled = false, nextTriggerAt = null, snoozedUntil = null,
                            endedAt = now, endReason = EndReason.MISSED,
                        ) else alarm.copy(enabled = false, nextTriggerAt = null, snoozedUntil = null)
                    )
                    continue
                }
                // A repeating alarm stays on: fall through and arm its next occurrence.
            }
            reschedule(alarm.copy(snoozedUntil = snoozed?.takeIf { it > now }))
        }
        return missed
    }

    /** Computes the next occurrence, stores it, and registers it with AlarmManager. */
    private suspend fun reschedule(alarm: Alarm): Alarm {
        scheduler.cancel(alarm.id, snooze = false)
        if (alarm.snoozedUntil == null) scheduler.cancel(alarm.id, snooze = true)
        val next = NextTrigger.compute(alarm, now())?.toInstant()?.toEpochMilli()
        if (next != null) scheduler.schedule(alarm.id, next, snooze = false)
        val updated = alarm.copy(nextTriggerAt = next)
        if (updated != alarm) dao.update(updated)
        return updated
    }

    private fun pinnedDateStillAhead(alarm: Alarm): Boolean {
        val date = alarm.dateEpochDay ?: return false
        return NextTrigger.compute(alarm.copy(enabled = true, endedAt = null, days = 0, dateEpochDay = date), now()) != null
    }

    private fun now(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneId.systemDefault())

    companion object {
        val ENDED_RETENTION_MS = TimeUnit.DAYS.toMillis(7)
        val LATE_RING_WINDOW_MS = TimeUnit.MINUTES.toMillis(10)
    }
}
