package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln

/**
 * Reconstruction of the archived Windows Phone Toolkit TiltEffect, not Desktop's tile press.
 * The original source uses MaxAngle=.3rad, MaxDepression=25px, and a 200ms pause followed by
 * a 100ms logarithmic release. Position-dependent X/Y rotation is intentionally asymmetric.
 */
internal data class PhoneTiltFrame(
    val rotationX: Float,
    val rotationY: Float,
    val depth: Float,
)

internal object PhoneToolkitTilt {
    const val RELEASE_DELAY_MS = 200
    const val RELEASE_DURATION_MS = 100
    private const val MAX_DEPRESSION = 25f
    private const val MAX_ANGLE_RADIANS = .3f

    fun at(touchX: Float, touchY: Float, width: Float, height: Float): PhoneTiltFrame {
        if (width <= 0f || height <= 0f) return PhoneTiltFrame(0f, 0f, 0f)
        val normalizedX = (touchX / width).coerceIn(0f, 1f)
        val normalizedY = (touchY / height).coerceIn(0f, 1f)
        val x = abs(normalizedX - .5f)
        val y = abs(normalizedY - .5f)
        val magnitude = x + y
        val xContribution = if (magnitude > 0f) x / magnitude else 0f
        val angle = magnitude * MAX_ANGLE_RADIANS * (180.0 / PI).toFloat()
        val xDirection = when { normalizedX > .5f -> -1f; normalizedX < .5f -> 1f; else -> 0f }
        val yDirection = when { normalizedY > .5f -> 1f; normalizedY < .5f -> -1f; else -> 0f }
        return PhoneTiltFrame(
            rotationX = angle * (1f - xContribution) * yDirection,
            rotationY = angle * xContribution * xDirection,
            depth = (1f - magnitude) * MAX_DEPRESSION,
        )
    }
}

private val PhoneReleaseEase = Easing { t ->
    (ln((1f + t.coerceIn(0f, 1f)).toDouble()) / ln(2.0)).toFloat()
}

internal fun Modifier.phoneToolkitTilePress(
    enabled: Boolean = true,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
    onPressStart: (() -> Unit)? = null,
    tiltEnabled: Boolean = true,
): Modifier = composed {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)
    val latestOnPressStart by rememberUpdatedState(onPressStart)
    val density = LocalDensity.current.density
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun currentBounds(): Rect = coordinates?.takeIf { it.isAttached }?.let {
        Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    } ?: bounds
    var target by remember { mutableStateOf(PhoneTiltFrame(0f, 0f, 0f)) }
    var pressed by remember { mutableStateOf(false) }
    var wasPressed by remember { mutableStateOf(false) }
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val depth = remember { Animatable(0f) }
    LaunchedEffect(pressed, target, tiltEnabled) {
        if (!tiltEnabled) {
            rotX.snapTo(0f)
            rotY.snapTo(0f)
            depth.snapTo(0f)
        } else if (pressed) {
            rotX.snapTo(target.rotationX)
            rotY.snapTo(target.rotationY)
            depth.snapTo(target.depth * density)
        } else if (wasPressed) {
            delay(PhoneToolkitTilt.RELEASE_DELAY_MS.toLong())
            coroutineScope {
                launch { rotX.animateTo(0f, tween(PhoneToolkitTilt.RELEASE_DURATION_MS, easing = PhoneReleaseEase)) }
                launch { rotY.animateTo(0f, tween(PhoneToolkitTilt.RELEASE_DURATION_MS, easing = PhoneReleaseEase)) }
                launch { depth.animateTo(0f, tween(PhoneToolkitTilt.RELEASE_DURATION_MS, easing = PhoneReleaseEase)) }
            }
        }
    }
    this
        .onGloballyPositioned {
            coordinates = it
            bounds = currentBounds()
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (!enabled) disabled()
            onClick { if (enabled) latestOnClick(currentBounds()); enabled }
            onLongClick { if (enabled) latestOnLongClick(); enabled }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { point ->
                    // Disco changes the edited tile on pointer-down, before release.
                    // Keep this in the existing recognizer so recomposition does not
                    // cancel a second competing pointerInput handler.
                    latestOnPressStart?.invoke()
                    if (tiltEnabled) {
                        target = PhoneToolkitTilt.at(
                            point.x, point.y, size.width.toFloat(), size.height.toFloat(),
                        )
                        wasPressed = true
                        pressed = true
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                    }
                },
                onTap = { latestOnClick(currentBounds()) },
                onLongPress = { latestOnLongClick() },
            )
        }
        .graphicsLayer {
            rotationX = if (enabled && tiltEnabled) rotX.value else 0f
            rotationY = if (enabled && tiltEnabled) rotY.value else 0f
            val camera = maxOf(900f, 2f * bounds.width, 2f * bounds.height)
            // Compose's GraphicsLayerScope exposes cameraDistance but not translationZ.
            // Projecting a depressed plane backward shrinks it by d/(d+depression).
            val perspectiveScale = if (enabled && tiltEnabled)
                camera / (camera + depth.value) else 1f
            scaleX = perspectiveScale
            scaleY = perspectiveScale
            cameraDistance = camera
        }
}
