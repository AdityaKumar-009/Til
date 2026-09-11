package com.flivoro.tile8auncher.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keeps Start and All Apps mounted while a vertically locked drag moves between them.
 *
 * The child rows retain their own LazyListState and continue to receive horizontal gestures.
 * A page change requested by a button or BackHandler follows the same spring as a released drag.
 */
@Composable
fun FingerFollowingVerticalNavigation(
    showAllApps: Boolean,
    onShowAllAppsChange: (Boolean) -> Unit,
    startContent: @Composable () -> Unit,
    allAppsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDraggingChanged: (Boolean) -> Unit = {},
    resetRequest: Int = 0,
    progressState: MutableFloatState = remember { mutableFloatStateOf(if (showAllApps) 1f else 0f) },
) {
    var progress by progressState
    val swipeVelocityThreshold = with(LocalDensity.current) { 300.dp.toPx() }
    val focusManager = LocalFocusManager.current
    val startHidden by remember { derivedStateOf { progress >= 0.999f } }
    val appsHidden by remember { derivedStateOf { progress <= 0.001f } }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settleAnimation = remember { Animatable(progress) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val dragFrameJob = remember { mutableStateOf<Job?>(null) }
    val dragTargetProgress = remember { floatArrayOf(progress) }

    // Release must continue the motion the user actually saw, not a higher-rate raw pointer stream.
    // These values are deliberately non-snapshot storage: they are sampled by the gesture state
    // machine and must not cause composition by themselves.
    val lastRenderedFrameNanos = remember { longArrayOf(0L) }
    val lastRenderedUptimeMillis = remember { longArrayOf(0L) }
    val renderedProgressVelocity = remember { floatArrayOf(0f) }

    // When a gesture completes we commit the logical page only after the moving layers reach their
    // anchor. The following showAllApps update must therefore not start a second animation.
    val gestureTargetPage = remember { mutableStateOf<Boolean?>(null) }
    var previousResetRequest by remember { mutableIntStateOf(resetRequest) }
    val latestShowAllApps by rememberUpdatedState(showAllApps)
    val latestOnPageChange by rememberUpdatedState(onShowAllAppsChange)
    val latestOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    fun cancelSettle() {
        settleJob.value?.cancel()
        settleJob.value = null
    }

    fun cancelDragFrame() {
        dragFrameJob.value?.cancel()
        dragFrameJob.value = null
    }

    fun resetRenderedVelocityTracking() {
        lastRenderedFrameNanos[0] = 0L
        lastRenderedUptimeMillis[0] = 0L
        renderedProgressVelocity[0] = 0f
    }

    fun queueDragFrame() {
        if (dragFrameJob.value?.isActive == true) return
        dragFrameJob.value = scope.launch {
            withFrameNanos { frameTimeNanos ->
                if (isDragging) {
                    val nextProgress = dragTargetProgress[0].coerceIn(0f, 1f)
                    val previousFrameTime = lastRenderedFrameNanos[0]
                    if (previousFrameTime > 0L) {
                        val deltaSeconds = (frameTimeNanos - previousFrameTime) / 1_000_000_000f
                        if (deltaSeconds in 0.001f..0.1f) {
                            renderedProgressVelocity[0] = (
                                (nextProgress - progress) / deltaSeconds
                            ).coerceIn(-4f, 4f)
                        } else {
                            renderedProgressVelocity[0] = 0f
                        }
                    }
                    progress = nextProgress
                    lastRenderedFrameNanos[0] = frameTimeNanos
                    lastRenderedUptimeMillis[0] = SystemClock.uptimeMillis()
                }
            }
            dragFrameJob.value = null
        }
    }

    fun settleTo(
        target: Float,
        initialVelocity: Float,
        onSettled: (() -> Unit)? = null,
    ) {
        cancelSettle()
        if (abs(progress - target) < 0.001f && abs(initialVelocity) < 0.001f) {
            progress = target
            dragTargetProgress[0] = target
            onSettled?.invoke()
            return
        }

        settleJob.value = scope.launch {
            // The animation starts at the exact value currently submitted to the graphics layers,
            // and with the velocity measured from those rendered frames. This makes finger-following
            // and auto-settle one continuous motion rather than two phases.
            settleAnimation.snapTo(progress)
            settleAnimation.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialVelocity = initialVelocity.coerceIn(-4f, 4f),
            ) {
                progress = value.coerceIn(0f, 1f)
                dragTargetProgress[0] = progress
            }
            progress = target.coerceIn(0f, 1f)
            dragTargetProgress[0] = progress
            onSettled?.invoke()
            // Cancellation is handled synchronously by cancelSettle(). Only a normally completed
            // job clears itself here, so an interrupted old settle can never erase a newer job.
            settleJob.value = null
        }
    }

    LaunchedEffect(showAllApps, resetRequest) {
        if (!showAllApps) focusManager.clearFocus()
        val isReset = resetRequest != previousResetRequest
        previousResetRequest = resetRequest
        if (isReset) {
            cancelSettle()
            cancelDragFrame()
            isDragging = false
            dragTargetProgress[0] = 0f
            resetRenderedVelocityTracking()
            latestOnDraggingChanged(false)
            gestureTargetPage.value = false
            settleTo(0f, initialVelocity = 0f)
            if (latestShowAllApps) {
                latestOnPageChange(false)
            }
            return@LaunchedEffect
        }

        val targetPage = showAllApps
        val wasRequestedByGesture = gestureTargetPage.value == targetPage
        if (wasRequestedByGesture) {
            gestureTargetPage.value = null
        }
        if (!wasRequestedByGesture && !isDragging) {
            settleTo(if (targetPage) 1f else 0f, initialVelocity = 0f)
        }
    }

    fun finishDrag(pointerVelocityY: Float) {
        if (!isDragging) return

        // Never inject an unrendered raw sample at finger-up. The visible frame is the start of the
        // settle; the latest raw position is used only for deciding which anchor should win.
        cancelDragFrame()
        isDragging = false
        latestOnDraggingChanged(false)

        val height = viewportHeightPx
        if (height <= 0f) {
            settleTo(if (latestShowAllApps) 1f else 0f, initialVelocity = 0f)
            return
        }

        val decisionProgress = dragTargetProgress[0].coerceIn(0f, 1f)
        val targetShowAllApps = when {
            pointerVelocityY <= -swipeVelocityThreshold -> true
            pointerVelocityY >= swipeVelocityThreshold -> false
            else -> decisionProgress >= 0.5f
        }

        val now = SystemClock.uptimeMillis()
        val renderedSampleAge = now - lastRenderedUptimeMillis[0]
        val pointerProgressVelocity = (-pointerVelocityY / height).coerceIn(-4f, 4f)
        val visualProgressVelocity = if (renderedSampleAge in 0L..80L) {
            renderedProgressVelocity[0].coerceIn(-4f, 4f)
        } else {
            0f
        }
        // A one-frame flick does not yet have two rendered samples from which to derive velocity.
        // In that narrow case fall back to the fresh pointer velocity; normal drags continue with
        // the velocity of the actual on-screen motion.
        val releaseProgressVelocity = if (abs(visualProgressVelocity) >= 0.05f) {
            visualProgressVelocity
        } else {
            pointerProgressVelocity
        }

        val changesPage = targetShowAllApps != latestShowAllApps
        gestureTargetPage.value = targetShowAllApps
        settleTo(
            target = if (targetShowAllApps) 1f else 0f,
            initialVelocity = releaseProgressVelocity,
        ) {
            // Do not recompose Start/All Apps into a different logical state while their layers are
            // still moving. Commit only at the anchor; both surfaces have remained mounted for the
            // entire transition, so this state handoff is visually inert.
            if (changesPage && targetShowAllApps != latestShowAllApps) {
                latestOnPageChange(targetShowAllApps)
            } else {
                gestureTargetPage.value = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewportHeightPx = it.height.toFloat() }
            .pointerInput(resetRequest, enabled) {
                if (!enabled) return@pointerInput
                var velocityTracker = VelocityTracker()
                var lastVelocitySampleUptimeMillis = 0L

                detectVerticalDragGestures(
                    onDragStart = {
                        cancelSettle()
                        cancelDragFrame()
                        dragTargetProgress[0] = progress
                        resetRenderedVelocityTracking()
                        velocityTracker = VelocityTracker()
                        lastVelocitySampleUptimeMillis = 0L
                        isDragging = true
                        latestOnDraggingChanged(true)
                    },
                    onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                        if (viewportHeightPx > 0f) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            lastVelocitySampleUptimeMillis = change.uptimeMillis
                            change.consume()
                            dragTargetProgress[0] = (
                                dragTargetProgress[0] - dragAmount / viewportHeightPx
                            ).coerceIn(0f, 1f)
                            // Pointer hardware can report faster than the display. Submit at most one
                            // progress value per display frame, then measure that rendered motion for
                            // a seamless release continuation.
                            queueDragFrame()
                        }
                    },
                    onDragEnd = {
                        val sampleAge = if (lastVelocitySampleUptimeMillis > 0L) {
                            SystemClock.uptimeMillis() - lastVelocitySampleUptimeMillis
                        } else {
                            Long.MAX_VALUE
                        }
                        val releaseVelocityY = if (sampleAge in 0L..80L) {
                            velocityTracker.calculateVelocity().y
                        } else {
                            0f
                        }
                        finishDrag(releaseVelocityY)
                    },
                    onDragCancel = {
                        finishDrag(0f)
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -viewportHeightPx * progress
                }
                .then(if (startHidden) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            startContent()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = viewportHeightPx * (1f - progress)
                }
                .then(if (appsHidden) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            allAppsContent()
        }
    }
}
