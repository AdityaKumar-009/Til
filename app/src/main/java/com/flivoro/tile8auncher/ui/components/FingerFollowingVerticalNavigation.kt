package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
    val startHidden by remember { derivedStateOf { progress >= 1f } }
    val appsHidden by remember { derivedStateOf { progress <= 0f } }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settleAnimation = remember { Animatable(progress) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val gestureTargetPage = remember { mutableStateOf<Boolean?>(null) }
    var previousResetRequest by remember { mutableIntStateOf(resetRequest) }
    val latestShowAllApps by rememberUpdatedState(showAllApps)
    val latestOnPageChange by rememberUpdatedState(onShowAllAppsChange)
    val latestOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    fun cancelSettle() {
        settleJob.value?.cancel()
        settleJob.value = null
    }

    fun settleTo(target: Float, initialVelocity: Float) {
        cancelSettle()
        if (abs(progress - target) < 0.001f && abs(initialVelocity) < 0.001f) {
            progress = target
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
            }
            progress = target.coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(showAllApps, resetRequest) {
        if (!showAllApps) focusManager.clearFocus()
        val isReset = resetRequest != previousResetRequest
        previousResetRequest = resetRequest
        if (isReset) {
            cancelSettle()
            isDragging = false
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
        gestureTargetPage.value = null
        if (!wasRequestedByGesture && !isDragging) {
            settleTo(if (targetPage) 1f else 0f, initialVelocity = 0f)
        }
    }

    fun finishDrag(velocityY: Float) {
        if (!isDragging) return
        isDragging = false
        latestOnDraggingChanged(false)

        val height = viewportHeightPx
        if (height <= 0f) {
            settleTo(if (latestShowAllApps) 1f else 0f, initialVelocity = 0f)
            return
        }

        val targetShowAllApps = when {
            velocityY <= -swipeVelocityThreshold -> true
            velocityY >= swipeVelocityThreshold -> false
            else -> progress >= 0.5f
        }
        val progressVelocity = (-velocityY / height).coerceIn(-8f, 8f)
        gestureTargetPage.value = targetShowAllApps
        settleTo(if (targetShowAllApps) 1f else 0f, progressVelocity)
        if (targetShowAllApps != latestShowAllApps) {
            latestOnPageChange(targetShowAllApps)
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

                detectVerticalDragGestures(
                    onDragStart = {
                        cancelSettle()
                        velocityTracker = VelocityTracker()
                        isDragging = true
                        latestOnDraggingChanged(true)
                    },
                    onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                        if (viewportHeightPx > 0f) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            progress = (progress - dragAmount / viewportHeightPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        finishDrag(velocityTracker.calculateVelocity().y)
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
