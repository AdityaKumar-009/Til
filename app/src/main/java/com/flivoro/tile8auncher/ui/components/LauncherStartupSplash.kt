package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The launcher owns this surface only while core Start + All Apps state is being prepared.
 * This has no timeout and is never replayed by Home, Back or returning from another app.
 */
@Composable
fun LauncherStartupSplash(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090909)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MosaicBloomLoader(accentColor = StartPersonalization.accentColor)
            Spacer(Modifier.height(28.dp))
            Text(
                text = message,
                color = Color(0xFFC2C2C2),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MosaicBloomLoader(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "mosaic-bloom")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = BLOOM_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        ),
        label = "mosaic-bloom-phase",
    )
    val tileColors = remember(accentColor) {
        listOf(
            accentColor, Color(0xFF8B74DF), accentColor,
            Color(0xFF8B74DF), Color(0xFFC1B4F6), Color(0xFF8B74DF),
            accentColor, Color(0xFF8B74DF), accentColor,
        )
    }

    Canvas(
        modifier = Modifier
            .size(54.dp)
            .graphicsLayer { rotationZ = -4f },
    ) {
        val cellSize = 14.dp.toPx()
        val gap = 6.dp.toPx()
        val step = cellSize + gap
        val corner = CornerRadius(4.dp.toPx())

        for (index in 0 until 9) {
            val row = index / 3
            val column = index % 3
            val stagger = index * BLOOM_STAGGER_MILLIS / BLOOM_DURATION_MILLIS.toFloat()
            val localPhase = (phase - stagger + 1f) % 1f
            val bloom = bloomEnvelope(localPhase)
            val scale = 0.72f + 0.28f * bloom
            val alpha = 0.28f + 0.72f * bloom
            val drawnSize = cellSize * scale
            val center = Offset(
                x = column * step + cellSize / 2f,
                y = row * step + cellSize / 2f,
            )
            val topLeft = Offset(
                x = center.x - drawnSize / 2f,
                y = center.y - drawnSize / 2f,
            )

            if (index == 4) {
                val glowRadius = cellSize * 1.65f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tileColors[index].copy(alpha = 0.34f * bloom),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
            }

            drawRoundRect(
                color = tileColors[index].copy(alpha = alpha),
                topLeft = topLeft,
                size = Size(drawnSize, drawnSize),
                cornerRadius = corner,
            )
        }
    }
}

/**
 * A periodic bloom envelope: each tile reaches the same size and speed at the loop
 * boundary, so the shared animation phase never visibly snaps when it repeats.
 */
private fun bloomEnvelope(phase: Float): Float {
    return when {
        phase < 0.25f -> smoothStep(phase / 0.25f)
        phase < 0.58f -> 1f
        phase < 0.73f -> lerp(1f, 0.79f, smoothStep((phase - 0.58f) / 0.15f))
        else -> lerp(0.79f, 0f, smoothStep((phase - 0.73f) / 0.27f))
    }
}

private fun smoothStep(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

private fun lerp(start: Float, end: Float, amount: Float): Float {
    return start + (end - start) * amount
}

private const val BLOOM_DURATION_MILLIS = 3_200
private const val BLOOM_STAGGER_MILLIS = 80
