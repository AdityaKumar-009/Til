package com.flivoro.tile8auncher.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Cached path geometry for the transparent Windows artwork. Paths are created only when viewport
 * size changes; animation frames only transform/draw the cached geometry.
 */
internal class WindowsBackgroundGeometry(
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    val ribbonPeriod = viewportWidth * 1.88f
    val blossomPeriod = viewportWidth * 2.05f
    val mountainPeriod = viewportWidth * 2.15f
    val dragonPeriod = viewportWidth * 2.20f
    val swirlPeriod = viewportWidth * 1.90f

    val ribbonMain: Path = Path().apply {
        moveTo(0f, viewportHeight * 0.12f)
        cubicTo(
            ribbonPeriod * 0.20f, viewportHeight * 0.02f,
            ribbonPeriod * 0.28f, viewportHeight * 0.58f,
            ribbonPeriod * 0.50f, viewportHeight * 0.52f,
        )
        cubicTo(
            ribbonPeriod * 0.72f, viewportHeight * 0.46f,
            ribbonPeriod * 0.78f, viewportHeight * 0.96f,
            ribbonPeriod, viewportHeight * 0.84f,
        )
        lineTo(ribbonPeriod, viewportHeight)
        cubicTo(
            ribbonPeriod * 0.74f, viewportHeight * 0.98f,
            ribbonPeriod * 0.66f, viewportHeight * 0.62f,
            ribbonPeriod * 0.49f, viewportHeight * 0.66f,
        )
        cubicTo(
            ribbonPeriod * 0.27f, viewportHeight * 0.72f,
            ribbonPeriod * 0.17f, viewportHeight * 0.28f,
            0f, viewportHeight * 0.35f,
        )
        close()
    }

    val ribbonHighlight: Path = Path().apply {
        moveTo(0f, viewportHeight * 0.32f)
        cubicTo(
            ribbonPeriod * 0.18f, viewportHeight * 0.24f,
            ribbonPeriod * 0.32f, viewportHeight * 0.76f,
            ribbonPeriod * 0.53f, viewportHeight * 0.69f,
        )
        cubicTo(
            ribbonPeriod * 0.74f, viewportHeight * 0.61f,
            ribbonPeriod * 0.84f, viewportHeight * 0.89f,
            ribbonPeriod, viewportHeight * 0.79f,
        )
    }

    val blossomBranch: Path = Path().apply {
        moveTo(0f, viewportHeight * 0.91f)
        cubicTo(
            blossomPeriod * 0.17f, viewportHeight * 0.78f,
            blossomPeriod * 0.20f, viewportHeight * 0.42f,
            blossomPeriod * 0.36f, viewportHeight * 0.30f,
        )
        cubicTo(
            blossomPeriod * 0.47f, viewportHeight * 0.21f,
            blossomPeriod * 0.58f, viewportHeight * 0.40f,
            blossomPeriod * 0.68f, viewportHeight * 0.26f,
        )
        cubicTo(
            blossomPeriod * 0.77f, viewportHeight * 0.14f,
            blossomPeriod * 0.86f, viewportHeight * 0.23f,
            blossomPeriod, viewportHeight * 0.10f,
        )
    }

    val mountainFar: Path = Path().apply {
        moveTo(0f, viewportHeight)
        lineTo(0f, viewportHeight * 0.70f)
        lineTo(mountainPeriod * 0.14f, viewportHeight * 0.52f)
        lineTo(mountainPeriod * 0.25f, viewportHeight * 0.67f)
        lineTo(mountainPeriod * 0.39f, viewportHeight * 0.43f)
        lineTo(mountainPeriod * 0.52f, viewportHeight * 0.66f)
        lineTo(mountainPeriod * 0.67f, viewportHeight * 0.50f)
        lineTo(mountainPeriod * 0.83f, viewportHeight * 0.69f)
        lineTo(mountainPeriod, viewportHeight * 0.54f)
        lineTo(mountainPeriod, viewportHeight)
        close()
    }

    val mountainNear: Path = Path().apply {
        moveTo(0f, viewportHeight)
        lineTo(0f, viewportHeight * 0.84f)
        lineTo(mountainPeriod * 0.18f, viewportHeight * 0.67f)
        lineTo(mountainPeriod * 0.34f, viewportHeight * 0.85f)
        lineTo(mountainPeriod * 0.52f, viewportHeight * 0.62f)
        lineTo(mountainPeriod * 0.69f, viewportHeight * 0.82f)
        lineTo(mountainPeriod * 0.86f, viewportHeight * 0.68f)
        lineTo(mountainPeriod, viewportHeight * 0.82f)
        lineTo(mountainPeriod, viewportHeight)
        close()
    }

    val dragonBody: Path = Path().apply {
        moveTo(dragonPeriod * 0.10f, viewportHeight * 0.34f)
        cubicTo(
            dragonPeriod * 0.19f, viewportHeight * 0.17f,
            dragonPeriod * 0.33f, viewportHeight * 0.21f,
            dragonPeriod * 0.39f, viewportHeight * 0.38f,
        )
        cubicTo(
            dragonPeriod * 0.45f, viewportHeight * 0.54f,
            dragonPeriod * 0.58f, viewportHeight * 0.52f,
            dragonPeriod * 0.64f, viewportHeight * 0.36f,
        )
        cubicTo(
            dragonPeriod * 0.72f, viewportHeight * 0.15f,
            dragonPeriod * 0.84f, viewportHeight * 0.21f,
            dragonPeriod * 0.91f, viewportHeight * 0.38f,
        )
    }

    val dragonWing: Path = Path().apply {
        moveTo(dragonPeriod * 0.42f, viewportHeight * 0.36f)
        lineTo(dragonPeriod * 0.50f, viewportHeight * 0.19f)
        lineTo(dragonPeriod * 0.56f, viewportHeight * 0.39f)
        lineTo(dragonPeriod * 0.63f, viewportHeight * 0.21f)
        lineTo(dragonPeriod * 0.66f, viewportHeight * 0.42f)
    }

    val swirl: Path = Path().apply {
        moveTo(swirlPeriod * 0.04f, viewportHeight * 0.62f)
        cubicTo(
            swirlPeriod * 0.19f, viewportHeight * 0.31f,
            swirlPeriod * 0.37f, viewportHeight * 0.86f,
            swirlPeriod * 0.51f, viewportHeight * 0.55f,
        )
        cubicTo(
            swirlPeriod * 0.65f, viewportHeight * 0.25f,
            swirlPeriod * 0.82f, viewportHeight * 0.79f,
            swirlPeriod * 0.96f, viewportHeight * 0.47f,
        )
    }
}

