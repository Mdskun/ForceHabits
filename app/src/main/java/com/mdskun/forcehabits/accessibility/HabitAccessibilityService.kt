package com.mdskun.forcehabits.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.mdskun.forcehabits.lock.LockOverlayActivity
import java.util.Calendar

class HabitAccessibilityService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var lastBlockedPackage = ""
    private var lastBlockTime      = 0L
    private val usageTodayMs       = mutableMapOf<String, Long>()
    private var lastResumedPackage = ""
    private var lastResumeTime     = 0L
    private var lastTrackingDate   = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("habit_prefs", MODE_PRIVATE)
        lastTrackingDate = todayString()
        loadUsageFromPrefs()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (!prefs.getBoolean("screen_limit_enabled", false)) return

        val allBlockedApps = getAllBlockedPackages()
        if (allBlockedApps.isEmpty()) return

        // Midnight reset
        val today = todayString()
        if (today != lastTrackingDate) {
            usageTodayMs.clear(); saveUsageToPrefs(); lastTrackingDate = today
        }

        val now = System.currentTimeMillis()

        // Accumulate time for the previous foreground app if it was blocked
        if (lastResumedPackage.isNotEmpty() && lastResumedPackage in allBlockedApps) {
            val elapsed = now - lastResumeTime
            usageTodayMs[lastResumedPackage] = (usageTodayMs[lastResumedPackage] ?: 0L) + elapsed
            saveUsageToPrefs()
        }
        lastResumedPackage = packageName
        lastResumeTime     = now

        if (packageName !in allBlockedApps) return

        val nightBlocked       = isInNightBlock()
        val limitMins          = prefs.getInt("daily_usage_limit_minutes", 0)
        val usedMs             = usageTodayMs[packageName] ?: 0L
        val usedMins           = (usedMs / 60_000).toInt()
        val usageLimitExceeded = limitMins > 0 && usedMins >= limitMins

        if (!nightBlocked && !usageLimitExceeded) return

        // Debounce — same app within 5 seconds
        if (packageName == lastBlockedPackage && now - lastBlockTime < 5_000) return
        lastBlockedPackage = packageName
        lastBlockTime      = now

        if (!Settings.canDrawOverlays(this)) return

        val blockReason = when {
            nightBlocked && usageLimitExceeded -> "BOTH"
            nightBlocked                       -> "NIGHT_BLOCK"
            else                               -> "USAGE_LIMIT"
        }

        startActivity(Intent(this, LockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("habit_id",         -99L)
            putExtra("habit_name",       "Screen Time Limit")
            putExtra("screen_time_mode", true)
            putExtra("blocked_app",      packageName)
            putExtra("block_reason",     blockReason)
            putExtra("used_minutes",     usedMins)
            putExtra("limit_minutes",    limitMins)
        })
    }

    override fun onInterrupt() {
        val elapsed = System.currentTimeMillis() - lastResumeTime
        if (lastResumedPackage.isNotEmpty() && lastResumedPackage in getAllBlockedPackages()) {
            usageTodayMs[lastResumedPackage] = (usageTodayMs[lastResumedPackage] ?: 0L) + elapsed
            saveUsageToPrefs()
        }
    }

    // Union of every per-habit blocked-apps set stored by setBlockedAppsForHabit().
    // Returns EMPTY SET if nothing was ever configured — means block nothing.
    private fun getAllBlockedPackages(): Set<String> {
        val merged = mutableSetOf<String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("blocked_apps_habit_") && value is Set<*>) {
                @Suppress("UNCHECKED_CAST")
                merged.addAll(value as Set<String>)
            }
        }
        return merged   // no fallback — empty = block nothing
    }

    // Night block: only active when the "night_block_enabled" flag is true
    // AND the current time is within the configured window.
    private fun isInNightBlock(): Boolean {
        if (!prefs.getBoolean("night_block_enabled", false)) return false
        val startH = prefs.getInt("block_start_hour",   22)
        val startM = prefs.getInt("block_start_minute", 30)
        val endH   = prefs.getInt("block_end_hour",     6)
        val endM   = prefs.getInt("block_end_minute",   0)
        val now    = Calendar.getInstance()
        val nowMin   = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMin = startH * 60 + startM
        val endMin   = endH   * 60 + endM
        // Handles overnight windows e.g. 22:30 → 06:00
        return if (startMin > endMin) nowMin >= startMin || nowMin < endMin
               else nowMin in startMin until endMin
    }

    private fun todayString(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun saveUsageToPrefs() {
        val today = todayString(); val e = prefs.edit()
        usageTodayMs.forEach { (pkg, ms) -> e.putLong("usage_${today}_$pkg", ms) }
        e.apply()
    }

    private fun loadUsageFromPrefs() {
        val today = todayString()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("usage_${today}_") && value is Long)
                usageTodayMs[key.removePrefix("usage_${today}_")] = value
        }
    }

    companion object {
        // These are ONLY used as the default pre-selection in the UI picker.
        // They are NEVER used as a fallback for blocking — user must explicitly choose.
        val defaultBlockedPackages: Set<String> = setOf(
            "com.instagram.android", "com.twitter.android",
            "com.facebook.katana",   "com.facebook.android",
            "com.zhiliaoapp.musically", "com.snapchat.android",
            "com.reddit.frontpage",  "com.pinterest",
            "com.linkedin.android",  "com.google.android.youtube",
            "app.rvx.android.youtube", "com.whatsapp", "org.telegram.messenger"
        )

        fun setEnabled(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean("screen_limit_enabled", enabled).apply()
        }

        /**
         * Saves exactly the packages the user chose for this habit.
         * Key: "blocked_apps_habit_{habitId}"
         * The AccessibilityService merges all habit keys at runtime.
         */
        fun setBlockedAppsForHabit(context: Context, habitId: Long, packages: Set<String>) {
            prefs(context).edit()
                .putStringSet("blocked_apps_habit_$habitId", packages)
                .apply()
        }

        fun getBlockedAppsForHabit(context: Context, habitId: Long): Set<String> =
            prefs(context).getStringSet("blocked_apps_habit_$habitId", null) ?: emptySet()

        /**
         * Saves night block config.
         * [enabled] = whether night block is actually active.
         * Times are always saved so they survive toggling off/on.
         */
        fun setNightBlock(
            context: Context,
            startH: Int, startM: Int,
            endH: Int,   endM: Int,
            enabled: Boolean = true
        ) {
            prefs(context).edit()
                .putBoolean("night_block_enabled",  enabled)
                .putInt("block_start_hour",   startH)
                .putInt("block_start_minute", startM)
                .putInt("block_end_hour",     endH)
                .putInt("block_end_minute",   endM)
                .apply()
        }

        fun setDailyUsageLimit(context: Context, minutes: Int) {
            prefs(context).edit().putInt("daily_usage_limit_minutes", minutes).apply()
        }

        fun isEnabled(context: Context): Boolean =
            prefs(context).getBoolean("screen_limit_enabled", false)

        private fun prefs(context: Context) =
            context.getSharedPreferences("habit_prefs", Context.MODE_PRIVATE)
    }
}
