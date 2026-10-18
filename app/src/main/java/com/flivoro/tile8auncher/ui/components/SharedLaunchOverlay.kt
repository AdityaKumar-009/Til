package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.FlipReverseReason
import com.flivoro.tile8auncher.ui.animation.WindowsLaunchMotion
import com.flivoro.tile8auncher.ui.phone.MobileStartMotion
import com.flivoro.tile8auncher.ui.phone.PhoneStartChoreography
import com.flivoro.tile8auncher.ui.phone.PhoneStartMotionFrame
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

/**
 * An adapter for running the existing phone launch samples over Desktop tiles.
 * No new motion curve: all rotation, translation, scale and opacity values come
 * from PhoneStartChoreography (WP8.1) or MobileStartMotion (W10M).
 * Phone-to-phone overrides run on PhoneLauncherSurface's actual native tile grid.
 */
internal data class SharedLaunchFrame(
    val tileAlpha: Float,
    val tileRotationY: Float,
    val tileTranslationX: Float,
    val tileTranslationY: Float,
    val tileScale: Float,
    val tilePivotX: Float,
    val appAlpha: Float,
)

internal object SharedLaunchMotion {
    fun duration(style: MasterLaunchAnimation, heightCss: Float = 850f, allApps: Boolean = false): Int =
        when (style) {
            MasterLaunchAnimation.PHONE_8 -> PhoneStartChoreography.totalMillis(
                LauncherUiMode.PHONE_8, exiting = true,
                viewportHeightCssPx = heightCss,
            )
            MasterLaunchAnimation.MOBILE_10 -> MobileStartMotion.EXIT_TOTAL_MS
            MasterLaunchAnimation.DEFAULT, MasterLaunchAnimation.DESKTOP ->
                WindowsLaunchMotion.DurationMillis
        }