/**
 * Draws transparent vector artwork over the already-painted solid Background color.
 *
 * The viewport never moves and no backing bitmap exists. Every layer samples a Double world
 * coordinate and draws only the two/three cells that intersect the screen, so it cannot run out of
 * artwork regardless of scroll distance.
 */
internal fun DrawScope.drawWindowsBackgroundArtwork(
    geometry: WindowsBackgroundGeometry,
    wallpaperStyle: Int,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    when (wallpaperStyle.coerceIn(0, 9)) {
        0 -> drawRibbons(geometry, worldX, palette)
        1 -> drawRobots(geometry, worldX, palette, motion)
        2 -> drawCity(geometry, worldX, palette, motion)
        3 -> drawSwirlsAndBubbles(geometry, worldX, palette, motion)
        4 -> drawBlossom(geometry, worldX, palette)
        5 -> drawGarden(geometry, worldX, palette, motion)
        6 -> drawFacets(geometry, worldX, palette)
        7 -> drawMountains(geometry, worldX, palette)
        8 -> drawDragon(geometry, worldX, palette, motion)
        9 -> drawGearsTheme(geometry, worldX, palette, motion)
    }
}

private inline fun DrawScope.forEachWorldCell(
    worldX: Double,
    rate: Float,
    periodPx: Float,
    block: DrawScope.(cellIndex: Long, leftPx: Float, variant: Int) -> Unit,
) {
    val period = periodPx.coerceAtLeast(1f).toDouble()
    val sceneX = worldX * rate.toDouble()
    val baseCell = BackgroundWorldMath.cellIndex(sceneX, period)
    val visibleCells = (size.width / periodPx.coerceAtLeast(1f)).toInt() + 4
    for (relative in -1..visibleCells) {
        val cell = baseCell + relative
        val left = BackgroundWorldMath.localCellX(sceneX, period, cell)
        block(cell, left, BackgroundWorldMath.variant(cell, 3))
    }
}

