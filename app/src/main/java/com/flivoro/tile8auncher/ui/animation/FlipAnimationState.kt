package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.flivoro.tile8auncher.data.TileModel

data class FlipAnimationState(
    val isRunning: Boolean = false,
    val sourceTile: TileModel? = null,
    val sourceBounds: Rect = Rect.Zero,
    val appIcon: ImageBitmap? = null,
    val accentColor: Color = Color(0xFF0078D7),
)
