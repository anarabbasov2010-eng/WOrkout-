package com.workout.app.anatomy

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.workout.app.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    var target by remember { mutableStateOf(TargetMuscle.CORE) }
    var slowMotion by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf<AnatomySurface?>(null) }

    Column(
        Modifier.fillMaxSize().background(AnatomyInk).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                )
                Text(
                    stringResource(R.string.anatomy_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                )
            }
            Box(
                Modifier.size(42.dp).background(AnatomyPanel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
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
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .defaultMinSize(minHeight = 270.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1C261C), Color(0xFF101512), Color(0xFF151B17))),
                    RoundedCornerShape(28.dp)
                )
                .border(1.dp, Color(0xFF344331), RoundedCornerShape(28.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    AnatomySurface(context).also {
                        surface = it
                        it.setLayer(layer)
                        it.setTarget(target)
                        it.setSlowMotion(slowMotion)
                    }
                },
                update = {
                    it.setLayer(layer)
                    it.setTarget(target)
                    it.setSlowMotion(slowMotion)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            IconButton(
                onClick = { surface?.resetRotation() },
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
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
                        onClick = { layer = item; surface?.setLayer(item) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(13.dp),
                        color = if (selected) AnatomyLime else Color.Transparent
                    ) {
                        Text(
                            stringResource(item.labelRes),
                            color = if (selected) AnatomyInk else AnatomyMuted,
                            fontSize = 11.sp,
                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 11.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = AnatomyPanel), shape = RoundedCornerShape(22.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SectionLabel(stringResource(R.string.anatomy_focus_header))
                        Text(
                            stringResource(target.labelRes),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            (TargetMuscle.entries.indexOf(target) + 1).toString() + "/05",
                            color = AnatomyLime,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                        )
                        Text(
                            stringResource(R.string.anatomy_focus_tag),
                            color = AnatomyMuted,
                            fontSize = 9.sp,
                            letterSpacing = .8.sp
                        )
                    }
                }
                Text(
                    stringResource(target.descriptionRes),
                    color = AnatomyMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    TargetMuscle.entries.forEach { item ->
                        FilterChip(
                            selected = target == item,
                            onClick = { target = item; surface?.setTarget(item) },
                            label = { Text(stringResource(item.labelRes), fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF35452A),
                                selectedLabelColor = AnatomyLime,
                                labelColor = AnatomyMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = target == item,
                                borderColor = Color(0xFF374139),
                                selectedBorderColor = Color(0xFF738B52)
                            )
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFF303831))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.anatomy_motion_title),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.anatomy_motion_subtitle),
                            color = AnatomyMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = slowMotion,
                        onCheckedChange = { slowMotion = it; surface?.setSlowMotion(it) },
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
        Text(
            stringResource(R.string.anatomy_disclaimer),
            color = AnatomyMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = AnatomyMuted,
        fontSize = 9.sp,
        letterSpacing = 1.3.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
    )
}

private class AnatomySurface(context: Context) : GLSurfaceView(context) {
    private val body = AnatomyRenderer()
    private var lastX = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(body)
        renderMode = RENDERMODE_CONTINUOUSLY
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; true }
                MotionEvent.ACTION_MOVE -> { body.rotation += (event.x - lastX) * .62f; lastX = event.x; true }
                else -> true
            }
        }
    }

    fun setLayer(layer: BodyLayer) { body.layer = layer }
    fun setTarget(target: TargetMuscle) { body.target = target }
    fun setSlowMotion(enabled: Boolean) { body.speed = if (enabled) .12f else .42f }
    fun resetRotation() { body.rotation = -12f }
}

private class AnatomyRenderer : GLSurfaceView.Renderer {
    @Volatile var rotation = -12f
    @Volatile var layer = BodyLayer.MUSCLES
    @Volatile var target = TargetMuscle.CORE
    @Volatile var speed = .42f

