package io.github.nrupeshsurya.wisp

import android.app.Application
import android.content.Context
import io.github.nrupeshsurya.wisp.alarm.AlarmRepository
import io.github.nrupeshsurya.wisp.alarm.AlarmScheduler
import io.github.nrupeshsurya.wisp.alarm.Notifications
import io.github.nrupeshsurya.wisp.alarm.TimerRepository
import io.github.nrupeshsurya.wisp.data.AppDatabase
import io.github.nrupeshsurya.wisp.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny manual service locator; the app is small enough not to need a DI framework. */
class App : Application() {
    val prefs by lazy { Prefs(this) }
    val notifications by lazy { Notifications(this) }
    private val db by lazy { AppDatabase.create(this) }
    private val scheduler by lazy { AlarmScheduler(this) }
    val alarms by lazy { AlarmRepository(db.alarms(), scheduler) }
    val timers by lazy { TimerRepository(db.timers(), scheduler, notifications) }

    /** For short work that must outlive the screen that started it. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun canScheduleExact() = scheduler.canScheduleExact()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
    }
}

val Context.app: App get() = applicationContext as App
