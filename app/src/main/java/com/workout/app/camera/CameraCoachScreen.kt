package com.workout.app.camera

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.key
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import com.workout.app.R
import com.workout.app.pose.Movement
import com.workout.app.pose.PoseRepCounter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private val CoachLime = Color(0xFFB7F34A)
private val CoachBg = Color(0xFF0B0D0C)
private val CoachCard = Color(0xFF191D1B)
private val CoachRaised = Color(0xFF242B25)
private val CoachMuted = Color(0xFFADB6AE)
private val CoachWarning = Color(0xFFFFB45B)

private enum class CameraCue(val resource: Int, val warning: Boolean = false) {
    WAITING(R.string.camera_cue_waiting),
    LANDMARKS_NOT_VISIBLE(R.string.camera_cue_landmarks_missing, true),
    LOW_CONFIDENCE(R.string.camera_cue_low_confidence, true),
    ANGLE_UNAVAILABLE(R.string.camera_cue_angle_unavailable, true),
    SQUAT_LOW(R.string.camera_cue_squat_low),
    SQUAT_HIGH(R.string.camera_cue_squat_high),
    SQUAT_MID(R.string.camera_cue_squat_mid),
    PUSHUP_LOW(R.string.camera_cue_pushup_low),
    PUSHUP_HIGH(R.string.camera_cue_pushup_high),
    PUSHUP_MID(R.string.camera_cue_pushup_mid),
    CURL_HIGH(R.string.camera_cue_curl_high),
    CURL_LOW(R.string.camera_cue_curl_low),
    CURL_MID(R.string.camera_cue_curl_mid)
}

private data class PoseMetrics(val angleDegrees: Float?, val confidence: Float, val cue: CameraCue) {
    fun display(context: Context): String {
        val message = context.getString(cue.resource)
        if (angleDegrees == null) return message
        val metrics = context.getString(
            R.string.camera_metrics_format,
            angleDegrees.roundToInt(),
            (confidence * 100).roundToInt()
        )
        return "$metrics\n$message"
    }
}

private enum class CameraMode { FRONT, BACK }

private fun movementNameResource(movement: Movement): Int = when (movement) {
    Movement.SQUAT -> R.string.exercise_squat
    Movement.PUSH_UP -> R.string.exercise_push_up
    Movement.BICEP_CURL -> R.string.exercise_curl
    Movement.LUNGE -> R.string.exercise_lunge
}

