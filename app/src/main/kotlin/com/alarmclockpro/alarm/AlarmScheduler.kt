package com.alarmclockpro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.alarmclockpro.MainActivity

/** What [AlarmRepository] needs from AlarmManager; an interface so lifecycle rules can be unit-tested. */
interface AlarmScheduling {
    fun schedule(alarmId: Long, triggerAt: Long, snooze: Boolean)
    fun cancel(alarmId: Long, snooze: Boolean)
    fun cancelAll(alarmId: Long) {
        cancel(alarmId, snooze = false)
        cancel(alarmId, snooze = true)
    }
}

/**
 * Thin wrapper over AlarmManager. Every alarm uses setAlarmClock(), the only API that is exempt from
 * Doze and shows the alarm icon in the status bar. Request codes: alarm id * 2 for the regular
 * occurrence, id * 2 + 1 for a snooze, so both can be pending at once.
 */
class AlarmScheduler(private val context: Context) : AlarmScheduling {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    override fun schedule(alarmId: Long, triggerAt: Long, snooze: Boolean) {
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent())
        alarmManager.setAlarmClock(info, fireIntent(alarmId, snooze))
    }

    override fun cancel(alarmId: Long, snooze: Boolean) {
        alarmManager.cancel(fireIntent(alarmId, snooze))
    }

    /** Timers are not "alarm clocks" for the status bar, but still need to fire exactly. */
    fun scheduleTimer(timerId: Long, triggerAt: Long) {
        val pi = timerIntent(timerId)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelTimer(timerId: Long) = alarmManager.cancel(timerIntent(timerId))

    private fun fireIntent(alarmId: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE_ALARM)
            .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        val code = (alarmId * 2 + if (snooze) 1 else 0).toInt()
        return PendingIntent.getBroadcast(context, code, intent, FLAGS)
    }

    private fun timerIntent(timerId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE_TIMER)
            .putExtra(AlarmReceiver.EXTRA_ID, timerId)
        return PendingIntent.getBroadcast(context, TIMER_CODE_BASE + timerId.toInt(), intent, FLAGS)
    }

    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), FLAGS)

    private companion object {
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        const val TIMER_CODE_BASE = 1_000_000_000
    }
}
