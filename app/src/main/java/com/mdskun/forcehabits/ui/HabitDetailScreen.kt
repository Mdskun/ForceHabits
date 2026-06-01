package com.mdskun.forcehabits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdskun.forcehabits.data.model.*
import com.mdskun.forcehabits.proof.ProofScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel()
) {
    val habit      by viewModel.habit.collectAsState(initial = null)
    val logs       by viewModel.recentLogs.collectAsState(initial = emptyList())
    val todayLogs  by viewModel.todayLogs.collectAsState(initial = emptyList())
    var showProof  by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val today       = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    LaunchedEffect(habitId) { viewModel.loadHabit(habitId) }

    // ── Full-screen proof overlay ─────────────────────────────────
    // ProofScreen MUST fill the entire screen — the camera view needs
    // real pixel dimensions to render. Floating close button via Box.
    if (showProof && habit != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProofScreen(
                habit = habit!!,
                isOverlay = false,
                onProofCompleted = {
                    viewModel.markComplete(habitId, todayLogs.size)
                    showProof = false
                }
            )
            // Floating close button — doesn't consume height from ProofScreen
            IconButton(
                onClick = { showProof = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 52.dp, start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // ── Normal detail view ────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit?.name ?: "Habit Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        val h = habit ?: run {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val accentColor   = accentFor(h.type)
        val typeEmoji     = emojiFor(h.type)
        val isScreenLimit = h.frequency == ScheduleFrequency.SCREEN_LIMIT
        val isFullyDone   = h.lastCompletedDate == today

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = accentColor.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(typeEmoji, fontSize = 52.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(h.name, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(getProofLabel(h.proofType), color = accentColor, fontSize = 13.sp)
                }
            }

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("🔥 Streak", "${h.streakCount} days", accentColor, Modifier.weight(1f))
                StatCard(
                    "📅 Today",
                    if (isFullyDone) "Done ✓" else "Pending",
                    if (isFullyDone) Color(0xFF4CAF50) else accentColor,
                    Modifier.weight(1f)
                )
                StatCard(
                    "🔔 Schedule",
                    when (h.frequency) {
                        ScheduleFrequency.DAILY         -> "Daily"
                        ScheduleFrequency.EVERY_X_HOURS -> "Every ${h.intervalHours}h"
                        ScheduleFrequency.EVERY_X_DAYS  -> "Every ${h.intervalDays}d"
                        ScheduleFrequency.WEEKLY        -> "Weekly"
                        ScheduleFrequency.SCREEN_LIMIT  -> "Auto"
                    },
                    accentColor, Modifier.weight(1f)
                )
            }

            // Schedule detail
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏰", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(h.scheduleDisplayText(), fontSize = 14.sp)
                }
            }

            // Screen limit info or Complete button
            if (isScreenLimit) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE91E63).copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔒", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto-enforced — no alarm sound",
                                fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Math challenge fires silently when you open a restricted app past your limits.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Button(
                    onClick = { showProof = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isFullyDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFullyDone) Color(0xFF4CAF50) else accentColor
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (isFullyDone) "✓ Completed Today!"
                        else "Complete Now — Show Proof",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
            }

            // Recent history
            if (logs.isNotEmpty()) {
                Text("Recent History", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                logs.take(10).forEach { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(9.dp)
                                .background(Color(0xFF4CAF50), RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(log.completedDate, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Habit?") },
            text = { Text("This will delete '${habit?.name}' and cancel all its alarms. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHabit()
                    showDelete = false
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

private fun accentFor(type: HabitType) = when (type) {
    HabitType.EXERCISE     -> Color(0xFFFF6B35)
    HabitType.PLANT_CARE   -> Color(0xFF4CAF50)
    HabitType.HYDRATION    -> Color(0xFF2196F3)
    HabitType.READING      -> Color(0xFFFF9800)
    HabitType.SCREEN_LIMIT -> Color(0xFFE91E63)
    HabitType.CUSTOM       -> Color(0xFF9C27B0)
}

private fun emojiFor(type: HabitType) = when (type) {
    HabitType.EXERCISE     -> "💪"
    HabitType.PLANT_CARE   -> "🪴"
    HabitType.HYDRATION    -> "💧"
    HabitType.READING      -> "📚"
    HabitType.SCREEN_LIMIT -> "📵"
    HabitType.CUSTOM       -> "⭐"
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
        }
    }
}
