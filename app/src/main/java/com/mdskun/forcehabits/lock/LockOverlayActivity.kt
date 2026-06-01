package com.mdskun.forcehabits.lock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mdskun.forcehabits.ui.theme.ForceHabitsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // Back button completely disabled until proof/challenge is completed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* blocked */ }
        })

        val habitId      = intent.getLongExtra("habit_id", -1L)
        val habitName    = intent.getStringExtra("habit_name") ?: "Habit"
        val blockReason  = intent.getStringExtra("block_reason") ?: ""
        val usedMinutes  = intent.getIntExtra("used_minutes", 0)
        val limitMinutes = intent.getIntExtra("limit_minutes", 0)

        setContent {
            ForceHabitsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LockOverlayScreen(
                        habitId      = habitId,
                        habitName    = habitName,
                        blockReason  = blockReason,
                        usedMinutes  = usedMinutes,
                        limitMinutes = limitMinutes,
                        onUnlocked   = { finish() }
                    )
                }
            }
        }
    }
}