    private var program = 0
    private lateinit var sphere: SphereMesh
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val rotatedViewProjection = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val model = FloatArray(16)
    private val normalMatrix = FloatArray(16)
    private val inverseModel = FloatArray(16)
    private val mvp = FloatArray(16)
    private var posHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var normalMatrixHandle = 0
    private var colorHandle = 0
    private var frame = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.043f, .055f, .047f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        normalMatrixHandle = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        sphere = SphereMesh()
        Matrix.setLookAtM(view, 0, 0f, .08f, 5.2f, 0f, .02f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 39f, width.toFloat() / height.coerceAtLeast(1), .1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        frame += .012f * speed
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        Matrix.setRotateM(rotationMatrix, 0, rotation + sin(frame.toDouble()).toFloat() * 1.3f, 0f, 1f, 0f)
        Matrix.multiplyMM(rotatedViewProjection, 0, viewProjection, 0, rotationMatrix, 0)
        drawStage()
        if (layer != BodyLayer.SKELETON) drawMuscles()
        if (layer != BodyLayer.MUSCLES) drawSkeleton()
    }

    private fun drawStage() {
        part(0f, -1.10f, -.02f, .64f, .035f, .36f, floatArrayOf(.095f, .16f, .10f, 1f))
        part(0f, -1.075f, -.02f, .48f, .012f, .27f, floatArrayOf(.13f, .22f, .11f, 1f))
    }

    private fun drawMuscles() {
        val skin = floatArrayOf(.39f, .45f, .37f, 1f)
        val shadow = floatArrayOf(.16f, .21f, .17f, 1f)
        val torso = floatArrayOf(.25f, .32f, .26f, 1f)
        val chest = if (target == TargetMuscle.CHEST) lime() else floatArrayOf(.31f, .40f, .31f, 1f)
        val core = if (target == TargetMuscle.CORE) lime() else floatArrayOf(.27f, .35f, .27f, 1f)
        val back = if (target == TargetMuscle.BACK) lime() else torso
        val arm = if (target == TargetMuscle.ARMS) lime() else floatArrayOf(.31f, .38f, .30f, 1f)
        val leg = if (target == TargetMuscle.LEGS) lime() else floatArrayOf(.29f, .36f, .28f, 1f)

        // Head, jaw and neck.
        part(0f, 1.49f, 0f, .145f, .18f, .125f, skin)
        part(0f, 1.405f, .025f, .105f, .085f, .105f, skin)
        part(0f, 1.255f, 0f, .075f, .12f, .075f, skin)
        part(0f, 1.12f, -.015f, .255f, .115f, .12f, shadow)

        // Upper torso silhouette, with paired pectorals and deltoid caps.
        part(0f, .87f, 0f, .265f, .32f, .145f, torso)
        part(-.135f, .955f, .105f, .155f, .145f, .082f, chest)
        part(.135f, .955f, .105f, .155f, .145f, .082f, chest)
        part(0f, .81f, .115f, .025f, .12f, .028f, shadow)
        for (side in listOf(-1f, 1f)) {
            part(side * .285f, 1.035f, .005f, .135f, .14f, .12f, if (target == TargetMuscle.ARMS) lime() else torso)
            part(side * .255f, .79f, -.075f, .115f, .22f, .09f, back)
            part(side * .31f, .78f, .025f, .105f, .19f, .105f, arm)
            part(side * .395f, .80f, .105f, .105f, .13f, .075f, arm)
            part(side * .415f, .60f, .01f, .075f, .19f, .075f, arm)
            part(side * .445f, .47f, .035f, .065f, .095f, .065f, skin)
            part(side * .46f, .38f, .055f, .052f, .065f, .034f, skin)
        }

        // Separate abdominal segments and obliques instead of one smooth waist blob.
        part(0f, .53f, -.005f, .205f, .19f, .115f, torso)
        for (row in 0..2) {
            val y = .625f - row * .105f
            for (side in listOf(-1f, 1f)) {
                part(side * .067f, y, .103f, .062f, .043f, .035f, core)
            }
        }
        for (side in listOf(-1f, 1f)) {
            part(side * .178f, .55f, .042f, .055f, .15f, .065f, if (target == TargetMuscle.CORE) lime() else shadow)
            part(side * .137f, .245f, -.055f, .125f, .13f, .10f, if (target == TargetMuscle.BACK) lime() else torso)
            part(side * .135f, .225f, .075f, .125f, .125f, .085f, if (target == TargetMuscle.LEGS) lime() else shadow)
        }

        // Legs: separate quadriceps heads, kneecaps, calves and feet give a more human silhouette.
        for (side in listOf(-1f, 1f)) {
            part(side * .142f, -.015f, .055f, .112f, .265f, .115f, leg)
            part(side * .195f, -.045f, .095f, .055f, .205f, .065f, leg)
            part(side * .096f, -.06f, .112f, .047f, .19f, .055f, leg)
            part(side * .145f, -.315f, .085f, .082f, .085f, .075f, skin)
            part(side * .15f, -.51f, -.035f, .075f, .22f, .075f, if (target == TargetMuscle.BACK) lime() else shadow)
            part(side * .15f, -.63f, .055f, .085f, .19f, .085f, leg)
            part(side * .15f, -.83f, .015f, .06f, .14f, .06f, skin)
            part(side * .15f, -.99f, .075f, .105f, .055f, .17f, skin)
        }

        // Fine upper-back forms visible as the model turns.
        part(0f, .84f, -.105f, .095f, .23f, .035f, back)
        for (side in listOf(-1f, 1f)) {
            part(side * .125f, .91f, -.115f, .105f, .12f, .045f, back)
        }
    }

