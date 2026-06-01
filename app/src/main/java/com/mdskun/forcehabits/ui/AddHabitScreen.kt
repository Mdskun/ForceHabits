package com.mdskun.forcehabits.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdskun.forcehabits.accessibility.HabitAccessibilityService
import com.mdskun.forcehabits.data.model.HabitType
import com.mdskun.forcehabits.data.model.ProofType
import com.mdskun.forcehabits.data.model.ScheduleFrequency
import com.mdskun.forcehabits.proof.AppPickerScreen

@Composable
fun TimePickerButton(
    hour: Int, minute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    label: String = ""
) {
    val context = LocalContext.current
    val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val apm = if (hour < 12) "AM" else "PM"
    val mm  = minute.toString().padStart(2, '0')

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, Color(0xFFFF6B35).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable {
                    TimePickerDialog(context, { _, h, m -> onTimeSelected(h, m) }, hour, minute, false).show()
                }
                .padding(vertical = 18.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$h12:$mm", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35))
                Spacer(Modifier.width(8.dp))
                Text(apm, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35).copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Text("⏰", fontSize = 22.sp)
            }
        }
        Text("Tap to change", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddHabitViewModel = hiltViewModel()
) {
    val saveComplete by viewModel.saveComplete.collectAsState()
    LaunchedEffect(saveComplete) {
        if (saveComplete) { viewModel.resetSaveComplete(); onSaved() }
    }

    var name          by remember { mutableStateOf("") }
    var selectedType  by remember { mutableStateOf(HabitType.CUSTOM) }
    var selectedProof by remember { mutableStateOf(ProofType.NONE) }
    var isSaving      by remember { mutableStateOf(false) }

    var frequency     by remember { mutableStateOf(ScheduleFrequency.DAILY) }
    var remHour       by remember { mutableIntStateOf(8) }
    var remMinute     by remember { mutableIntStateOf(0) }
    var intervalHours by remember { mutableIntStateOf(4) }
    var intervalDays  by remember { mutableIntStateOf(2) }
    var weekDays      by remember { mutableStateOf(setOf(2, 3, 4, 5, 6)) }

    var blockStartHour    by remember { mutableIntStateOf(22) }
    var blockStartMinute  by remember { mutableIntStateOf(30) }
    var blockEndHour      by remember { mutableIntStateOf(6) }
    var blockEndMinute    by remember { mutableIntStateOf(0) }
    var dailyLimitMinutes by remember { mutableIntStateOf(35) }
    var hasNightBlock     by remember { mutableStateOf(true) }
    var hasDailyLimit     by remember { mutableStateOf(true) }

    // App picker state — which apps to block for this screen limit habit
    val context = LocalContext.current
    var selectedApps  by remember {
        mutableStateOf(HabitAccessibilityService.defaultBlockedPackages)
    }
    var showAppPicker by remember { mutableStateOf(false) }

    var customPhotoLabels by remember { mutableStateOf("") }
    var customPhotoCount  by remember { mutableIntStateOf(1) }

    val isScreenLimit = selectedType == HabitType.SCREEN_LIMIT
    val isCustom      = selectedType == HabitType.CUSTOM

    LaunchedEffect(selectedType) {
        when (selectedType) {
            HabitType.EXERCISE     -> { selectedProof = ProofType.POSE_SEQUENCE;    frequency = ScheduleFrequency.DAILY }
            HabitType.PLANT_CARE   -> { selectedProof = ProofType.PHOTO_LABEL;      frequency = ScheduleFrequency.DAILY }
            HabitType.HYDRATION    -> { selectedProof = ProofType.PHOTO_LABEL;      frequency = ScheduleFrequency.EVERY_X_HOURS; intervalHours = 4 }
            HabitType.READING      -> { selectedProof = ProofType.MULTI_PHOTO_TEXT; frequency = ScheduleFrequency.DAILY }
            HabitType.SCREEN_LIMIT -> { selectedProof = ProofType.SCREEN_TIME;      frequency = ScheduleFrequency.SCREEN_LIMIT }
            HabitType.CUSTOM       -> { selectedProof = ProofType.NONE;             frequency = ScheduleFrequency.DAILY }
        }
    }

    // Show app picker as a full-screen overlay
    if (showAppPicker) {
        AppPickerScreen(
            title = "Select Apps to Block",
            initialSelected = selectedApps,
            onConfirm = { picked ->
                selectedApps = picked
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Habit") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp)
                .fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Habit Name") },
                placeholder = { Text("e.g. Morning Exercise, No Social Media After 10 PM...") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            SectionLabel("Habit Type")
            HabitTypeGrid(selected = selectedType, onSelect = { selectedType = it })

            if (!isScreenLimit) {
                SectionLabel("Proof Required")
                ProofTypePicker(selected = selectedProof, onSelect = { selectedProof = it })
            }

            if (isCustom && selectedProof == ProofType.PHOTO_LABEL) {
                SectionLabel("Photo Proof Config")
                CustomPhotoConfig(
                    labels = customPhotoLabels, onLabelsChange = { customPhotoLabels = it },
                    photoCount = customPhotoCount, onPhotoCountChange = { customPhotoCount = it }
                )
            }

            if (isScreenLimit) {
                SectionLabel("Screen Limit Settings")
                ScreenLimitConfig(
                    selectedApps = selectedApps,
                    onOpenAppPicker = { showAppPicker = true },
                    hasNightBlock = hasNightBlock, onHasNightBlockChange = { hasNightBlock = it },
                    blockStartHour = blockStartHour, blockStartMinute = blockStartMinute,
                    onBlockStartChange = { h, m -> blockStartHour = h; blockStartMinute = m },
                    blockEndHour = blockEndHour, blockEndMinute = blockEndMinute,
                    onBlockEndChange = { h, m -> blockEndHour = h; blockEndMinute = m },
                    hasDailyLimit = hasDailyLimit, onHasDailyLimitChange = { hasDailyLimit = it },
                    dailyLimitMinutes = dailyLimitMinutes, onDailyLimitChange = { dailyLimitMinutes = it }
                )
            } else {
                SectionLabel("Schedule")
                ScheduleConfig(
                    frequency = frequency, onFrequencyChange = { frequency = it },
                    remHour = remHour, remMinute = remMinute,
                    onTimeChange = { h, m -> remHour = h; remMinute = m },
                    intervalHours = intervalHours, onIntervalHoursChange = { intervalHours = it },
                    intervalDays = intervalDays, onIntervalDaysChange = { intervalDays = it },
                    weekDays = weekDays, onWeekDaysChange = { weekDays = it }
                )
            }

            ProofExplanation(selectedType, selectedProof)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isBlank() || isSaving) return@Button
                    isSaving = true
                    viewModel.saveHabit(
                        name = name.trim(), type = selectedType, proofType = selectedProof,
                        frequency = frequency, remHour = remHour, remMinute = remMinute,
                        intervalHours = intervalHours, intervalDays = intervalDays,
                        weekDays = weekDays.sorted().joinToString(","),
                        blockStartHour = blockStartHour, blockStartMinute = blockStartMinute,
                        blockEndHour = blockEndHour, blockEndMinute = blockEndMinute,
                        dailyLimitMinutes = if (isScreenLimit && hasDailyLimit) dailyLimitMinutes else 0,
                        hasNightBlock = isScreenLimit && hasNightBlock,
                        customPhotoLabels = customPhotoLabels, customPhotoCount = customPhotoCount,
                        blockedApps = if (isScreenLimit) selectedApps else emptySet()
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (isScreenLimit) "Save & Activate Screen Limit" else "Save Habit & Set Alarm",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Screen limit config — now includes app picker ─────────────────
@Composable
private fun ScreenLimitConfig(
    selectedApps: Set<String>,
    onOpenAppPicker: () -> Unit,
    hasNightBlock: Boolean, onHasNightBlockChange: (Boolean) -> Unit,
    blockStartHour: Int, blockStartMinute: Int, onBlockStartChange: (Int, Int) -> Unit,
    blockEndHour: Int, blockEndMinute: Int, onBlockEndChange: (Int, Int) -> Unit,
    hasDailyLimit: Boolean, onHasDailyLimitChange: (Boolean) -> Unit,
    dailyLimitMinutes: Int, onDailyLimitChange: (Int) -> Unit
) {
    val accent = Color(0xFFFF6B35)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── App selection ──────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📱", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Blocked Apps", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${selectedApps.size} app${if (selectedApps.size != 1) "s" else ""} selected",
                        fontSize = 12.sp, color = accent)
                }
                OutlinedButton(
                    onClick = onOpenAppPicker,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                ) { Text("Choose Apps") }
            }
            if (selectedApps.isEmpty()) {
                Text("⚠️ No apps selected — limit won't block anything.",
                    fontSize = 12.sp, color = Color(0xFFFF9800))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        // ── Night block ────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Night block", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Block selected apps between two times every night",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
            Switch(checked = hasNightBlock, onCheckedChange = onHasNightBlockChange,
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f)))
        }
        AnimatedVisibility(visible = hasNightBlock) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePickerButton(hour = blockStartHour, minute = blockStartMinute,
                    onTimeSelected = onBlockStartChange, label = "Block FROM")
                TimePickerButton(hour = blockEndHour, minute = blockEndMinute,
                    onTimeSelected = onBlockEndChange, label = "Block UNTIL (next day)")
                Text("e.g. FROM 10:30 PM → UNTIL 6:00 AM — math challenge fires when you open a blocked app during this window",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        // ── Daily limit ────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱️", fontSize = 20.sp); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Per-day usage limit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Block after X minutes total across selected apps",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
            Switch(checked = hasDailyLimit, onCheckedChange = onHasDailyLimitChange,
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.4f)))
        }
        AnimatedVisibility(visible = hasDailyLimit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(value = dailyLimitMinutes.toFloat(),
                        onValueChange = { onDailyLimitChange(it.toInt()) },
                        valueRange = 5f..180f, steps = 34, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                    Spacer(Modifier.width(8.dp))
                    Text("$dailyLimitMinutes min", fontWeight = FontWeight.Bold, color = accent,
                        fontSize = 15.sp, modifier = Modifier.width(64.dp))
                }
                Text("After $dailyLimitMinutes min of combined use today, math challenge fires. Resets midnight.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

// ── Schedule config ───────────────────────────────────────────────
@Composable
private fun ScheduleConfig(
    frequency: ScheduleFrequency, onFrequencyChange: (ScheduleFrequency) -> Unit,
    remHour: Int, remMinute: Int, onTimeChange: (Int, Int) -> Unit,
    intervalHours: Int, onIntervalHoursChange: (Int) -> Unit,
    intervalDays: Int, onIntervalDaysChange: (Int) -> Unit,
    weekDays: Set<Int>, onWeekDaysChange: (Set<Int>) -> Unit
) {
    val options = listOf(
        ScheduleFrequency.DAILY         to "Daily",
        ScheduleFrequency.EVERY_X_HOURS to "Every X Hours",
        ScheduleFrequency.EVERY_X_DAYS  to "Every X Days",
        ScheduleFrequency.WEEKLY        to "Weekly"
    )
    val accent = Color(0xFFFF6B35)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (freq, label) ->
                val sel = frequency == freq
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                        .border(if (sel) 2.dp else 1.dp,
                            if (sel) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { onFrequencyChange(freq) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) accent else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                }
            }
        }
        when (frequency) {
            ScheduleFrequency.EVERY_X_HOURS ->
                TimePickerButton(hour = remHour, minute = remMinute, onTimeSelected = onTimeChange, label = "Start Time (first alarm)")
            else ->
                TimePickerButton(hour = remHour, minute = remMinute, onTimeSelected = onTimeChange, label = "Reminder Time")
        }
        when (frequency) {
            ScheduleFrequency.EVERY_X_HOURS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat every:", fontSize = 13.sp, modifier = Modifier.width(90.dp))
                    Slider(value = intervalHours.toFloat(), onValueChange = { onIntervalHoursChange(it.toInt()) },
                        valueRange = 1f..12f, steps = 10, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                    Spacer(Modifier.width(8.dp))
                    Text("$intervalHours hr${if (intervalHours > 1) "s" else ""}", fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.width(52.dp))
                }
            }
            ScheduleFrequency.EVERY_X_DAYS -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat every:", fontSize = 13.sp, modifier = Modifier.width(90.dp))
                    Slider(value = intervalDays.toFloat(), onValueChange = { onIntervalDaysChange(it.toInt()) },
                        valueRange = 2f..30f, steps = 27, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
                    Spacer(Modifier.width(8.dp))
                    Text("$intervalDays days", fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.width(60.dp))
                }
            }
            ScheduleFrequency.WEEKLY -> WeekDayPicker(selected = weekDays, onSelectionChange = onWeekDaysChange)
            else -> {}
        }
    }
}

