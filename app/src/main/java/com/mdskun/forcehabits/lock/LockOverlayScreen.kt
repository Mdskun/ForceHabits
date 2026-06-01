package com.mdskun.forcehabits.lock

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdskun.forcehabits.proof.MathChallengeScreen
import com.mdskun.forcehabits.proof.ProofScreen

@Composable
fun LockOverlayScreen(
    habitId: Long,
    habitName: String,
    onUnlocked: () -> Unit,
    blockReason: String = "",
    usedMinutes: Int = 0,
    limitMinutes: Int = 0,
    viewModel: LockViewModel = hiltViewModel()
) {
    val state  by viewModel.state.collectAsState()

    LaunchedEffect(habitId) { viewModel.loadHabit(habitId) }

    // ── Screen-time mode: silent, math challenge gate ──
    if (habitId == -99L) {
        ScreenTimeLockGate(
            blockReason  = blockReason,
            usedMinutes  = usedMinutes,
            limitMinutes = limitMinutes,
            onUnlocked   = {
                viewModel.markHabitComplete(habitId)
                onUnlocked()
            }
        )
        return
    }

    // ── Regular habit alarm ──
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text("🔔", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("HABIT REMINDER", color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text(habitName, color = Color(0xFFFF6B35), fontSize = 28.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Complete your habit proof to stop the alarm",
                color = Color(0xFFAAAAAA), fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))

            state.habit?.let { habit ->
                ProofScreen(
                    habit = habit,
                    isOverlay = true,
                    onProofCompleted = {
                        viewModel.markHabitComplete(habitId)
                        onUnlocked()
                    }
                )
            } ?: CircularProgressIndicator(color = Color(0xFFFF6B35))
        }
    }
}

@Composable
private fun ScreenTimeLockGate(
    blockReason: String,
    usedMinutes: Int,
    limitMinutes: Int,
    onUnlocked: () -> Unit
) {
    val reasonText = when (blockReason) {
        "NIGHT_BLOCK"  -> "📵 Night block is active."
        "USAGE_LIMIT"  -> "⏱️ You've hit your ${limitMinutes}-min daily limit ($usedMinutes min used)."
        "BOTH"         -> "📵 Night block + ⏱️ ${usedMinutes}/${limitMinutes} min daily limit."
        else           -> "📵 Screen time limit reached."
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))
    ) {
        // Reason banner at top
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE91E63).copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(reasonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("Solve the math problem below to continue.",
                    color = Color(0xFFAAAAAA), fontSize = 12.sp)
            }
        }

        // Usage bar for limit-based blocks
        if (limitMinutes > 0 && usedMinutes > 0) {
            LinearProgressIndicator(
                progress = { (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 16.dp),
                color = Color(0xFFE91E63), trackColor = Color(0xFF333333)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Math challenge fills the rest of the screen
        MathChallengeScreen(
            prompt = "📵 Screen Time",
            subPrompt = "Solve this to dismiss. Each wrong answer adds a wait.",
            onSolved = onUnlocked
        )
    }
}