private fun DrawScope.drawRibbons(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
) {
    forEachWorldCell(worldX, WallpaperParallax.GLOW_RATE, g.ribbonPeriod) { _, left, variant ->
        val x = left + g.ribbonPeriod * (0.22f + variant * 0.23f)
        drawCircle(
            color = palette.secondary.copy(alpha = 0.18f),
            radius = g.viewportWidth * (0.42f + variant * 0.035f),
            center = Offset(x, g.viewportHeight * (0.22f + variant * 0.18f)),
        )
    }

    forEachWorldCell(worldX, WallpaperParallax.RIBBON_RATE, g.ribbonPeriod) { _, left, _ ->
        withTransform({ translate(left, 0f) }) {
            drawPath(g.ribbonMain, palette.primary.copy(alpha = 0.58f))
        }
    }
    forEachWorldCell(worldX, WallpaperParallax.HIGHLIGHT_RATE, g.ribbonPeriod) { _, left, _ ->
        withTransform({ translate(left, 0f) }) {
            drawPath(
                g.ribbonHighlight,
                palette.highlight.copy(alpha = 0.40f),
                style = Stroke(width = max(2f, g.viewportWidth * 0.012f)),
            )
        }
    }
}

private fun DrawScope.drawRobots(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    val period = g.viewportWidth * 1.92f
    val followX = WindowsMotionAccent.artworkOffsetX(motion, g.viewportWidth)
    val followY = WindowsMotionAccent.artworkOffsetY(motion, g.viewportHeight)

    forEachWorldCell(worldX, WallpaperParallax.IMAGE_RATE, period) { cell, left, variant ->
        val bodyW = g.viewportWidth * 0.18f
        val bodyH = g.viewportHeight * 0.17f
        val robotX = left + period * (0.18f + variant * 0.22f) + followX
        val robotY = g.viewportHeight * (0.23f + variant * 0.17f) + followY
        val bodyColor = if (variant % 2 == 0) palette.secondary else palette.primary

        drawRoundRect(
            color = bodyColor.copy(alpha = 0.64f),
            topLeft = Offset(robotX, robotY),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.08f),
        )
        drawRoundRect(
            color = palette.highlight.copy(alpha = 0.50f),
            topLeft = Offset(robotX + bodyW * 0.12f, robotY - bodyH * 0.32f),
            size = Size(bodyW * 0.72f, bodyH * 0.38f),
            cornerRadius = CornerRadius(bodyW * 0.06f),
        )
        drawCircle(
            palette.shadow.copy(alpha = 0.78f),
            radius = bodyW * 0.035f,
            center = Offset(robotX + bodyW * 0.31f, robotY - bodyH * 0.13f),
        )
        drawCircle(
            palette.shadow.copy(alpha = 0.78f),
            radius = bodyW * 0.035f,
            center = Offset(robotX + bodyW * 0.57f, robotY - bodyH * 0.13f),
        )
        drawLine(
            palette.highlight.copy(alpha = 0.45f),
            Offset(robotX - bodyW * 0.18f, robotY + bodyH * 0.30f),
            Offset(robotX, robotY + bodyH * 0.48f),
            strokeWidth = max(2f, bodyW * 0.035f),
        )
        drawLine(
            palette.highlight.copy(alpha = 0.45f),
            Offset(robotX + bodyW, robotY + bodyH * 0.48f),
            Offset(robotX + bodyW * 1.18f, robotY + bodyH * 0.25f),
            strokeWidth = max(2f, bodyW * 0.035f),
        )

        val gearAlpha = 0.48f + motion.activity * 0.24f
        drawGear(
            center = Offset(robotX + bodyW * 0.28f, robotY + bodyH * 0.63f),
            radius = bodyW * 0.16f,
            rotationDegrees = WindowsMotionAccent.robotGearDegrees(motion.phaseSeconds, variant * 2),
            color = palette.highlight.copy(alpha = gearAlpha),
        )
        drawGear(
            center = Offset(robotX + bodyW * 0.67f, robotY + bodyH * 0.66f),
            radius = bodyW * 0.12f,
            rotationDegrees = WindowsMotionAccent.robotGearDegrees(motion.phaseSeconds, variant * 2 + 1),
            color = palette.shadow.copy(alpha = gearAlpha),
        )

        val smallX = left + period * (0.70f - variant * 0.05f)
        val smallY = g.viewportHeight * (0.66f - variant * 0.08f)
        drawRoundRect(
            color = palette.faint.copy(alpha = 0.34f),
            topLeft = Offset(smallX, smallY),
            size = Size(bodyW * 0.58f, bodyH * 0.55f),
            cornerRadius = CornerRadius(bodyW * 0.05f),
        )
        if (BackgroundWorldMath.variant(cell, 2) == 0) {
            drawCircle(
                color = palette.highlight.copy(alpha = 0.22f),
                radius = bodyW * 0.18f,
                center = Offset(smallX + bodyW * 0.28f, smallY - bodyH * 0.12f),
                style = Stroke(width = max(1.5f, bodyW * 0.025f)),
            )
        }
    }
}

