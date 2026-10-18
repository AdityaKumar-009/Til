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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.FlipReverseReason
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.animation.StoryLaunchMotion
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** One display clock and one source display list, shared by all three skins. */
internal class StoryLaunchScene(val state: FlipAnimationState, val progress: () -> Float) {
    var viewport by mutableStateOf(Rect.Zero)
    var sourceLayer by mutableStateOf<GraphicsLayer?>(null)
    var capturedSourceBounds by mutableStateOf(Rect.Zero)

    fun isSelected(id: String): Boolean {
        val tile = state.sourceTile ?: return false
        return tile.id == id || (!tile.packageName.isNullOrBlank() &&
            (id == "app_${tile.packageName}" || id == "phone_app_${tile.packageName}"))
    }
}

internal val LocalStoryLaunchScene = staticCompositionLocalOf<StoryLaunchScene?> { null }

/**
 * Transfer drawing ownership of the selected, REAL tile to the launch plane.
 * It is a reusable GPU display list, not a bitmap copy or another icon decode.
 * Its original cell draws nothing, so there cannot be a stationary duplicate.
 * Every other physical tile/row samples the same spatial motion in graphicsLayer.
 */
@Composable
internal fun Modifier.storyLaunchActor(
    id: String,
    origin: LaunchOrigin,
    sourceCandidate: Boolean = true,
): Modifier {
    val scene = LocalStoryLaunchScene.current ?: return this
    if (scene.state.origin != origin) return this
    var bounds by remember(id) { mutableStateOf(Rect.Zero) }
    val measured = this.onGloballyPositioned {
        bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }
    if (sourceCandidate && scene.isSelected(id) && scene.state.sourceBounds.width > 1f &&
        scene.state.sourceBounds.height > 1f) {
        val layer = rememberGraphicsLayer()
        DisposableEffect(scene, layer) {
            onDispose { if (scene.sourceLayer === layer) scene.sourceLayer = null }
        }
        return measured.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            if (scene.sourceLayer !== layer) scene.sourceLayer = layer
            if (bounds.width > 0f && bounds.height > 0f && scene.capturedSourceBounds != bounds) {
                scene.capturedSourceBounds = bounds
            }
            // The launch overlay, after all the surrounding actors, draws it.
        }
    }
    return measured.graphicsLayer {
        val viewport = scene.viewport
        if (viewport.width > 1f && viewport.height > 1f) {
            val motion = StoryLaunchMotion.neighbor(scene.state.masterLaunchAnimation,
                scene.progress(), bounds, StoryLaunchMotion.source(
                    scene.capturedSourceBounds.takeIf { it.width > 0f } ?: scene.state.sourceBounds, viewport), viewport)
            alpha = motion.alpha
            translationX = motion.x
            translationY = motion.y
            scaleX = motion.scale
            scaleY = motion.scale
            rotationX = motion.rotationX
            rotationY = motion.rotationY
            transformOrigin = TransformOrigin(motion.pivotX, motion.pivotY)
            cameraDistance = maxOf(viewport.width * 3f, viewport.height * 2f)
        }
    }
}

