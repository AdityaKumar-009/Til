package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
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
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign

/**
 * Adds a small, shared rubber band at either horizontal scroll edge.
 *
 * Apply this to the LazyRow itself. The row still owns normal scrolling and flings. Pixels actually
 * consumed by either Start or All Apps also advance one lightweight shared wallpaper coordinate;
 * unconsumed edge rubber-banding and vertical gestures do not affect it.
 */
@Composable
fun Modifier.elasticHorizontalScroll(
    resistance: Float = 0.52f,
    maxOverscroll: Dp = 96.dp,
): Modifier {
    val density = LocalDensity.current
    val resistanceFactor = resistance.coerceIn(0.05f, 1f)
    val maxOverscrollPx = with(density) { maxOverscroll.toPx() }.coerceAtLeast(1f)
    var overscrollPx by remember { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
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
            returnAnimation.animateTo(
                targetValue = 0f,
                initialVelocity = velocity.coerceIn(-maxOverscrollPx * 8f, maxOverscrollPx * 8f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) {
                overscrollPx = value
            }
            overscrollPx = 0f
        }
    }

    val connection = remember(resistanceFactor, maxOverscrollPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f || overscrollPx == 0f) {
                    return Offset.Zero
                }

                val returning = (overscrollPx > 0f && available.x < 0f) ||
                    (overscrollPx < 0f && available.x > 0f)
                if (!returning) return Offset.Zero

                cancelReturn()
                val consumedMagnitude = min(abs(available.x), abs(overscrollPx))
                val consumedX = if (available.x < 0f) -consumedMagnitude else consumedMagnitude
                overscrollPx = (overscrollPx + consumedX).takeUnless { abs(it) < 0.5f } ?: 0f
                return Offset(consumedX, 0f)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) &&
                    consumed.x != 0f
                ) {
                    SharedWallpaperScroll.onContentConsumed(consumed.x)
                }

                if (source != NestedScrollSource.UserInput &&
                    source != NestedScrollSource.SideEffect ||
                    available.x == 0f
                ) {
                    return Offset.Zero
                }

                cancelReturn()
                val cap = if (viewportWidthPx > 0f) {
                    min(maxOverscrollPx, viewportWidthPx * 0.24f)
                } else {
                    maxOverscrollPx
                }
                overscrollPx = elasticOffset(overscrollPx, available.x, cap, resistanceFactor)
                // A fling must stop at the real list edge, then transfer its remaining
                // velocity to the return spring. Consuming it here prolongs the fling.
                return if (source == NestedScrollSource.UserInput) Offset(available.x, 0f) else Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                startReturn(available.x * resistanceFactor)
                return Velocity(available.x, 0f)
            }
        }
    }

    return this
        .onSizeChanged { viewportWidthPx = it.width.toFloat() }
        .nestedScroll(connection)
        .graphicsLayer { translationX = overscrollPx }
}

/** Integrates diminishing resistance without a hard limit or dependence on event frequency. */
internal fun elasticOffset(offset: Float, delta: Float, cap: Float, resistance: Float): Float {
    if (delta == 0f) return offset
    val direction = delta.sign
    val current = (offset * direction).coerceIn(0f, cap)
    return direction * (cap - (cap - current) * exp(-abs(delta) * resistance / cap))
}
