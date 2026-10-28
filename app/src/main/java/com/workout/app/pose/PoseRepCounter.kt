package com.workout.app.pose

import kotlin.math.acos
import kotlin.math.sqrt

enum class Movement(val label: String) {
    SQUAT("Squat"), PUSH_UP("Push-up"), BICEP_CURL("Bicep curl"), LUNGE("Lunge")
}

/**
 * Heuristic rep counter, not a form-safety certification. Each completed rep needs
 * a low-endpoint and a high-endpoint with adequate landmark confidence.
 */
class PoseRepCounter(private val movement: Movement) {
    private var reachedLowEndpoint = false
    var repetitions: Int = 0
        private set

    fun update(jointAngleDegrees: Float?, confidence: Float): Int? {
        val angle = jointAngleDegrees ?: return null
        if (!angle.isFinite() || confidence < MIN_CONFIDENCE || angle !in 10f..178f) return null

        val low = when (movement) {
            Movement.SQUAT, Movement.LUNGE -> angle < 105f
            Movement.PUSH_UP -> angle < 100f
            Movement.BICEP_CURL -> angle > 145f
        }
        val high = when (movement) {
            Movement.SQUAT, Movement.LUNGE -> angle > 155f
            Movement.PUSH_UP -> angle > 150f
            Movement.BICEP_CURL -> angle < 65f
        }
        if (low) reachedLowEndpoint = true
        if (reachedLowEndpoint && high) {
            reachedLowEndpoint = false
            repetitions += 1
            return repetitions
        }
        return null
    }

    companion object {
        const val MIN_CONFIDENCE = 0.50f

        fun angle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
            val v1x = ax - bx
            val v1y = ay - by
            val v2x = cx - bx
            val v2y = cy - by
            val dot = v1x * v2x + v1y * v2y
            val m1 = sqrt(v1x * v1x + v1y * v1y)
            val m2 = sqrt(v2x * v2x + v2y * v2y)
            if (m1 < 1e-5f || m2 < 1e-5f) return Float.NaN
            return Math.toDegrees(acos((dot / (m1 * m2)).coerceIn(-1f, 1f).toDouble())).toFloat()
        }
    }
}
