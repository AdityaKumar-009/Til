package com.flivoro.tile8auncher.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import kotlin.math.roundToInt

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

    fun queueDragFrame() {
        if (dragFrameJob.value?.isActive == true) return
        dragFrameJob.value = scope.launch {
            withFrameNanos { }
            if (isDragging) {
                progress = dragTargetProgress[0].coerceIn(0f, 1f)
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
            settleAnimation.snapTo(progress)
            settleAnimation.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                initialVelocity = initialVelocity,
            ) {
                progress = value.coerceIn(0f, 1f)
                dragTargetProgress[0] = progress
            }
            progress = target.coerceIn(0f, 1f)
            dragTargetProgress[0] = progress
            onSettled?.invoke()
            settleJob.value = null
        }
    }

    fun settleReleasedDragTo(
        target: Float,
        onSettled: (() -> Unit)? = null,
    ) {
        cancelSettle()

        // Finger-up must never inject a new velocity or position into the visual stream. Start from
        // exactly the progress that is already on screen, then ease directly to the chosen anchor.
        // This deliberately avoids a velocity-driven spring here: a fast pointer sample can move a
        // large fraction of the viewport on the first post-release frame and looks like a page jump.
        val start = progress.coerceIn(0f, 1f)
        val distance = abs(target - start)
        if (distance < 0.001f) {
            progress = target
            dragTargetProgress[0] = target
            onSettled?.invoke()
            return
        }

        // Keep short releases short while giving longer releases enough time to visibly finish.
        // The duration only depends on the remaining distance, so both Start -> All Apps and the
        // reverse path behave identically and cannot overshoot the destination.
        val durationMillis = (100f + 180f * distance)
            .roundToInt()
            .coerceIn(100, 280)

        settleJob.value = scope.launch {
            settleAnimation.snapTo(start)
            settleAnimation.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = FastOutSlowInEasing,
                ),
                initialVelocity = 0f,
            ) {
                progress = value.coerceIn(0f, 1f)
                dragTargetProgress[0] = progress
            }
            progress = target.coerceIn(0f, 1f)
            dragTargetProgress[0] = progress
            onSettled?.invoke()
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

        // Keep the exact frame the user last saw. A queued raw pointer sample is allowed to decide
        // the destination below, but it is never flushed into progress when the finger is lifted.
        cancelDragFrame()
        isDragging = false
        latestOnDraggingChanged(false)

        val height = viewportHeightPx
        if (height <= 0f) {
            settleReleasedDragTo(if (latestShowAllApps) 1f else 0f)
            return
        }

        val decisionProgress = dragTargetProgress[0].coerceIn(0f, 1f)
        val targetShowAllApps = when {
            pointerVelocityY <= -swipeVelocityThreshold -> true
            pointerVelocityY >= swipeVelocityThreshold -> false
            else -> decisionProgress >= 0.5f
        }

        val changesPage = targetShowAllApps != latestShowAllApps
        gestureTargetPage.value = targetShowAllApps
        settleReleasedDragTo(
            target = if (targetShowAllApps) 1f else 0f,
        ) {
            // Commit only at the final anchor. Both pages stayed mounted during the complete motion,
            // so changing the logical page cannot replace content halfway through the animation.
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
                            // Pointer hardware can report faster than the display. Submit only the
                            // latest position on the next frame so the drag itself stays efficient.
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
