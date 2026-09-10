package com.flivoro.tile8auncher.ui.components

import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flivoro.tile8auncher.data.FlipAnimationMode
import com.flivoro.tile8auncher.data.TileType
import com.flivoro.tile8auncher.data.TimeCurve
import com.flivoro.tile8auncher.ui.animation.AllAppsLaunchMotion
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.LaunchFrame
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.animation.Quad
import com.flivoro.tile8auncher.ui.animation.WindowsLaunchMotion
import kotlinx.coroutines.flow.first
import kotlin.math.cbrt
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A single rotating plane, viewed from the center of the launcher window.
 *
 * Back while the launch is still in flight reverses this exact same progress
 * track from its current frame back to the source. No alternate geometry,
 * easing, perspective or face swap is introduced: the original 0 -> 1 launch
 * path simply becomes currentProgress -> 0. Once frame 0 has actually been
 * submitted, Back is re-dispatched to the launcher so its existing dismissal
 * logic restores the source tile without ever handing off to the target app.
 */
@Composable
fun FlipLaunchOverlay(
    state: FlipAnimationState,
    onAnimationEnd: () -> Unit,
    destinationContent: (@Composable () -> Unit)? = null,
) {
    val tile = state.sourceTile ?: return
    if (!state.isRunning) return
    val modern = state.animationMode == FlipAnimationMode.MODERN
    val allApps = state.origin == LaunchOrigin.ALL_APPS
    val progress = remember(state) { Animatable(0f) }
    val finish by rememberUpdatedState(onAnimationEnd)
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var finalFrameDrawn by remember(state) { mutableStateOf(false) }
    var reverseRequest by remember(state) { mutableIntStateOf(0) }
    var reversing by remember(state) { mutableStateOf(false) }
    var reverseCompleted by remember(state) { mutableStateOf(false) }

    // The overlay is composed after the shell BackHandler, so while a launch is
    // active this callback has priority. Repeated Back presses during reversal
    // are ignored; they must not restart or speed-change the same physical path.
    BackHandler(enabled = state.isRunning && !reversing && !reverseCompleted) {
        reversing = true
        reverseRequest++
    }

    LaunchedEffect(state) {
        progress.animateTo(
            1f,
            tween(
                durationMillis = state.timing.durationMillis.coerceIn(100, 2000),
                easing = LinearEasing,
            ),
        )
        // A completed Animatable value is not proof that its final frame was drawn.
        // Let that frame be submitted before another Activity can cover this window.
        snapshotFlow { finalFrameDrawn }.first { it }
        withFrameNanos { }
        // If Back arrived on the final launch frame, reversal may start after the
        // forward Animatable completed. This guard prevents that race from handing
        // off to the external app while the reverse path is already underway.
        if (!reversing && !reverseCompleted) finish()
    }

    LaunchedEffect(reverseRequest) {
        if (reverseRequest == 0) return@LaunchedEffect

        val currentProgress = progress.value.coerceIn(0f, 1f)
        val reverseDuration = reverseLaunchDurationMillis(
            state.timing.durationMillis.coerceIn(100, 2000),
            currentProgress,
        )
        progress.animateTo(
            0f,
            tween(durationMillis = reverseDuration, easing = LinearEasing),
        )

        // Submit the exact source frame before unhiding the real source tile.
        reverseCompleted = true
        withFrameNanos { }
        backDispatcher?.onBackPressed()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                // The launch owns pointer input until handoff/reversal completes,
                // including taps outside the card. System Back remains responsive.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        var windowOrigin by remember { mutableStateOf(Offset.Zero) }
        val source = WindowsLaunchMotion.sourceBounds(
            state.sourceBounds.translate(-windowOrigin), width, height,
        )
        val frontMatrix = remember { Matrix() }
        val backMatrix = remember { Matrix() }
        val logoMatrix = remember { Matrix() }
        val frontPoints = remember { FloatArray(8) }
        val backPoints = remember { FloatArray(8) }
        val logoPoints = remember { FloatArray(8) }
        var sourceLogo by remember(state) { mutableStateOf<Rect?>(null) }
        val sharedLogo = !allApps && destinationContent == null && tile.tileType !in listOf(
            TileType.CLOCK, TileType.CALENDAR, TileType.DESKTOP,
        )
        val frontRect = remember(source.width, source.height) {
            floatArrayOf(0f, 0f, source.width, 0f, source.width, source.height, 0f, source.height)
        }
        val backRect = remember(width, height) {
            floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
        }

        fun frame(): LaunchFrame {
            val time = progress.value
            if (allApps) {
                return AllAppsLaunchMotion.frame(
                    if (state.timing.curve == TimeCurve.REFERENCE) time
                    else AllAppsLaunchMotion.progressForExpansion(state.timing.transform(time)),
                    width,
                    height,
                )
            }
            val motionProgress = when {
                state.timing.curve == TimeCurve.REFERENCE -> time
                modern -> 1f - cbrt(1f - state.timing.transform(time))
                else -> WindowsLaunchMotion.progressForRotation(state.timing.transform(time))
            }
            return WindowsLaunchMotion.frame(motionProgress, source, width, height, modern)
        }

        Box(Modifier.fillMaxSize().onGloballyPositioned { windowOrigin = it.positionInWindow() }) {
            // Both faces have fixed layout dimensions. Progress is read only during
            // drawing, so animation does not remeasure text or icons every frame.
            WindowsTileFace(
                tile,
                state.appIcon,
                Modifier
                    .requiredSize(
                        with(density) { source.width.toDp() },
                        with(density) { source.height.toDp() },
                    )
                    .projectedFace(
                        frontMatrix,
                        frontRect,
                        frontPoints,
                        frame = ::frame,
                        visible = { it.isFrontFace && !modern && !allApps },
                    ),
                logoModifier = if (sharedLogo) {
                    Modifier
                        .onGloballyPositioned {
                            sourceLogo = it.boundsInWindow().translate(-windowOrigin)
                        }
                        .alpha(0f)
                } else Modifier,
            )
            val iconSize = with(density) { (min(width, height) * .20f).toDp() }
                .coerceIn(48.dp, 104.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (allApps) AllAppsLaunchMotion.opacity(progress.value) else 1f
                    }
                    .projectedFace(
                        backMatrix,
                        backRect,
                        backPoints,
                        frame = ::frame,
                        visible = { !it.isFrontFace || modern },
                        onDrawn = { if (progress.value == 1f) finalFrameDrawn = true },
                    )
                    .background(state.accentColor),
                contentAlignment = Alignment.Center,
            ) {
                if (destinationContent != null) {
                    destinationContent()
                } else if (!sharedLogo) {
                    when {
                        tile.iconGlyph.isNotEmpty() -> MetroIcon(
                            tile.iconGlyph,
                            color = Color.White,
                            size = iconSize,
                        )

                        state.appIcon != null -> Image(
                            state.appIcon,
                            tile.title,
                            Modifier.size(iconSize),
                        )

                        else -> MetroIcon("app", color = Color.White, size = iconSize)
                    }
                }
            }
            if (sharedLogo) {
                // This is the same drawable for the entire turn. Replacing two
                // independently scaled face icons at the edge caused a size reset.
                val logoSizePx = with(density) { iconSize.toPx() }
                val logoRect = remember(logoSizePx) {
                    floatArrayOf(
                        0f, 0f,
                        logoSizePx, 0f,
                        logoSizePx, logoSizePx,
                        0f, logoSizePx,
                    )
                }
                Box(
                    Modifier
                        .size(iconSize)
                        .projectedFace(
                            logoMatrix,
                            logoRect,
                            logoPoints,
                            frame = ::frame,
                            visible = { sourceLogo != null },
                            quad = { current ->
                                WindowsLaunchMotion.logoQuad(
                                    current,
                                    source,
                                    width,
                                    height,
                                    sourceLogo ?: Rect.Zero,
                                    logoSizePx,
                                )
                            },
                        ),
                ) {
                    when {
                        tile.iconGlyph.isNotEmpty() -> MetroIcon(
                            tile.iconGlyph,
                            color = Color.White,
                            size = iconSize,
                        )

                        state.appIcon != null -> Image(
                            state.appIcon,
                            tile.title,
                            Modifier.fillMaxSize(),
                        )

                        else -> MetroIcon("app", color = Color.White, size = iconSize)
                    }
                }
            }
        }
    }
}

/**
 * Reversal traverses the same normalized timeline at the same temporal speed.
 * For example, backing out at 60% of a 650 ms launch takes ~390 ms to return.
 */
internal fun reverseLaunchDurationMillis(baseDurationMillis: Int, progress: Float): Int {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.001f) return 1
    return (baseDurationMillis.coerceAtLeast(1) * p)
        .roundToInt()
        .coerceAtLeast(16)
}

private fun Modifier.projectedFace(
    matrix: Matrix,
    sourcePoints: FloatArray,
    destinationPoints: FloatArray,
    frame: () -> LaunchFrame,
    visible: (LaunchFrame) -> Boolean,
    quad: (LaunchFrame) -> Quad = { it.quad },
    onDrawn: () -> Unit = {},
): Modifier = drawWithContent {
    val current = frame()
    if (visible(current) && current.quad.bounds.width > .1f) {
        quad(current).writeTo(destinationPoints)
        matrix.reset()
        if (matrix.setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)) {
            val canvas = drawContext.canvas.nativeCanvas
            val checkpoint = canvas.save()
            canvas.concat(matrix)
            drawContent()
            canvas.restoreToCount(checkpoint)
            onDrawn()
        }
    }
}
