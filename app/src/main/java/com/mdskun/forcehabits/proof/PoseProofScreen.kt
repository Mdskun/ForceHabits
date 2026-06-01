package com.mdskun.forcehabits.proof

import android.Manifest
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.mdskun.forcehabits.data.model.Habit
import com.mdskun.forcehabits.data.model.PoseConfig
import com.mdskun.forcehabits.data.model.PoseStep
import com.mdskun.forcehabits.data.model.json
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import java.util.concurrent.Executors
import kotlin.math.abs

private const val TAG = "PoseProof"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PoseProofScreen(habit: Habit, onCompleted: () -> Unit) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    if (cameraPermission.status.isGranted) {
        PoseCamera(habit = habit, onCompleted = onCompleted)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📷", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Camera Permission Required",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { cameraPermission.launchPermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
            ) {
                Text("Grant Camera Permission")
            }
        }
    }
}

@Composable
private fun PoseCamera(habit: Habit, onCompleted: () -> Unit) {

    var currentPoseIndex by remember { mutableIntStateOf(0) }
    var holdProgress     by remember { mutableFloatStateOf(0f) }
    var poseDetected     by remember { mutableStateOf(false) }
    var feedbackText     by remember { mutableStateOf("Stand back so your head & shoulders are visible") }
    var cameraError      by remember { mutableStateOf<String?>(null) }
    var debugConfigInfo  by remember { mutableStateOf("") }
    var frameCount       by remember { mutableIntStateOf(0) }
    var landmarkCount    by remember { mutableIntStateOf(0) }
    var detectorStatus   by remember { mutableStateOf("Initializing...") }
    var detectorResetTrigger by remember { mutableIntStateOf(0) }

    // Parse config — always falls back to defaults on any error
    val config = remember {
        try {
            val raw = habit.proofConfig
            debugConfigInfo = "Config Length: ${raw.length}"
            json.decodeFromString<PoseConfig>(raw).also {
                Log.d(TAG, "Loaded ${it.poses.size} poses from config")
                debugConfigInfo = "Poses: ${it.poses.size}, Hold: ${it.holdDurationSeconds}s"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Config parse failed: ${e.message}")
            debugConfigInfo = "Parse Error: ${e.message}"
            PoseConfig()
        }
    }

    val lifecycleOwner    = LocalLifecycleOwner.current
    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()
    var holdJob          by remember { mutableStateOf<Job?>(null) }

    // Refs that survive recomposition without re-creating
    val overlayRef       = remember { mutableStateOf<SkeletonOverlayView?>(null) }
    val currentPoseRef   = remember { mutableStateOf(if (config.poses.isNotEmpty()) config.poses[0] else PoseStep("", "")) }

    // Background executor — ML Kit MUST NOT run on the main thread
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler      = remember { Handler(Looper.getMainLooper()) }

    val poseDetector = remember(detectorResetTrigger) {
        try {
            Log.d(TAG, "Creating PoseDetector (Stable 17.0.0)")
            val options = PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
            PoseDetection.getClient(options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PoseDetector", e)
            detectorStatus = "Creation Error: ${e.message}"
            // Final fallback
            PoseDetection.getClient(PoseDetectorOptions.Builder().build())
        }
    }

    // Try a simple detector if accurate fails? Actually let's stick to accurate but ensure cleanup.
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing pose detector and executor")
            try {
                poseDetector.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing pose detector", e)
            }
            analysisExecutor.shutdown()
        }
    }

    // All poses done
    if (currentPoseIndex >= config.poses.size) {
        LaunchedEffect(Unit) { onCompleted() }
        return
    }

    // Keep currentPoseRef up to date when index changes
    LaunchedEffect(currentPoseIndex) {
        if (currentPoseIndex < config.poses.size) {
            currentPoseRef.value = config.poses[currentPoseIndex]
            Log.d(TAG, "Now on pose $currentPoseIndex: '${config.poses[currentPoseIndex].name}'")
        }
    }

    // Show camera error if binding failed
    if (cameraError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D0D)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text("❌", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Camera error: $cameraError",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Step header ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Pose ${currentPoseIndex + 1} / ${config.poses.size}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                config.poses.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(
                                when {
                                    i < currentPoseIndex  -> Color(0xFF4CAF50)
                                    i == currentPoseIndex -> Color(0xFFFF6B35)
                                    else                  -> Color(0xFF444444)
                                },
                                CircleShape
                            )
                    )
                }
            }
        }

        // Instruction text
        Text(
            config.poses.getOrNull(currentPoseIndex)?.instruction ?: "No Instruction",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        // Debug Config Info
        Text(
            "$debugConfigInfo | Frames: $frameCount | Landmarks: $landmarkCount",
            color = if (landmarkCount > 0) Color.Green else if (frameCount > 0) Color.Cyan else Color.Yellow,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "Detector: $detectorStatus",
            color = if (detectorStatus.contains("Error")) Color.Red else Color.White.copy(alpha = 0.6f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { detectorResetTrigger++ }
        )
        if (detectorStatus.contains("Error")) {
            Button(
                onClick = { detectorResetTrigger++ },
                modifier = Modifier.padding(top = 4.dp).height(24.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("RETRY AI", fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── Camera + overlay ──────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    Log.d(TAG, "Creating PreviewView")
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                        val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        try {
                            val provider = future.get()

                            val preview = Preview.Builder()
                                .setTargetResolution(Size(480, 640))
                                .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val analyzer = ImageAnalysis.Builder()
                                .setTargetResolution(Size(480, 640))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()

                            analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                                mainHandler.post { frameCount++ }
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                // ── Simplest possible InputImage creation ──
                                val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                                poseDetector.process(input)
                                    .addOnSuccessListener { pose ->
                                        val count = pose.allPoseLandmarks.size
                                        mainHandler.post { 
                                            detectorStatus = if (count > 0) "Detecting ($count)" else "Running (No Person)"
                                            landmarkCount = count
                                            // Pass the raw imageProxy dimensions to the overlay
                                            overlayRef.value?.updatePose(pose, imageProxy.width.toFloat(), imageProxy.height.toFloat(), imageProxy.imageInfo.rotationDegrees)

                                            // Check against current pose
                                            val step = currentPoseRef.value
                                            val (detected, feedback) = checkPoseWithFeedback(pose, step)

                                            feedbackText = feedback
                                            poseDetected = detected

                                            if (detected) {
                                                if (holdJob == null || holdJob?.isCompleted == true) {
                                                    holdJob = scope.launch {
                                                        val steps = config.holdDurationSeconds * 10
                                                        for (i in 0 until steps) {
                                                            holdProgress = (i + 1).toFloat() / steps
                                                            delay(100L)
                                                            if (!poseDetected) {
                                                                holdProgress = 0f
                                                                return@launch
                                                            }
                                                        }
                                                        // Success — advance to next pose
                                                        holdProgress = 0f
                                                        poseDetected = false
                                                        currentPoseIndex++
                                                        holdJob = null
                                                    }
                                                }
                                            } else {
                                                holdJob?.cancel()
                                                holdJob = null
                                                holdProgress = 0f
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "ML Kit process error: ${e.message}")
                                        mainHandler.post {
                                            detectorStatus = "Error: ${e.message}"
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }

                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analyzer
                            )
                            Log.d(TAG, "Camera bound successfully")

                        } catch (e: Exception) {
                            Log.e(TAG, "Camera setup failed: ${e.message}", e)
                            mainHandler.post { cameraError = e.message ?: "Unknown error" }
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Skeleton overlay on top of camera
            AndroidView(
                factory = { ctx ->
                    SkeletonOverlayView(ctx).apply {
                        alpha = 1f
                        visibility = android.view.View.VISIBLE
                        elevation = 10f 
                        overlayRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Feedback banner at bottom of camera area
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .background(
                        if (poseDetected) Color(0xEE00C853) else Color(0xDD000000),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    feedbackText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (poseDetected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Hold progress bar ─────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(8.dp)
                .background(Color(0xFF333333), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(holdProgress)
                    .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
            )
        }
        Text(
            if (poseDetected) "Hold for ${config.holdDurationSeconds}s..."
            else "Hold the pose until the bar fills",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )
    }
}

// ── Skeleton overlay ─────────────────────────────────────────────
class SkeletonOverlayView(context: android.content.Context) : View(context) {

    private var pose: Pose?  = null
    private var imgW         = 1f
    private var imgH         = 1f
    @Suppress("unused")
    private var rotDegrees   = 0

    private val dotPaint = Paint().apply {
        color     = AndroidColor.WHITE
        style     = Paint.Style.FILL
        isAntiAlias = true
    }
    private val bonePaint = Paint().apply {
        color       = AndroidColor.argb(210, 255, 107, 53)
        style       = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap   = Paint.Cap.ROUND
        isAntiAlias = true
    }

    init {
        setWillNotDraw(false)   // Required for custom View drawing
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun updatePose(pose: Pose, rawW: Float, rawH: Float, rotation: Int) {
        this.pose       = pose
        this.imgW       = rawW
        this.imgH       = rawH
        this.rotDegrees = rotation
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // DRAW A TEST CIRCLE to prove the overlay is on top and working
        canvas.drawCircle(50f, 50f, 20f, dotPaint.apply { color = AndroidColor.GREEN })
        
        val p = pose ?: return
        if (width == 0 || height == 0) return

        fun pt(type: Int): android.graphics.PointF? {
            val lm = p.getPoseLandmark(type) ?: return null
            
            // Standard ML Kit coordinate mapping
            var x = lm.position.x
            var y = lm.position.y

            // Simple scaling
            val scaleX = width.toFloat() / imgW
            val scaleY = height.toFloat() / imgH
            
            // Mirror for front camera (usually needed)
            val finalX = width - (x * scaleX)
            val finalY = y * scaleY

            return android.graphics.PointF(finalX, finalY)
        }

        val bones = listOf(
            PoseLandmark.NOSE           to PoseLandmark.LEFT_EAR,
            PoseLandmark.NOSE           to PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_SHOULDER  to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER  to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW     to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW    to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_SHOULDER  to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP       to PoseLandmark.RIGHT_HIP
        )
        for ((a, b) in bones) {
            val pa = pt(a) ?: continue
            val pb = pt(b) ?: continue
            canvas.drawLine(pa.x, pa.y, pb.x, pb.y, bonePaint)
        }

        val keyPoints = listOf(
            PoseLandmark.NOSE, PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP
        )
        for (type in keyPoints) {
            val pt = pt(type) ?: continue
            canvas.drawCircle(pt.x, pt.y, 11f, dotPaint)
        }
    }
}

// ── Pose logic (shoulder-width normalised, resolution-independent) ──
fun checkPoseWithFeedback(pose: Pose, step: PoseStep): Pair<Boolean, String> {
    if (pose.allPoseLandmarks.isEmpty())
        return false to "No person detected — step back"

    val nose      = pose.getPoseLandmark(PoseLandmark.NOSE)
    val lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
    val rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
    val lWrist    = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
    val rWrist    = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

    if (nose == null || lShoulder == null || rShoulder == null) {
        return false to "Show your head and shoulders in frame"
    }

    // IGNORE LIKELIHOOD - If the landmark exists, use it
    val midX = (lShoulder.position.x + rShoulder.position.x) / 2f
    val midY = (lShoulder.position.y + rShoulder.position.y) / 2f
    val sw   = abs(rShoulder.position.x - lShoulder.position.x).coerceAtLeast(10f)

    return when (step.name) {
        "Neck Left" -> {
            val offset = (nose.position.x - midX) / sw
            Log.d(TAG, "Pose: Neck Left, offset: $offset, target: 0.1")
            if (offset > 0.1f) true to "✓ Neck Left — hold it!"
            else false to "Turn head LEFT — ${pct(offset, 0.1f)}% there"
        }
        "Neck Right" -> {
            val offset = (midX - nose.position.x) / sw
            Log.d(TAG, "Pose: Neck Right, offset: $offset, target: 0.1")
            if (offset > 0.1f) true to "✓ Neck Right — hold it!"
            else false to "Turn head RIGHT — ${pct(offset, 0.1f)}% there"
        }
        "Neck Up" -> {
            val normUp = (midY - nose.position.y) / sw
            Log.d(TAG, "Pose: Neck Up, normUp: $normUp, target: 0.25")
            if (normUp > 0.25f) true to "✓ Looking Up — hold it!"
            else false to "Look UP at the ceiling — ${pct(normUp, 0.25f)}% there"
        }
        "Neck Down" -> {
            val normDown = (nose.position.y - midY) / sw
            Log.d(TAG, "Pose: Neck Down, normDown: $normDown, target: -0.3")
            if (normDown > -0.3f) true to "✓ Chin Down — hold it!"
            else false to "Drop chin to chest — ${pct(normDown + 0.4f, 0.2f)}% there"
        }
        "Both Arms Up" -> {
            if (lWrist == null || rWrist == null) {
                Log.d(TAG, "Pose: Both Arms Up, wrists missing (L: ${lWrist != null}, R: ${rWrist != null})")
                return false to "Wrists not visible — raise arms fully"
            }
            val lUp = (lShoulder.position.y - lWrist.position.y) / sw
            val rUp = (rShoulder.position.y - rWrist.position.y) / sw
            val t   = 0.15f
            Log.d(TAG, "Pose: Both Arms Up, lUp: $lUp, rUp: $rUp, target: $t")
            when {
                lUp >= t && rUp >= t -> true  to "✓ Both Arms Up — hold it!"
                lUp < t && rUp < t   -> false to "Raise BOTH arms above head — ${pct(minOf(lUp, rUp), t)}%"
                lUp < t              -> false to "Left arm needs to go higher"
                else                 -> false to "Right arm needs to go higher"
            }
        }
        else -> {
            val ok = (nose.inFrameLikelihood > 0.45f) && (lShoulder.inFrameLikelihood > 0.45f)
            if (ok) true to "✓ Pose OK — hold it!" else false to "Stand in frame"
        }
    }
}

private fun pct(value: Float, max: Float): Int =
    ((value / max) * 100f).toInt().coerceIn(0, 99)

// Keep for any legacy callers
fun checkPose(pose: Pose, step: PoseStep): Boolean = checkPoseWithFeedback(pose, step).first
