package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import com.flivoro.tile8auncher.data.TimeCurve
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlin.math.abs
import kotlin.math.max
import com.flivoro.tile8auncher.ui.phone.sourceColorArrival

/** One display clock and one source display list, shared by all three skins. */
internal class StoryLaunchScene(val state: FlipAnimationState, val progress: () -> Float) {
    // Snapshot-derived once per animation frame, instead of independently evaluating
    // the cubic easing for each visible tile and every layer of the launch overlay.
    val traveledPhase by derivedStateOf {
        StoryLaunchMotion.traveledPhase(progress(), state.timing)
    }
    var viewport by mutableStateOf(Rect.Zero)
    // Return has no bitmap-readback prerequisite. Prepare its ownership gate
    // immediately so the first composed Back frame does not briefly expose
    // the stationary original tile before the launch effect hides it.
    var sourcePrepared by mutableStateOf(state.direction == FlipAnimationDirection.REVERSE)
    // Direction may change without reconstructing this scene (in-place Back).
    var returning by mutableStateOf(state.direction == FlipAnimationDirection.REVERSE)
    var capturedSourceBounds by mutableStateOf(state.sourceBounds)
    var sourceOwner by mutableStateOf<Any?>(null)
    var sourceLayer by mutableStateOf<GraphicsLayer?>(null)
    var sourceMeasured = false

    fun matchesSourceBounds(bounds: Rect): Boolean {
        val target = state.sourceBounds
        return target.width > 1f && target.height > 1f && bounds.overlaps(target) &&
            abs(bounds.center.y - target.center.y) <= maxOf(bounds.height, target.height) * .51f
    }

    fun isSelected(id: String): Boolean {
        val tile = state.sourceTile ?: return false
        return tile.id == id || (!tile.packageName.isNullOrBlank() &&
            (id == "app_${tile.packageName}" || id == "phone_app_${tile.packageName}"))
    }
}

internal val LocalStoryLaunchScene = staticCompositionLocalOf<StoryLaunchScene?> { null }

/** Only the tile TITLE uses this opacity; the logo keeps its own bounded scale. */
internal val LocalStoryTileTitleOpacity = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * The growing solid face is the same physical surface as the touched tile.
 * Do not fade a second, larger card into existence after the source tile is
 * already shrinking/fading. For photo/translucent tiles, keep the established
 * smooth color-arrival policy that reaches solid color at 50% launch time.
 */
internal fun connectedStoryBackgroundAlpha(
    sourceInitialOpacity: Float,
    rawProgress: Float,
    sourcePictureMode: Boolean = false,
): Float {
    // A tile-photo is an opaque painted face, NOT a transparent tile. Its
    // photographed pixels may dissolve into the accent, but the physical
    // expanding panel must remain fully covered from the first drawn frame.
    if (sourcePictureMode) return 1f
    val start = sourceInitialOpacity.coerceIn(0f, 1f)
    // Honor a deliberately translucent W10M tile's actual start opacity;
    // don't teleport to zero alpha at tap and fade back in by halfway.
    return start + (1f - start) * sourceColorArrival(rawProgress)
}


