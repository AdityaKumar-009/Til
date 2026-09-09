package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Windows 8.1 Tile Press and Click interaction.
 *
 * Phase 1: 00:04 - 00:05 (Touch Down)
 * Tile tilts dynamically into 3D towards the exact touch point:
 * - Dynamic 3D perspective tilt: rotationX / rotationY between -10° and +10° (~8-12°)
 * - Perspective scale: ~0.96f
 * - Perspective camera distance: 10f
 * - Seamless gesture integration with scroll container (no lag during scrolling).
 */
class TileCoordinatesHolder {
    var coordinates: LayoutCoordinates? = null
}

fun Modifier.metroTilePress(
    onClick: (bounds: Rect) -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val coordsHolder = remember { TileCoordinatesHolder() }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    var isPressed by remember { mutableStateOf(false) }
    var targetRotX by remember { mutableFloatStateOf(0f) }
    var targetRotY by remember { mutableFloatStateOf(0f) }

    val scale = animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "TileScale"
    )

    val rotationX = animateFloatAsState(
        targetValue = if (isPressed) targetRotX else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "TileRotX"
    )

    val rotationY = animateFloatAsState(
        targetValue = if (isPressed) targetRotY else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "TileRotY"
    )

    this
        .onGloballyPositioned { coordinates ->
            // Store reference without State to eliminate scroll recompositions
            coordsHolder.coordinates = coordinates
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w > 0f && h > 0f) {
                        val nx = ((offset.x - (w / 2f)) / (w / 2f)).coerceIn(-1f, 1f)
                        val ny = ((offset.y - (h / 2f)) / (h / 2f)).coerceIn(-1f, 1f)
                        targetRotX = -ny * 10f
                        targetRotY = nx * 10f
                    }
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                    targetRotX = 0f
                    targetRotY = 0f
                },
                onTap = {
                    val bounds = coordsHolder.coordinates?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero
                    currentClick(bounds)
                },
                onLongPress = {
                    currentLongClick?.invoke()
                }
            )
        }
        .graphicsLayer {
            this.scaleX = scale.value
            this.scaleY = scale.value
            this.rotationX = rotationX.value
            this.rotationY = rotationY.value
            this.cameraDistance = 10f // Authentic 3D tilt perspective in Compose
            this.transformOrigin = TransformOrigin(0.5f, 0.5f)
        }
}
