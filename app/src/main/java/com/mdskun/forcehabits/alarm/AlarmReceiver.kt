package com.mdskun.forcehabits.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.mdskun.forcehabits.data.db.HabitDatabase
import com.mdskun.forcehabits.data.model.ScheduleFrequency
import com.mdskun.forcehabits.lock.LockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // ── Reboot: reschedule all alarms ──
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db        = HabitDatabase.getInstance(context)
                    val scheduler = AlarmScheduler(context)
                    db.habitDao().getAllActiveHabits().first().forEach { scheduler.schedule(it) }
                } finally { pending.finish() }
            }
            return
        }

        val habitId      = intent.getLongExtra("habit_id", -1L)
        val habitName    = intent.getStringExtra("habit_name") ?: "Habit"
        val sessionIndex = intent.getIntExtra("session_index", 0)

        // Start alarm sound (ForegroundService)
        context.startForegroundService(
            Intent(context, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_START_ALARM
                putExtra("habit_id",   habitId)
                putExtra("habit_name", habitName)
            }
        )

        // Show lock overlay
        if (Settings.canDrawOverlays(context)) {
            context.startActivity(
                Intent(context, LockOverlayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("habit_id",      habitId)
                    putExtra("habit_name",    habitName)
                    putExtra("session_index", sessionIndex)
                }
            )
        }

        // Reschedule next occurrence
        CoroutineScope(Dispatchers.IO).launch {
            val db    = HabitDatabase.getInstance(context)
            val habit = db.habitDao().getHabitById(habitId) ?: return@launch
            val scheduler = AlarmScheduler(context)

            when (habit.frequency) {
                // Daily / Every-X-days / Weekly reschedule themselves to next occurrence
                ScheduleFrequency.DAILY,
                ScheduleFrequency.EVERY_X_DAYS,
                ScheduleFrequency.WEEKLY -> scheduler.schedule(habit)

                // Every-X-hours: schedule the NEXT slot from now
                ScheduleFrequency.EVERY_X_HOURS -> {
                    val nextMs = System.currentTimeMillis() + habit.intervalHours * 60 * 60 * 1000L
                    // Use internal helper via reflection isn't clean — just schedule() which finds next slot
                    scheduler.schedule(habit)
                }

                ScheduleFrequency.SCREEN_LIMIT -> { /* no alarm */ }
            }
        }
    }
}