/**
 * Transfer drawing ownership of the selected, REAL tile to the launch plane.
 * Its drawing is recorded once and cached before travel, with no icon decode.
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
    val potentialSource = sourceCandidate && scene.isSelected(id) &&
        scene.state.sourceBounds.width > 1f && scene.state.sourceBounds.height > 1f
    val token = remember(scene, id) { Any() }
    val capturedLayer = if (potentialSource) rememberGraphicsLayer() else null
    val measured = this.onGloballyPositioned {
        bounds = Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
        // Claim the source as soon as Android lays it out. Relying on a
        // draw pass can deadlock when a returning cell is culled at alpha=0:
        // no sourceOwner is published, the overlay cannot crossfade into it,
        // and the tile pops back only after the overlay is removed.
        if (potentialSource && scene.sourceOwner == null && scene.matchesSourceBounds(bounds)) {
            scene.sourceOwner = token
        }
    }
    if (potentialSource) {
        DisposableEffect(scene, token) {
            onDispose {
                if (scene.sourceOwner === token) {
                    scene.sourceOwner = null
                    scene.sourceLayer = null
                }
            }
        }
    }
    fun ownsSource(): Boolean = potentialSource && (scene.sourceOwner === token ||
        (scene.sourceOwner == null && scene.matchesSourceBounds(bounds)))
    return measured.graphicsLayer {
        val viewport = scene.viewport
        if (ownsSource()) {
            // Blend the animated face into the very SAME physical source cell
            // before removing the overlay; otherwise Back ends with a blank
            // beat and then the original tile suddenly reappears.
            // The real source must first draw its display list. Alpha zero
            // can cause Android/Compose to cull the row entirely before it
            // publishes sourceLayer, creating a 500ms reverse capture timeout
            // and a disappearing/reappearing tile. Keep it drawable behind
            // the full-screen overlay until its image is prepared.
            alpha = if (scene.returning && scene.sourcePrepared &&
                scene.sourceOwner === token)
                StoryLaunchMotion.returnToSourceOpacity(
                    scene.state.masterLaunchAnimation, scene.traveledPhase)
            else 1f
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            rotationX = 0f
            rotationY = 0f
            rotationZ = 0f
        } else if (!sourceCandidate && scene.isSelected(id)) {
            // Desktop All Apps transfers its icon plate; the title stays on
            // the row and fades. Keep that row's layout anchor stationary.
            alpha = 1f - StoryLaunchMotion.ease(.05f, .60f,
                scene.traveledPhase)
        } else if (viewport.width > 1f && viewport.height > 1f) {
            val motion = StoryLaunchMotion.neighborAtTravel(scene.state.masterLaunchAnimation,
                scene.traveledPhase, bounds, StoryLaunchMotion.source(
                    scene.capturedSourceBounds.takeIf { it.width > 0f } ?: scene.state.sourceBounds, viewport), viewport,
                returning = scene.returning)
            alpha = motion.alpha
            translationX = motion.x
            translationY = motion.y
            scaleX = motion.scale
            scaleY = motion.scale
            rotationX = motion.rotationX
            rotationY = motion.rotationY
            rotationZ = motion.rotationZ
            transformOrigin = TransformOrigin(motion.pivotX, motion.pivotY)
            cameraDistance = maxOf(viewport.width * 3f, viewport.height * 2f)
        }
    }.drawWithContent {
        if (ownsSource()) {
            // Cache the actual display list once. No bitmap/GPU readback or
            // per-frame recording; retain labels, app art and photo crop at t=0.
            if (scene.sourceLayer == null && capturedLayer != null) {
                capturedLayer.record { this@drawWithContent.drawContent() }
                scene.sourceLayer = capturedLayer
            }
            // One real tile is visible during initial preparation. Once the
            // moving material plane starts, the original selected tile yields
            // all pixels; on Back it resumes only when the moving plane has
            // reached practically identical geometry. No bitmap clone, extra
            // colored tile, or offscreen RenderNode is ever painted.
            if (StoryLaunchMotion.shouldDrawOriginalCell(
                    scene.state.masterLaunchAnimation, scene.sourcePrepared,
                    scene.returning, scene.traveledPhase)) {
                // Reveal the original rendered glyph, title and wallpaper
                // while its overlaid twin is still shrinking toward the cell.
                drawContent()
            }
            if (scene.sourceOwner !== token) scene.sourceOwner = token
            // Freeze geometry while departing: transformed parent rows must
            // never feed their moving coordinates back into the source anchor.
            if (!scene.sourceMeasured && scene.progress() <= .001f && bounds.width > 0f) {
                scene.capturedSourceBounds = bounds
                scene.sourceMeasured = true
            }
        } else drawContent()
    }
}

@Composable
internal fun StoryLaunchOverlay(
    state: FlipAnimationState,
    progress: Animatable<Float, AnimationVector1D>,
    onAnimationEnd: () -> Unit,
    onReverseAnimationEnd: (FlipReverseReason) -> Unit,
    destinationContent: (@Composable () -> Unit)? = null,
    sourceFace: (@Composable (Modifier) -> Unit)? = null,
    destinationIcon: (@Composable (Modifier) -> Unit)? = null,
    sourceForeground: (@Composable (Modifier) -> Unit)? = null,
    sourceBackground: (@Composable (Modifier) -> Unit)? = null,
) {
    val scene = LocalStoryLaunchScene.current ?: return
    val tile = state.sourceTile ?: return
    val density = LocalDensity.current
    val finishForward by rememberUpdatedState(onAnimationEnd)
    val finishReverse by rememberUpdatedState(onReverseAnimationEnd)
    val reverseReason by rememberUpdatedState(state.reverseReason)
    var finalFrameDrawn by remember(scene) { mutableStateOf(false) }
    var firstFrameDrawn by remember(scene) { mutableStateOf(false) }
    LaunchedEffect(scene, state.direction) {
        scene.returning = state.direction == FlipAnimationDirection.REVERSE
        val duration = StoryLaunchMotion.effectiveDuration(
            state.masterLaunchAnimation, state.timing)
        if (state.direction == FlipAnimationDirection.FORWARD) {
            finalFrameDrawn = false
            progress.snapTo(0f)
            // Submit the original selected tile once, then transfer ALL
            // painting responsibility to the single expanding panel. The old
            // 500ms GPU bitmap readback had no remaining consumer and could
            // stall the animation before its first moving frame.
            withFrameNanos { }
            scene.sourcePrepared = true
            progress.animateTo(1f, tween(duration, easing = LinearEasing))
            snapshotFlow { finalFrameDrawn }.first { it }
            withFrameNanos { }
            android.util.Log.i("MosaicStory", "${state.masterLaunchAnimation.name} forward complete")
            finishForward()
        } else {
            firstFrameDrawn = false
            // Returning is composited from the cached app icon and live tile;
            // no bitmap readback is required. Waiting for a cullable source
            // RenderNode here caused up to 500 ms of blank/full-screen pause.
            scene.sourcePrepared = true
            val start = StoryLaunchMotion.progress(progress.value)
            // Keep the same spatial curve, but avoid replaying an elaborate
            // full-duration launch backwards when Back should feel understated.
            val remaining = (duration * start * .85f).roundToInt()
            if (remaining > 0) progress.animateTo(0f,
                tween(remaining, easing = if (state.timing.curve == TimeCurve.REFERENCE)
                    StoryLaunchMotion.returnEasing(start) else LinearEasing))
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
        val currentFrame by remember(scene, state.masterLaunchAnimation, source, viewport) {
            derivedStateOf {
                StoryLaunchMotion.panelAtTravel(
                    state.masterLaunchAnimation, scene.traveledPhase, source, viewport,
                    scene.progress(), returning = scene.returning,
                )
            }
        }
        fun frame() = currentFrame
        fun reconstructedFaceAlpha(): Float = if (scene.sourceLayer == null) 1f
            else StoryLaunchMotion.ease(0f, .16f, scene.traveledPhase)

        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = frame().backdropAlpha * (if (scene.returning) .25f else 1f)
        }.background(Color.Black))
        // Fixed layout: all travel, rotation and expansion happens in GPU layers.
        // No per-frame relayout, bitmap work, blur, shader, or per-tile Animator.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val f = frame()
            // Fade the overlay out ONLY as its real source cell fades in;
            // never end the animation before both tiles have shared frames.
            val returnOwnership = if (scene.returning && scene.sourceOwner != null)
                1f - StoryLaunchMotion.returnToSourceOpacity(
                    scene.state.masterLaunchAnimation, scene.traveledPhase)
            else 1f
            // During forward preparation the original cell is still drawing.
            // Publish the foreground face and hide that cell in the same frame.
            alpha = if (state.direction == FlipAnimationDirection.FORWARD &&
                state.sourceBounds.width > 1f && state.sourceBounds.height > 1f &&
                !scene.sourcePrepared) 0f else returnOwnership
            transformOrigin = TransformOrigin(f.pivotX, f.pivotY)
            scaleX = f.bounds.width / w
            scaleY = f.bounds.height / h
            translationX = f.bounds.left - (w - f.bounds.width) * f.pivotX
            translationY = f.bounds.top - (h - f.bounds.height) * f.pivotY
            rotationX = f.rotationX
            rotationY = f.rotationY
            rotationZ = f.rotationZ
            cameraDistance = maxOf(w * 3f, h * 2f)
            // Transform and crop the one physical tile face, not two differently
            // sized panels. Door/Page/Arc still use their original perspective.
            clip = true
        }) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = reconstructedFaceAlpha() }) {
        // Artwork and destination share ONE perspective plane and hinge. An
        // independent artwork rotation would visibly detach it from Door/Page.
        Box(Modifier.fillMaxSize().graphicsLayer {
            // The face is already exactly the source tile's size at progress=0.
            // The previous independent alpha ramp made a large replacement
            // rectangle emerge while the real tile bitmap was still small.
            alpha = connectedStoryBackgroundAlpha(
                state.sourceInitialOpacity, scene.progress(), state.sourcePictureMode)
        }.background(state.accentColor))

        // Separate the tile's MATERIAL from its LOGO. The material expands all
        // the way to the viewport with the original physical panel. Previously
        // the entire tile bitmap was FillBounds-scaled here: that magnified a
        // 30dp glyph to nearly screen height, as seen in the 60fps recording.
        //
        // Source artwork (tile picture or rich app backdrop) fills the panel
        // only while it dissolves to the resolved accent. It MUST NOT include
        // the tile's glyph, which has its own bounded-scale foreground below.
        if (sourceBackground != null || state.sourcePictureMode) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = frame().sourceAlpha *
                    (1f - sourceColorArrival(scene.progress()))
            }) {
                if (sourceBackground != null) {
                    sourceBackground(Modifier.fillMaxSize())
                } else if (state.sourcePictureMode) {
                    PhoneTilePicture(
                        modifier = Modifier.fillMaxSize(),
                        wallpaperStyle = state.sourceWallpaperStyle,
                        tileLeftPx = state.sourceBounds.left - origin.x,
                        tileTopPx = state.sourceBounds.top - origin.y,
                        gridWidthPx = w,
                        gridHeightPx = h,
                    )
                }
            }
        }

        // Keep the original tile's *foreground layout size* while the panel
        // grows behind it. Inverse panel scaling cancels the parent zoom, then
        // reintroduces only the previous, gentle 1.0 -> 1.3x glyph growth.
        // The inverse applies to both axes so wide All Apps icon plates do not
        // stretch as their parent becomes a portrait screen.
        Box(
            Modifier.size(
                with(density) { source.width.toDp() },
                with(density) { source.height.toDp() },
            ).align(Alignment.Center).graphicsLayer {
                val f = frame()
                scaleX = f.sourceScale * w / f.bounds.width.coerceAtLeast(1f)
                scaleY = f.sourceScale * h / f.bounds.height.coerceAtLeast(1f)
                // For an external app, this IS the final splash glyph.
                // Switching to a separate 78dp icon near the end reintroduced
                // a small -> large logo jump even when the panel was continuous.
                alpha = if (destinationContent == null) 1f else f.sourceAlpha
                clip = true
            },
            contentAlignment = Alignment.Center,
        ) {
            // The icon is ONE persistent composable from the very first
            // frame through the 50% midpoint and through Back. The former
            // full-tile/icon-only crossfade used two independently aligned
            // glyphs and visibly teleported the icon at their midpoint.
            // Only supplementary title/live data fades away.
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f - StoryLaunchMotion.iconOnlyOpacity(scene.progress())
            }, contentAlignment = Alignment.Center) {
                if (sourceForeground != null) {
                    CompositionLocalProvider(LocalStoryTileTitleOpacity provides {
                        StoryLaunchMotion.titleOpacity(scene.progress())
                    }) {
                        sourceForeground(Modifier.fillMaxSize())
                    }
                } else if (state.origin != LaunchOrigin.ALL_APPS) {
                    CompositionLocalProvider(LocalStoryTileTitleOpacity provides {
                        StoryLaunchMotion.titleOpacity(scene.progress())
                    }) {
                        WindowsTileFace(
                            tile = if (tile.tileType ==
                                com.flivoro.tile8auncher.data.TileType.DESKTOP)
                                tile.copy(
                                    tileType = com.flivoro.tile8auncher.data.TileType.APP,
                                    iconGlyph = tile.iconGlyph.ifBlank { "desktop" },
                                ) else tile,
                            appIcon = state.appIcon,
                            modifier = Modifier.fillMaxSize(),
                            logoModifier = Modifier.graphicsLayer { alpha = 0f },
                            allowAppDerivedBackground = false,
                            backgroundAlpha = 0f,
                        )
                    }
                }
            }
            // The single glyph follows a smooth source-to-center offset,
            // sharing the expanding material's perspective plane and size.
            // No alpha change, substitute icon, or discrete layout switch.
            Box(Modifier.fillMaxSize().graphicsLayer {
                val centered = StoryLaunchMotion.iconCenterProgress(scene.progress())
                val sourceX = StoryLaunchMotion.iconOriginOffsetX(
                    state.origin, source.width, density.density)
                val sourceY = StoryLaunchMotion.iconOriginOffsetY(
                    state.origin, tile.size, density.density)
                translationX = sourceX * (1f - centered)
                translationY = sourceY * (1f - centered)
                alpha = 1f
            }, contentAlignment = Alignment.Center) {
                when {
                    destinationIcon != null -> destinationIcon(Modifier.fillMaxSize())
                    tile.iconGlyph.isNotBlank() && tile.iconGlyph != "app" ->
                        MetroIcon(tile.iconGlyph, color = Color.White, size = 42.dp)
                    state.appIcon != null -> Image(
                        bitmap = state.appIcon,
                        contentDescription = tile.title,
                        modifier = Modifier.size(42.dp),
                    )
                    else -> MetroIcon("app", color = Color.White, size = 42.dp)
                }
            }
        }
        // Internal Settings/widgets are still the SAME mounted destination
        // subtree, revealed late over the growing tile. External apps do not
        // need a second splash icon: the original foreground above stays
        // attached to the panel until Android takes over its own window.
        if (destinationContent != null) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = frame().contentAlpha
            }, contentAlignment = Alignment.Center) {
                destinationContent()
            }
        }
        }
        // Initial artwork and bounded logo share the same transformed panel.
        // Transparent All Apps gutters never become a solid row at time zero.
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - reconstructedFaceAlpha()
        }.drawWithContent {
            scene.sourceLayer?.let { layer ->
                if (layer.size.width > 0 && layer.size.height > 0) {
                    scale(size.width / layer.size.width, size.height / layer.size.height, Offset.Zero) {
                        drawLayer(layer)
                    }
                }
            }
        })
        }
    }
}
