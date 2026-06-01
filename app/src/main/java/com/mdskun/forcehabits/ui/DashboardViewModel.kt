package com.mdskun.forcehabits.ui


import androidx.lifecycle.ViewModel
import com.mdskun.forcehabits.data.db.HabitDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dao: HabitDao
) : ViewModel() {
    val habits = dao.getAllActiveHabits()
}