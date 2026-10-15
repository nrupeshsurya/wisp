package com.alarmclockpro.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmclockpro.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the receiver alive until it completes. */
private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fired by AlarmManager. Hands straight over to [RingService] (setAlarmClock allows a background FGS start). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) return
        when (intent.action) {
            ACTION_FIRE_ALARM -> RingService.ring(context, id, isTimer = false, snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false))
            ACTION_FIRE_TIMER -> RingService.ring(context, id, isTimer = true)
        }
    }

    companion object {
        const val ACTION_FIRE_ALARM = "com.alarmclockpro.FIRE_ALARM"
        const val ACTION_FIRE_TIMER = "com.alarmclockpro.FIRE_TIMER"
        const val EXTRA_ID = "id"
        const val EXTRA_SNOOZE = "snooze"
    }
}

/** Re-arms everything after boot, app update, or a clock / time-zone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // AlarmManager forgets our alarms on reboot and on app update; a time change keeps them.
        val alarmsWereLost = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        val app = context.app
        launchAsync {
            app.alarms.purgeExpired()
            app.alarms.rescheduleAll(detectMissed = alarmsWereLost).forEach { app.notifications.showMissed(it) }
            app.timers.rescheduleAll().forEach { RingService.ring(context, it.id, isTimer = true) }
        }
    }
}

/** Notification actions that work while nothing is ringing (e.g. dismissing a snoozed alarm). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val app = context.app
        when (intent.action) {
            DISMISS_SNOOZED -> launchAsync {
                app.notifications.cancelSnoozed(id)
                app.alarms.dismiss(id)
            }
        }
    }

    companion object {
        const val DISMISS_SNOOZED = "com.alarmclockpro.DISMISS_SNOOZED"
        private const val EXTRA_ID = "id"

        fun intent(context: Context, action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
