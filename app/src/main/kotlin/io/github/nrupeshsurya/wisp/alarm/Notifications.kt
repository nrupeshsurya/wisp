package io.github.nrupeshsurya.wisp.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.nrupeshsurya.wisp.MainActivity
import io.github.nrupeshsurya.wisp.R
import io.github.nrupeshsurya.wisp.data.Alarm
import io.github.nrupeshsurya.wisp.data.Timer
import io.github.nrupeshsurya.wisp.ui.Format

class Notifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                // Sound is played by RingService itself so it can loop, ramp up and use the alarm stream.
                NotificationChannel(CH_RINGING, "Ringing alarms & timers", NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null); enableVibration(false) },
                NotificationChannel(CH_STATUS, "Snoozed alarms, running timers & stopwatch", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_MISSED, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    fun areEnabled() = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canUseFullScreen() = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()

    /** [quiet] = the ring screen is already showing, so don't also pop a heads-up over it. */
    fun ringing(
        title: String,
        text: String,
        fullScreen: PendingIntent,
        actions: List<Pair<String, PendingIntent>>,
        quiet: Boolean = false,
    ): Notification =
        NotificationCompat.Builder(context, if (quiet) CH_STATUS else CH_RINGING)
            .setSmallIcon(R.drawable.ic_alarm_vector)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .apply { if (!quiet) setFullScreenIntent(fullScreen, true) }
            .setContentIntent(fullScreen)
            .apply { actions.forEach { (label, pi) -> addAction(0, label, pi) } }
            .build()

    fun showSnoozed(alarm: Alarm, until: Long) {
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_alarm_vector)
            .setContentTitle("Snoozed" + alarm.label.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
            .setContentText("Rings again at ${Format.time(context, until)}")
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, "Dismiss", ActionReceiver.intent(context, ActionReceiver.DISMISS_SNOOZED, alarm.id))
            .build()
        notifySafely(snoozeId(alarm.id), n)
    }

    fun cancelSnoozed(alarmId: Long) = manager.cancel(snoozeId(alarmId))

    fun showMissed(alarm: Alarm) {
        val time = Format.time(context, alarm.hour, alarm.minute)
        val n = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_alarm_off_vector)
            .setContentTitle("Missed alarm · $time")
            .setContentText(alarm.label.ifBlank { "The phone was off when this alarm was due" })
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notifySafely(MISSED_BASE + alarm.id.toInt(), n)
    }

    fun showTimerRunning(timer: Timer) {
        val endsAt = timer.endsAt ?: return
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_hourglass_vector)
            .setContentTitle(timer.label.ifBlank { "Timer" })
            .setWhen(endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(openApp(MainActivity.TAB_TIMER))
            .build()
        notifySafely(TIMER_BASE + timer.id.toInt(), n)
    }

    fun cancelTimer(timerId: Long) = manager.cancel(TIMER_BASE + timerId.toInt())

    fun showStopwatch(startedAtWallClock: Long) {
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stopwatch_vector)
            .setContentTitle("Stopwatch")
            .setWhen(startedAtWallClock)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(openApp(MainActivity.TAB_STOPWATCH))
            .build()
        notifySafely(STOPWATCH_ID, n)
    }

    fun cancelStopwatch() = manager.cancel(STOPWATCH_ID)

    private fun openApp(tab: Int = MainActivity.TAB_ALARMS): PendingIntent =
        PendingIntent.getActivity(
            context, tab,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notifySafely(id: Int, n: Notification) {
        if (areEnabled()) manager.notify(id, n)
    }

    private fun snoozeId(alarmId: Long) = SNOOZE_BASE + alarmId.toInt()

    companion object {
        const val CH_RINGING = "ringing"
        const val CH_STATUS = "status"
        const val CH_MISSED = "missed"
        const val RINGING_ID = 1
        private const val STOPWATCH_ID = 2
        private const val SNOOZE_BASE = 1_000
        private const val TIMER_BASE = 2_000_000
        private const val MISSED_BASE = 3_000_000
    }
}
