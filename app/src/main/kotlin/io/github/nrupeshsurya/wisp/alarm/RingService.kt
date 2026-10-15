package io.github.nrupeshsurya.wisp.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.nrupeshsurya.wisp.app
import io.github.nrupeshsurya.wisp.ui.Format
import io.github.nrupeshsurya.wisp.ui.ring.RingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service that rings one alarm or timer at a time: loops the sound on the alarm stream
 * (optionally ramping up), vibrates, shows the full-screen [RingActivity], and auto-silences.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING_ALARM, ACTION_RING_TIMER -> {
                // Must enter the foreground immediately; the details are filled in once loaded.
                goForeground("Alarm", "")
                val isTimer = intent.action == ACTION_RING_TIMER
                val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                scope.launch { startRinging(id, isTimer, snooze) }
            }
            ACTION_DISMISS -> scope.launch { finish(snoozeMinutes = null) }
            ACTION_SNOOZE -> scope.launch { finish(snoozeMinutes = app.prefs.snoozeMinutes) }
            ACTION_ADD_MINUTE -> scope.launch { addMinuteToTimer() }
            ACTION_UI_VISIBLE -> Ringing.current.value?.let { goForeground(title(it), subtitle(it), quiet = true) }
            else -> if (Ringing.current.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun startRinging(id: Long, isTimer: Boolean, snooze: Boolean) {
        val target = if (isTimer) {
            app.timers.onFired(id)?.let { RingTarget.TimerRing(it) }
        } else {
            app.alarms.onFired(id, snooze)?.let { RingTarget.AlarmRing(it) }
        }
        if (target == null) {
            if (Ringing.current.value == null) stopService()
            return
        }
        // One thing rings at a time: a new alarm ends whatever was ringing before.
        Ringing.current.value?.let { endCurrent(it, snoozeMinutes = null) }

        Ringing.set(target)
        if (target is RingTarget.AlarmRing) app.notifications.cancelSnoozed(target.alarm.id)
        goForeground(title(target), subtitle(target))
        startSound(target)
        startVibration(target)
        startActivity(RingActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        ringJob?.cancel()
        ringJob = scope.launch {
            delay(TimeUnit.MINUTES.toMillis(app.prefs.autoSilenceMinutes.toLong()))
            finish(snoozeMinutes = null, missed = true)
        }
    }

    private suspend fun finish(snoozeMinutes: Int?, missed: Boolean = false) {
        val target = Ringing.current.value
        if (target != null) endCurrent(target, snoozeMinutes, missed)
        stopService()
    }

    private suspend fun endCurrent(target: RingTarget, snoozeMinutes: Int?, missed: Boolean = false) {
        stopSoundAndVibration()
        ringJob?.cancel()
        when (target) {
            is RingTarget.AlarmRing -> {
                val until = snoozeMinutes?.let { app.alarms.snooze(target.alarm.id, it) }
                if (until != null) app.notifications.showSnoozed(target.alarm, until)
                else app.alarms.dismiss(target.alarm.id, missed)
            }
            is RingTarget.TimerRing -> app.timers.stop(target.timer.id)
        }
        Ringing.set(null)
    }

    private suspend fun addMinuteToTimer() {
        val target = Ringing.current.value as? RingTarget.TimerRing ?: return
        stopSoundAndVibration()
        ringJob?.cancel()
        app.timers.get(target.timer.id)?.let { app.timers.addMinute(it) }
        Ringing.set(null)
        stopService()
    }

    private fun goForeground(title: String, text: String, quiet: Boolean = false) {
        val fullScreen = PendingIntent.getActivity(
            this, 0, RingActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actions = when (Ringing.current.value) {
            is RingTarget.TimerRing -> listOf("Stop" to control(ACTION_DISMISS), "+1 min" to control(ACTION_ADD_MINUTE))
            else -> listOf("Snooze" to control(ACTION_SNOOZE), "Dismiss" to control(ACTION_DISMISS))
        }
        val notification = app.notifications.ringing(title, text, fullScreen, actions, quiet)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.RINGING_ID, notification, type)
    }

    private fun control(action: String) = PendingIntent.getService(
        this, action.hashCode(), Intent(this, RingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun startSound(target: RingTarget) {
        val (uriString, gradual) = when (target) {
            is RingTarget.AlarmRing -> target.alarm.soundUri to app.prefs.gradualVolume
            is RingTarget.TimerRing -> null to false
        }
        if (uriString == "") return // silent
        val candidates = listOfNotNull(
            uriString?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val mp = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setWakeMode(this@RingService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@RingService, uri)
                    isLooping = true
                    prepare()
                }
            }.getOrNull() ?: continue
            player = mp
            if (gradual) mp.setVolume(MIN_VOLUME, MIN_VOLUME)
            mp.start()
            if (gradual) scope.launch { rampVolume(mp) }
            return
        }
    }

    private suspend fun rampVolume(mp: MediaPlayer) {
        val steps = 30
        for (i in 1..steps) {
            delay(RAMP_MS / steps)
            if (player !== mp) return
            val v = MIN_VOLUME + (1f - MIN_VOLUME) * i / steps
            mp.setVolume(v, v)
        }
    }

    private fun startVibration(target: RingTarget) {
        val shouldVibrate = when (target) {
            is RingTarget.AlarmRing -> target.alarm.vibrate
            is RingTarget.TimerRing -> true
        }
        if (!shouldVibrate) return
        val v = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0), attrs)
    }

    private fun stopSoundAndVibration() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSoundAndVibration()
        scope.cancel()
        super.onDestroy()
    }

    private fun title(target: RingTarget) = when (target) {
        is RingTarget.AlarmRing -> target.alarm.label.ifBlank { "Alarm" }
        is RingTarget.TimerRing -> target.timer.label.ifBlank { "Time's up" }
    }

    private fun subtitle(target: RingTarget) = when (target) {
        is RingTarget.AlarmRing -> Format.time(this, target.alarm.hour, target.alarm.minute)
        is RingTarget.TimerRing -> "Timer · " + Format.duration(target.timer.durationMs)
    }

    companion object {
        const val ACTION_RING_ALARM = "ring_alarm"
        const val ACTION_RING_TIMER = "ring_timer"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_SNOOZE = "snooze"
        const val ACTION_ADD_MINUTE = "add_minute"
        const val ACTION_UI_VISIBLE = "ui_visible"
        const val EXTRA_ID = "id"
        const val EXTRA_SNOOZE = "snooze"
        private const val MIN_VOLUME = 0.1f
        private val RAMP_MS = TimeUnit.SECONDS.toMillis(30)

        fun ring(context: Context, id: Long, isTimer: Boolean, snooze: Boolean = false) {
            val intent = Intent(context, RingService::class.java)
                .setAction(if (isTimer) ACTION_RING_TIMER else ACTION_RING_ALARM)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SNOOZE, snooze)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Dismiss/snooze/stop from the ring screen. The service is foreground, so a plain start is allowed. */
        fun control(context: Context, action: String) {
            context.startService(Intent(context, RingService::class.java).setAction(action))
        }
    }
}
