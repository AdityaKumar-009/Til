package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MetroIcon(
    glyph: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 32.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val width = this.size.width.toInt().coerceAtLeast(1)
            val height = this.size.height.toInt().coerceAtLeast(1)
            val key = "${glyph.lowercase()}:${color.value}:$width:$height"
            val bitmap = metroIconCache.get(key) ?: ImageBitmap(width, height).also { image ->
                CanvasDrawScope().draw(this, layoutDirection, Canvas(image), this.size) {
                    drawMetroGlyph(glyph, color)
                }
                metroIconCache.put(key, image)
            }
            onDrawBehind { drawImage(bitmap) }
        })
    }
}

// Scrolling reuses textures instead of allocating and tessellating glyph paths each frame.
private val metroIconCache = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun DrawScope.drawMetroGlyph(glyph: String, color: Color) {

    val w = this.size.width
    val h = this.size.height
    val strokeW = (w * 0.08f).coerceAtLeast(2f)

    when (glyph.lowercase()) {
        "mail" -> {
            // Windows 8 Envelope
            val left = w * 0.12f
            val top = h * 0.22f
            val right = w * 0.88f
            val bottom = h * 0.78f
            val envPath = Path().apply {
                moveTo(left, top)
                lineTo(right, top)
                lineTo(right, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(envPath, color = color, style = Stroke(width = strokeW))

            // Flap fold lines
            val flapPath = Path().apply {
                moveTo(left, top)
                lineTo(w * 0.5f, h * 0.55f)
                lineTo(right, top)
            }
            drawPath(flapPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        "reading_list" -> {
            // Exact Reading List Icon from Video (frame 556/558):
            // Curled sheet top-left with 3 horizontal reading bars below!
            // Bar 1 (top with left curl)
            val curlPath = Path().apply {
                moveTo(w * 0.45f, h * 0.32f)
                lineTo(w * 0.82f, h * 0.32f)
                lineTo(w * 0.82f, h * 0.40f)
                lineTo(w * 0.36f, h * 0.40f)
                // Curl fold
                cubicTo(
                    w * 0.22f, h * 0.40f,
                    w * 0.22f, h * 0.24f,
                    w * 0.36f, h * 0.24f,
                )
                cubicTo(
                    w * 0.44f, h * 0.24f,
                    w * 0.45f, h * 0.30f,
                    w * 0.45f, h * 0.32f,
                )
            }
            drawPath(curlPath, color = color, style = Fill)

            // Bar 2
            drawRect(
                color = color,
                topLeft = Offset(w * 0.30f, h * 0.46f),
                size = Size(w * 0.52f, h * 0.08f),
            )
            // Bar 3
            drawRect(
                color = color,
                topLeft = Offset(w * 0.30f, h * 0.58f),
                size = Size(w * 0.52f, h * 0.08f),
            )
            // Bar 4
            drawRect(
                color = color,
                topLeft = Offset(w * 0.36f, h * 0.70f),
                size = Size(w * 0.46f, h * 0.08f),
            )
        }

        "help" -> {
            // Help+Tips: Question mark inside circle
            drawCircle(
                color = color,
                radius = w * 0.40f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(width = strokeW),
            )
            // Question mark curve
            val qmPath = Path().apply {
                moveTo(w * 0.38f, h * 0.38f)
                cubicTo(
                    w * 0.38f, h * 0.28f,
                    w * 0.62f, h * 0.28f,
                    w * 0.62f, h * 0.42f,
                )
                cubicTo(
                    w * 0.62f, h * 0.52f,
                    w * 0.50f, h * 0.52f,
                    w * 0.50f, h * 0.60f,
                )
            }
            drawPath(qmPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            // Dot
            drawCircle(
                color = color,
                radius = strokeW * 0.7f,
                center = Offset(w * 0.50f, h * 0.72f),
                style = Fill,
            )
        }

        "store" -> {
            // Windows Store shopping bag with 4 Windows tiles inside
            val bagPath = Path().apply {
                moveTo(w * 0.18f, h * 0.35f)
                lineTo(w * 0.82f, h * 0.35f)
                lineTo(w * 0.78f, h * 0.85f)
                lineTo(w * 0.22f, h * 0.85f)
                close()
            }
            drawPath(bagPath, color = color, style = Fill)

            // Bag handle (cutout)
            val handlePath = Path().apply {
                moveTo(w * 0.35f, h * 0.35f)
                cubicTo(
                    w * 0.35f, h * 0.16f,
                    w * 0.65f, h * 0.16f,
                    w * 0.65f, h * 0.35f,
                )
            }
            drawPath(handlePath, color = color, style = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round))

            // Windows logo cutout inside bag (4 squares)
            val gap = w * 0.03f
            val sqSize = w * 0.08f
            val cx = w * 0.5f
            val cy = h * 0.60f
            val cutColor = Color(0xFF008A00)
            drawRect(cutColor, topLeft = Offset(cx - sqSize - (gap / 2), cy - sqSize - (gap / 2)), size = Size(sqSize, sqSize))
            drawRect(cutColor, topLeft = Offset(cx + (gap / 2), cy - sqSize - (gap / 2)), size = Size(sqSize, sqSize))
            drawRect(cutColor, topLeft = Offset(cx - sqSize - (gap / 2), cy + (gap / 2)), size = Size(sqSize, sqSize))
            drawRect(cutColor, topLeft = Offset(cx + (gap / 2), cy + (gap / 2)), size = Size(sqSize, sqSize))
        }

        "weather" -> {
            // Sun glyph with rays
            val cx = w * 0.5f
            val cy = h * 0.5f
            drawCircle(color = color, radius = w * 0.20f, center = Offset(cx, cy), style = Fill)
            val rayLen = w * 0.12f
            for (i in 0 until 8) {
                val angle = ((i * 45.0 * Math.PI) / 180.0).toFloat()
                val r1 = w * 0.26f
                val r2 = r1 + rayLen
                drawLine(
                    color = color,
                    start = Offset(cx + (cos(angle) * r1), cy + (sin(angle) * r1)),
                    end = Offset(cx + (cos(angle) * r2), cy + (sin(angle) * r2)),
                    strokeWidth = strokeW * 0.8f,
                    cap = StrokeCap.Round,
                )
            }
        }

        "money" -> {
            // Ascending bar chart with up arrow
            val barW = w * 0.10f
            val base = h * 0.78f
            // Bar 1
            drawRect(color, topLeft = Offset(w * 0.18f, base - (h * 0.20f)), size = Size(barW, h * 0.20f))
            // Bar 2
            drawRect(color, topLeft = Offset(w * 0.32f, base - (h * 0.34f)), size = Size(barW, h * 0.34f))
            // Bar 3
            drawRect(color, topLeft = Offset(w * 0.46f, base - (h * 0.48f)), size = Size(barW, h * 0.48f))
            // Bar 4
            drawRect(color, topLeft = Offset(w * 0.60f, base - (h * 0.62f)), size = Size(barW, h * 0.62f))
            // Arrow line
            val arrowPath = Path().apply {
                moveTo(w * 0.15f, base - (h * 0.15f))
                lineTo(w * 0.75f, base - (h * 0.68f))
            }
            drawPath(arrowPath, color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            // Arrow head
            val headPath = Path().apply {
                moveTo(w * 0.62f, base - (h * 0.68f))
                lineTo(w * 0.78f, base - (h * 0.70f))
                lineTo(w * 0.76f, base - (h * 0.54f))
                close()
            }
            drawPath(headPath, color, style = Fill)
        }

        "calendar" -> {
            // Calendar grid
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * 0.18f),
                size = Size(w * 0.68f, h * 0.64f),
                style = Stroke(width = strokeW),
            )
            // Top header line
            drawLine(
                color = color,
                start = Offset(w * 0.16f, h * 0.36f),
                end = Offset(w * 0.84f, h * 0.36f),
                strokeWidth = strokeW,
            )
            // Grid dots/squares inside
            val dw = w * 0.08f
            for (row in 0..2) {
                for (col in 0..3) {
                    drawRect(
                        color = color,
                        topLeft = Offset((w * 0.26f) + (col * w * 0.14f), (h * 0.44f) + (row * h * 0.10f)),
                        size = Size(dw, dw),
                    )
                }
            }
        }

        "photos" -> {
            // Photo frame with mountains and sun
            drawRect(color, topLeft = Offset(w * 0.14f, h * 0.20f), size = Size(w * 0.72f, h * 0.60f), style = Stroke(width = strokeW))
            // Sun
            drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.32f, h * 0.38f), style = Fill)
            // Mountains
            val mtnPath = Path().apply {
                moveTo(w * 0.20f, h * 0.75f)
                lineTo(w * 0.44f, h * 0.48f)
                lineTo(w * 0.62f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.55f)
                lineTo(w * 0.82f, h * 0.75f)
                close()
            }
            drawPath(mtnPath, color, style = Fill)
        }

        "ie" -> {
            // Internet Explorer "e" with orbit ring
            val cx = w * 0.5f
            val cy = h * 0.52f
            // Ring
            val ringPath = Path().apply {
                moveTo(w * 0.12f, h * 0.68f)
                cubicTo(
                    w * 0.20f, h * 0.30f,
                    w * 0.80f, h * 0.22f,
                    w * 0.88f, h * 0.34f,
                )
            }
            drawPath(ringPath, color = color, style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round))
            // Center 'e' shape
            drawCircle(color, radius = w * 0.26f, center = Offset(cx, cy), style = Stroke(width = strokeW))
            drawLine(color, start = Offset(w * 0.24f, cy), end = Offset(w * 0.76f, cy), strokeWidth = strokeW)
        }

        "clock" -> {
            // Clock circle
            drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = strokeW))
            // Hands
            drawLine(color, start = Offset(w * 0.5f, h * 0.5f), end = Offset(w * 0.5f, h * 0.24f), strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(color, start = Offset(w * 0.5f, h * 0.5f), end = Offset(w * 0.70f, h * 0.5f), strokeWidth = strokeW, cap = StrokeCap.Round)
        }

        "desktop" -> {
            // Desktop monitor
            drawRect(color, topLeft = Offset(w * 0.15f, h * 0.18f), size = Size(w * 0.70f, h * 0.50f), style = Stroke(width = strokeW))
            // Stand
            drawLine(color, start = Offset(w * 0.5f, h * 0.68f), end = Offset(w * 0.5f, h * 0.80f), strokeWidth = strokeW)
            drawLine(color, start = Offset(w * 0.35f, h * 0.80f), end = Offset(w * 0.65f, h * 0.80f), strokeWidth = strokeW, cap = StrokeCap.Round)
        }

        "camera" -> {
            // Camera icon
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.16f, h * 0.32f),
                size = Size(w * 0.68f, h * 0.48f),
                style = Stroke(width = strokeW),
            )
            // Top flash bump
            drawRect(color, topLeft = Offset(w * 0.38f, h * 0.22f), size = Size(w * 0.24f, h * 0.10f), style = Stroke(width = strokeW))
            // Lens
            drawCircle(color, radius = w * 0.15f, center = Offset(w * 0.50f, h * 0.56f), style = Stroke(width = strokeW))
        }

        "settings" -> {
            // Gear icon
            val cx = w * 0.5f
            val cy = h * 0.5f
            drawCircle(color, radius = w * 0.20f, center = Offset(cx, cy), style = Stroke(width = strokeW))
            for (i in 0 until 6) {
                val angle = ((i * 60.0 * Math.PI) / 180.0).toFloat()
                val r1 = w * 0.22f
                val r2 = w * 0.34f
                drawLine(
                    color = color,
                    start = Offset(cx + (cos(angle) * r1), cy + (sin(angle) * r1)),
                    end = Offset(cx + (cos(angle) * r2), cy + (sin(angle) * r2)),
                    strokeWidth = strokeW * 1.2f,
                    cap = StrokeCap.Square,
                )
            }
        }

        "power" -> {
            // Power symbol (circle cut at top with vertical bar)
            val arcRect = Rect(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.82f)
            val arcPath = Path().apply {
                arcTo(arcRect, 300f, 300f, forceMoveTo = false)
            }
            drawPath(arcPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.12f),
                end = Offset(w * 0.5f, h * 0.46f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }

        "search" -> {
            // Magnifying glass
            val r = w * 0.24f
            val cx = w * 0.42f
            val cy = h * 0.42f
            drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeW))
            drawLine(
                color = color,
                start = Offset(cx + (r * 0.7f), cy + (r * 0.7f)),
                end = Offset(w * 0.82f, h * 0.82f),
                strokeWidth = strokeW * 1.1f,
                cap = StrokeCap.Round,
            )
        }

        "arrow_down" -> {
            // Circle with down arrow
            drawCircle(color, radius = w * 0.44f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = strokeW * 0.9f))
            val p = Path().apply {
                moveTo(w * 0.32f, h * 0.42f)
                lineTo(w * 0.50f, h * 0.60f)
                lineTo(w * 0.68f, h * 0.42f)
            }
            drawPath(p, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        "arrow_up" -> {
            // Circle with up arrow
            drawCircle(color, radius = w * 0.44f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = strokeW * 0.9f))
            val p = Path().apply {
                moveTo(w * 0.32f, h * 0.58f)
                lineTo(w * 0.50f, h * 0.40f)
                lineTo(w * 0.68f, h * 0.58f)
            }
            drawPath(p, color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        "music" -> {
            // Windows 8 Beamed double music note (♫)
            val noteR = w * 0.11f
            val head1Center = Offset(w * 0.32f, h * 0.70f)
            drawCircle(color, radius = noteR, center = head1Center, style = Fill)
            val head2Center = Offset(w * 0.68f, h * 0.58f)
            drawCircle(color, radius = noteR, center = head2Center, style = Fill)

            drawLine(
                color = color,
                start = Offset(head1Center.x + noteR * 0.8f, head1Center.y),
                end = Offset(head1Center.x + noteR * 0.8f, h * 0.22f),
                strokeWidth = strokeW,
                cap = StrokeCap.Square
            )
            drawLine(
                color = color,
                start = Offset(head2Center.x + noteR * 0.8f, head2Center.y),
                end = Offset(head2Center.x + noteR * 0.8f, h * 0.12f),
                strokeWidth = strokeW,
                cap = StrokeCap.Square
            )

            val beamPath = Path().apply {
                moveTo(head1Center.x + noteR * 0.8f - strokeW / 2, h * 0.22f)
                lineTo(head2Center.x + noteR * 0.8f + strokeW / 2, h * 0.12f)
                lineTo(head2Center.x + noteR * 0.8f + strokeW / 2, h * 0.22f)
                lineTo(head1Center.x + noteR * 0.8f - strokeW / 2, h * 0.32f)
                close()
            }
            drawPath(beamPath, color = color, style = Fill)
        }

        "people" -> {
            // Windows 8 People: Two silhouette figures
            drawCircle(color, radius = w * 0.12f, center = Offset(w * 0.66f, h * 0.30f), style = Fill)
            val p2Body = Path().apply {
                moveTo(w * 0.52f, h * 0.68f)
                cubicTo(w * 0.52f, h * 0.50f, w * 0.80f, h * 0.50f, w * 0.80f, h * 0.68f)
                close()
            }
            drawPath(p2Body, color = color, style = Fill)

            drawCircle(color, radius = w * 0.16f, center = Offset(w * 0.38f, h * 0.36f), style = Fill)
            val p1Body = Path().apply {
                moveTo(w * 0.18f, h * 0.80f)
                cubicTo(w * 0.18f, h * 0.56f, w * 0.58f, h * 0.56f, w * 0.58f, h * 0.80f)
                close()
            }
            drawPath(p1Body, color = color, style = Fill)
        }

        "skype" -> {
            // Windows 8 Skype: Circle badge with bold S inside
            val cx = w * 0.5f
            val cy = h * 0.5f
            drawCircle(color, radius = w * 0.38f, center = Offset(cx, cy), style = Stroke(width = strokeW))
            val sPath = Path().apply {
                moveTo(w * 0.60f, h * 0.36f)
                cubicTo(w * 0.38f, h * 0.32f, w * 0.36f, h * 0.48f, w * 0.50f, h * 0.50f)
                cubicTo(w * 0.64f, h * 0.52f, w * 0.64f, h * 0.66f, w * 0.40f, h * 0.64f)
            }
            drawPath(sPath, color = color, style = Stroke(width = strokeW * 1.3f, cap = StrokeCap.Round))
        }

        "maps" -> {
            // Windows 8 Maps: Pin marker
            val pinCx = w * 0.5f
            val pinCy = h * 0.38f
            val pinR = w * 0.22f
            val pinPath = Path().apply {
                moveTo(pinCx, h * 0.80f)
                cubicTo(pinCx - pinR * 1.1f, pinCy + pinR * 0.5f, pinCx - pinR, pinCy - pinR, pinCx, pinCy - pinR)
                cubicTo(pinCx + pinR, pinCy - pinR, pinCx + pinR * 1.1f, pinCy + pinR * 0.5f, pinCx, h * 0.80f)
                close()
            }
            drawPath(pinPath, color = color, style = Stroke(width = strokeW))
            drawCircle(color, radius = w * 0.08f, center = Offset(pinCx, pinCy), style = Fill)
        }

        "news" -> {
            // Windows 8 News: Newspaper page with headline and columns
            val pageRect = Rect(w * 0.16f, h * 0.16f, w * 0.84f, h * 0.84f)
            drawRect(color, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height), style = Stroke(width = strokeW))
            drawRect(color, topLeft = Offset(w * 0.24f, h * 0.24f), size = Size(w * 0.52f, h * 0.10f), style = Fill)
            drawRect(color, topLeft = Offset(w * 0.24f, h * 0.40f), size = Size(w * 0.22f, h * 0.30f), style = Fill)
            for (i in 0..2) {
                drawLine(
                    color = color,
                    start = Offset(w * 0.52f, h * 0.42f + i * (h * 0.10f)),
                    end = Offset(w * 0.76f, h * 0.42f + i * (h * 0.10f)),
                    strokeWidth = strokeW * 0.8f,
                    cap = StrokeCap.Round
                )
            }
        }

        "app" -> {
            // Windows 8 4-Tile Grid Logo
            val sq = w * 0.26f
            val gap = w * 0.08f
            val left = (w - (sq * 2 + gap)) / 2f
            val top = (h - (sq * 2 + gap)) / 2f
            drawRect(color, topLeft = Offset(left, top), size = Size(sq, sq), style = Fill)
            drawRect(color, topLeft = Offset(left + sq + gap, top), size = Size(sq, sq), style = Fill)
            drawRect(color, topLeft = Offset(left, top + sq + gap), size = Size(sq, sq), style = Fill)
            drawRect(color, topLeft = Offset(left + sq + gap, top + sq + gap), size = Size(sq, sq), style = Fill)
        }

        "pin" -> {
            // Thumbtack / Pin icon
            val p = Path().apply {
                moveTo(w * 0.30f, h * 0.20f)
                lineTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.62f, h * 0.45f)
                lineTo(w * 0.75f, h * 0.55f)
                lineTo(w * 0.54f, h * 0.55f)
                lineTo(w * 0.50f, h * 0.85f)
                lineTo(w * 0.46f, h * 0.55f)
                lineTo(w * 0.25f, h * 0.55f)
                lineTo(w * 0.38f, h * 0.45f)
                close()
            }
            drawPath(p, color = color, style = Fill)
        }

        "unpin" -> {
            // Thumbtack with diagonal slash
            val p = Path().apply {
                moveTo(w * 0.30f, h * 0.20f)
                lineTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.62f, h * 0.45f)
                lineTo(w * 0.75f, h * 0.55f)
                lineTo(w * 0.54f, h * 0.55f)
                lineTo(w * 0.50f, h * 0.85f)
                lineTo(w * 0.46f, h * 0.55f)
                lineTo(w * 0.25f, h * 0.55f)
                lineTo(w * 0.38f, h * 0.45f)
                close()
            }
            drawPath(p, color = color, style = Fill)
            drawLine(
                color = color,
                start = Offset(w * 0.15f, h * 0.15f),
                end = Offset(w * 0.85f, h * 0.85f),
                strokeWidth = strokeW * 1.2f,
                cap = StrokeCap.Round
            )
        }

        "video" -> {
            // Video player / film slate
            drawRect(color, topLeft = Offset(w * 0.16f, h * 0.22f), size = Size(w * 0.68f, h * 0.56f), style = Stroke(width = strokeW))
            val playPath = Path().apply {
                moveTo(w * 0.42f, h * 0.38f)
                lineTo(w * 0.64f, h * 0.50f)
                lineTo(w * 0.42f, h * 0.62f)
                close()
            }
            drawPath(playPath, color = color, style = Fill)
        }

        "games" -> {
            // Gamepad controller
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.32f),
                size = Size(w * 0.70f, h * 0.42f),
                style = Stroke(width = strokeW),
            )
            // D-pad
            drawLine(color, start = Offset(w * 0.32f, h * 0.45f), end = Offset(w * 0.32f, h * 0.61f), strokeWidth = strokeW * 1.1f, cap = StrokeCap.Round)
            drawLine(color, start = Offset(w * 0.24f, h * 0.53f), end = Offset(w * 0.40f, h * 0.53f), strokeWidth = strokeW * 1.1f, cap = StrokeCap.Round)
            // Buttons
            drawCircle(color, radius = w * 0.04f, center = Offset(w * 0.64f, h * 0.48f), style = Fill)
            drawCircle(color, radius = w * 0.04f, center = Offset(w * 0.74f, h * 0.56f), style = Fill)
        }

        "calculator" -> {
            // Calculator
            drawRect(color, topLeft = Offset(w * 0.20f, h * 0.16f), size = Size(w * 0.60f, h * 0.68f), style = Stroke(width = strokeW))
            drawRect(color, topLeft = Offset(w * 0.28f, h * 0.24f), size = Size(w * 0.44f, h * 0.14f), style = Stroke(width = strokeW * 0.8f))
            // 4 keys
            drawRect(color, topLeft = Offset(w * 0.28f, h * 0.46f), size = Size(w * 0.16f, h * 0.10f), style = Fill)
            drawRect(color, topLeft = Offset(w * 0.56f, h * 0.46f), size = Size(w * 0.16f, h * 0.10f), style = Fill)
            drawRect(color, topLeft = Offset(w * 0.28f, h * 0.62f), size = Size(w * 0.16f, h * 0.10f), style = Fill)
            drawRect(color, topLeft = Offset(w * 0.56f, h * 0.62f), size = Size(w * 0.16f, h * 0.10f), style = Fill)
        }

        "folder", "files" -> {
            // File folder
            val fPath = Path().apply {
                moveTo(w * 0.15f, h * 0.28f)
                lineTo(w * 0.42f, h * 0.28f)
                lineTo(w * 0.50f, h * 0.36f)
                lineTo(w * 0.85f, h * 0.36f)
                lineTo(w * 0.85f, h * 0.76f)
                lineTo(w * 0.15f, h * 0.76f)
                close()
            }
            drawPath(fPath, color = color, style = Stroke(width = strokeW))
        }

        "plus" -> {
            // Plus sign (+)
            val cx = w * 0.5f
            val cy = h * 0.5f
            val len = w * 0.30f
            drawLine(color, start = Offset(cx - len, cy), end = Offset(cx + len, cy), strokeWidth = strokeW * 1.2f, cap = StrokeCap.Round)
            drawLine(color, start = Offset(cx, cy - len), end = Offset(cx, cy + len), strokeWidth = strokeW * 1.2f, cap = StrokeCap.Round)
        }

        else -> {
            // Default Windows 8 4-square grid logo (never an empty/vacant wireframe)
            val sq = w * 0.24f
            val gap = w * 0.08f
            val left = (w - (sq * 2 + gap)) / 2f
            val top = (h - (sq * 2 + gap)) / 2f
            drawRect(color, topLeft = Offset(left, top), size = Size(sq, sq), style = Fill)
            drawRect(color, topLeft = Offset(left + sq + gap, top), size = Size(sq, sq), style = Fill)
            drawRect(color, topLeft = Offset(left, top + sq + gap), size = Size(sq, sq), style = Fill)
            drawRect(color, topLeft = Offset(left + sq + gap, top + sq + gap), size = Size(sq, sq), style = Fill)
        }
    }
}