    fun frame(
        style: MasterLaunchAnimation,
        raw: Float,
        source: Rect = Rect(80f, 80f, 200f, 200f),
        viewportWidth: Float = 400f,
        viewportHeight: Float = 850f,
        allApps: Boolean = false,
    ): SharedLaunchFrame {
        val p = raw.coerceIn(0f, 1f)
        val millis = (duration(style, viewportHeight, allApps) * p).roundToInt()
        val native: PhoneStartMotionFrame = when (style) {
            MasterLaunchAnimation.PHONE_8 -> {
                if (allApps) PhoneStartChoreography.sampleAppListExit(
                    elapsedMillis = millis,
                    animationIndex = 0f,
                    viewportHeightCssPx = viewportHeight,
                    viewportWidthCssPx = viewportWidth,
                    selected = true,
                    tileLeftCssPx = source.left,
                    tileWidthCssPx = source.width,
                )
                else PhoneStartChoreography.sample(
                    mode = LauncherUiMode.PHONE_8,
                    exiting = true,
                    elapsedMillis = millis,
                    mobileRowFraction = (source.center.y / viewportHeight).coerceIn(0f, 1f),
                    animationIndex = 0f,
                    viewportHeightCssPx = viewportHeight,
                    viewportWidthCssPx = viewportWidth,
                    tileLeftCssPx = source.left,
                    selected = true,
                    tileWidthCssPx = source.width,
                )
            }
            MasterLaunchAnimation.MOBILE_10 -> MobileStartMotion.sample(
                exiting = true,
                elapsedMillis = millis,
                row = (source.center.y / viewportHeight).coerceIn(0f, 1f),
                centerX = source.center.x,
                centerY = source.center.y,
                viewportCenterX = viewportWidth * .5f,
                viewportCenterY = viewportHeight * .5f,
                selected = true,
            )
            else -> PhoneStartMotionFrame(
                alpha = 1f - p, rotationY = 0f, translationXPx = 0f,
                translationYPx = 0f, translationZPx = 0f, scale = 1f,
                pivotX = .5f,
            )
        }
        // These are the pre-existing phone motion samples. The destination
        // handoff is a still colored frame, not an invented fourth transition.
        return SharedLaunchFrame(
            tileAlpha = native.alpha,
            tileRotationY = native.rotationY,
            tileTranslationX = native.translationXPx,
            tileTranslationY = native.translationYPx,
            tileScale = native.scale,
            tilePivotX = native.pivotX,
            appAlpha = if (p >= 1f) 1f else 0f,
        )
    }
}

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
    var lastFramePresented by remember(tile.id, state.sourceBounds) { mutableStateOf(false) }
    var firstFramePresented by remember(tile.id, state.sourceBounds) { mutableStateOf(false) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        },
    ) {
        val width = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        var origin by remember { mutableStateOf(Offset.Zero) }
        val src = WindowsLaunchMotion.sourceBounds(
            state.sourceBounds.translate(-origin), width, height,
        )
        val cssSource = with(density) {
            Rect(src.left.toDp().value, src.top.toDp().value,
                src.right.toDp().value, src.bottom.toDp().value)
        }
        val viewportWidthCss = with(density) { width.toDp().value }
        val viewportHeightCss = with(density) { height.toDp().value }
        fun frame() = SharedLaunchMotion.frame(
            style, progress.value, cssSource, viewportWidthCss, viewportHeightCss,
            state.origin == com.flivoro.tile8auncher.ui.animation.LaunchOrigin.ALL_APPS,
        )
        val duration = SharedLaunchMotion.duration(style, viewportHeightCss)
        LaunchedEffect(tile.id, state.sourceBounds, state.direction, style) {
            if (state.direction == FlipAnimationDirection.FORWARD) {
                lastFramePresented = false
                progress.snapTo(0f)
                progress.animateTo(1f, tween(duration, easing = LinearEasing))
                snapshotFlow { lastFramePresented }.first { it }
                withFrameNanos { }
                finishForward()
            } else {
                firstFramePresented = false
                val backwardsMillis = (duration * progress.value.coerceIn(0f, 1f)).roundToInt()
                if (backwardsMillis > 0) {
                    progress.animateTo(0f, tween(backwardsMillis, easing = LinearEasing))
                } else progress.snapTo(0f)
                snapshotFlow { firstFramePresented }.first { it }
                withFrameNanos { }
                finishReverse(reverseReason ?: FlipReverseReason.BACK)
            }
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            Box(
                Modifier.offset { IntOffset(src.left.roundToInt(), src.top.roundToInt()) }
                    .size(with(density) { src.width.toDp() }, with(density) { src.height.toDp() })
                    .graphicsLayer {
                        val motion = frame()
                        alpha = motion.tileAlpha
                        rotationY = motion.tileRotationY
                        translationX = with(density) { motion.tileTranslationX.dp.toPx() }
                        translationY = with(density) { motion.tileTranslationY.dp.toPx() }
                        scaleX = motion.tileScale
                        scaleY = motion.tileScale
                        transformOrigin = TransformOrigin(motion.tilePivotX, .5f)
                        cameraDistance = maxOf(900f, height * 1.5f)
                    },
            ) {
                WindowsTileFace(tile, state.appIcon, Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        alpha = frame().appAlpha
                    }
                    .background(state.accentColor),
                contentAlignment = Alignment.Center,
            ) {
                if (destinationContent != null) {
                    destinationContent()
                } else {
                    when {
                        tile.iconGlyph.isNotEmpty() -> MetroIcon(
                            tile.iconGlyph, color = androidx.compose.ui.graphics.Color.White, size = 78.dp)
                        state.appIcon != null -> Image(
                            state.appIcon, tile.title, Modifier.size(78.dp))
                        else -> MetroIcon(
                            "app", color = androidx.compose.ui.graphics.Color.White, size = 78.dp)
                    }
                }
            }
            // Prevent beginning the external Activity until the terminal Compose
            // frame has actually drawn (not just reached a numeric progress=1).
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0f }
                .then(Modifier.onGloballyPositioned {
                    // onGloballyPositioned alone is insufficient for terminal
                    // frames; progress is still sampled in graphicsLayer above.
                }))
            if (progress.value >= 1f) {
                androidx.compose.runtime.SideEffect { lastFramePresented = true }
            }
            if (progress.value <= 0f) {
                androidx.compose.runtime.SideEffect { firstFramePresented = true }
            }
        }
    }
}
