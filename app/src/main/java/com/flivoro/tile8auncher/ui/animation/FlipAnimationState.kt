package com.flivoro.tile8auncher.ui.animation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.flivoro.tile8auncher.data.FlipAnimationMode
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.LaunchTiming

enum class LaunchOrigin { START, ALL_APPS }

enum class FlipAnimationDirection { FORWARD, REVERSE }

enum class FlipReverseReason { BACK, HOME }

data class FlipAnimationState(
    val isRunning: Boolean = false,
    val sourceTile: TileModel? = null,
    val sourceBounds: Rect = Rect.Zero,
    val appIcon: ImageBitmap? = null,
    val accentColor: Color = Color(0xFF0078D7),
    val animationMode: FlipAnimationMode = FlipAnimationMode.CLASSIC,
    val timing: LaunchTiming = LaunchTiming(),
    val hasInternalWindow: Boolean = false,
    val origin: LaunchOrigin = LaunchOrigin.START,
    val direction: FlipAnimationDirection = FlipAnimationDirection.FORWARD,
    val reverseReason: FlipReverseReason? = null,
)
