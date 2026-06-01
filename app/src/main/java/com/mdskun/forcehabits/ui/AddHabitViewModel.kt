package com.mdskun.forcehabits.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdskun.forcehabits.accessibility.HabitAccessibilityService
import com.mdskun.forcehabits.alarm.AlarmScheduler
import com.mdskun.forcehabits.data.db.HabitDao
import com.mdskun.forcehabits.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject

@HiltViewModel
class AddHabitViewModel @Inject constructor(
    private val dao: HabitDao,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete

    fun resetSaveComplete() { _saveComplete.value = false }

    fun saveHabit(
        name: String,
        type: HabitType,
        proofType: ProofType,
        frequency: ScheduleFrequency,
        remHour: Int,
        remMinute: Int,
        intervalHours: Int = 4,
        intervalDays: Int = 1,
        weekDays: String = "2,3,4,5,6",
        blockStartHour: Int = 22,
        blockStartMinute: Int = 30,
        blockEndHour: Int = 6,
        blockEndMinute: Int = 0,
        dailyLimitMinutes: Int = 0,
        hasNightBlock: Boolean = false,
        customPhotoLabels: String = "",
        customPhotoCount: Int = 1,
        blockedApps: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            val proofConfig = buildProofConfig(type, proofType, customPhotoLabels, customPhotoCount)

            val habit = Habit(
                name = name, type = type, proofType = proofType, frequency = frequency,
                reminderHour = remHour, reminderMinute = remMinute,
                intervalHours = intervalHours, intervalDays = intervalDays, weekDays = weekDays,
                // Always store block times (even when night block toggle is off,
                // store the chosen times so they persist if user re-enables)
                blockStartHour   = blockStartHour,
                blockStartMinute = blockStartMinute,
                blockEndHour     = blockEndHour,
                blockEndMinute   = blockEndMinute,
                dailyUsageLimitMinutes = dailyLimitMinutes,
                proofConfig = proofConfig
            )

            val id    = dao.insertHabit(habit)
            val saved = habit.copy(id = id)

            if (frequency != ScheduleFrequency.SCREEN_LIMIT) {
                alarmScheduler.schedule(saved)
            }

            if (type == HabitType.SCREEN_LIMIT) {
                // ── Save blocked apps ──────────────────────────────────
                // Use exactly what the user selected — no fallback to defaults.
                // If they selected nothing, save an empty set (which means block nothing).
                HabitAccessibilityService.setBlockedAppsForHabit(context, id, blockedApps)

                // ── Save night block times — always, regardless of toggle ──
                // The toggle controls whether it's enforced; the times are always stored
                // so the AccessibilityService knows what window to use.
                HabitAccessibilityService.setNightBlock(
                    context,
                    blockStartHour, blockStartMinute,
                    blockEndHour,   blockEndMinute,
                    enabled = hasNightBlock
                )

                // ── Save daily usage limit ─────────────────────────────
                HabitAccessibilityService.setDailyUsageLimit(context, dailyLimitMinutes)

                HabitAccessibilityService.setEnabled(context, true)
            }

            _saveComplete.value = true
        }
    }

    private fun buildProofConfig(
        type: HabitType, proofType: ProofType,
        customPhotoLabels: String, customPhotoCount: Int
    ): String = when (proofType) {
        ProofType.POSE_SEQUENCE -> json.encodeToString(PoseConfig())
        ProofType.PHOTO_LABEL -> {
            val labels = when {
                type == HabitType.CUSTOM && customPhotoLabels.isNotBlank() ->
                    customPhotoLabels.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                type == HabitType.PLANT_CARE -> plantPhotoConfig().requiredLabels
                type == HabitType.HYDRATION  -> hydrationPhotoConfig().requiredLabels
                else -> listOf("Object")
            }
            val count = if (type == HabitType.CUSTOM) customPhotoCount else 1
            json.encodeToString(PhotoLabelConfig(requiredLabels = labels, photoCount = count))
        }
        ProofType.MULTI_PHOTO_TEXT -> json.encodeToString(MultiPhotoTextConfig())
        else -> "{}"
    }
}