@Composable
private fun WeekDayPicker(selected: Set<Int>, onSelectionChange: (Set<Int>) -> Unit) {
    val days = listOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun")
    val accent = Color(0xFFFF6B35)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("On which days:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            days.forEach { (dayInt, label) ->
                val sel = dayInt in selected
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) accent else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (sel) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { val new = if (sel) selected - dayInt else selected + dayInt; if (new.isNotEmpty()) onSelectionChange(new) }
                    .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun CustomPhotoConfig(labels: String, onLabelsChange: (String) -> Unit, photoCount: Int, onPhotoCountChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = labels, onValueChange = onLabelsChange,
            label = { Text("What should the photo show?") },
            placeholder = { Text("e.g. Coffee, Cup, Mug, Drink") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Comma-separated. ML Kit detects if any label matches.", fontSize = 11.sp) })
        Text("Photos required: $photoCount", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Slider(value = photoCount.toFloat(), onValueChange = { onPhotoCountChange(it.toInt()) },
            valueRange = 1f..8f, steps = 6,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF6B35), activeTrackColor = Color(0xFFFF6B35)))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
}

@Composable
fun HabitTypeGrid(selected: HabitType, onSelect: (HabitType) -> Unit) {
    val types = listOf(
        HabitType.EXERCISE     to ("💪" to "Exercise"),
        HabitType.PLANT_CARE   to ("🪴" to "Plant Care"),
        HabitType.HYDRATION    to ("💧" to "Hydration"),
        HabitType.READING      to ("📚" to "Reading"),
        HabitType.SCREEN_LIMIT to ("📵" to "Screen Limit"),
        HabitType.CUSTOM       to ("⭐" to "Custom")
    )
    val accent = Color(0xFFFF6B35)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        types.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (type, lbl) ->
                    val (emoji, nm) = lbl; val sel = selected == type
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                        .border(if (sel) 2.dp else 1.dp, if (sel) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onSelect(type) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 22.sp); Spacer(Modifier.height(4.dp))
                            Text(nm, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) accent else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ProofTypePicker(selected: ProofType, onSelect: (ProofType) -> Unit) {
    val options = listOf(
        ProofType.POSE_SEQUENCE    to ("🤸" to "Pose Sequence"),
        ProofType.PHOTO_LABEL      to ("📷" to "Photo Proof"),
        ProofType.MULTI_PHOTO_TEXT to ("📖" to "4-Page Photos"),
        ProofType.NONE             to ("✅" to "Manual Check")
    )
    val accent = Color(0xFFFF6B35)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (proof, lbl) ->
            val (emoji, nm) = lbl; val sel = selected == proof
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (sel) accent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)
                .border(if (sel) 2.dp else 1.dp, if (sel) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clickable { onSelect(proof) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                Text(nm, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) accent else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (sel) Text("✓", color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProofExplanation(habitType: HabitType, proofType: ProofType) {
    val (icon, title, desc) = when {
        habitType == HabitType.SCREEN_LIMIT -> Triple("📵", "Math Challenge Enforcement",
            "No alarm sound. When you open a blocked app past your limit, a math problem appears. Wrong answers add a wait timer. Can't muscle-memory it like a PIN.")
        proofType == ProofType.POSE_SEQUENCE -> Triple("🤸", "Pose Sequence",
            "Front camera detects body poses. Hold each pose for 2s. Alarm stops only when all poses pass.")
        proofType == ProofType.PHOTO_LABEL -> Triple("📷", "Photo Proof",
            "Take a photo. ML Kit confirms on-device it shows the right object.")
        proofType == ProofType.MULTI_PHOTO_TEXT -> Triple("📖", "4-Page Photos",
            "4 photos of different pages. OCR confirms real text and no duplicates.")
        else -> Triple("✅", "Manual Check-in", "No proof. Tap Done to complete.")
    }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(icon, fontSize = 24.sp); Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}
