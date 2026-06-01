package com.mdskun.forcehabits.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Pose proof ───────────────────────────────────────────────────
// Keep PoseStep minimal — matches Tutorial exactly so JSON round-trips cleanly
@Serializable
data class PoseConfig(
    val poses: List<PoseStep> = defaultExercisePoses(),
    val holdDurationSeconds: Int = 2
)

@Serializable
data class PoseStep(
    val name: String,
    val instruction: String
)

fun defaultExercisePoses(): List<PoseStep> = listOf(
    PoseStep("Neck Left",    "Turn your head to the LEFT"),
    PoseStep("Neck Right",   "Turn your head to the RIGHT"),
    PoseStep("Neck Up",      "Tilt head UP — look at ceiling"),
    PoseStep("Neck Down",    "Drop chin to chest"),
    PoseStep("Both Arms Up", "Raise BOTH arms above your head")
)

// ── Photo label proof ────────────────────────────────────────────
@Serializable
data class PhotoLabelConfig(
    val requiredLabels: List<String>,
    val minimumConfidence: Float = 0.65f,
    val photoCount: Int = 1
)

fun plantPhotoConfig() = PhotoLabelConfig(
    requiredLabels = listOf("Plant", "Flower", "Tree", "Houseplant", "Vegetation", "Leaf")
)

fun hydrationPhotoConfig() = PhotoLabelConfig(
    requiredLabels = listOf("Drink", "Cup", "Glass", "Water", "Beverage", "Bottle")
)

// ── Multi-page reading proof ─────────────────────────────────────
@Serializable
data class MultiPhotoTextConfig(
    val requiredPhotoCount: Int = 4,
    val minUniqueWordsPerPhoto: Int = 30
)

// ── Shared JSON instance ─────────────────────────────────────────
val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
