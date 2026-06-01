package com.mdskun.forcehabits

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HabitApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // Alarm channel — HIGH importance, no silent override
        nm.createNotificationChannel(
            NotificationChannel(
                "alarm_channel", "Habit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Habit reminder alarms"
                setBypassDnd(true)
                enableVibration(true)
            }
        )

        // Foreground service channel
        nm.createNotificationChannel(
            NotificationChannel(
                "service_channel", "Habit Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps alarm sound running"
            }
        )
    }
}