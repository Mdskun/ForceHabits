package com.mdskun.forcehabits.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mdskun.forcehabits.data.model.Habit
import com.mdskun.forcehabits.data.model.HabitType
import com.mdskun.forcehabits.data.model.ProofType

/**
 * Routes to the correct proof screen based on habit.proofType.
 * Always fills the available space — callers must give this a bounded container.
 * The dark background is set here so every proof screen has consistent framing.
 */
@Composable
fun ProofScreen(
    habit: Habit,
    isOverlay: Boolean = false,
    onProofCompleted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        when (habit.proofType) {
            ProofType.POSE_SEQUENCE ->
                PoseProofScreen(habit = habit, onCompleted = onProofCompleted)

            ProofType.PHOTO_LABEL ->
                PhotoLabelProofScreen(habit = habit, onCompleted = onProofCompleted)

            ProofType.MULTI_PHOTO_TEXT ->
                MultiPhotoTextProofScreen(habit = habit, onCompleted = onProofCompleted)

            ProofType.SCREEN_TIME ->
                // Screen-time proof is handled by the lock overlay separately
                // If somehow routed here, show the math challenge
                ScreenTimeProofScreen(onCompleted = onProofCompleted)

            ProofType.NONE ->
                NormalHabitProofScreen(habit = habit, onCompleted = onProofCompleted)
        }
    }
}
