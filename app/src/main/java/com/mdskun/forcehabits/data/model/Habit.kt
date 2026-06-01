package com.mdskun.forcehabits.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class HabitType {
    EXERCISE, PLANT_CARE, HYDRATION, READING,
    SCREEN_LIMIT, CUSTOM
}

enum class ProofType {
    POSE_SEQUENCE,
    PHOTO_LABEL,
    MULTI_PHOTO_TEXT,
    SCREEN_TIME,
    NONE
}

// ── Schedule frequency ──────────────────────────────────────────
// DAILY        → fires every day at a single time
// EVERY_X_HOURS→ fires every N hours from a start time (e.g. every 4h from 8AM)
// EVERY_X_DAYS → fires every N days at a fixed time (e.g. every 2 days)
// WEEKLY       → fires on specific days of the week at a fixed time
//                weekDays encoded as comma-separated ints: "1,3,5" = Mon,Wed,Fri (Calendar.DAY_OF_WEEK)
// SCREEN_LIMIT → no alarm, accessibility service enforces
enum class ScheduleFrequency {
    DAILY,
    EVERY_X_HOURS,
    EVERY_X_DAYS,
    WEEKLY,
    SCREEN_LIMIT
}

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val type: HabitType,
    val proofType: ProofType,

    // ── Schedule ────────────────────────────────────────────────
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,

    // The base reminder time (used by DAILY, EVERY_X_DAYS, WEEKLY)
    val reminderHour: Int   = 8,
    val reminderMinute: Int = 0,

    // EVERY_X_HOURS: interval (e.g. 4 = every 4 hours)
    val intervalHours: Int  = 4,

    // EVERY_X_DAYS: interval (e.g. 2 = every other day)
    val intervalDays: Int   = 1,

    // WEEKLY: comma-separated Calendar.DAY_OF_WEEK values e.g. "2,4,6" = Mon,Wed,Fri
    val weekDays: String    = "2,3,4,5,6", // Mon–Fri by default

    // ── Completion tracking ──────────────────────────────────────
    val isActive: Boolean   = true,
    val streakCount: Int    = 0,
    val lastCompletedDate: String = "",   // yyyy-MM-dd

    // ── Proof config JSON ────────────────────────────────────────
    val proofConfig: String = "{}",

    // ── Screen limit fields ─────────────────────────────────────
    val blockStartHour: Int   = 22,
    val blockStartMinute: Int = 30,
    val blockEndHour: Int     = 6,
    val blockEndMinute: Int   = 0,
    val dailyUsageLimitMinutes: Int = 0
) {
    // Parse weekDays string → list of Calendar.DAY_OF_WEEK ints
    fun getParsedWeekDays(): List<Int> =
        weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }

    // Human-readable schedule summary for dashboard display
    fun scheduleDisplayText(): String = when (frequency) {
        ScheduleFrequency.DAILY ->
            "Daily at ${formatTime(reminderHour, reminderMinute)}"
        ScheduleFrequency.EVERY_X_HOURS ->
            "Every ${intervalHours}h from ${formatTime(reminderHour, reminderMinute)}"
        ScheduleFrequency.EVERY_X_DAYS ->
            "Every ${intervalDays} day${if (intervalDays > 1) "s" else ""} at ${formatTime(reminderHour, reminderMinute)}"
        ScheduleFrequency.WEEKLY -> {
            val dayNames = mapOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu",
                6 to "Fri", 7 to "Sat", 1 to "Sun")
            val days = getParsedWeekDays().mapNotNull { dayNames[it] }.joinToString(", ")
            "$days at ${formatTime(reminderHour, reminderMinute)}"
        }
        ScheduleFrequency.SCREEN_LIMIT -> {
            buildString {
                if (blockStartHour != blockEndHour || blockStartMinute != blockEndMinute)
                    append("🌙 ${formatTime(blockStartHour, blockStartMinute)}–${formatTime(blockEndHour, blockEndMinute)}")
                if (dailyUsageLimitMinutes > 0) {
                    if (isNotEmpty()) append("  ")
                    append("⏱️ ${dailyUsageLimitMinutes}min/day")
                }
                if (isEmpty()) append("Auto-enforced")
            }
        }
    }
}

// Shared formatting helper (also used in DashboardScreen)
fun formatTime(hour: Int, minute: Int): String {
    val h   = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val m   = minute.toString().padStart(2, '0')
    val apm = if (hour < 12) "AM" else "PM"
    return "$h:$m $apm"
}
