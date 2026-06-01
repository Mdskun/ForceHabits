package com.mdskun.forcehabits.lock

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdskun.forcehabits.alarm.AlarmSoundService
import com.mdskun.forcehabits.data.db.HabitDao
import com.mdskun.forcehabits.data.db.HabitLog
import com.mdskun.forcehabits.data.model.Habit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class LockState(val habit: Habit? = null)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val dao: HabitDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state

    fun loadHabit(habitId: Long) {
        if (habitId == -99L) return          // screen-time mode — no DB habit
        viewModelScope.launch {
            _state.value = LockState(habit = dao.getHabitById(habitId))
        }
    }

    fun markHabitComplete(habitId: Long) {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            if (habitId != -99L) {
                // Log completion
                dao.insertLog(HabitLog(habitId = habitId, completedDate = today))
                // Update streak
                dao.getHabitById(habitId)?.let { habit ->
                    dao.updateHabit(habit.copy(
                        streakCount = habit.streakCount + 1,
                        lastCompletedDate = today
                    ))
                }
                // Stop alarm sound — only regular habit alarms play sound
                runCatching {
                    context.startService(
                        Intent(context, AlarmSoundService::class.java).apply {
                            action = AlarmSoundService.ACTION_STOP_ALARM
                        }
                    )
                }
            }
            // Screen-time mode (-99L): no alarm was playing, nothing to stop
        }
    }
}
