package com.mdskun.forcehabits.proof

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdskun.forcehabits.data.model.Habit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ── Screen time: math challenge ───────────────────────────────────
@Composable
fun ScreenTimeProofScreen(onCompleted: () -> Unit) {
    MathChallengeScreen(
        prompt    = "📵 Screen Time Limit",
        subPrompt = "Solve this to continue. It's meant to make you pause.",
        onSolved  = onCompleted
    )
}

@Composable
fun MathChallengeScreen(
    prompt: String,
    subPrompt: String,
    onSolved: () -> Unit
) {
    var problem     by remember { mutableStateOf(generateProblem()) }
    var userAnswer  by remember { mutableStateOf("") }
    var isWrong     by remember { mutableStateOf(false) }
    var wrongCount  by remember { mutableIntStateOf(0) }
    var cooldownSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldownSec) {
        if (cooldownSec > 0) { delay(1000); cooldownSec-- }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧮", fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text(prompt, color = Color.White, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subPrompt, color = Color(0xFFAAAAAA), fontSize = 13.sp,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Solve this:", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(problem.question, color = Color.White, fontSize = 32.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .width(160.dp).height(56.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                .border(2.dp,
                    if (isWrong) Color(0xFFFF5252) else Color(0xFFFF6B35).copy(0.5f),
                    RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (userAnswer.isEmpty()) "?" else userAnswer,
                color = if (userAnswer.isEmpty()) Color(0xFF555555) else Color(0xFFFF6B35),
                fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
        }

        if (isWrong) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (cooldownSec > 0) "Wrong. Wait ${cooldownSec}s..." else "Wrong. Try again.",
                color = Color(0xFFFF5252), fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        MathNumberPad(
            enabled = cooldownSec == 0,
            onDigit = { d ->
                isWrong = false
                if (d == "-" && userAnswer.isEmpty()) {
                    userAnswer = "-"
                } else if (userAnswer.length < 6) {
                    userAnswer += d
                    // Auto-check when answer reaches expected length
                    if (userAnswer.length >= problem.answerLength) {
                        userAnswer.toIntOrNull()?.let { entered ->
                            if (entered == problem.answer) {
                                onSolved()
                            } else {
                                isWrong = true; wrongCount++
                                cooldownSec = when (wrongCount) { 1->3; 2->5; 3->10; else->15 }
                                userAnswer = ""
                                if (wrongCount >= 3) problem = generateProblem()
                            }
                        }
                    }
                }
            },
            onDelete = { isWrong = false; if (userAnswer.isNotEmpty()) userAnswer = userAnswer.dropLast(1) },
            onSubmit = {
                userAnswer.toIntOrNull()?.let { entered ->
                    if (entered == problem.answer) {
                        onSolved()
                    } else {
                        isWrong = true; wrongCount++
                        cooldownSec = when (wrongCount) { 1->3; 2->5; 3->10; else->15 }
                        userAnswer = ""
                        if (wrongCount >= 3) problem = generateProblem()
                    }
                }
            }
        )
    }
}

data class MathProblem(val question: String, val answer: Int, val answerLength: Int)

fun generateProblem(): MathProblem = when (Random.nextInt(4)) {
    0 -> { val a = Random.nextInt(20, 99); val b = Random.nextInt(20, 99)
        MathProblem("$a + $b = ?", a + b, (a + b).toString().length) }
    1 -> { val a = Random.nextInt(50, 99); val b = Random.nextInt(10, a)
        MathProblem("$a − $b = ?", a - b, (a - b).toString().length) }
    2 -> { val a = Random.nextInt(3, 9); val b = Random.nextInt(3, 9)
        MathProblem("$a × $b = ?", a * b, (a * b).toString().length) }
    else -> { val a = Random.nextInt(10, 49); val b = Random.nextInt(10, 49); val c = Random.nextInt(10, 35)
        MathProblem("$a + $b + $c = ?", a + b + c, (a + b + c).toString().length) }
}

@Composable
private fun MathNumberPad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit
) {
    val accent = if (enabled) Color(0xFFFF6B35) else Color(0xFF444444)
    val rows = listOf(
        listOf("1","2","3"), listOf("4","5","6"),
        listOf("7","8","9"), listOf("-","0","⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f).aspectRatio(1.6f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (enabled) Color(0xFF1E1E1E) else Color(0xFF111111))
                            .then(if (enabled) Modifier.clickable {
                                if (key == "⌫") onDelete() else onDigit(key)
                            } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(key, color = if (enabled) Color.White else Color(0xFF444444),
                            fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Button(
            onClick = onSubmit, enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Check Answer", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

// ── Normal habit manual check-in ─────────────────────────────────
@Composable
fun NormalHabitProofScreen(habit: Habit, onCompleted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✅", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(habit.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Tap below to confirm you've completed this habit.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onCompleted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(14.dp)
        ) { Text("I've done it!", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

// ── App picker ────────────────────────────────────────────────────
data class AppInfo(val packageName: String, val label: String, val iconBitmap: Bitmap?)

private fun Drawable.toBitmapSafe(size: Int = 96): Bitmap {
    val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}

@Composable
fun AppPickerScreen(
    title: String = "Select Apps to Block",
    initialSelected: Set<String> = emptySet(),
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context     = LocalContext.current
    var allApps     by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loading     by remember { mutableStateOf(true) }

    // ── FIX: use an immutable Set stored as a value in state.
    // MutableSet doesn't trigger recomposition on content change.
    // We store a plain Set and replace the whole reference on every toggle.
    var selectedPkgs by remember { mutableStateOf(initialSelected) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm        = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val userApps  = installed
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { info ->
                    AppInfo(
                        packageName = info.packageName,
                        label       = pm.getApplicationLabel(info).toString(),
                        iconBitmap  = runCatching {
                            pm.getApplicationIcon(info.packageName).toBitmapSafe()
                        }.getOrNull()
                    )
                }
                .sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) {
                allApps = userApps
                loading = false
            }
        }
    }

    val filtered = if (searchQuery.isBlank()) allApps
    else allApps.filter {
        it.label.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header — with status bar padding so it doesn't go under the bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()          // ← pushes header below status bar
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Text("✕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onConfirm(selectedPkgs) }) {
                Text(
                    "Done (${selectedPkgs.size})",
                    color = Color(0xFFFF6B35),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search apps...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )
        Spacer(Modifier.height(4.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF6B35))
                    Spacer(Modifier.height(8.dp))
                    Text("Loading apps...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            return
        }

        // Select-all / none row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filtered.count { it.packageName in selectedPkgs }} of ${filtered.size} selected",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                // Replace with new immutable set — triggers recomposition
                selectedPkgs = filtered.map { it.packageName }.toSet()
            }) { Text("All", fontSize = 12.sp) }
            TextButton(onClick = {
                selectedPkgs = emptySet()
            }) { Text("None", fontSize = 12.sp) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                val isSelected = app.packageName in selectedPkgs

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Replace entire set reference — guaranteed recomposition
                            selectedPkgs = if (isSelected) {
                                selectedPkgs - app.packageName
                            } else {
                                selectedPkgs + app.packageName
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.iconBitmap != null) {
                        Image(
                            bitmap = app.iconBitmap.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF333333), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text("📱", fontSize = 22.sp) }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1)
                        Text(
                            app.packageName, fontSize = 11.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFFFF6B35) else Color.Transparent)
                            .border(2.dp,
                                if (isSelected) Color(0xFFFF6B35) else Color(0xFF555555),
                                CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
