package com.workout.app.anatomy

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

private val AnatomyLime = Color(0xFFB7F34A)
private val AnatomyInk = Color(0xFF0B0D0C)
private val AnatomyPanel = Color(0xFF191D1B)

enum class BodyLayer(val label: String) { MUSCLES("Muscles"), SKELETON("Skeleton"), BOTH("Both") }
enum class TargetMuscle(val label: String) { CORE("Core"), CHEST("Chest"), BACK("Back"), LEGS("Legs"), ARMS("Arms") }

@Composable
fun AnatomyStudioScreen(onBack: () -> Unit) {
    var layer by remember { mutableStateOf(BodyLayer.MUSCLES) }
    var target by remember { mutableStateOf(TargetMuscle.CORE) }
    var slowMotion by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf<AnatomySurface?>(null) }

    Column(Modifier.fillMaxSize().background(AnatomyInk).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("ANATOMICAL STUDIO", color = AnatomyLime, fontSize = 11.sp)
                Text("Interactive 3D model", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.Filled.RotateRight, null, tint = AnatomyLime)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            BodyLayer.entries.forEach { item ->
                FilterChip(selected = layer == item, onClick = { layer = item; surface?.setLayer(item) }, label = { Text(item.label, fontSize = 11.sp) })
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(AnatomyPanel, RoundedCornerShape(24.dp))) {
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
            Text("DRAG TO ROTATE", color = AnatomyLime, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
        }
        Text("Target muscle group", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TargetMuscle.entries.forEach { item ->
                FilterChip(selected = target == item, onClick = { target = item; surface?.setTarget(item) }, label = { Text(item.label, fontSize = 11.sp) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Slow-motion pulse", color = Color.White)
                Text("Visual effect only; not a validated technique assessment.", color = Color.Gray, fontSize = 11.sp)
            }
            Switch(checked = slowMotion, onCheckedChange = { slowMotion = it; surface?.setSlowMotion(it) })
        }
        Text("Stylized procedural model, not a medical reference. Realistic licensed anatomy and biomechanical review remain required for production.", color = Color(0xFFADB6AE), fontSize = 11.sp)
    }
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
                MotionEvent.ACTION_MOVE -> { body.rotation += (event.x - lastX) * .75f; lastX = event.x; true }
                else -> true
            }
        }
    }
    fun setLayer(layer: BodyLayer) { body.layer = layer }
    fun setTarget(target: TargetMuscle) { body.target = target }
    fun setSlowMotion(enabled: Boolean) { body.speed = if (enabled) .15f else .65f }
}

private class AnatomyRenderer : GLSurfaceView.Renderer {
    @Volatile var rotation = -12f
    @Volatile var layer = BodyLayer.MUSCLES
    @Volatile var target = TargetMuscle.CORE
    @Volatile var speed = .65f

    private var program = 0
    private lateinit var sphere: SphereMesh
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val rotatedViewProjection = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var posHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private var frame = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.043f, .051f, .047f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        sphere = SphereMesh()
        Matrix.setLookAtM(view, 0, 0f, .08f, 5.2f, 0f, .02f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 38f, width.toFloat() / height.coerceAtLeast(1), .1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        frame += .015f * speed
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        Matrix.setRotateM(rotationMatrix, 0, rotation + sin(frame.toDouble()).toFloat() * 2f, 0f, 1f, 0f)
        Matrix.multiplyMM(rotatedViewProjection, 0, viewProjection, 0, rotationMatrix, 0)
        if (layer != BodyLayer.SKELETON) drawMuscles()
        if (layer != BodyLayer.MUSCLES) drawSkeleton()
    }

