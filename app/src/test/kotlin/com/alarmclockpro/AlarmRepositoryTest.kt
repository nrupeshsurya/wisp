package com.alarmclockpro

import com.alarmclockpro.alarm.AlarmRepository
import com.alarmclockpro.alarm.AlarmScheduling
import com.alarmclockpro.data.Alarm
import com.alarmclockpro.data.AlarmDao
import com.alarmclockpro.data.Days
import com.alarmclockpro.data.EndReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/** Lifecycle rules of temporary / repeating / snoozed alarms, against in-memory fakes. */
class AlarmRepositoryTest {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
    }

    private val zone = ZoneId.of("Europe/London")
    private fun at(h: Int, m: Int, day: Int = 15) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private var now = at(10, 0) // Thursday
    private val dao = FakeDao()
    private val scheduler = FakeScheduler()
    private val repo = AlarmRepository(dao, scheduler) { now }
    private val minute = 60_000L

    @Test fun savingArmsTheNextOccurrence() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        assertEquals(at(11, 0), a.nextTriggerAt)
        assertEquals(at(11, 0), scheduler.pending[a.id to false])
    }

    @Test fun temporaryAlarmMovesToRecentlyEndedAfterDismiss() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        now = at(11, 0)
        assertNotNull(repo.onFired(a.id, snooze = false))
        repo.dismiss(a.id)
        val ended = dao.get(a.id)!!
        assertTrue(ended.isEnded)
        assertFalse(ended.enabled)
        assertEquals(EndReason.DISMISSED, ended.endReason)
    }

    @Test fun permanentOneTimeAlarmIsOnlyDisabled() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = false))
        now = at(11, 0)
        repo.onFired(a.id, snooze = false)
        repo.dismiss(a.id)
        val after = dao.get(a.id)!!
        assertFalse(after.enabled)
        assertFalse(after.isEnded)
    }

    @Test fun repeatingAlarmIsArmedForTomorrowAsSoonAsItFires() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, days = Days.WEEKDAYS))
        now = at(11, 0)
        repo.onFired(a.id, snooze = false)
        assertEquals(at(11, 0, day = 16), scheduler.pending[a.id to false])
        repo.dismiss(a.id)
        assertTrue(dao.get(a.id)!!.enabled)
    }

    @Test fun repeatingAlarmIsNeverTemporary() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, days = Days.WEEKDAYS, temporary = true))
        assertFalse(a.temporary)
    }

    @Test fun snoozeThenRingAgainThenDismissEnds() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        now = at(11, 0)
        repo.onFired(a.id, snooze = false)
        repo.snooze(a.id, 10)
        assertEquals(at(11, 10), scheduler.pending[a.id to true])
        assertFalse(dao.get(a.id)!!.isEnded)
        now = at(11, 10)
        assertNotNull(repo.onFired(a.id, snooze = true))
        repo.dismiss(a.id)
        assertTrue(dao.get(a.id)!!.isEnded)
    }

    @Test fun staleSnoozeTriggerIsIgnored() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0))
        assertNull(repo.onFired(a.id, snooze = true))
    }

    @Test fun skipNextMovesToFollowingDay() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, days = Days.WEEKDAYS))
        val skipped = repo.skipNext(a)
        assertEquals(at(11, 0, day = 16), skipped.nextTriggerAt)
        assertEquals(at(11, 0), repo.undoSkip(skipped).nextTriggerAt)
    }

    @Test fun restoreReArmsAnEndedAlarm() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        now = at(11, 0); repo.onFired(a.id, false); repo.dismiss(a.id)
        val restored = repo.restore(dao.get(a.id)!!)
        assertFalse(restored.isEnded)
        assertEquals(at(11, 0, day = 16), restored.nextTriggerAt)
    }

    @Test fun purgeRemovesOnlyAlarmsEndedOverAWeekAgo() = runBlocking {
        val old = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        now = at(11, 0); repo.onFired(old.id, false); repo.dismiss(old.id)
        val recent = repo.save(Alarm(hour = 12, minute = 0, temporary = true))
        now = at(12, 0, day = 21); repo.onFired(recent.id, false); repo.dismiss(recent.id)
        now = at(12, 0, day = 22)
        repo.purgeExpired()
        assertNull(dao.get(old.id))
        assertNotNull(dao.get(recent.id))
    }

    // --- Reboot handling ---

    @Test fun afterRebootFutureAlarmsAreReArmed() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        scheduler.pending.clear() // reboot wipes AlarmManager
        assertTrue(repo.rescheduleAll(detectMissed = true).isEmpty())
        assertEquals(at(11, 0), scheduler.pending[a.id to false])
    }

    @Test fun afterRebootSlightlyLateAlarmRingsNow() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        scheduler.pending.clear()
        now = at(11, 3)
        assertTrue(repo.rescheduleAll(detectMissed = true).isEmpty())
        assertEquals(now, scheduler.pending[a.id to false])
        assertFalse(dao.get(a.id)!!.isEnded)
    }

    @Test fun afterRebootLongOverdueTemporaryAlarmIsMissed() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        scheduler.pending.clear()
        now = at(11, 30)
        assertEquals(listOf(a.id), repo.rescheduleAll(detectMissed = true).map { it.id })
        val after = dao.get(a.id)!!
        assertEquals(EndReason.MISSED, after.endReason)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test fun afterRebootLongOverdueRepeatingAlarmIsReportedButKept() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, days = Days.WEEKDAYS))
        scheduler.pending.clear()
        now = at(11, 30)
        assertEquals(1, repo.rescheduleAll(detectMissed = true).size)
        assertTrue(dao.get(a.id)!!.enabled)
        assertEquals(at(11, 0, day = 16), scheduler.pending[a.id to false])
    }

    @Test fun afterRebootPendingSnoozeIsKept() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        now = at(11, 0); repo.onFired(a.id, false); repo.snooze(a.id, 10)
        scheduler.pending.clear()
        now = at(11, 5)
        repo.rescheduleAll(detectMissed = true)
        assertEquals(at(11, 10), scheduler.pending[a.id to true])
        assertNull(scheduler.pending[a.id to false])
    }

    @Test fun clockChangeDoesNotMarkMissed() = runBlocking {
        val a = repo.save(Alarm(hour = 11, minute = 0, temporary = true))
        now = at(11, 30) // AlarmManager still holds it and will fire it
        assertTrue(repo.rescheduleAll(detectMissed = false).isEmpty())
        assertFalse(dao.get(a.id)!!.isEnded)
    }
}

