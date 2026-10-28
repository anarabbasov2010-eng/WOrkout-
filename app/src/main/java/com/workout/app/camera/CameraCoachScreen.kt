package com.workout.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import com.workout.app.pose.Movement
import com.workout.app.pose.PoseRepCounter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private val CoachLime = Color(0xFFB7F34A)
private val CoachBg = Color(0xFF0B0D0C)
private val CoachCard = Color(0xFF191D1B)

@Composable
fun CameraCoachScreen(exercise: String, onRepCount: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialMovement = Movement.entries.firstOrNull { it.label.equals(exercise, ignoreCase = true) } ?: Movement.SQUAT
    var movement by remember(exercise) { mutableStateOf(initialMovement) }
    var reps by remember { mutableIntStateOf(0) }
    var sessionReps by remember(exercise) { mutableIntStateOf(0) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val movementRef = remember { AtomicReference(movement) }
    LaunchedEffect(movement) {
        movementRef.set(movement)
        reps = 0
    }

    Column(Modifier.fillMaxSize().background(CoachBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("CAMERA TECHNIQUE", color = CoachLime, fontSize = 11.sp)
                Text(movement.label, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.CameraAlt, null, tint = CoachLime)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Movement.entries.forEach { type ->
                FilterChip(selected = movement == type, onClick = { movement = type }, label = { Text(type.label, fontSize = 10.sp) })
            }
        }
        if (permissionGranted) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                movement = movementRef,
                onNewReps = { value ->
                    reps = value
                    sessionReps = (sessionReps + 1).coerceAtMost(100_000)
                    onRepCount(sessionReps)
                },
                onError = { cameraError = it },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).background(CoachCard, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.CameraAlt, null, tint = CoachLime, modifier = Modifier.size(44.dp))
                    Text("Camera permission is needed for on-device pose analysis.", color = Color.White)
                    Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = CoachCard), shape = RoundedCornerShape(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("REP COUNTER", color = CoachLime, fontSize = 11.sp)
                    Text("$reps", color = Color.White, style = MaterialTheme.typography.displayMedium)
                    Text("estimate from visible pose landmarks", color = Color.Gray, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    FilledTonalButton(onClick = {
                        reps = (reps + 1).coerceAtMost(100_000)
                        sessionReps = (sessionReps + 1).coerceAtMost(100_000)
                        onRepCount(sessionReps)
                    }) {
                        Icon(Icons.Filled.FitnessCenter, null)
                        Spacer(Modifier.width(5.dp))
                        Text("+1 manual")
                    }
                    TextButton(onClick = { reps = 0 }) { Text("Clear display") }
                }
            }
        }
        Text(
            cameraError ?: "Camera frames are processed on this device and are not saved or uploaded. Accuracy depends on lighting, framing and joint visibility. Stop if you feel pain or dizziness.",
            color = Color(0xFFB8C0B8), fontSize = 11.sp
        )
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    movement: AtomicReference<Movement>,
    onNewReps: (Int) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val detectorRef = remember { AtomicReference<PoseDetector?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val counterRef = remember { AtomicReference(movement.get() to PoseRepCounter(movement.get())) }

    DisposableEffect(Unit) {
        onDispose {
            providerRef.get()?.unbindAll()
            detectorRef.get()?.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier.background(CoachCard, RoundedCornerShape(24.dp)),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val provider = future.get()
                        providerRef.set(provider)
                        val detector = PoseDetection.getClient(
                            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
                        )
                        detectorRef.set(detector)
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null) {
                                proxy.close()
                            } else {
                                val selected = movement.get()
                                var pair = counterRef.get()
                                if (pair.first != selected) {
                                    pair = selected to PoseRepCounter(selected)
                                    counterRef.set(pair)
                                }
                                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                detector.process(image)
                                    .addOnSuccessListener { pose ->
                                        val (angle, confidence) = extractAngle(pose, selected)
                                        val value = pair.second.update(angle, confidence)
                                        if (value != null) main.post { onNewReps(value) }
                                    }
                                    .addOnFailureListener { error ->
                                        main.post { onError("Pose estimate unavailable: ${error.localizedMessage ?: "unknown error"}") }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                    } catch (error: Exception) {
                        main.post { onError("Camera could not start: ${error.localizedMessage ?: "device unavailable"}") }
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

private fun extractAngle(pose: Pose, movement: Movement): Pair<Float?, Float> {
    val trio = when (movement) {
        Movement.SQUAT, Movement.LUNGE -> Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        Movement.PUSH_UP -> Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        Movement.BICEP_CURL -> Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
    }
    val landmarks = listOf(trio.first, trio.second, trio.third).map { pose.getPoseLandmark(it) }
    if (landmarks.any { it == null }) return null to 0f
    val first = landmarks[0]!!
    val middle = landmarks[1]!!
    val last = landmarks[2]!!
    val confidence = minOf(first.inFrameLikelihood, middle.inFrameLikelihood, last.inFrameLikelihood)
    val angle = PoseRepCounter.angle(
        first.position.x, first.position.y,
        middle.position.x, middle.position.y,
        last.position.x, last.position.y
    )
    return angle to confidence
}