    private fun drawSkeleton() {
        val boneColor = floatArrayOf(.77f, .83f, .70f, 1f)
        val jointColor = floatArrayOf(.70f, .92f, .40f, 1f)
        part(0f, 1.49f, .015f, .115f, .13f, .10f, boneColor)
        bone(0f, 1.32f, 0f, 0f, 1.05f, 0f, .035f, boneColor)
        bone(0f, 1.04f, 0f, 0f, .73f, 0f, .032f, boneColor)
        bone(0f, .72f, 0f, 0f, .42f, 0f, .032f, boneColor)
        bone(0f, .42f, 0f, 0f, .25f, 0f, .04f, boneColor)

        // Rib cage, clavicles and shoulder girdle.
        for (i in 0..3) {
            val y = .99f - i * .075f
            val spread = .145f + i * .025f
            for (side in listOf(-1f, 1f)) {
                bone(0f, y, .035f, side * spread, y - .015f, .035f, .018f, boneColor)
            }
        }
        for (side in listOf(-1f, 1f)) {
            bone(0f, 1.08f, .01f, side * .22f, 1.035f, .01f, .027f, boneColor)
            bone(side * .22f, 1.035f, .01f, side * .34f, .94f, .01f, .035f, boneColor)
            bone(side * .34f, .94f, .01f, side * .395f, .76f, .01f, .03f, boneColor)
            bone(side * .395f, .76f, .01f, side * .435f, .60f, .01f, .023f, boneColor)
            bone(side * .435f, .60f, .01f, side * .45f, .47f, .01f, .02f, boneColor)
            bone(side * .06f, .30f, 0f, side * .19f, .21f, 0f, .04f, boneColor)
            bone(side * .19f, .21f, 0f, side * .15f, -.28f, 0f, .055f, boneColor)
            bone(side * .15f, -.28f, 0f, side * .15f, -.32f, 0f, .043f, jointColor)
            bone(side * .15f, -.32f, 0f, side * .15f, -.78f, .005f, .033f, boneColor)
            bone(side * .15f, -.78f, .005f, side * .15f, -.96f, .05f, .022f, boneColor)
            part(side * .15f, -.29f, .04f, .055f, .055f, .055f, jointColor)
            part(side * .34f, .94f, .025f, .055f, .055f, .055f, jointColor)
        }
        part(0f, .28f, .01f, .10f, .09f, .07f, jointColor)
    }

