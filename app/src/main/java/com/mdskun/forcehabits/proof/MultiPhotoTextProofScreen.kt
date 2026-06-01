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
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MultiPhotoTextProofScreen(
    habit: com.mdskun.forcehabits.data.model.Habit,
    onCompleted: () -> Unit
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }
    if (!cameraPermission.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📷", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))
            Text("Camera Permission Required", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { cameraPermission.launchPermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))) {
                Text("Grant Camera Permission")
            }
        }
        return
    }

    MultiPhotoCamera(habit = habit, onCompleted = onCompleted)
}

@Composable
private fun MultiPhotoCamera(
    habit: com.mdskun.forcehabits.data.model.Habit,
    onCompleted: () -> Unit
) {
    val requiredPhotos   = 4
    var photosCompleted  by remember { mutableIntStateOf(0) }
    var capturedTexts    by remember { mutableStateOf(listOf<String>()) }
    var isCapturing      by remember { mutableStateOf(false) }
    var errorMessage     by remember { mutableStateOf("") }
    var imageCapture     by remember { mutableStateOf<ImageCapture?>(null) }
    val context           = LocalContext.current
    val lifecycleOwner    = LocalLifecycleOwner.current
    val executor          = remember { Executors.newSingleThreadExecutor() }
    val recognizer        = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    DisposableEffect(Unit) { onDispose { recognizer.close(); executor.shutdown() } }

    if (photosCompleted >= requiredPhotos) {
        LaunchedEffect(Unit) { onCompleted() }
        return
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📚 Reading Proof", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Photo ${photosCompleted + 1} of $requiredPhotos — Show a DIFFERENT page each time",
                color = Color(0xFFAAAAAA), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(requiredPhotos) { i ->
                    Box(modifier = Modifier.size(16.dp).background(
                        when {
                            i < photosCompleted  -> Color(0xFF4CAF50)
                            i == photosCompleted -> Color(0xFFFF6B35)
                            else                 -> Color(0xFF444444)
                        }, CircleShape
                    ))
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { pv ->
                        ProcessCameraProvider.getInstance(ctx).also { future ->
                            future.addListener({
                                val provider = future.get()
                                val preview  = Preview.Builder().build()
                                    .also { it.setSurfaceProvider(pv.surfaceProvider) }
                                val capture  = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                                imageCapture = capture
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                } catch (e: Exception) { e.printStackTrace() }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (isCapturing) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF6B35))
                        Spacer(Modifier.height(8.dp))
                        Text("Reading text from page...", color = Color.White)
                    }
                }
            }
        }

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color(0xFFFF5252), fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                isCapturing = true
                errorMessage = ""
                capturePagePhoto(context, imageCapture, recognizer, executor, capturedTexts, photosCompleted)
                { ok, text, err ->
                    isCapturing = false
                    if (ok && text != null) {
                        capturedTexts = capturedTexts + text
                        photosCompleted++
                    } else {
                        errorMessage = err ?: "Try again with a clearer photo of a text page"
                    }
                }
            },
            enabled = !isCapturing,
            modifier = Modifier.size(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = CircleShape
        ) { Text("📷", fontSize = 24.sp) }
        Spacer(Modifier.height(16.dp))
    }
}

private fun capturePagePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    executor: java.util.concurrent.Executor,
    previousTexts: List<String>,
    photoIndex: Int,
    onResult: (Boolean, String?, String?) -> Unit
) {
    val file = File(context.cacheDir, "page_${photoIndex}_${System.currentTimeMillis()}.jpg")
    imageCapture?.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(), executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val words = visionText.text.split("\\s+".toRegex()).filter { it.length > 3 }
                        if (words.size < 30) {
                            onResult(false, null, "Not enough text (${words.size} words). Show a full text page.")
                            return@addOnSuccessListener
                        }
                        val snippet = words.take(20).joinToString(" ")
                        val isDuplicate = previousTexts.any { prev ->
                            val prevWords = prev.split("\\s+".toRegex()).take(20).joinToString(" ")
                            simpleSimilarity(snippet, prevWords) > 0.7
                        }
                        if (isDuplicate) onResult(false, null, "Same page detected. Show a different page.")
                        else onResult(true, visionText.text, null)
                    }
                    .addOnFailureListener { e ->
                        onResult(false, null, "Recognition failed: ${e.message}")
                    }
            }
            override fun onError(exc: ImageCaptureException) {
                onResult(false, null, "Camera error: ${exc.message}")
            }
        }
    )
}

private fun simpleSimilarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val common = a.zip(b).count { (c1, c2) -> c1 == c2 }
    return common.toDouble() / maxOf(a.length, b.length)
}
