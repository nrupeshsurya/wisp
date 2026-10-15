package io.github.nrupeshsurya.wisp.alarm

import io.github.nrupeshsurya.wisp.data.Timer
import io.github.nrupeshsurya.wisp.data.TimerDao
import io.github.nrupeshsurya.wisp.data.TimerState
import kotlinx.coroutines.flow.Flow

class TimerRepository(
    private val dao: TimerDao,
    private val scheduler: AlarmScheduler,
    private val notifications: Notifications,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(): Flow<List<Timer>> = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    /** Creates a timer and starts it straight away — that's what you want 99% of the time. */
    suspend fun createAndStart(durationMs: Long, label: String) {
        val id = dao.insert(Timer(label = label, durationMs = durationMs))
        start(dao.get(id) ?: return)
    }

    suspend fun start(timer: Timer) {
        val endsAt = clock() + timer.remainingAt(clock()).let { if (it == 0L) timer.durationMs else it }
        val updated = timer.copy(state = TimerState.RUNNING, endsAt = endsAt, remainingMs = null)
        dao.update(updated)
        scheduler.scheduleTimer(timer.id, endsAt)
        notifications.showTimerRunning(updated)
    }

    suspend fun pause(timer: Timer) {
        scheduler.cancelTimer(timer.id)
        dao.update(timer.copy(state = TimerState.PAUSED, remainingMs = timer.remainingAt(clock()), endsAt = null))
        notifications.cancelTimer(timer.id)
    }

    suspend fun reset(timer: Timer) {
        scheduler.cancelTimer(timer.id)
        dao.update(timer.copy(state = TimerState.IDLE, endsAt = null, remainingMs = null))
        notifications.cancelTimer(timer.id)
    }

    suspend fun addMinute(timer: Timer) {
        val minute = 60_000L
        when (timer.state) {
            TimerState.RUNNING -> {
                val updated = timer.copy(endsAt = (timer.endsAt ?: clock()) + minute)
                dao.update(updated)
                scheduler.scheduleTimer(timer.id, updated.endsAt!!)
                notifications.showTimerRunning(updated)
            }
            TimerState.PAUSED -> dao.update(timer.copy(remainingMs = (timer.remainingMs ?: 0) + minute))
            TimerState.FINISHED -> start(timer.copy(state = TimerState.PAUSED, remainingMs = minute))
            TimerState.IDLE -> dao.update(timer.copy(durationMs = timer.durationMs + minute))
        }
    }

    suspend fun delete(timer: Timer) {
        scheduler.cancelTimer(timer.id)
        notifications.cancelTimer(timer.id)
        dao.delete(timer)
    }

    /** Returns the timer to ring, or null if the trigger is stale. */
    suspend fun onFired(id: Long): Timer? {
        val timer = dao.get(id) ?: return null
        if (timer.state != TimerState.RUNNING) return null
        val finished = timer.copy(state = TimerState.FINISHED, endsAt = null)
        dao.update(finished)
        notifications.cancelTimer(id)
        return finished
    }

    /** Stopping a finished timer resets it so it can be reused with one tap. */
    suspend fun stop(id: Long) {
        val timer = dao.get(id) ?: return
        if (timer.state == TimerState.FINISHED) dao.update(timer.copy(state = TimerState.IDLE))
    }

    /** After a reboot AlarmManager forgets everything; re-arm running timers (or finish overdue ones). */
    suspend fun rescheduleAll(): List<Timer> {
        val overdue = mutableListOf<Timer>()
        for (timer in dao.getAll()) {
            if (timer.state != TimerState.RUNNING) continue
            val endsAt = timer.endsAt ?: continue
            if (endsAt <= clock()) {
                overdue += timer
            } else {
                scheduler.scheduleTimer(timer.id, endsAt)
                notifications.showTimerRunning(timer)
            }
        }
        return overdue
    }
}
