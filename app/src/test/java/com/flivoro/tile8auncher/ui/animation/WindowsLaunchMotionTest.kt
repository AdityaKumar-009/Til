package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class WindowsLaunchMotionTest {
    @Test
    fun `retiming can produce a constant turn rate without changing the spatial path`() {
        for (step in 0..100) {
            val fraction = step / 100f
            val mapped = WindowsLaunchMotion.progressForRotation(fraction)
            assertEquals(fraction, WindowsLaunchMotion.rotationFractionAtProgress(mapped), .0001f)
        }
    }
    @Test
    fun `full width wide and large tiles keep their logo on its center path`() {
        val width = 1080f
        val height = 2400f
        for (tileHeight in listOf(470f, 948f)) {
            for (top in listOf(160f, 780f, 1300f)) {
                val source = Rect(66f, top, 1014f, top + tileHeight)
                var lastDistance = Float.POSITIVE_INFINITY
                for (step in 0..1000) {
                    val frame = WindowsLaunchMotion.frame(step / 1000f, source, width, height)
                    if (frame.quad.bounds.width < 2f) continue
                    val center = diagonalIntersection(frame.quad)
                    assertEquals("centered tile must not move its logo sideways", width / 2f, center.x, .15f)
                    val distance = kotlin.math.abs(center.y - height / 2f)
                    assertTrue("logo reversed direction at step $step for $source",
                        distance <= lastDistance + .15f)
                    lastDistance = distance
                }
            }
        }
    }

    private fun diagonalIntersection(quad: Quad): Offset {
        val a = quad.topLeft
        val b = quad.bottomRight
        val c = quad.topRight
        val d = quad.bottomLeft
        val rx = (b.x - a.x).toDouble()
        val ry = (b.y - a.y).toDouble()
        val sx = (d.x - c.x).toDouble()
        val sy = (d.y - c.y).toDouble()
        val t = ((c.x - a.x) * sy - (c.y - a.y) * sx) / (rx * sy - ry * sx)
        return Offset((a.x + t * rx).toFloat(), (a.y + t * ry).toFloat())
    }

    @Test
    fun `classic motion starts at the tile and ends at the viewport`() {
        val viewport = Viewport("landscape", 1920f, 1080f)
        val source = Rect(137f, 211f, 509f, 563f)

        val initial = WindowsLaunchMotion.frame(
            progress = 0f,
            source = source,
            width = viewport.width,
            height = viewport.height,
        )
        assertQuadEquals(Quad.of(source), initial.quad, 0f, "classic initial frame")
        assertTrue("the opening frame should show the front face", initial.isFrontFace)
        assertEquals("initial rotation", 0f, initial.rotationDegrees, 0f)
        assertEquals("initial expansion", 0f, initial.expansion, 0f)

        val final = WindowsLaunchMotion.frame(
            progress = 1f,
            source = source,
            width = viewport.width,
            height = viewport.height,
        )
        assertQuadEquals(
            Quad.of(Rect(0f, 0f, viewport.width, viewport.height)),
            final.quad,
            0f,
            "classic final frame",
        )
        assertFalse("the final frame should show the back face", final.isFrontFace)
        assertEquals("final expansion", 1f, final.expansion, 0f)
    }

    @Test
    fun `modern motion reaches exact tile and viewport endpoints`() {
        val viewport = Viewport("portrait", 1080f, 1920f)
        val source = Rect(173f, 347f, 431f, 605f)
        val expectedInitial = Quad.of(source)
        val expectedFinal = Quad.of(Rect(0f, 0f, viewport.width, viewport.height))

        val initial = WindowsLaunchMotion.frame(
            progress = 0f,
            source = source,
            width = viewport.width,
            height = viewport.height,
            modern = true,
        )
        val final = WindowsLaunchMotion.frame(
            progress = 1f,
            source = source,
            width = viewport.width,
            height = viewport.height,
            modern = true,
        )

        assertQuadEquals(expectedInitial, initial.quad, 0f, "modern initial frame")
        assertQuadEquals(expectedFinal, final.quad, 0f, "modern final frame")
        assertFinite(initial, "modern initial frame")
        assertFinite(final, "modern final frame")
    }

    @Test
    fun `classic frames remain finite and contained across viewport and tile shapes`() {
        for (viewport in viewports()) {
            for (shape in tileShapes()) {
                for (placement in placements()) {
                    val source = sourceBounds(viewport, shape, placement)
                    for (step in 0..PROGRESS_STEPS) {
                        val progress = step.toFloat() / PROGRESS_STEPS
                        val frame = WindowsLaunchMotion.frame(
                            progress = progress,
                            source = source,
                            width = viewport.width,
                            height = viewport.height,
                        )
                        val context = "$viewport, $shape, $placement, p=$progress"
                        assertFinite(frame, context)
                        assertContained(frame, viewport, context)
                    }
                }
            }
        }
    }

    @Test
    fun `vertical edges stay vertical during the classic rotation`() {
        for (viewport in viewports()) {
            for (shape in tileShapes()) {
                for (placement in placements()) {
                    val source = sourceBounds(viewport, shape, placement)
                    for (step in 0..PROGRESS_STEPS) {
                        val progress = step.toFloat() / PROGRESS_STEPS
                        val frame = WindowsLaunchMotion.frame(
                            progress = progress,
                            source = source,
                            width = viewport.width,
                            height = viewport.height,
                        )
                        val quad = frame.quad
                        val context = "$viewport, $shape, $placement, p=$progress"
                        assertEquals(
                            "$context left edge tilted",
                            quad.topLeft.x,
                            quad.bottomLeft.x,
                            GEOMETRY_TOLERANCE,
                        )
                        assertEquals(
                            "$context right edge tilted",
                            quad.topRight.x,
                            quad.bottomRight.x,
                            GEOMETRY_TOLERANCE,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `classic visibility changes from front to back once`() {
        for (viewport in viewports()) {
            for (shape in tileShapes()) {
                for (placement in placements()) {
                    val source = sourceBounds(viewport, shape, placement)
                    var previous = WindowsLaunchMotion.frame(
                        progress = 0f,
                        source = source,
                        width = viewport.width,
                        height = viewport.height,
                    )
                    var changes = 0

                    for (step in 1..PROGRESS_STEPS) {
                        val progress = step.toFloat() / PROGRESS_STEPS
                        val current = WindowsLaunchMotion.frame(
                            progress = progress,
                            source = source,
                            width = viewport.width,
                            height = viewport.height,
                        )
                        if (current.isFrontFace != previous.isFrontFace) changes++
                        previous = current
                    }

                    val context = "$viewport, $shape, $placement"
                    assertEquals("$context visibility changes", 1, changes)
                    assertTrue("$context should start on the front", WindowsLaunchMotion.frame(
                        progress = 0f,
                        source = source,
                        width = viewport.width,
                        height = viewport.height,
                    ).isFrontFace)
                    assertFalse("$context should finish on the back", previous.isFrontFace)
                }
            }
        }
    }

    @Test
    fun `physical corner set stays continuous when texture order switches`() {
        for (viewport in viewports()) {
            for (shape in tileShapes()) {
                for (placement in placements()) {
                    val source = sourceBounds(viewport, shape, placement)
                    var beforePrevious: LaunchFrame? = null
                    var before: LaunchFrame? = null
                    var switchFound = false

                    for (step in 0..PROGRESS_STEPS) {
                        val progress = step.toFloat() / PROGRESS_STEPS
                        val current = WindowsLaunchMotion.frame(
                            progress = progress,
                            source = source,
                            width = viewport.width,
                            height = viewport.height,
                        )
                        if (before != null && before!!.isFrontFace != current.isFrontFace) {
                            val switchDistance = symmetricCornerSetDistance(before!!.quad, current.quad)
                            val baseline = beforePrevious?.let {
                                symmetricCornerSetDistance(it.quad, before!!.quad)
                            } ?: switchDistance
                            val normalizedDistance = switchDistance / max(viewport.width, viewport.height)
                            val normalizedBaseline = baseline / max(viewport.width, viewport.height)
                            val context = "$viewport, $shape, $placement, p=$progress"
                            assertTrue(
                                "$context corner set jumped by $normalizedDistance, baseline $normalizedBaseline",
                                normalizedDistance <= normalizedBaseline * 4f + FACE_SWITCH_FLOOR,
                            )
                            switchFound = true
                        }
                        beforePrevious = before
                        before = current
                    }

                    assertTrue("$viewport, $shape, $placement never switched face", switchFound)
                }
            }
        }
    }

    @Test
    fun `classic angle and expansion are monotone`() {
        val viewport = Viewport("tablet", 2560f, 1600f)
        val source = sourceBounds(viewport, TileShape.LARGE, Placement.CENTER)
        var previous = WindowsLaunchMotion.frame(
            progress = 0f,
            source = source,
            width = viewport.width,
            height = viewport.height,
        )

        for (step in 1..PROGRESS_STEPS) {
            val progress = step.toFloat() / PROGRESS_STEPS
            val current = WindowsLaunchMotion.frame(
                progress = progress,
                source = source,
                width = viewport.width,
                height = viewport.height,
            )
            val context = "tablet center LARGE, p=$progress"
            assertTrue(
                "$context angle moved backwards",
                current.rotationDegrees + MONOTONIC_TOLERANCE >= previous.rotationDegrees,
            )
            assertTrue(
                "$context expansion moved backwards",
                current.expansion + MONOTONIC_TOLERANCE >= previous.expansion,
            )
            previous = current
        }

        assertEquals("angle at start", 0f, WindowsLaunchMotion.frame(
            progress = 0f,
            source = source,
            width = viewport.width,
            height = viewport.height,
        ).rotationDegrees, 0f)
        assertTrue("angle should advance", previous.rotationDegrees > 0f)
        assertTrue("expansion should advance", previous.expansion > 0f)
    }

    @Test
    fun `uniform viewport scaling preserves normalized classic geometry`() {
        val scale = 1.75f
        val base = Viewport("base", 1200f, 800f)
        val scaled = Viewport("scaled", base.width * scale, base.height * scale)
        val progressSamples = listOf(0f, .17f, .33f, .49f, .67f, .83f, 1f)

        for (shape in tileShapes()) {
            for (placement in placements()) {
                val baseSource = sourceBounds(base, shape, placement)
                val scaledSource = sourceBounds(scaled, shape, placement)
                for (progress in progressSamples) {
                    val baseFrame = WindowsLaunchMotion.frame(
                        progress = progress,
                        source = baseSource,
                        width = base.width,
                        height = base.height,
                    )
                    val scaledFrame = WindowsLaunchMotion.frame(
                        progress = progress,
                        source = scaledSource,
                        width = scaled.width,
                        height = scaled.height,
                    )
                    val context = "$shape, $placement, p=$progress"
                    assertEquals("$context face", baseFrame.isFrontFace, scaledFrame.isFrontFace)
                    assertEquals(
                        "$context angle",
                        baseFrame.rotationDegrees,
                        scaledFrame.rotationDegrees,
                        MONOTONIC_TOLERANCE,
                    )
                    assertEquals(
                        "$context expansion",
                        baseFrame.expansion,
                        scaledFrame.expansion,
                        MONOTONIC_TOLERANCE,
                    )
                    assertQuadEquals(
                        baseFrame.quad,
                        scaledFrame.quad.scaledBy(1f / scale),
                        NORMALIZED_GEOMETRY_TOLERANCE,
                        context,
                    )
                }
            }
        }
    }

    @Test
    fun `modern mode uses finite contained fallback for invalid and scrolled out sources`() {
        val viewport = Viewport("ultrawide", 3440f, 1440f)
        val badSources = listOf(
            Rect(Float.NaN, 20f, 300f, 300f),
            Rect(20f, Float.POSITIVE_INFINITY, 300f, 300f),
            Rect(80f, 80f, 80f, 300f),
            Rect(-900f, -700f, -300f, -200f),
            Rect(3800f, 1700f, 4300f, 2200f),
        )

        for (source in badSources) {
            val fallback = WindowsLaunchMotion.sourceBounds(source, viewport.width, viewport.height)
            val context = "source=$source"
            assertFinite(fallback, context)
            assertTrue("$context fallback should have width", fallback.width > 1f)
            assertTrue("$context fallback should have height", fallback.height > 1f)
            assertContained(fallback, viewport, context)
            assertEquals("$context fallback center x", viewport.width / 2f, fallback.center.x, GEOMETRY_TOLERANCE)
            assertEquals("$context fallback center y", viewport.height / 2f, fallback.center.y, GEOMETRY_TOLERANCE)

            for (progress in listOf(0f, .5f, 1f, Float.NaN, Float.POSITIVE_INFINITY)) {
                val frame = WindowsLaunchMotion.frame(
                    progress = progress,
                    source = source,
                    width = viewport.width,
                    height = viewport.height,
                    modern = true,
                )
                assertFinite(frame, "$context, modern p=$progress")
                assertContained(frame, viewport, "$context, modern p=$progress")
            }
        }
    }

    private fun viewports() = listOf(
        Viewport("portrait", 1080f, 1920f),
        Viewport("landscape", 1920f, 1080f),
        Viewport("tablet", 2560f, 1600f),
        Viewport("ultrawide", 3440f, 1440f),
    )

    private fun tileShapes() = TileShape.entries

    private fun placements() = listOf(
        Placement.TOP_LEFT,
        Placement.TOP_RIGHT,
        Placement.BOTTOM_LEFT,
        Placement.BOTTOM_RIGHT,
        Placement.CENTER,
    )

    private fun sourceBounds(viewport: Viewport, shape: TileShape, placement: Placement): Rect {
        val shortSide = min(viewport.width, viewport.height)
        val tileWidth = shortSide * shape.widthFraction
        val tileHeight = shortSide * shape.heightFraction
        val left = when (placement) {
            Placement.TOP_LEFT, Placement.BOTTOM_LEFT -> 0f
            Placement.TOP_RIGHT, Placement.BOTTOM_RIGHT -> viewport.width - tileWidth
            Placement.CENTER -> (viewport.width - tileWidth) / 2f
        }
        val top = when (placement) {
            Placement.TOP_LEFT, Placement.TOP_RIGHT -> 0f
            Placement.BOTTOM_LEFT, Placement.BOTTOM_RIGHT -> viewport.height - tileHeight
            Placement.CENTER -> (viewport.height - tileHeight) / 2f
        }
        return Rect(left, top, left + tileWidth, top + tileHeight)
    }

    private fun assertFinite(frame: LaunchFrame, context: String) {
        assertFinite(frame.quad, context)
        assertTrue("$context rotation is not finite", frame.rotationDegrees.isFinite())
        assertTrue("$context expansion is not finite", frame.expansion.isFinite())
    }

    private fun assertFinite(rect: Rect, context: String) {
        assertTrue("$context left is not finite", rect.left.isFinite())
        assertTrue("$context top is not finite", rect.top.isFinite())
        assertTrue("$context right is not finite", rect.right.isFinite())
        assertTrue("$context bottom is not finite", rect.bottom.isFinite())
    }

    private fun assertFinite(quad: Quad, context: String) {
        for ((name, point) in quad.namedCorners()) {
            assertTrue("$context $name x is not finite", point.x.isFinite())
            assertTrue("$context $name y is not finite", point.y.isFinite())
        }
    }

    private fun assertContained(frame: LaunchFrame, viewport: Viewport, context: String) {
        assertContained(frame.quad, viewport, context)
        val bounds = frame.quad.bounds
        assertTrue("$context has an inverted horizontal bound", bounds.left <= bounds.right + GEOMETRY_TOLERANCE)
        assertTrue("$context has an inverted vertical bound", bounds.top <= bounds.bottom + GEOMETRY_TOLERANCE)
    }

    private fun assertContained(rect: Rect, viewport: Viewport, context: String) {
        assertTrue("$context left escapes viewport", rect.left >= -GEOMETRY_TOLERANCE)
        assertTrue("$context top escapes viewport", rect.top >= -GEOMETRY_TOLERANCE)
        assertTrue("$context right escapes viewport", rect.right <= viewport.width + GEOMETRY_TOLERANCE)
        assertTrue("$context bottom escapes viewport", rect.bottom <= viewport.height + GEOMETRY_TOLERANCE)
    }

    private fun assertContained(quad: Quad, viewport: Viewport, context: String) {
        for ((name, point) in quad.namedCorners()) {
            assertTrue("$context $name x=${point.x} escapes left", point.x >= -GEOMETRY_TOLERANCE)
            assertTrue(
                "$context $name x=${point.x} escapes right",
                point.x <= viewport.width + GEOMETRY_TOLERANCE,
            )
            assertTrue("$context $name y=${point.y} escapes top", point.y >= -GEOMETRY_TOLERANCE)
            assertTrue(
                "$context $name y=${point.y} escapes bottom",
                point.y <= viewport.height + GEOMETRY_TOLERANCE,
            )
        }
    }

    private fun assertQuadEquals(expected: Quad, actual: Quad, tolerance: Float, context: String) {
        assertPointEquals(expected.topLeft, actual.topLeft, tolerance, "$context top-left")
        assertPointEquals(expected.topRight, actual.topRight, tolerance, "$context top-right")
        assertPointEquals(expected.bottomRight, actual.bottomRight, tolerance, "$context bottom-right")
        assertPointEquals(expected.bottomLeft, actual.bottomLeft, tolerance, "$context bottom-left")
    }

    private fun assertPointEquals(expected: Offset, actual: Offset, tolerance: Float, context: String) {
        assertEquals("$context x", expected.x, actual.x, tolerance)
        assertEquals("$context y", expected.y, actual.y, tolerance)
    }

    private fun symmetricCornerSetDistance(first: Quad, second: Quad): Float {
        val firstCorners = first.corners()
        val secondCorners = second.corners()
        return max(
            maxDistanceToNearest(firstCorners, secondCorners),
            maxDistanceToNearest(secondCorners, firstCorners),
        )
    }

    private fun maxDistanceToNearest(from: List<Offset>, to: List<Offset>): Float = from.maxOf { point ->
        to.minOf { candidate -> distance(point, candidate) }
    }

    private fun distance(first: Offset, second: Offset): Float {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun Quad.scaledBy(scale: Float) = Quad(
        topLeft = topLeft * scale,
        topRight = topRight * scale,
        bottomRight = bottomRight * scale,
        bottomLeft = bottomLeft * scale,
    )

    private fun Quad.corners() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    private fun Quad.namedCorners() = listOf(
        "top-left" to topLeft,
        "top-right" to topRight,
        "bottom-right" to bottomRight,
        "bottom-left" to bottomLeft,
    )

    private data class Viewport(val name: String, val width: Float, val height: Float) {
        override fun toString() = "$name ${width.toInt()}x${height.toInt()}"
    }

    private enum class TileShape(val widthFraction: Float, val heightFraction: Float) {
        SMALL_SQUARE(.12f, .12f),
        WIDE(.32f, .16f),
        LARGE(.32f, .32f),
        FULL_WIDTH_WIDE(.94f, .47f),
        FULL_WIDTH_LARGE(.94f, .94f),
    }

    private enum class Placement {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER,
    }

    private companion object {
        const val PROGRESS_STEPS = 1000
        const val GEOMETRY_TOLERANCE = .5f
        const val NORMALIZED_GEOMETRY_TOLERANCE = .25f
        const val MONOTONIC_TOLERANCE = .01f
        const val FACE_SWITCH_FLOOR = .005f
    }
}
