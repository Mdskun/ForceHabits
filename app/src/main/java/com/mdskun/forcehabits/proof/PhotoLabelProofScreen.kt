package com.mdskun.forcehabits.proof

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.mdskun.forcehabits.data.model.*
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoLabelProofScreen(habit: Habit, onCompleted: () -> Unit) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }
    if (cameraPermission.status.isGranted) {
        PhotoLabelCamera(habit = habit, onCompleted = onCompleted)
    } else {
        CameraPermissionUI { cameraPermission.launchPermissionRequest() }
    }
}

@Composable
private fun CameraPermissionUI(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📷", fontSize = 52.sp)
        Spacer(Modifier.height(16.dp))
        Text("Camera Permission Required", fontWeight = FontWeight.Bold,
            fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Grant camera access to take proof photos.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))) {
            Text("Grant Camera Permission")
        }
    }
}

@Composable
private fun PhotoLabelCamera(habit: Habit, onCompleted: () -> Unit) {
    val config = remember {
        try { json.decodeFromString<PhotoLabelConfig>(habit.proofConfig) }
        catch (e: Exception) {
            when (habit.type) {
                HabitType.PLANT_CARE -> plantPhotoConfig()
                HabitType.HYDRATION  -> hydrationPhotoConfig()
                else -> PhotoLabelConfig(requiredLabels = listOf("Object"))
            }
        }
    }

    // Use a ref so the capture button always sees the latest ImageCapture instance
    // regardless of when the camera finishes binding
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    var cameraReady     by remember { mutableStateOf(false) }
    var analysisState   by remember { mutableStateOf<AnalysisState>(AnalysisState.Idle) }

    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Use a single-thread executor for the capture callback (off main thread)
    val executor       = remember { Executors.newSingleThreadExecutor() }

    val labeler = remember {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(config.minimumConfidence)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            labeler.close()
            executor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

        Text(
            getPhotoInstruction(habit.type),
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp)
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                            .build()

                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                            // Only mark ready AFTER successful bind
                            imageCaptureRef.value = capture
                            cameraReady = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading overlay while camera binds
            if (!cameraReady) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF6B35))
                        Spacer(Modifier.height(8.dp))
                        Text("Starting camera...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // Analysis overlay
            if (analysisState != AnalysisState.Idle) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    when (val s = analysisState) {
                        is AnalysisState.Analyzing -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFFF6B35))
                                Spacer(Modifier.height(8.dp))
                                Text("Analyzing photo...", color = Color.White, fontSize = 14.sp)
                            }
                        }
                        is AnalysisState.Failed -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text("❌", fontSize = 48.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(s.message, color = Color.White,
                                    textAlign = TextAlign.Center, fontSize = 14.sp)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { analysisState = AnalysisState.Idle },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                                ) { Text("Try Again") }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // Capture button — only enabled when camera is ready and not currently analyzing
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val capture = imageCaptureRef.value
                if (capture == null) {
                    // Camera not ready yet — show message
                    analysisState = AnalysisState.Failed("Camera not ready yet. Wait a moment and try again.")
                    return@Button
                }
                analysisState = AnalysisState.Analyzing
                captureAndAnalyze(context, capture, labeler, config, executor) { result ->
                    analysisState = result
                    if (result is AnalysisState.Success) onCompleted()
                }
            },
            enabled = cameraReady && analysisState == AnalysisState.Idle,
            modifier = Modifier.size(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                disabledContainerColor = Color(0xFF555555)
            ),
            shape = CircleShape
        ) {
            Text("📷", fontSize = 24.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun getPhotoInstruction(type: HabitType): String = when (type) {
    HabitType.PLANT_CARE -> "📷 Take a photo of your plant 🪴"
    HabitType.HYDRATION  -> "📷 Take a photo of your water/drink 💧"
    else -> "📷 Take a photo as proof"
}

sealed class AnalysisState {
    object Idle : AnalysisState()
    object Analyzing : AnalysisState()
    data class Success(val labels: List<String>) : AnalysisState()
    data class Failed(val message: String) : AnalysisState()
}

private fun captureAndAnalyze(
    context: Context,
    imageCapture: ImageCapture,           // non-null — caller guarantees this
    labeler: com.google.mlkit.vision.label.ImageLabeler,
    config: PhotoLabelConfig,
    executor: java.util.concurrent.Executor,
    onResult: (AnalysisState) -> Unit
) {
    val photoFile = File(context.cacheDir, "proof_${System.currentTimeMillis()}.jpg")

    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(photoFile).build(),
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val image = InputImage.fromFilePath(context, Uri.fromFile(photoFile))
                    labeler.process(image)
                        .addOnSuccessListener { labels ->
                            val detectedLabels = labels.map { it.text }
                            val matched = config.requiredLabels.any { required ->
                                detectedLabels.any { detected ->
                                    detected.contains(required, ignoreCase = true) ||
                                    required.contains(detected, ignoreCase = true)
                                }
                            }
                            if (matched) {
                                onResult(AnalysisState.Success(detectedLabels))
                            } else {
                                onResult(AnalysisState.Failed(
                                    "Didn't see: ${config.requiredLabels.take(3).joinToString(", ")}\n" +
                                    "Detected: ${detectedLabels.take(4).joinToString(", ")}\n\n" +
                                    "Move closer, better lighting, or try again."
                                ))
                            }
                        }
                        .addOnFailureListener { e ->
                            onResult(AnalysisState.Failed("ML Kit error: ${e.message}\nTry again."))
                        }
                } catch (e: Exception) {
                    onResult(AnalysisState.Failed("Photo error: ${e.message}\nTry again."))
                }
            }

            override fun onError(exc: ImageCaptureException) {
                onResult(AnalysisState.Failed("Camera capture failed: ${exc.message}\nTry again."))
            }
        }
    )
}
