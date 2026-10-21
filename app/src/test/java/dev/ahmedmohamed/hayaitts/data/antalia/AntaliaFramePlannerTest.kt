package dev.ahmedmohamed.hayaitts.data.antalia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AntaliaFramePlannerTest {
    @Test fun expandsAndPadsToEvenMelFrameCount() {
        val hidden = FloatArray(3 * 192) { (it / 192).toFloat() }
        val plan = AntaliaFramePlanner.plan(
            ids = longArrayOf(1, 4, 2),
            hidden = hidden,
            logDurations = floatArrayOf(0f, 0f, 0f),
            spaceTokenId = 3,
        )
        assertArrayEquals(intArrayOf(1, 1, 1), plan.durations)
        assertEquals(4, plan.paddedFrames)
        assertEquals(4 * 192, plan.conditioning.size)
        assertEquals(0f, plan.conditioning[0])
        assertEquals(1f, plan.conditioning[192])
        assertEquals(2f, plan.conditioning[384])
        assertEquals(2f, plan.conditioning[576])
    }

    @Test fun rejectsInvalidModelShapes() {
        assertThrows(IllegalArgumentException::class.java) {
            AntaliaFramePlanner.plan(longArrayOf(1, 2), FloatArray(192), floatArrayOf(0f, 0f), 3)
        }
    }

    @Test fun respectsSpeedAndDurationCap() {
        val plan = AntaliaFramePlanner.plan(
            ids = longArrayOf(1, 2),
            hidden = FloatArray(384),
            logDurations = floatArrayOf(0f, 10f),
            spaceTokenId = 3,
            speed = 2f,
        )
        assertArrayEquals(intArrayOf(1, 60), plan.durations)
        assertEquals(62, plan.paddedFrames)
    }
}