private class FakeScheduler : AlarmScheduling {
    val pending = mutableMapOf<Pair<Long, Boolean>, Long>()
    override fun schedule(alarmId: Long, triggerAt: Long, snooze: Boolean) { pending[alarmId to snooze] = triggerAt }
    override fun cancel(alarmId: Long, snooze: Boolean) { pending.remove(alarmId to snooze) }
}

private class FakeDao : AlarmDao {
    private val rows = MutableStateFlow<Map<Long, Alarm>>(emptyMap())
    private var nextId = 1L
    override fun observeAll(): Flow<List<Alarm>> = throw UnsupportedOperationException()
    override suspend fun getAll() = rows.value.values.toList()
    override suspend fun get(id: Long) = rows.value[id]
    override suspend fun insert(alarm: Alarm): Long {
        val id = if (alarm.id != 0L) alarm.id else nextId++
        rows.value = rows.value + (id to alarm.copy(id = id))
        return id
    }
    override suspend fun update(alarm: Alarm) { rows.value = rows.value + (alarm.id to alarm) }
    override suspend fun delete(alarm: Alarm) { rows.value = rows.value - alarm.id }
    override suspend fun purgeEndedBefore(before: Long): Int {
        val doomed = rows.value.values.filter { (it.endedAt ?: Long.MAX_VALUE) < before }
        rows.value = rows.value - doomed.map { it.id }.toSet()
        return doomed.size
    }
    override suspend fun clearEnded() { rows.value = rows.value.filterValues { it.endedAt == null } }
}
