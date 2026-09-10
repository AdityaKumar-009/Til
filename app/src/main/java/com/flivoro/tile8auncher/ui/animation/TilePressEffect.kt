package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Holder avoids making layout coordinates Compose state during horizontal scrolling. */
class TileCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Windows 8-era pointer feedback.
 *
 * Microsoft's WinJS pointerDown/pointerUp animation is 167 ms using
 * cubic-bezier(0.1, 0.9, 0.2, 1) and scales the pressed surface to 0.975.
 * The former Tile8 implementation used a generic spring, 0.96 scale and a
 * +/-10 degree 3-D tilt; that was visibly more dramatic than Windows 8.1.
 *
 * This modifier affects only touch feedback. It does not alter Tile8's app
 * opening/FlipLaunchOverlay animation.
 */
fun Modifier.metroTilePress(
    onClick: (bounds: Rect) -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val coordsHolder = remember { TileCoordinatesHolder() }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) Windows81Motion.PointerPressedScale else 1f,
        animationSpec = tween(
            durationMillis = Windows81Motion.PointerDurationMillis,
            easing = Windows81Motion.Fluid,
        ),
        label = "Windows81PointerScale",
    )

    this
        .onGloballyPositioned { coordsHolder.coordinates = it }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = {
                    val bounds = coordsHolder.coordinates
                        ?.takeIf { it.isAttached }
                        ?.boundsInWindow()
                        ?: Rect.Zero
                    currentClick(bounds)
                },
                onLongPress = { currentLongClick?.invoke() },
            )
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}
