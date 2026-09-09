package com.flivoro.tile8auncher.ui.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllAppsLaunchMotionTest {
    @Test
    fun `linear preset produces a constant window expansion rate`() {
        for (step in 0..100) {
            val fraction = step / 100f
            val p = AllAppsLaunchMotion.progressForExpansion(fraction)
            assertEquals(fraction, AllAppsLaunchMotion.expansionFraction(p), .0001f)
        }
    }
    @Test
    fun `measured window edges match reference frames after removing the title bar`() {
        // Frame/time, colored-face TL/BR and left/right colored heights from the video.
        val samples = listOf(
            floatArrayOf(33f, 358f, 191f, 1430f, 829f, 718f, 563f),
            floatArrayOf(100f, 182f, 113f, 1601f, 911f, 877f, 724f),
            floatArrayOf(133f, 98f, 76f, 1713f, 967f, 953f, 833f),
            floatArrayOf(200f, 42f, 51f, 1811f, 1019f, 1004f, 934f),
            floatArrayOf(400f, 8f, 34f, 1896f, 1065f, 1039f, 1023f),
        )
        for (s in samples) {
            val q = AllAppsLaunchMotion.frame(s[0] / 650f, 1920f, 1080f).quad
            assertEquals("left at ${s[0]}ms", s[1], q.topLeft.x, 5f)
            assertEquals("top excluding title bar", s[2] - s[5] * 30f / 1050f, q.topLeft.y, 5f)
            assertEquals("right", s[3], q.bottomRight.x, 5f)
            assertEquals("bottom", s[4], q.bottomRight.y, 5f)
        }
    }

    @Test
    fun `10ms samples stay contained with one visible app face across screen shapes`() {
        for ((w, h) in listOf(1080f to 2400f, 2400f to 1080f, 2560f to 1600f, 320f to 480f)) {
            var previousScale = 0f
            var previousAngle = 31f
            for (ms in 0..650 step 10) {
                val frame = AllAppsLaunchMotion.frame(ms / 650f, w, h)
                assertFalse(frame.isFrontFace)
                assertTrue(frame.expansion >= previousScale)
                assertTrue(frame.rotationDegrees <= previousAngle)
                for (p in listOf(frame.quad.topLeft, frame.quad.topRight, frame.quad.bottomLeft, frame.quad.bottomRight)) {
                    assertTrue(p.x.isFinite() && p.y.isFinite())
                    assertTrue(p.x >= -.01f && p.x <= w + .01f)
                    assertTrue(p.y >= -.01f && p.y <= h + .01f)
                }
                previousScale = frame.expansion
                previousAngle = frame.rotationDegrees
            }
            val end = AllAppsLaunchMotion.frame(1f, w, h).quad.bounds
            assertEquals(w, end.width, .001f)
            assertEquals(h, end.height, .001f)
        }
    }
}