@Composable
internal fun StoryLaunchOverlay(
    state: FlipAnimationState,
    progress: Animatable<Float, AnimationVector1D>,
    onAnimationEnd: () -> Unit,
    onReverseAnimationEnd: (FlipReverseReason) -> Unit,
    destinationContent: (@Composable () -> Unit)? = null,
) {
    val scene = LocalStoryLaunchScene.current ?: return
    val tile = state.sourceTile ?: return
    val density = LocalDensity.current
    val finishForward by rememberUpdatedState(onAnimationEnd)
    val finishReverse by rememberUpdatedState(onReverseAnimationEnd)
    val reverseReason by rememberUpdatedState(state.reverseReason)
    var finalFrameDrawn by remember(scene) { mutableStateOf(false) }
    var firstFrameDrawn by remember(scene) { mutableStateOf(false) }
    var destinationCommitted by remember(scene) { mutableStateOf(false) }
    LaunchedEffect(scene, state.direction) {
        val duration = StoryLaunchMotion.duration(state.masterLaunchAnimation)
        if (state.direction == FlipAnimationDirection.FORWARD) {
            finalFrameDrawn = false
            progress.snapTo(0f)
            // Give the real selected cell one drawing pass to publish its display
            // list. Shortcuts with no visible cell use the cached fallback below.
            withFrameNanos { }
            progress.animateTo(1f, tween(duration, easing = LinearEasing))
            snapshotFlow { finalFrameDrawn }.first { it }
            withFrameNanos { }
            destinationCommitted = true
            android.util.Log.i("MosaicStory", "${state.masterLaunchAnimation.name} forward complete")
            finishForward()
        } else {
            firstFrameDrawn = false
            val remaining = (duration * StoryLaunchMotion.progress(progress.value)).roundToInt()
            if (remaining > 0) progress.animateTo(0f, tween(remaining, easing = LinearEasing))
            else progress.snapTo(0f)
            snapshotFlow { firstFrameDrawn }.first { it }
            withFrameNanos { }
            android.util.Log.i("MosaicStory", "${state.masterLaunchAnimation.name} reverse complete")
            finishReverse(reverseReason ?: FlipReverseReason.BACK)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()
        .onGloballyPositioned {
            scene.viewport = Rect(it.positionInWindow(),
                Size(it.size.width.toFloat(), it.size.height.toFloat()))
        }
        .pointerInput(scene, state.direction) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // Once an internal Settings window is open its real controls
                    // remain interactive. During travel the scene owns all input.
                    if (progress.value < .999f || state.direction == FlipAnimationDirection.REVERSE) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (progress.value >= 1f && !finalFrameDrawn) finalFrameDrawn = true
            if (progress.value <= 0f && !firstFrameDrawn) firstFrameDrawn = true
        }) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val viewport = Rect(0f, 0f, w, h)
        val origin = scene.viewport.topLeft
        val source = StoryLaunchMotion.source((scene.capturedSourceBounds.takeIf { it.width > 0f }
            ?: state.sourceBounds).translate(-origin), viewport)
        fun frame() = StoryLaunchMotion.panel(state.masterLaunchAnimation,
            scene.progress(), source, viewport)

        Box(Modifier.fillMaxSize().graphicsLayer { alpha = frame().backdropAlpha }
            .background(Color.Black))
        // Fixed layout: all travel, rotation and expansion happens in GPU layers.
        // No per-frame relayout, bitmap work, blur, shader, or per-tile Animator.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val f = frame()
            transformOrigin = TransformOrigin(f.pivotX, f.pivotY)
            scaleX = f.bounds.width / w
            scaleY = f.bounds.height / h
            translationX = f.bounds.left - (w - f.bounds.width) * f.pivotX
            translationY = f.bounds.top - (h - f.bounds.height) * f.pivotY
            rotationX = f.rotationX
            rotationY = f.rotationY
            cameraDistance = maxOf(w * 3f, h * 2f)
        }) {
        // Artwork and destination share ONE perspective plane and hinge. An
        // independent artwork rotation would visibly detach it from Door/Page.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = frame().surfaceAlpha }
            .background(state.accentColor))

        val captured = scene.sourceLayer
        val sourceWidth = captured?.size?.width?.toFloat()?.takeIf { it > 0f } ?: source.width
        val sourceHeight = captured?.size?.height?.toFloat()?.takeIf { it > 0f } ?: source.height
        Box(Modifier.size(with(density) { sourceWidth.toDp() }, with(density) { sourceHeight.toDp() })
            .align(Alignment.Center)
            .graphicsLayer {
                val f = frame()
                // Preserve the exact source aspect ratio; icons and labels never
                // stretch when a wide row becomes a portrait destination window.
                scaleX = f.sourceScale * w / f.bounds.width
                scaleY = f.sourceScale * h / f.bounds.height
                alpha = f.sourceAlpha
            }.drawWithContent {
                val live = scene.sourceLayer
                if (live != null) drawLayer(live) else drawContent()
            }.background(state.accentColor), contentAlignment = Alignment.Center) {
            // Only used for a shortcut that has no physical cell on this page.
            if (captured == null) {
                if (state.appIcon != null) Image(state.appIcon, tile.title, Modifier.size(56.dp))
                else MetroIcon(tile.iconGlyph.ifEmpty { "app" }, size = 56.dp)
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val f = frame()
            alpha = f.contentAlpha
        }, contentAlignment = Alignment.Center) {
            // Heavy internal apps are mounted once the final frame was submitted,
            // so Settings decoding/composition cannot stall the opening motion.
            val returningContentVisible by remember(scene, state.direction) {
                derivedStateOf { state.direction == FlipAnimationDirection.REVERSE && progress.value > .64f }
            }
            if (destinationContent != null && (destinationCommitted || returningContentVisible)) {
                destinationContent()
            } else {
                Box(Modifier.size(78.dp).graphicsLayer {
                    val f = frame()
                    scaleX = w / f.bounds.width
                    scaleY = h / f.bounds.height
                }, contentAlignment = Alignment.Center) {
                    if (tile.iconGlyph.isNotEmpty()) MetroIcon(tile.iconGlyph, color = Color.White, size = 78.dp)
                    else if (state.appIcon != null) Image(state.appIcon, tile.title, Modifier.fillMaxSize())
                    else MetroIcon("app", color = Color.White, size = 78.dp)
                }
            }
        }
        }
    }
}
