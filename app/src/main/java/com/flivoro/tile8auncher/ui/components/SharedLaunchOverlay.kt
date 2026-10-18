package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.FlipReverseReason
import com.flivoro.tile8auncher.ui.animation.WindowsLaunchMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Exact saved choice is shared by all three launcher skins, never an implicit mode override. */
internal data class SharedLaunchFrame(
    val tileAlpha: Float,
    val tileRotationY: Float,
    val tileExpansion: Float,
    val appAlpha: Float,
    val appRotationY: Float,
    val appOffsetX: Float,
    val appScale: Float,
)

internal object SharedLaunchMotion {
    fun duration(style: MasterLaunchAnimation): Int = when (style) {
        MasterLaunchAnimation.DEFAULT -> 500
        MasterLaunchAnimation.FLIP -> 440
        MasterLaunchAnimation.TURNSTILE -> 410
        MasterLaunchAnimation.ZOOM -> 360
        MasterLaunchAnimation.SLIDE -> 320
        MasterLaunchAnimation.FADE -> 260
    }

    fun frame(style: MasterLaunchAnimation, raw: Float): SharedLaunchFrame {
        val t = FastOutSlowInEasing.transform(raw.coerceIn(0f, 1f))
        fun phase(a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)
        return when (style) {
            MasterLaunchAnimation.DEFAULT, MasterLaunchAnimation.FLIP -> {
                val leave = phase(0f, .57f)
                val enter = phase(.38f, 1f)
                SharedLaunchFrame(1f - leave, -89f * leave, .50f * leave,
                    enter, 82f * (1f - enter), 0f, 1f)
            }
            MasterLaunchAnimation.TURNSTILE -> {
                val leave = phase(0f, .52f)
                val enter = phase(.26f, 1f)
                SharedLaunchFrame(1f - leave, -78f * leave, 0f,
                    enter, 76f * (1f - enter), 0f, 1f)
            }
            MasterLaunchAnimation.ZOOM -> {
                val appear = phase(.40f, .85f)
                SharedLaunchFrame(1f - appear, 0f, t,
                    appear, 0f, 0f, 1f)
            }
            MasterLaunchAnimation.SLIDE -> {
                SharedLaunchFrame(1f - phase(0f, .70f), 0f, 0f,
                    phase(0f, .25f), 0f, 1f - t, 1f)
            }
            MasterLaunchAnimation.FADE -> {
                SharedLaunchFrame(1f - t, 0f, 0f, t, 0f, 0f, 1f)
            }
        }
    }
}

/**
 * Opt-in app-launch overlay. "Default" NEVER comes here: Desktop keeps its fitted
 * original FlipLaunchOverlay and the two phone modes keep their native choreography.
 * For an explicit preset this one overlay owns forward + cancelled reverse motion.
 */
@Composable
internal fun SharedLaunchOverlay(
    state: FlipAnimationState,
    progress: Animatable<Float, AnimationVector1D>,
    onAnimationEnd: () -> Unit,
    onReverseAnimationEnd: (FlipReverseReason) -> Unit,
    destinationContent: (@Composable () -> Unit)? = null,
) {
    val tile = state.sourceTile ?: return
    if (!state.isRunning) return
    val style = state.masterLaunchAnimation
    val finishForward by rememberUpdatedState(onAnimationEnd)
    val finishReverse by rememberUpdatedState(onReverseAnimationEnd)
    val reverseReason by rememberUpdatedState(state.reverseReason)
    LaunchedEffect(tile.id, state.sourceBounds, state.direction, style) {
        val duration = SharedLaunchMotion.duration(style)
        if (state.direction == FlipAnimationDirection.FORWARD) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(duration, easing = LinearEasing))
            // Let the terminal frame be submitted before launching another Activity.
            withFrameNanos { }
            withFrameNanos { }
            finishForward()
        } else {
            val remaining = (duration * progress.value.coerceIn(0f, 1f)).roundToInt()
            if (remaining > 0) {
                progress.animateTo(0f, tween(remaining, easing = LinearEasing))
            } else progress.snapTo(0f)
            withFrameNanos { }
            finishReverse(reverseReason ?: FlipReverseReason.BACK)
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        // Bounds arrive in window coordinates; layout and graphics transforms are local.
        // Center fallback preserves behavior for launcher-internal Settings without a tile.
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        var origin = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
        val src = WindowsLaunchMotion.sourceBounds(
            state.sourceBounds.translate(-origin.value), widthPx, heightPx)
        val sourceWidth = with(density) { src.width.toDp() }
        val sourceHeight = with(density) { src.height.toDp() }
        val sourceX = src.left.roundToInt()
        val sourceY = src.top.roundToInt()
        val camera = maxOf(1000f, heightPx * 2f)

        Box(Modifier.fillMaxSize().onGloballyPositioned { origin.value = it.positionInWindow() }) {
            Box(
                Modifier.offset { IntOffset(sourceX, sourceY) }
                    .size(sourceWidth, sourceHeight)
                    .graphicsLayer {
                        val f = SharedLaunchMotion.frame(style, progress.value)
                        alpha = f.tileAlpha
                        rotationY = f.tileRotationY
                        transformOrigin = if (style == MasterLaunchAnimation.TURNSTILE)
                            TransformOrigin(0f, .5f) else TransformOrigin.Center
                        scaleX = 1f + (widthPx / src.width - 1f) * f.tileExpansion
                        scaleY = 1f + (heightPx / src.height - 1f) * f.tileExpansion
                        translationX = (widthPx * .5f - src.center.x) * f.tileExpansion
                        translationY = (heightPx * .5f - src.center.y) * f.tileExpansion
                        cameraDistance = camera
                    },
            ) {
                WindowsTileFace(tile, state.appIcon, Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val f = SharedLaunchMotion.frame(style, progress.value)
                        alpha = f.appAlpha
                        rotationY = f.appRotationY
                        translationX = f.appOffsetX * widthPx
                        scaleX = f.appScale
                        scaleY = f.appScale
                        transformOrigin = if (style == MasterLaunchAnimation.TURNSTILE)
                            TransformOrigin(1f, .5f) else TransformOrigin.Center
                        cameraDistance = camera
                    }
                    .background(state.accentColor),
                contentAlignment = Alignment.Center,
            ) {
                if (destinationContent != null) {
                    destinationContent()
                } else {
                    when {
                        tile.iconGlyph.isNotEmpty() -> MetroIcon(tile.iconGlyph,
                            color = androidx.compose.ui.graphics.Color.White, size = 78.dp)
                        state.appIcon != null -> Image(
                            bitmap = state.appIcon, contentDescription = tile.title,
                            modifier = Modifier.size(78.dp))
                        else -> MetroIcon("app",
                            color = androidx.compose.ui.graphics.Color.White, size = 78.dp)
                    }
                }
            }
        }
    }
}