private fun DrawScope.drawCity(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    val farPeriod = g.viewportWidth * 1.55f
    forEachWorldCell(worldX, 0.035f, farPeriod) { _, left, variant ->
        repeat(6) { i ->
            val buildingW = farPeriod * 0.10f
            val height = g.viewportHeight * (0.13f + ((i + variant * 2) % 5) * 0.045f)
            val x = left + farPeriod * (0.03f + i * 0.16f)
            drawRect(
                palette.shadow.copy(alpha = 0.30f),
                topLeft = Offset(x, g.viewportHeight - height),
                size = Size(buildingW, height),
            )
        }
    }

    val nearPeriod = g.viewportWidth * 1.74f
    forEachWorldCell(worldX, WallpaperParallax.IMAGE_RATE, nearPeriod) { cell, left, variant ->
        repeat(5) { i ->
            val buildingW = nearPeriod * (0.105f + (i % 2) * 0.018f)
            val height = g.viewportHeight * (0.22f + ((i * 3 + variant) % 4) * 0.075f)
            val x = left + nearPeriod * (0.055f + i * 0.185f)
            val top = g.viewportHeight - height
            drawRect(
                if (i % 2 == 0) palette.secondary.copy(alpha = 0.58f)
                else palette.primary.copy(alpha = 0.48f),
                topLeft = Offset(x, top),
                size = Size(buildingW, height),
            )
            repeat(3) { row ->
                repeat(2) { column ->
                    val lightIndex = (
                        BackgroundWorldMath.variant(cell, 7) * 30 +
                            i * 6 + row * 2 + column
                        )
                    drawRect(
                        color = palette.highlight.copy(
                            alpha = WindowsMotionAccent.cityLightAlpha(
                                motion.phaseSeconds,
                                lightIndex,
                            ),
                        ),
                        topLeft = Offset(
                            x + buildingW * (0.22f + column * 0.42f),
                            top + height * (0.18f + row * 0.22f),
                        ),
                        size = Size(
                            max(2f, buildingW * 0.12f),
                            max(2f, g.viewportHeight * 0.008f),
                        ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSwirlsAndBubbles(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    forEachWorldCell(worldX, 0.052f, g.swirlPeriod) { _, left, variant ->
        withTransform({ translate(left, (variant - 1) * g.viewportHeight * 0.05f) }) {
            drawPath(
                g.swirl,
                palette.secondary.copy(alpha = 0.48f),
                style = Stroke(width = max(3f, g.viewportWidth * 0.017f)),
            )
            drawPath(
                g.swirl,
                palette.highlight.copy(alpha = 0.20f),
                style = Stroke(width = max(1.5f, g.viewportWidth * 0.006f)),
            )
        }
    }

    val bubblePeriod = g.viewportWidth * 1.82f
    forEachWorldCell(worldX, WallpaperParallax.HIGHLIGHT_RATE, bubblePeriod) { cell, left, variant ->
        repeat(7) { index ->
            val unique = BackgroundWorldMath.variant(cell, 11) * 7 + index
            val travel = WindowsMotionAccent.bubbleTravel(motion.phaseSeconds, unique)
            val x = left + bubblePeriod * (0.08f + ((index * 0.139f + variant * 0.057f) % 0.84f))
            val y = g.viewportHeight * (1.06f - travel * 1.15f)
            val radius = g.viewportWidth * (0.012f + (index % 4) * 0.006f)
            val sway = sin(motion.phaseSeconds * 0.72f + unique * 0.73f) * g.viewportWidth * 0.012f
            drawCircle(
                palette.highlight.copy(alpha = 0.18f + (index % 3) * 0.045f),
                radius = radius,
                center = Offset(x + sway, y),
                style = Stroke(width = max(1.2f, radius * 0.14f)),
            )
        }
    }
}

private fun DrawScope.drawBlossom(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
) {
    forEachWorldCell(worldX, 0.060f, g.blossomPeriod) { _, left, variant ->
        withTransform({ translate(left, (variant - 1) * g.viewportHeight * 0.025f) }) {
            drawPath(
                g.blossomBranch,
                palette.shadow.copy(alpha = 0.55f),
                style = Stroke(width = max(3f, g.viewportWidth * 0.014f)),
            )
        }
        repeat(9) { i ->
            val fx = 0.18f + i * 0.085f
            val fy = 0.26f + ((i * 3 + variant) % 5) * 0.075f
            val center = Offset(left + g.blossomPeriod * fx, g.viewportHeight * fy)
            val petal = g.viewportWidth * (0.014f + (i % 3) * 0.004f)
            repeat(4) { p ->
                val angle = p * (PI.toFloat() / 2f)
                drawOval(
                    color = if (i % 2 == 0) palette.highlight.copy(alpha = 0.46f)
                    else palette.primary.copy(alpha = 0.38f),
                    topLeft = Offset(
                        center.x + cos(angle) * petal - petal * 0.55f,
                        center.y + sin(angle) * petal - petal * 0.36f,
                    ),
                    size = Size(petal * 1.1f, petal * 0.72f),
                )
            }
        }
    }
}

private fun DrawScope.drawGarden(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    val farPeriod = g.viewportWidth * 1.76f
    forEachWorldCell(worldX, 0.032f, farPeriod) { _, left, variant ->
        drawCircle(
            palette.shadow.copy(alpha = 0.22f),
            radius = g.viewportWidth * 0.43f,
            center = Offset(
                left + farPeriod * (0.28f + variant * 0.18f),
                g.viewportHeight * 1.04f,
            ),
        )
    }

    val period = g.viewportWidth * 2.08f
    forEachWorldCell(worldX, 0.082f, period) { _, left, variant ->
        repeat(8) { i ->
            val x = left + period * (0.07f + i * 0.115f)
            val stemTop = g.viewportHeight * (0.57f + ((i + variant) % 4) * 0.07f)
            drawLine(
                palette.secondary.copy(alpha = 0.48f),
                Offset(x, g.viewportHeight),
                Offset(x + (i % 2) * g.viewportWidth * 0.025f, stemTop),
                strokeWidth = max(2f, g.viewportWidth * 0.007f),
            )
            drawOval(
                palette.highlight.copy(alpha = 0.30f),
                topLeft = Offset(x - g.viewportWidth * 0.025f, stemTop),
                size = Size(g.viewportWidth * 0.05f, g.viewportHeight * 0.025f),
            )
        }

        val birdX = left + period * (0.58f + variant * 0.055f) +
            WindowsMotionAccent.artworkOffsetX(motion, g.viewportWidth)
        val birdY = g.viewportHeight * (0.25f + variant * 0.09f) +
            WindowsMotionAccent.artworkOffsetY(motion, g.viewportHeight)
        val bodyRadius = g.viewportWidth * 0.026f
        drawOval(
            palette.primary.copy(alpha = 0.62f),
            topLeft = Offset(birdX - bodyRadius, birdY - bodyRadius * 0.55f),
            size = Size(bodyRadius * 2.2f, bodyRadius * 1.1f),
        )
        val wingAngle = WindowsMotionAccent.birdWingDegrees(
            motion.phaseSeconds,
            motion.scrollVelocity,
            motion.activity,
        )
        rotate(wingAngle, Offset(birdX, birdY)) {
            drawOval(
                palette.highlight.copy(alpha = 0.54f),
                topLeft = Offset(birdX - bodyRadius * 0.15f, birdY - bodyRadius * 1.15f),
                size = Size(bodyRadius * 1.45f, bodyRadius * 1.15f),
            )
        }
        drawLine(
            palette.highlight.copy(alpha = 0.45f),
            Offset(birdX + bodyRadius * 1.15f, birdY),
            Offset(birdX + bodyRadius * 1.55f, birdY - bodyRadius * 0.18f),
            strokeWidth = max(1.5f, bodyRadius * 0.12f),
        )
    }
}

private fun DrawScope.drawFacets(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
) {
    val period = g.viewportWidth * 1.62f
    forEachWorldCell(worldX, 0.045f, period) { _, left, variant ->
        val unit = period / 5f
        repeat(5) { column ->
            repeat(5) { row ->
                val center = Offset(
                    left + column * unit + unit * 0.42f +
                        ((column + row + variant) % 3) * g.viewportWidth * 0.018f,
                    row * g.viewportHeight / 4f,
                )
                val color = when ((column + row + variant) % 3) {
                    0 -> palette.primary.copy(alpha = 0.26f)
                    1 -> palette.secondary.copy(alpha = 0.34f)
                    else -> palette.highlight.copy(alpha = 0.18f)
                }
                rotate(
                    degrees = 45f + ((column + row) % 2) * 12f,
                    pivot = center,
                ) {
                    drawRect(
                        color = color,
                        topLeft = Offset(center.x - unit * 0.22f, center.y - g.viewportHeight * 0.065f),
                        size = Size(unit * 0.44f, g.viewportHeight * 0.13f),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMountains(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
) {
    forEachWorldCell(worldX, 0.028f, g.mountainPeriod) { _, left, _ ->
        withTransform({ translate(left, 0f) }) {
            drawPath(g.mountainFar, palette.shadow.copy(alpha = 0.38f))
        }
    }
    forEachWorldCell(worldX, 0.068f, g.mountainPeriod) { _, left, _ ->
        withTransform({ translate(left, 0f) }) {
            drawPath(g.mountainNear, palette.secondary.copy(alpha = 0.54f))
        }
    }
    val starPeriod = g.viewportWidth * 1.7f
    forEachWorldCell(worldX, 0.015f, starPeriod) { cell, left, variant ->
        repeat(8) { i ->
            val x = left + starPeriod * (0.05f + i * 0.115f)
            val y = g.viewportHeight * (0.09f + ((i * 5 + variant) % 6) * 0.065f)
            val radius = max(1.2f, g.viewportWidth * (0.0025f + (i % 3) * 0.001f))
            val alpha = 0.22f + BackgroundWorldMath.variant(cell + i, 3) * 0.07f
            drawCircle(palette.highlight.copy(alpha = alpha), radius, Offset(x, y))
        }
    }
}

private fun DrawScope.drawDragon(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    val followX = WindowsMotionAccent.artworkOffsetX(motion, g.viewportWidth)
    val followY = WindowsMotionAccent.artworkOffsetY(motion, g.viewportHeight)
    forEachWorldCell(worldX, WallpaperParallax.IMAGE_RATE, g.dragonPeriod) { _, left, variant ->
        withTransform({
            translate(left + followX, followY + (variant - 1) * g.viewportHeight * 0.025f)
        }) {
            drawPath(
                g.dragonBody,
                palette.primary.copy(alpha = 0.66f),
                style = Stroke(width = max(5f, g.viewportWidth * 0.024f)),
            )
            drawPath(
                g.dragonBody,
                palette.highlight.copy(alpha = 0.28f),
                style = Stroke(width = max(2f, g.viewportWidth * 0.007f)),
            )
            rotate(
                WindowsMotionAccent.dragonTailDegrees(
                    motion.phaseSeconds,
                    motion.scrollVelocity,
                    motion.activity,
                ),
                pivot = Offset(g.dragonPeriod * 0.40f, g.viewportHeight * 0.37f),
            ) {
                drawPath(
                    g.dragonWing,
                    palette.secondary.copy(alpha = 0.56f),
                    style = Stroke(width = max(3f, g.viewportWidth * 0.012f)),
                )
            }
        }

        val head = Offset(
            left + g.dragonPeriod * 0.10f + followX,
            g.viewportHeight * 0.34f + followY + (variant - 1) * g.viewportHeight * 0.025f,
        )
        drawCircle(palette.primary.copy(alpha = 0.70f), g.viewportWidth * 0.040f, head)
        drawCircle(
            palette.highlight.copy(alpha = 0.72f),
            g.viewportWidth * 0.006f,
            Offset(head.x - g.viewportWidth * 0.010f, head.y - g.viewportHeight * 0.005f),
        )
        drawLine(
            palette.highlight.copy(alpha = 0.45f),
            Offset(head.x - g.viewportWidth * 0.025f, head.y - g.viewportHeight * 0.025f),
            Offset(head.x - g.viewportWidth * 0.050f, head.y - g.viewportHeight * 0.060f),
            strokeWidth = max(2f, g.viewportWidth * 0.006f),
        )
    }
}

private fun DrawScope.drawGearsTheme(
    g: WindowsBackgroundGeometry,
    worldX: Double,
    palette: BackgroundPalette,
    motion: WindowsMotionAccentFrame,
) {
    val period = g.viewportWidth * 1.84f
    forEachWorldCell(worldX, WallpaperParallax.IMAGE_RATE, period) { cell, left, variant ->
        repeat(4) { index ->
            val xFraction = when (index) {
                0 -> 0.17f
                1 -> 0.43f
                2 -> 0.70f
                else -> 0.86f
            }
            val yFraction = when (index) {
                0 -> 0.24f + variant * 0.06f
                1 -> 0.52f - variant * 0.05f
                2 -> 0.30f + variant * 0.07f
                else -> 0.70f
            }
            val radius = g.viewportWidth * (0.055f + (index % 3) * 0.018f)
            drawGear(
                center = Offset(left + period * xFraction, g.viewportHeight * yFraction),
                radius = radius,
                rotationDegrees = WindowsMotionAccent.gearDegrees(
                    motion.phaseSeconds,
                    index + BackgroundWorldMath.variant(cell, 4),
                ),
                color = if (index % 2 == 0) palette.highlight.copy(alpha = 0.52f)
                else palette.secondary.copy(alpha = 0.62f),
            )
        }
    }
}

private val GEAR_COS = FloatArray(12) { index ->
    cos(index * (2.0 * PI / 12.0)).toFloat()
}
private val GEAR_SIN = FloatArray(12) { index ->
    sin(index * (2.0 * PI / 12.0)).toFloat()
}

/** A gear uses precomputed spoke directions; there is no trigonometry or Path allocation per frame. */
private fun DrawScope.drawGear(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    color: Color,
) {
    val stroke = max(1.5f, radius * 0.10f)
    rotate(rotationDegrees, center) {
        drawCircle(color, radius, center, style = Stroke(width = stroke))
        drawCircle(color, radius * 0.28f, center, style = Stroke(width = stroke))
        for (index in GEAR_COS.indices) {
            val cos = GEAR_COS[index]
            val sin = GEAR_SIN[index]
            val inner = Offset(
                center.x + cos * radius * 0.82f,
                center.y + sin * radius * 0.82f,
            )
            val outer = Offset(
                center.x + cos * radius * 1.14f,
                center.y + sin * radius * 1.14f,
            )
            drawLine(color, inner, outer, strokeWidth = stroke)
        }
    }
}
