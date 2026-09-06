package com.flivoro.tile8auncher.ui.animation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Windows 8.1 Tile Press and Click interaction.
 * Provides a crisp 3D perspective tilt on touch-down, and reliably delivers
 * the tile's screen coordinates on click to trigger the 3D flip expansion.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.metroTilePress(
    onClick: (bounds: Rect) -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var currentBounds by remember { mutableStateOf(Rect.Zero) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "TileScale"
    )

    val rotationX by animateFloatAsState(
        targetValue = if (isPressed) 7f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TileRotX"
    )

    val rotationY by animateFloatAsState(
        targetValue = if (isPressed) -7f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TileRotY"
    )

    this
        .onGloballyPositioned { coordinates ->
            currentBounds = coordinates.boundsInWindow()
        }
        .graphicsLayer {
            this.scaleX = scale
            this.scaleY = scale
            this.rotationX = rotationX
            this.rotationY = rotationY
            this.cameraDistance = 14000f * density
            this.transformOrigin = TransformOrigin(0.5f, 0.5f)
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                onClick(currentBounds)
            },
            onLongClick = onLongClick
        )
}