@Composable
fun CameraCoachScreen(exercise: String, onRepCount: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialMovement = Movement.entries.firstOrNull { it.label.equals(exercise, ignoreCase = true) } ?: Movement.SQUAT
    var movement by remember(exercise) { mutableStateOf(initialMovement) }
    var reps by remember { mutableIntStateOf(0) }
    var sessionReps by remember(exercise) { mutableIntStateOf(0) }
    var counterResetToken by remember { mutableIntStateOf(0) }
    var lens by remember { mutableStateOf(CameraMode.FRONT) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var poseFeedback by remember { mutableStateOf(context.getString(R.string.camera_cue_waiting)) }
    var poseWarning by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val movementRef = remember { AtomicReference(movement) }
    LaunchedEffect(movement) {
        movementRef.set(movement)
        reps = 0
        poseFeedback = context.getString(R.string.camera_cue_waiting)
        poseWarning = false
    }

    LazyColumn(
        Modifier.fillMaxSize().background(CoachBg),
        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(43.dp).background(CoachCard, RoundedCornerShape(15.dp))
                ) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.camera_back), tint = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.camera_kicker),
                        color = CoachLime,
                        fontSize = 9.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        stringResource(R.string.camera_coach),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(Modifier.size(42.dp).background(CoachCard, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, null, tint = CoachLime, modifier = Modifier.size(21.dp))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.camera_choose_movement),
                        color = CoachMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.camera_on_device_tag),
                        color = CoachLime,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = .8.sp
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Movement.entries.forEach { type ->
                        FilterChip(
                            selected = movement == type,
                            onClick = { movement = type },
                            label = { Text(stringResource(movementNameResource(type)), fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF35472A),
                                selectedLabelColor = CoachLime,
                                labelColor = CoachMuted
                            )
                        )
                    }
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 410.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF273326), Color(0xFF151A16), Color(0xFF0F1310)))
                    )
                    .border(1.dp, Color(0xFF354331), RoundedCornerShape(25.dp))
            ) {
                if (permissionGranted) {
                    key(lens, counterResetToken) {
                        CameraPreview(
                            lifecycleOwner = lifecycleOwner,
                            movement = movementRef,
                            frontCamera = lens == CameraMode.FRONT,
                            onNewReps = { value ->
                                reps = value
                                sessionReps = (sessionReps + 1).coerceAtMost(100_000)
                                onRepCount(sessionReps)
                            },
                            onPoseMetrics = { metrics ->
                                poseFeedback = metrics.display(context)
                                poseWarning = metrics.cue.warning
                            },
                            onError = { cameraError = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        Modifier.align(Alignment.TopStart).padding(12.dp)
                            .background(Color(0xD90B0D0C), RoundedCornerShape(11.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(CoachLime, CircleShape))
                        Text(
                            stringResource(R.string.camera_live),
                            color = Color.White,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = { lens = if (lens == CameraMode.FRONT) CameraMode.BACK else CameraMode.FRONT },
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                            .size(38.dp).background(Color(0xD90B0D0C), CircleShape)
                    ) {
                        Icon(Icons.Filled.FlipCameraAndroid, stringResource(R.string.camera_flip), tint = CoachLime)
                    }
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(12.dp)
                            .fillMaxWidth(.88f)
                            .background(Color(0xE60B0D0C), RoundedCornerShape(13.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.camera_frame_hint_title),
                            color = CoachLime,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            stringResource(R.string.camera_frame_hint_body),
                            color = Color.White.copy(alpha = .88f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    Column(
                        Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(66.dp).background(Color(0xFF35472A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Visibility, null, tint = CoachLime, modifier = Modifier.size(32.dp))
                        }
                        Text(
                            stringResource(R.string.camera_permission_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.permission_camera),
                            color = CoachMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { requestPermission.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = CoachLime, contentColor = CoachBg),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.camera_allow), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CoachCard), shape = RoundedCornerShape(23.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.camera_rep_counter),
                                color = CoachMuted,
                                fontSize = 9.sp,
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                stringResource(movementNameResource(movement)),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            Modifier.size(74.dp).background(Color(0xFF35472A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(reps.toString(), color = CoachLime, fontSize = 31.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Text(
                        stringResource(R.string.camera_rep_counter_hint),
                        color = CoachMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                reps = (reps + 1).coerceAtMost(100_000)
                                sessionReps = (sessionReps + 1).coerceAtMost(100_000)
                                onRepCount(sessionReps)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CoachLime, contentColor = CoachBg),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Filled.FitnessCenter, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(stringResource(R.string.camera_manual_rep), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { reps = 0; counterResetToken++ },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.camera_reset_display), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CoachCard),
                shape = RoundedCornerShape(19.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (poseWarning) Icons.Filled.Visibility else Icons.Filled.CheckCircle,
                        null,
                        tint = if (poseWarning) CoachWarning else CoachLime,
                        modifier = Modifier.size(21.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            stringResource(R.string.camera_feedback_header),
                            color = if (poseWarning) CoachWarning else CoachLime,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            poseFeedback,
                            color = Color.White,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        if (cameraError != null) {
                            Text(
                                stringResource(R.string.camera_error_message, cameraError!!),
                                color = CoachWarning,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.camera_privacy_note),
                color = CoachMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    movement: AtomicReference<Movement>,
    frontCamera: Boolean,
    onNewReps: (Int) -> Unit,
    onPoseMetrics: (PoseMetrics) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val detectorRef = remember { AtomicReference<PoseDetector?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val counterRef = remember { AtomicReference(movement.get() to PoseRepCounter(movement.get())) }
    val lastMetricPost = remember { AtomicLong(0L) }

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
                                        val metrics = extractPoseMetrics(pose, selected)
                                        val value = pair.second.update(metrics.angleDegrees, metrics.confidence)
                                        val now = System.currentTimeMillis()
                                        if (now - lastMetricPost.get() >= 400L) {
                                            lastMetricPost.set(now)
                                            main.post { onPoseMetrics(metrics) }
                                        }
                                        if (value != null) main.post { onNewReps(value) }
                                    }
                                    .addOnFailureListener { error ->
                                        main.post {
                                            onError(ctx.getString(
                                                R.string.camera_error_pose,
                                                error.localizedMessage ?: ctx.getString(R.string.camera_error_unknown)
                                            ))
                                        }
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                        }
                        provider.unbindAll()
                        val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    } catch (error: Exception) {
                        main.post {
                            onError(ctx.getString(
                                R.string.camera_error_start,
                                error.localizedMessage ?: ctx.getString(R.string.camera_error_unknown)
                            ))
                        }
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

private fun extractPoseMetrics(pose: Pose, movement: Movement): PoseMetrics {
    val landmarkTriples = when (movement) {
        Movement.SQUAT, Movement.LUNGE -> listOf(
            Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        )
        Movement.PUSH_UP, Movement.BICEP_CURL -> listOf(
            Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        )
    }
    val candidates = landmarkTriples.mapNotNull { trio ->
        val points = listOf(trio.first, trio.second, trio.third).map { pose.getPoseLandmark(it) }
        if (points.any { it == null }) return@mapNotNull null
        val first = points[0]!!
        val middle = points[1]!!
        val last = points[2]!!
        val confidence = minOf(first.inFrameLikelihood, middle.inFrameLikelihood, last.inFrameLikelihood)
        val angle = PoseRepCounter.angle(
            first.position.x, first.position.y,
            middle.position.x, middle.position.y,
            last.position.x, last.position.y
        )
        angle to confidence
    }
    val best = candidates.maxByOrNull { it.second }
        ?: return PoseMetrics(null, 0f, CameraCue.LANDMARKS_NOT_VISIBLE)
    val angle = best.first
    val confidence = best.second
    val cue = when {
        confidence < PoseRepCounter.MIN_CONFIDENCE -> CameraCue.LOW_CONFIDENCE
        !angle.isFinite() -> CameraCue.ANGLE_UNAVAILABLE
        movement == Movement.SQUAT || movement == Movement.LUNGE -> when {
            angle < 105f -> CameraCue.SQUAT_LOW
            angle > 155f -> CameraCue.SQUAT_HIGH
            else -> CameraCue.SQUAT_MID
        }
        movement == Movement.PUSH_UP -> when {
            angle < 100f -> CameraCue.PUSHUP_LOW
            angle > 150f -> CameraCue.PUSHUP_HIGH
            else -> CameraCue.PUSHUP_MID
        }
        else -> when {
            angle > 145f -> CameraCue.CURL_HIGH
            angle < 65f -> CameraCue.CURL_LOW
            else -> CameraCue.CURL_MID
        }
    }
    return PoseMetrics(angle.takeIf { it.isFinite() }, confidence, cue)
}
