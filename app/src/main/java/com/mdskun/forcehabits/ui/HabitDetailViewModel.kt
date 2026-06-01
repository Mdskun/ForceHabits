package com.mdskun.forcehabits.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdskun.forcehabits.alarm.AlarmScheduler
import com.mdskun.forcehabits.alarm.AlarmSoundService
import com.mdskun.forcehabits.data.db.HabitDao
import com.mdskun.forcehabits.data.db.HabitLog
import com.mdskun.forcehabits.data.model.Habit
import com.mdskun.forcehabits.data.model.ScheduleFrequency
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    private val dao: HabitDao,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _habitId = MutableStateFlow(-1L)
    private val _habit   = MutableStateFlow<Habit?>(null)
    val habit: StateFlow<Habit?> = _habit

    val recentLogs: StateFlow<List<HabitLog>> = _habitId
        .flatMapLatest { id ->
            if (id == -1L) flowOf(emptyList())
            else dao.getLogsForHabit(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _todayLogs = MutableStateFlow<List<HabitLog>>(emptyList())
    val todayLogs: StateFlow<List<HabitLog>> = _todayLogs

    fun loadHabit(id: Long) {
        _habitId.value = id
        viewModelScope.launch {
            _habit.value = dao.getHabitById(id)
            refreshTodayLogs(id)
        }
    }

    private suspend fun refreshTodayLogs(habitId: Long) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        _todayLogs.value = dao.getLogsForDate(habitId, today)
    }

    fun markComplete(habitId: Long, sessionIndex: Int = 0) {
        viewModelScope.launch {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            dao.insertLog(HabitLog(habitId = habitId, completedDate = today, sessionIndex = sessionIndex))
            refreshTodayLogs(habitId)

            val habit = dao.getHabitById(habitId) ?: return@launch
            // Update streak — for all non-screen-limit habits, one completion per day counts
            dao.updateHabit(habit.copy(streakCount = habit.streakCount + 1, lastCompletedDate = today))
            _habit.value = dao.getHabitById(habitId)

            // Stop alarm if one is playing
            context.startService(
                Intent(context, AlarmSoundService::class.java).apply {
                    action = AlarmSoundService.ACTION_STOP_ALARM
                }
            )
        }
    }

    fun deleteHabit() {
        viewModelScope.launch {
            val habit = _habit.value ?: return@launch
            alarmScheduler.cancel(habit)
            dao.deleteHabit(habit)
        }
    }
}
