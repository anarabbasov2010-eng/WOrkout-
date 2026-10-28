package com.workout.app.anatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workout.app.R
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode
import org.json.JSONObject

private val AnatomyLime = Color(0xFFB7F34A)
private val AnatomyInk = Color(0xFF0B0D0C)
private val AnatomyPanel = Color(0xFF191D1B)
private val AnatomyPanelRaised = Color(0xFF222923)
private val AnatomyMuted = Color(0xFFADB6AE)

enum class BodyLayer(val labelRes: Int) {
    MUSCLES(R.string.anatomy_layer_muscles),
    SKELETON(R.string.anatomy_layer_skeleton),
    BOTH(R.string.anatomy_layer_both)
}

enum class TargetMuscle(val labelRes: Int, val descriptionRes: Int) {
    CORE(R.string.anatomy_target_core, R.string.anatomy_desc_core),
    CHEST(R.string.anatomy_target_chest, R.string.anatomy_desc_chest),
    BACK(R.string.anatomy_target_back, R.string.anatomy_desc_back),
    LEGS(R.string.anatomy_target_legs, R.string.anatomy_desc_legs),
    ARMS(R.string.anatomy_target_arms, R.string.anatomy_desc_arms)
}

@Composable
fun AnatomyStudioScreen(onBack: () -> Unit) {
    var layer by remember { mutableStateOf(BodyLayer.MUSCLES) }
    var target by remember { mutableStateOf<TargetMuscle?>(null) }
    var autoRotate by remember { mutableStateOf(false) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val centerNode = rememberNode(engine) { position = Position(y = 0.86f) }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(y = 0.86f, z = 4.35f)
        lookAt(centerNode)
    }
    val modelNode = rememberNode {
        ModelNode(
            modelInstance = modelLoader.createModelInstance(assetFileLocation = "models/ecorche.glb"),
            scaleToUnits = 1.7f
        )
    }
    val rotationState = remember { floatArrayOf(0f) }
    val lastFrameNanos = remember { longArrayOf(0L) }

    LaunchedEffect(modelNode, layer, target) {
        applyModelVisibility(modelNode, layer, target)
    }

    Column(
        Modifier.fillMaxSize().background(AnatomyInk)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp).background(AnatomyPanel, RoundedCornerShape(15.dp))
            ) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.anatomy_back), tint = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.anatomy_kicker),
                    color = AnatomyLime,
                    fontSize = 10.sp,
                    letterSpacing = 1.7.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    stringResource(R.string.anatomy_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Box(Modifier.size(42.dp).background(AnatomyPanel, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.RotateRight, null, tint = AnatomyLime, modifier = Modifier.size(21.dp))
            }
        }

        Text(
            stringResource(R.string.anatomy_subtitle),
            color = AnatomyMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 2.dp)
        )

        Box(
            Modifier.fillMaxWidth().weight(1f).defaultMinSize(minHeight = 260.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1C261C), Color(0xFF101512), Color(0xFF151B17))),
                    RoundedCornerShape(28.dp)
                )
                .border(1.dp, Color(0xFF344331), RoundedCornerShape(28.dp))
        ) {
            Scene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                modelLoader = modelLoader,
                cameraNode = cameraNode,
                cameraManipulator = rememberCameraManipulator(
                    orbitHomePosition = cameraNode.worldPosition,
                    targetPosition = centerNode.worldPosition
                ),
                childNodes = listOf(centerNode, modelNode),
                onFrame = { frame ->
                    if (lastFrameNanos[0] != 0L && autoRotate) {
                        val deltaSeconds = ((frame - lastFrameNanos[0]).coerceAtLeast(0L)) / 1_000_000_000f
                        rotationState[0] = (rotationState[0] + deltaSeconds * 7f) % 360f
                        modelNode.rotation = Rotation(y = rotationState[0])
                    }
                    lastFrameNanos[0] = frame
                }
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(13.dp)
                    .background(Color(0xD9161D18), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(6.dp).background(AnatomyLime, CircleShape))
                Text(
                    stringResource(R.string.anatomy_live_label),
                    color = Color.White,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = {
                    rotationState[0] = 0f
                    modelNode.rotation = Rotation(y = 0f)
                    cameraNode.position = Position(y = 0.86f, z = 4.35f)
                    cameraNode.lookAt(centerNode)
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                    .size(38.dp).background(Color(0xD9161D18), CircleShape)
            ) {
                Icon(Icons.Filled.RotateRight, stringResource(R.string.anatomy_reset_view), tint = AnatomyLime)
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(14.dp)
                    .background(Color(0xD9161D18), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.anatomy_drag_label),
                    color = AnatomyLime,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    stringResource(R.string.anatomy_drag_hint),
                    color = Color.White.copy(alpha = .82f),
                    fontSize = 10.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionLabel(stringResource(R.string.anatomy_layers_header))
            Row(
                Modifier.fillMaxWidth().background(AnatomyPanel, RoundedCornerShape(17.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BodyLayer.entries.forEach { item ->
                    val selected = layer == item
                    Surface(
                        onClick = { layer = item },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                        color = if (selected) AnatomyLime else Color.Transparent
                    ) {
                        Text(
                            stringResource(item.labelRes),
                            color = if (selected) AnatomyInk else AnatomyMuted,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 11.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = AnatomyPanel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SectionLabel(stringResource(R.string.anatomy_focus_header))
                        Text(
                            target?.let { stringResource(it.labelRes) } ?: stringResource(R.string.anatomy_all_groups),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            target?.let { (TargetMuscle.entries.indexOf(it) + 1).toString() + "/05" } ?: "ALL",
                            color = AnatomyLime,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(stringResource(R.string.anatomy_focus_tag), color = AnatomyMuted, fontSize = 9.sp, letterSpacing = .8.sp)
                    }
                }
                Text(
                    target?.let { stringResource(it.descriptionRes) } ?: stringResource(R.string.anatomy_desc_all),
                    color = AnatomyMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    FilterChip(
                        selected = target == null,
                        onClick = { target = null },
                        label = { Text(stringResource(R.string.anatomy_all_groups), fontSize = 11.sp, maxLines = 1) },
                        colors = focusChipColors()
                    )
                    TargetMuscle.entries.forEach { item ->
                        FilterChip(
                            selected = target == item,
                            onClick = { target = if (target == item) null else item },
                            label = { Text(stringResource(item.labelRes), fontSize = 11.sp, maxLines = 1) },
                            colors = focusChipColors()
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFF303831))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.anatomy_motion_title), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.anatomy_motion_subtitle), color = AnatomyMuted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    Switch(
                        checked = autoRotate,
                        onCheckedChange = { autoRotate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AnatomyInk,
                            checkedTrackColor = AnatomyLime,
                            uncheckedThumbColor = AnatomyMuted,
                            uncheckedTrackColor = AnatomyPanelRaised
                        )
                    )
                }
            }
        }
        Text(stringResource(R.string.anatomy_disclaimer), color = AnatomyMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 2.dp))
        Text(stringResource(R.string.anatomy_asset_credit), color = AnatomyMuted.copy(alpha = .8f), fontSize = 9.sp, lineHeight = 13.sp, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@Composable
private fun focusChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color(0xFF35452A),
    selectedLabelColor = AnatomyLime,
    labelColor = AnatomyMuted
)

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AnatomyMuted, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.ExtraBold)
}

private fun applyModelVisibility(model: ModelNode, layer: BodyLayer, target: TargetMuscle?) {
    model.renderableNodes.forEach { node ->
        val metadata = runCatching { JSONObject(node.extras ?: "{}") }.getOrNull()
        val name = (metadata?.optString("name")?.takeIf { it.isNotBlank() } ?: node.name.orEmpty()).lowercase()
        val tissueLayer = metadata?.optString("layer").orEmpty().lowercase()
        val isMuscle = tissueLayer.contains("muscle")
        val isSkeleton = tissueLayer == "skeleton"
        val matchingGroup = target == null || isTargetMuscle(name, target)
        node.isVisible = when (layer) {
            BodyLayer.MUSCLES -> isMuscle && matchingGroup
            BodyLayer.SKELETON -> isSkeleton
            BodyLayer.BOTH -> isSkeleton || (isMuscle && matchingGroup)
        }
    }
}

private fun isTargetMuscle(name: String, target: TargetMuscle): Boolean {
    val patterns = when (target) {
        TargetMuscle.CORE -> listOf("rectus abdominis", "external oblique", "internal oblique", "transversus abdominis", "quadratus lumborum", "psoas", "iliacus", "multifidus", "rotatores", "erector spinae", "thoracolumbar fascia", "intertransversarii")
        TargetMuscle.CHEST -> listOf("pectoralis", "serratus anterior", "subclavius", "intercostal", "transversus thoracis")
        TargetMuscle.BACK -> listOf("trapezius", "latissimus dorsi", "rhomboid", "levator scapulae", "teres major", "teres minor", "supraspinatus", "infraspinatus", "serratus posterior", "erector spinae", "multifidus", "thoracolumbar fascia", "splenius")
        TargetMuscle.LEGS -> listOf("gluteus", "tensor fasciae latae", "quadriceps", "vastus", "rectus femoris", "hamstring", "biceps femoris", "semitendinosus", "semimembranosus", "sartorius", "adductor", "gracilis", "gastrocnemius", "soleus", "tibialis", "fibularis", "peroneus", "plantaris", "popliteus", "extensor hallucis", "extensor digitorum longus", "flexor hallucis", "flexor digitorum longus", "iliopsoas")
        TargetMuscle.ARMS -> listOf("deltoid", "biceps brachii", "triceps brachii", "brachialis", "brachioradialis", "anconeus", "pronator", "supinator", "flexor carpi", "extensor carpi", "flexor digitorum superficialis", "extensor digitorum", "palmaris", "lumbrical", "thenar", "hypothenar", "abductor pollicis", "adductor pollicis", "opponens pollicis", "interossei")
    }
    return patterns.any(name::contains)
}
