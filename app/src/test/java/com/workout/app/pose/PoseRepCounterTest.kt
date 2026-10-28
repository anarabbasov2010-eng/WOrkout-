package com.workout.app.pose

import org.junit.Assert.*
import org.junit.Test

class PoseRepCounterTest {
    @Test fun countsOnlyAFullSquatCycle() {
        val counter = PoseRepCounter(Movement.SQUAT)
        assertNull(counter.update(170f, .95f))
        assertNull(counter.update(92f, .95f))
        assertEquals(1, counter.update(160f, .95f))
        assertNull(counter.update(165f, .95f))
        assertEquals(1, counter.repetitions)
    }

    @Test fun ignoresLowConfidenceFrames() {
        val counter = PoseRepCounter(Movement.PUSH_UP)
        assertNull(counter.update(75f, .2f))
        assertNull(counter.update(170f, .2f))
        assertEquals(0, counter.repetitions)
    }

    @Test fun computesRightAngle() {
        assertEquals(90f, PoseRepCounter.angle(1f, 0f, 0f, 0f, 0f, 1f), .1f)
    }
}
