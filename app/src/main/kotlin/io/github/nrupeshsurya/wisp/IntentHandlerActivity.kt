package io.github.nrupeshsurya.wisp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import io.github.nrupeshsurya.wisp.data.Alarm
import io.github.nrupeshsurya.wisp.data.Days
import io.github.nrupeshsurya.wisp.ui.Format
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.Calendar

/**
 * Handles the standard AlarmClock intents (SET_ALARM, SET_TIMER) that assistants and other apps send.
 * One-time alarms created this way are temporary by default.
 *
 * Has no UI (windowNoDisplay), so it must finish() before onResume: the save runs in the app scope.
 */
class IntentHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) setAlarm(intent) else openApp(MainActivity.TAB_ALARMS)
            AlarmClock.ACTION_SET_TIMER -> if (intent.hasExtra(AlarmClock.EXTRA_LENGTH)) setTimer(intent) else openApp(MainActivity.TAB_TIMER)
            else -> openApp(MainActivity.TAB_ALARMS)
        }
    }

    private fun setAlarm(intent: Intent) {
        val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty()
            .fold(0) { mask, calendarDay -> mask or Days.bit(calendarDayToDayOfWeek(calendarDay)) }
        val alarm = Alarm(
            hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0).coerceIn(0, 23),
            minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59),
            days = days,
            label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty(),
            vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true),
            temporary = days == 0,
        )
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        app.scope.launch {
            val at = app.alarms.save(alarm).nextTriggerAt
            if (at != null) toast("Alarm set for ${Format.until(at).removePrefix("in ")} from now")
        }
        if (!skipUi) openApp(MainActivity.TAB_ALARMS) else finish()
    }

    private fun setTimer(intent: Intent) {
        val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0).coerceIn(1, 24 * 3600)
        val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        app.scope.launch { app.timers.createAndStart(seconds * 1000L, label) }
        if (!skipUi) openApp(MainActivity.TAB_TIMER) else finish()
    }

    private fun openApp(tab: Int) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()

    private fun calendarDayToDayOfWeek(day: Int): DayOfWeek = when (day) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }
}
