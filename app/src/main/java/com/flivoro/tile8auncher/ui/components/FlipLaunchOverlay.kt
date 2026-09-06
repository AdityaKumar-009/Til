package com.flivoro.tile8auncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlin.math.roundToInt

/**
 * Exact replica of Windows 8.1 3D Tile Flip Animation:
 * 1. The clicked tile expands from its screen bounds into a full-screen card.
 * 2. Swings open around the Y-axis from -70° to 0° with camera perspective.
 * 3. Shows the app's solid accent color with the white glyph and splash outline.
 */
@Composable
fun FlipLaunchOverlay(
    state: FlipAnimationState,
    onAnimationEnd: () -> Unit
) {
    if (!state.isRunning || state.sourceTile == null) return

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(state) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 420,
                easing = CubicBezierEasing(0.08f, 0.92f, 0.15f, 1.0f)
            )
        )
        onAnimationEnd()
    }

    val p = animProgress.value
    val src = state.sourceBounds

    // If coordinates were zero or invalid, provide an authentic tile launch origin
    val effectiveLeft = if (src.width > 20f) src.left else screenWidthPx * 0.15f
    val effectiveTop = if (src.height > 20f) src.top else screenHeightPx * 0.35f
    val effectiveWidth = if (src.width > 20f) src.width else screenWidthPx * 0.40f
    val effectiveHeight = if (src.height > 20f) src.height else 140f * density.density

    // Interpolate bounds from tile position to full screen
    val currentLeft = effectiveLeft * (1f - p)
    val currentTop = effectiveTop * (1f - p)
    val currentWidth = effectiveWidth + (screenWidthPx - effectiveWidth) * p
    val currentHeight = effectiveHeight + (screenHeightPx - effectiveHeight) * p

    // 3D rotation swings open around the Y axis: from -70° to 0° (flat)
    val currentRotationY = -70f * (1f - p)

    // Icon scales smoothly from tile icon size to splash size
    val iconSizeDp = (42f + 48f * p).dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(currentLeft.roundToInt(), currentTop.roundToInt())
                }
                .size(
                    width = with(density) { currentWidth.toDp() },
                    height = with(density) { currentHeight.toDp() }
                )
                .graphicsLayer {
                    this.rotationY = currentRotationY
                    this.cameraDistance = 14000f * density.density
                    // Pivot on left-center so right side swings forward towards viewer
                    this.transformOrigin = TransformOrigin(0.2f, 0.5f)
                }
                .background(state.accentColor)
        ) {
            // Signature Windows 8.1 Splash screen outlined box (seen in frame 00:06 and 00:34)
            if (p > 0.3f) {
                Box(
                    modifier = Modifier
                        .size(170.dp, 105.dp)
                        .align(Alignment.Center)
                        .alpha(((p - 0.3f) * 1.4f).coerceIn(0f, 0.35f))
                        .border(1.dp, Color.White)
                )
            }

            // Centered App Glyph / Icon
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconSizeDp),
                contentAlignment = Alignment.Center
            ) {
                if (state.sourceTile.iconGlyph.isNotEmpty()) {
                    MetroIcon(
                        glyph = state.sourceTile.iconGlyph,
                        color = Color.White,
                        size = iconSizeDp
                    )
                } else if (state.appIcon != null) {
                    Image(
                        bitmap = state.appIcon,
                        contentDescription = state.sourceTile.title,
                        modifier = Modifier.size(iconSizeDp)
                    )
                } else {
                    MetroIcon(
                        glyph = "app",
                        color = Color.White,
                        size = iconSizeDp
                    )
                }
            }

            // App title at bottom left during early swing phase
            if (p < 0.5f) {
                Text(
                    text = state.sourceTile.title,
                    style = WindowsTypography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 1f - (p / 0.5f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 10.dp, y = (-8).dp)
                )
            }
        }
    }
}
