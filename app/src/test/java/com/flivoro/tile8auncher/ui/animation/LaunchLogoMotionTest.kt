package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LaunchLogoMotionTest {
    @Test
    fun `logo starts at the actual tile artwork and ends centered at the splash size`() {
        val source = Rect(70f, 420f, 530f, 880f)
        val artwork = Rect(170f, 150f, 290f, 270f)
        val first = WindowsLaunchMotion.logoQuad(
            WindowsLaunchMotion.frame(0f, source, 1080f, 2400f),
            source, 1080f, 2400f, artwork, 216f)
        assertEquals(240f, first.bounds.left, .001f)
        assertEquals(570f, first.bounds.top, .001f)
        assertEquals(120f, first.bounds.width, .001f)
        assertEquals(120f, first.bounds.height, .001f)
        val last = WindowsLaunchMotion.logoQuad(
            WindowsLaunchMotion.frame(1f, source, 1080f, 2400f),
            source, 1080f, 2400f, artwork, 216f)
        assertEquals(540f, last.bounds.center.x, .001f)
        assertEquals(1200f, last.bounds.center.y, .001f)
        assertEquals(216f, last.bounds.width, .001f)
        assertEquals(216f, last.bounds.height, .001f)
    }

    @Test
    fun `logo height and location do not reset when front becomes back`() {
        for ((width, height) in listOf(1080f to 2400f, 2400f to 1080f, 2560f to 1600f)) {
            for ((tileW, tileH) in listOf(200f to 200f, 400f to 400f, 900f to 400f, 900f to 900f)) {
                for ((x, y) in listOf(40f to 60f, (width - tileW - 40f) to (height - tileH - 60f))) {
                    val source = Rect(x, y, x + tileW, y + tileH)
                    for (offsetX in listOf(0f, tileW * .25f)) {
                        val artwork = Rect(tileW * .5f + offsetX - 60f, tileH * .5f - 76f,
                            tileW * .5f + offsetX + 60f, tileH * .5f + 44f)
                        var low = 0f
                        var high = 1f
                        repeat(22) {
                            val mid = (low + high) * .5f
                            if (WindowsLaunchMotion.frame(mid, source, width, height).isFrontFace) low = mid else high = mid
                        }
                        val before = WindowsLaunchMotion.logoQuad(
                            WindowsLaunchMotion.frame(low - .00001f, source, width, height),
                            source, width, height, artwork, 216f)
                        val after = WindowsLaunchMotion.logoQuad(
                            WindowsLaunchMotion.frame(high + .00001f, source, width, height),
                            source, width, height, artwork, 216f)
                        assertEquals("height reset for $source", averageHeight(before), averageHeight(after), .15f)
                        assertEquals("vertical jump for $source", before.bounds.center.y, after.bounds.center.y, .15f)
                        assertEquals("horizontal jump for $source", before.bounds.center.x, after.bounds.center.x, .15f)
                    }
                }
            }
        }
    }

    @Test
    fun `shared logo remains finite and inside the turning face for all tile sizes`() {
        for ((tileW, tileH) in listOf(200f to 200f, 400f to 400f, 940f to 450f, 940f to 940f)) {
            val source = Rect(70f, 400f, 70f + tileW, 400f + tileH)
            val artwork = Rect(tileW * .5f - 60f, tileH * .5f - 76f,
                tileW * .5f + 60f, tileH * .5f + 44f)
            for (step in 0..1000) {
                val frame = WindowsLaunchMotion.frame(step / 1000f, source, 1080f, 2400f)
                val logo = WindowsLaunchMotion.logoQuad(frame, source, 1080f, 2400f, artwork, 216f)
                for (point in listOf(logo.topLeft, logo.topRight, logo.bottomRight, logo.bottomLeft)) {
                    assertTrue(point.x.isFinite() && point.y.isFinite())
                    assertTrue(point.x >= frame.quad.bounds.left - .1f && point.x <= frame.quad.bounds.right + .1f)
                    assertTrue(point.y >= frame.quad.bounds.top - .1f && point.y <= frame.quad.bounds.bottom + .1f)
                }
            }
        }
    }

    private fun averageHeight(quad: Quad): Float =
        (abs(quad.bottomLeft.y - quad.topLeft.y) + abs(quad.bottomRight.y - quad.topRight.y)) * .5f
}