    private fun drawMuscles() {
        val skin = floatArrayOf(.27f, .31f, .26f, 1f)
        val torso = floatArrayOf(.22f, .28f, .22f, 1f)
        val core = if (target == TargetMuscle.CORE) lime() else floatArrayOf(.26f, .30f, .25f, 1f)
        part(0f, 1.43f, 0f, .15f, .18f, .13f, skin)
        part(0f, 1.21f, 0f, .075f, .13f, .09f, skin)
        part(0f, .80f, 0f, .29f, .39f, .15f, if (target == TargetMuscle.CHEST || target == TargetMuscle.BACK) lime() else torso)
        part(0f, .40f, 0f, .25f, .17f, .13f, core)
        for (side in listOf(-1f, 1f)) {
            part(side * .30f, 1.05f, 0f, .15f, .17f, .14f, if (target == TargetMuscle.BACK) lime() else torso)
            part(side * .42f, .82f, 0f, .115f, .25f, .11f, if (target == TargetMuscle.ARMS) lime() else torso)
            part(side * .52f, .53f, 0f, .085f, .22f, .085f, if (target == TargetMuscle.ARMS) lime() else skin)
            part(side * .55f, .28f, 0f, .075f, .09f, .075f, skin)
            part(side * .15f, .05f, 0f, .145f, .34f, .14f, if (target == TargetMuscle.LEGS) lime() else floatArrayOf(.28f, .31f, .25f, 1f))
            part(side * .15f, -.31f, 0f, .10f, .11f, .10f, skin)
            part(side * .15f, -.68f, 0f, .09f, .30f, .09f, if (target == TargetMuscle.LEGS) lime() else torso)
            part(side * .15f, -.99f, .05f, .12f, .07f, .18f, skin)
        }
    }

    private fun drawSkeleton() {
        val bone = floatArrayOf(.72f, .84f, .69f, 1f)
        val joint = floatArrayOf(.72f, .95f, .36f, 1f)
        bone(0f, 1.30f, 0f, 0f, .93f, 0f, .045f, bone)
        bone(0f, .91f, 0f, 0f, .48f, 0f, .055f, bone)
        bone(0f, .46f, 0f, 0f, .34f, 0f, .06f, bone)
        for (side in listOf(-1f, 1f)) {
            bone(0f, 1.05f, 0f, side * .37f, 1.02f, 0f, .032f, bone)
            bone(side * .37f, 1.02f, 0f, side * .43f, .71f, 0f, .03f, bone)
            bone(side * .43f, .71f, 0f, side * .53f, .38f, 0f, .025f, bone)
            bone(side * .15f, .30f, 0f, side * .16f, -.32f, 0f, .048f, bone)
            bone(side * .16f, -.32f, 0f, side * .15f, -.81f, 0f, .034f, bone)
            part(side * .16f, -.32f, 0f, .065f, .065f, .065f, joint)
        }
        part(0f, 1.36f, 0f, .085f, .085f, .085f, joint)
        part(0f, .48f, 0f, .07f, .07f, .07f, joint)
    }

    private fun bone(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, radius: Float, color: FloatArray) {
        val dx = bx - ax
        val dy = by - ay
        val dz = bz - az
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, (ax + bx) / 2f, (ay + by) / 2f, (az + bz) / 2f)
        Matrix.rotateM(model, 0, Math.toDegrees(kotlin.math.atan2(-dx.toDouble(), dy.toDouble())).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(model, 0, radius, length / 2f, radius)
        drawMesh(color)
    }

    private fun part(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        drawMesh(color)
    }

    private fun drawMesh(color: FloatArray) {
        Matrix.multiplyMM(mvp, 0, rotatedViewProjection, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
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
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private inner class SphereMesh {
        private val vertices: FloatBuffer
        private val indices: ShortBuffer
        private val count: Int
        init {
            val stacks = 18
            val slices = 24
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
            vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { data.forEach { put(it) }; position(0) }
            indices = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { idx.forEach { put(it) }; position(0) }
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
        private const val VERTEX_SHADER = "attribute vec3 aPosition; attribute vec3 aNormal; uniform mat4 uMvp; uniform mat4 uModel; varying vec3 vN; void main(){ vN=mat3(uModel)*aNormal; gl_Position=uMvp*vec4(aPosition,1.0); }"
        private const val FRAGMENT_SHADER = "precision mediump float; uniform vec4 uColor; varying vec3 vN; void main(){ vec3 n=normalize(vN); vec3 light=normalize(vec3(-0.35,0.65,0.8)); float d=max(dot(n,light),0.0); float rim=pow(1.0-max(dot(n,normalize(vec3(0.0,0.0,1.0))),0.0),2.0); vec3 c=uColor.rgb*(0.28+0.72*d)+vec3(0.10,0.22,0.04)*rim; gl_FragColor=vec4(c,uColor.a); }"
    }
}
