package com.mdskun.forcehabits.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mdskun.forcehabits.data.model.Habit
import com.mdskun.forcehabits.data.model.ScheduleFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(habit: Habit) {
        when (habit.frequency) {
            ScheduleFrequency.SCREEN_LIMIT -> return // no alarm
            ScheduleFrequency.DAILY        -> scheduleSingle(habit, nextOccurrence(habit.reminderHour, habit.reminderMinute, daysAhead = 0))
            ScheduleFrequency.EVERY_X_DAYS -> scheduleSingle(habit, nextEveryXDays(habit))
            ScheduleFrequency.EVERY_X_HOURS-> scheduleEveryXHours(habit)
            ScheduleFrequency.WEEKLY       -> scheduleWeekly(habit)
        }
    }

    // ── Single one-shot alarm, reschedules itself in AlarmReceiver ──
    private fun scheduleSingle(habit: Habit, triggerAtMs: Long, sessionIndex: Int = 0) {
        val requestCode = encodeRequestCode(habit.id, sessionIndex)
        val pi = buildPendingIntent(habit, sessionIndex, requestCode)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
    }

    // ── EVERY_X_HOURS: schedule first N alarms from reminderHour today ──
    // Each fires once; AlarmReceiver reschedules the next one
    private fun scheduleEveryXHours(habit: Habit) {
        val now = System.currentTimeMillis()
        var cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, habit.reminderHour)
            set(Calendar.MINUTE,      habit.reminderMinute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // Advance to the next upcoming slot
        while (cal.timeInMillis <= now) {
            cal.add(Calendar.HOUR_OF_DAY, habit.intervalHours)
        }
        scheduleSingle(habit, cal.timeInMillis, sessionIndex = 0)
    }

    // ── WEEKLY: one alarm per enabled weekday ──
    private fun scheduleWeekly(habit: Habit) {
        habit.getParsedWeekDays().forEachIndexed { idx, dayOfWeek ->
            val triggerMs = nextWeeklyOccurrence(dayOfWeek, habit.reminderHour, habit.reminderMinute)
            scheduleSingle(habit, triggerMs, sessionIndex = idx)
        }
    }

    // ── Cancel all alarms for a habit ──
    fun cancel(habit: Habit) {
        val slotCount = when (habit.frequency) {
            ScheduleFrequency.WEEKLY       -> habit.getParsedWeekDays().size
            ScheduleFrequency.EVERY_X_HOURS-> 1
            else -> 1
        }
        repeat(slotCount) { idx ->
            val pi = PendingIntent.getBroadcast(
                context,
                encodeRequestCode(habit.id, idx),
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@repeat
            alarmManager.cancel(pi)
        }
    }

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    // ── Helpers ──────────────────────────────────────────────────

    private fun buildPendingIntent(habit: Habit, sessionIndex: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("habit_id",      habit.id)
            putExtra("habit_name",    habit.name)
            putExtra("session_index", sessionIndex)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun encodeRequestCode(habitId: Long, sessionIndex: Int): Int =
        (habitId * 100 + sessionIndex).toInt()

    // Returns the next timestamp for a given HH:MM — today if still in future, else tomorrow
    fun nextOccurrence(hour: Int, minute: Int, daysAhead: Int = 0): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (daysAhead == 0 && cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun nextEveryXDays(habit: Habit): Long {
        // Check last completed: if done today, schedule intervalDays ahead
        val lastDate = habit.lastCompletedDate
        val today = todayString()
        val daysAhead = if (lastDate == today) habit.intervalDays else 0
        return nextOccurrence(habit.reminderHour, habit.reminderMinute, daysAhead)
    }

    private fun nextWeeklyOccurrence(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = cal.get(Calendar.DAY_OF_WEEK)
        var daysUntil = (dayOfWeek - today + 7) % 7
        if (daysUntil == 0 && cal.timeInMillis <= System.currentTimeMillis()) daysUntil = 7
        cal.add(Calendar.DAY_OF_YEAR, daysUntil)
        return cal.timeInMillis
    }

    private fun todayString(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
