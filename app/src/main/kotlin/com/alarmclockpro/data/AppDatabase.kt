package com.alarmclockpro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Alarm::class, Timer::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun timers(): TimerDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "clock.db").build()
    }
}
