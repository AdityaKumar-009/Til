package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill

@Composable
fun WindowsWallpaper(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF23053D),
    accentColor: Color = Color(0xFF5A148C),
    highlightColor: Color = Color(0xFF8824B8),
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base gradient (top-left to bottom-right deep violet)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    baseColor,
                    Color(0xFF140224),
                    Color(0xFF0C0117),
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            size = size,
        )

        // Swooping ribbon 1 (Main flowing curve across middle-right)
        val path1 = Path().apply {
            moveTo(w * 0.35f, 0f)
            cubicTo(
                w * 0.45f, h * 0.3f,
                w * 0.75f, h * 0.5f,
                w * 0.60f, h,
            )
            lineTo(w * 0.90f, h)
            cubicTo(
                w * 0.95f, h * 0.45f,
                w * 0.65f, h * 0.2f,
                w * 0.55f, 0f,
            )
            close()
        }
        drawPath(
            path = path1,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.45f),
                    highlightColor.copy(alpha = 0.35f),
                    Color(0xFF32094D).copy(alpha = 0.2f),
                ),
                start = Offset(w * 0.35f, 0f),
                end = Offset(w * 0.75f, h),
            ),
            style = Fill,
        )

        // Swooping ribbon 2 (Subtle second curve layering)
        val path2 = Path().apply {
            moveTo(w * 0.48f, 0f)
            cubicTo(
                w * 0.58f, h * 0.25f,
                w * 0.82f, h * 0.45f,
                w * 0.72f, h,
            )
            lineTo(w, h)
            lineTo(w, 0f)
            close()
        }
        drawPath(
            path = path2,
            brush = Brush.linearGradient(
                colors = listOf(
                    highlightColor.copy(alpha = 0.25f),
                    accentColor.copy(alpha = 0.15f),
                    Color.Transparent,
                ),
                start = Offset(w * 0.5f, 0f),
                end = Offset(w, h),
            ),
            style = Fill,
        )

        // Soft radial ambient light in top-left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4A0E72).copy(alpha = 0.35f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.15f, h * 0.2f),
                radius = w * 0.6f,
            ),
            center = Offset(w * 0.15f, h * 0.2f),
            radius = w * 0.6f,
        )
    }
}