    private fun bone(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        radius: Float,
        color: FloatArray
    ) {
        val dx = bx - ax
        val dy = by - ay
        val dz = bz - az
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, (ax + bx) / 2f, (ay + by) / 2f, (az + bz) / 2f)
        Matrix.rotateM(model, 0, Math.toDegrees(atan2(-dx.toDouble(), dy.toDouble())).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(model, 0, radius, length / 2f, radius)
        drawMesh(color)
    }

    private fun part(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        color: FloatArray
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        drawMesh(color)
    }

    private fun drawMesh(color: FloatArray) {
        Matrix.multiplyMM(mvp, 0, rotatedViewProjection, 0, model, 0)
        if (Matrix.invertM(inverseModel, 0, model, 0)) {
            Matrix.transposeM(normalMatrix, 0, inverseModel, 0)
        } else {
            Matrix.setIdentityM(normalMatrix, 0)
        }
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normalMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        sphere.draw(posHandle, normalHandle)
    }

    private fun lime() = floatArrayOf(.72f, .95f, .29f, 1f)

    private fun buildProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val p = GLES20.glCreateProgram()
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        GLES20.glAttachShader(p, vertexShader)
        GLES20.glAttachShader(p, fragmentShader)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private inner class SphereMesh {
        private val vertices: FloatBuffer
        private val indices: ShortBuffer
        private val count: Int

        init {
            val stacks = 28
            val slices = 32
            val data = ArrayList<Float>()
            val idx = ArrayList<Short>()
            for (i in 0..stacks) {
                val phi = Math.PI * i.toDouble() / stacks
                for (j in 0..slices) {
                    val theta = 2.0 * Math.PI * j.toDouble() / slices
                    val x = (-cos(theta) * sin(phi)).toFloat()
                    val y = cos(phi).toFloat()
                    val z = (sin(theta) * sin(phi)).toFloat()
                    data.add(x); data.add(y); data.add(z)
                    data.add(x); data.add(y); data.add(z)
                }
            }
            for (i in 0 until stacks) for (j in 0 until slices) {
                val a = (i * (slices + 1) + j).toShort()
                val b = (a.toInt() + slices + 1).toShort()
                idx.add(a); idx.add(b); idx.add((a.toInt() + 1).toShort())
                idx.add((a.toInt() + 1).toShort()); idx.add(b); idx.add((b.toInt() + 1).toShort())
            }
            vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                data.forEach { put(it) }; position(0)
            }
            indices = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                idx.forEach { put(it) }; position(0)
            }
            count = idx.size
        }

        fun draw(position: Int, normal: Int) {
            vertices.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, vertices)
            vertices.position(3)
            GLES20.glEnableVertexAttribArray(normal)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, vertices)
            indices.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, indices)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(normal)
        }
    }

    companion object {
        private const val VERTEX_SHADER = "attribute vec3 aPosition; attribute vec3 aNormal; uniform mat4 uMvp; uniform mat4 uNormalMatrix; varying vec3 vN; void main(){ vN=normalize(mat3(uNormalMatrix)*aNormal); gl_Position=uMvp*vec4(aPosition,1.0); }"
        private const val FRAGMENT_SHADER = "precision mediump float; uniform vec4 uColor; varying vec3 vN; void main(){ vec3 n=normalize(vN); vec3 light=normalize(vec3(-0.40,0.72,0.90)); float d=max(dot(n,light),0.0); float rim=pow(1.0-max(dot(n,normalize(vec3(0.0,0.0,1.0))),0.0),2.2); vec3 c=uColor.rgb*(0.24+0.76*d)+vec3(0.10,0.22,0.04)*rim; gl_FragColor=vec4(c,uColor.a); }"
    }
}
