package com.mdskun.forcehabits.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdskun.forcehabits.data.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddHabit: () -> Unit,
    onHabitClick: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val habits  by viewModel.habits.collectAsState(initial = emptyList())
    val today    = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val context  = LocalContext.current

    var hasOverlay       by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityEnabled(context)) }

    LaunchedEffect(Unit) {
        hasOverlay       = Settings.canDrawOverlays(context)
        hasAccessibility = isAccessibilityEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ForceHabits", fontWeight = FontWeight.Bold)
                        Text(
                            "${habits.count { it.lastCompletedDate == today }}/${habits.size} done today",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHabit, containerColor = Color(0xFFFF6B35)) {
                Icon(Icons.Default.Add, "Add Habit", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            if (!hasOverlay) {
                item {
                    SetupBanner(
                        icon = "🪟", title = "Allow overlay permission",
                        subtitle = "Required for the lock screen to appear when alarms fire.",
                        buttonText = "Grant", color = Color(0xFFFF6B35)
                    ) { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
                }
            }
            if (!hasAccessibility) {
                item {
                    SetupBanner(
                        icon = "♿", title = "Enable Accessibility Service",
                        subtitle = "Find 'ForceHabits Screen Monitor' in Accessibility → Installed apps and enable it.",
                        buttonText = "Open Settings", color = Color(0xFFE91E63)
                    ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
            }

            if (habits.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📋", fontSize = 52.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("No habits yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Tap + to add your first strict habit",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            items(habits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    isCompletedToday = habit.lastCompletedDate == today,
                    onClick = { onHabitClick(habit.id) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun isAccessibilityEnabled(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains(context.packageName, ignoreCase = true)
}

@Composable
private fun SetupBanner(
    icon: String, title: String, subtitle: String,
    buttonText: String, color: Color, onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                Spacer(Modifier.height(10.dp))
                Button(onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) { Text(buttonText, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
fun HabitCard(habit: Habit, isCompletedToday: Boolean, onClick: () -> Unit) {
    val typeEmoji = when (habit.type) {
        HabitType.EXERCISE     -> "💪"
        HabitType.PLANT_CARE   -> "🪴"
        HabitType.HYDRATION    -> "💧"
        HabitType.READING      -> "📚"
        HabitType.SCREEN_LIMIT -> "📵"
        HabitType.CUSTOM       -> "⭐"
    }
    val accentColor = when (habit.type) {
        HabitType.EXERCISE     -> Color(0xFFFF6B35)
        HabitType.PLANT_CARE   -> Color(0xFF4CAF50)
        HabitType.HYDRATION    -> Color(0xFF2196F3)
        HabitType.READING      -> Color(0xFFFF9800)
        HabitType.SCREEN_LIMIT -> Color(0xFFE91E63)
        HabitType.CUSTOM       -> Color(0xFF9C27B0)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(typeEmoji, fontSize = 24.sp) }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                // scheduleDisplayText() now handles all frequency types dynamically
                Text(
                    "⏰ ${habit.scheduleDisplayText()}  •  🔥 ${habit.streakCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(getProofLabel(habit.proofType), fontSize = 11.sp, color = accentColor)
            }

            Spacer(Modifier.width(8.dp))

            if (isCompletedToday) {
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(Color(0xFF4CAF50), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

fun getProofLabel(proofType: ProofType): String = when (proofType) {
    ProofType.POSE_SEQUENCE    -> "🤸 Pose sequence"
    ProofType.PHOTO_LABEL      -> "📷 Photo proof"
    ProofType.MULTI_PHOTO_TEXT -> "📖 4-page photos"
    ProofType.SCREEN_TIME      -> "🔒 Auto-enforced"
    ProofType.NONE             -> "✅ Manual check-in"
}
