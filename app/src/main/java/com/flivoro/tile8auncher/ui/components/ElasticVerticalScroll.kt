package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** Adds a resisted, spring-returning rubber band at either vertical scroll edge. */
@Composable
fun Modifier.elasticVerticalScroll(
    resistance: Float = 0.52f,
    maxOverscroll: Dp = 96.dp,
    classicPhonePhysics: Boolean = false,
): Modifier {
    val density = LocalDensity.current
    val resistanceFactor = resistance.coerceIn(0.05f, 1f)
    val maxOverscrollPx = with(density) { maxOverscroll.toPx() }.coerceAtLeast(1f)
    var overscrollPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val returnAnimation = remember { Animatable(0f) }
    val returnJob = remember { mutableStateOf<Job?>(null) }

    fun cancelReturn() {
        returnJob.value?.cancel()
        returnJob.value = null
    }

    fun startReturn(velocity: Float = 0f) {
        cancelReturn()
        if (abs(overscrollPx) < 0.5f && abs(velocity) < 1f) {
            overscrollPx = 0f
            return
        }
        val start = overscrollPx
        returnJob.value = scope.launch {
            returnAnimation.snapTo(start)
            if (classicPhonePhysics) {
                // Disco's BetterScroll uses a 300 ms bounce rather than an Android spring.
                returnAnimation.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(300, easing = CubicBezierEasing(.165f, .84f, .44f, 1f)),
                ) { overscrollPx = value }
            } else {
                returnAnimation.animateTo(
                    targetValue = 0f,
                    initialVelocity = velocity.coerceIn(-maxOverscrollPx * 8f, maxOverscrollPx * 8f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { overscrollPx = value }
            }
            overscrollPx = 0f
        }
    }

    val connection = remember(resistanceFactor, maxOverscrollPx, classicPhonePhysics) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y == 0f || overscrollPx == 0f) {
                    return Offset.Zero
                }
                val returning = (overscrollPx > 0f && available.y < 0f) ||
                    (overscrollPx < 0f && available.y > 0f)
                if (!returning) return Offset.Zero

                cancelReturn()
                val consumedMagnitude = min(abs(available.y), abs(overscrollPx))
                val consumedY = if (available.y < 0f) -consumedMagnitude else consumedMagnitude
                overscrollPx = (overscrollPx + consumedY).takeUnless { abs(it) < 0.5f } ?: 0f
                return Offset(0f, consumedY)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    (source != NestedScrollSource.UserInput && source != NestedScrollSource.SideEffect) ||
                    available.y == 0f
                ) return Offset.Zero

                cancelReturn()
                val cap = if (viewportHeightPx > 0f) {
                    min(maxOverscrollPx, viewportHeightPx * 0.24f)
                } else maxOverscrollPx
                overscrollPx = elasticOffset(overscrollPx, available.y, cap, resistanceFactor)
                // Keep touch drags responsive; fling velocity is handed to the return spring.
                return if (source == NestedScrollSource.UserInput) Offset(0f, available.y) else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                startReturn(available.y * resistanceFactor)
                return Velocity(0f, available.y)
            }
        }
    }

    return this
        .onSizeChanged { viewportHeightPx = it.height.toFloat() }
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = overscrollPx
            if (classicPhonePhysics) {
                // Disco overscrollFramework: a maximum 125 CSS-pixel pull
                // compresses the content axis from 62 to 58 (roughly 6.45%).
                val cap = min(maxOverscrollPx, if (viewportHeightPx > 0f)
                    viewportHeightPx * .24f else maxOverscrollPx).coerceAtLeast(1f)
                scaleY = 1f - (abs(overscrollPx) / cap).coerceIn(0f, 1f) * (1f - 58f / 62f)
                transformOrigin = TransformOrigin(.5f, if (overscrollPx >= 0f) 0f else 1f)
            }
        }
}
