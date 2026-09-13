package com.flivoro.tile8auncher.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.flivoro.tile8auncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val REPEATED_LAYER_WIDTHS = 3f
private const val WALLPAPER_STYLE_MIN = 0
private const val WALLPAPER_STYLE_MAX = 9

/**
 * Lightweight Windows 8.1-inspired Start wallpaper.
 *
 * Style 0 keeps the launcher's original cached vector ribbons. Styles 1..9 render the original
 * supplied stock JPEGs directly. Bitmap styles are drawn into one fixed viewport with a MIRROR
 * BitmapShader, so there is no finite moving backing layer that can ever scroll off-screen.
 *
 * The stock bitmap is aspect-preserving center-cropped (cover), which keeps its geometry correct on
 * tall portrait phones instead of independently stretching X and Y.
 */
@Composable
fun WindowsWallpaper(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF23053D),
    accentColor: Color = Color(0xFF5A148C),
    highlightColor: Color = Color(0xFF8824B8),
    enabled: Boolean = true,
    scrollOffsetPx: () -> Float = { 0f },
    wallpaperStyle: Int = 0,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val selectedStyle = wallpaperStyle.coerceIn(WALLPAPER_STYLE_MIN, WALLPAPER_STYLE_MAX)

        WallpaperBase(baseColor = baseColor)

        if (selectedStyle == 0) {
            PurpleGlowLayer(
                viewportWidthDp = maxWidth,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
            )
            PurpleRibbonLayer(
                viewportWidthDp = maxWidth,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                accentColor = accentColor,
                highlightColor = highlightColor,
            )
            PurpleHighlightLayer(
                viewportWidthDp = maxWidth,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                accentColor = accentColor,
                highlightColor = highlightColor,
            )
        } else {
            val bitmap = rememberWallpaperBitmap(wallpaperResourceId(selectedStyle))
            if (bitmap != null) {
                BitmapWallpaper(
                    bitmap = bitmap,
                    enabled = enabled,
                    scrollOffsetPx = scrollOffsetPx,
                )
            }
        }
    }
}

@Composable
private fun WallpaperBase(baseColor: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val baseBrush = Brush.linearGradient(
                    colors = listOf(
                        baseColor,
                        Color(0xFF140224),
                        Color(0xFF0C0117),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind {
                    drawRect(brush = baseBrush, size = size)
                }
            },
    )
}

@Composable
private fun PurpleGlowLayer(
    viewportWidthDp: Dp,
    viewportWidthPx: Float,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
) {
    Spacer(
        modifier = Modifier
            .repeatingWallpaperLayer(
                viewportWidthDp = viewportWidthDp,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                rate = WallpaperParallax.GLOW_RATE,
            )
            .drawWithCache {
                val tileWidth = viewportWidthPx
                val tileHeight = size.height
                val origins = tileOrigins(tileWidth)
                val mirrored = tileMirrors()
                val centers = Array(origins.size) { index ->
                    Offset(
                        x = tileX(origins[index], tileWidth, 0.15f, mirrored[index]),
                        y = tileHeight * 0.20f,
                    )
                }
                val brushes = Array(origins.size) { index ->
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4A0E72).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        center = centers[index],
                        radius = tileWidth * 0.60f,
                    )
                }

                onDrawBehind {
                    for (index in centers.indices) {
                        drawCircle(
                            brush = brushes[index],
                            center = centers[index],
                            radius = tileWidth * 0.60f,
                        )
                    }
                }
            },
    )
}

@Composable
private fun PurpleRibbonLayer(
    viewportWidthDp: Dp,
    viewportWidthPx: Float,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
    accentColor: Color,
    highlightColor: Color,
) {
    Spacer(
        modifier = Modifier
            .repeatingWallpaperLayer(
                viewportWidthDp = viewportWidthDp,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                rate = WallpaperParallax.RIBBON_RATE,
            )
            .drawWithCache {
                val tileWidth = viewportWidthPx
                val tileHeight = size.height
                val origins = tileOrigins(tileWidth)
                val mirrored = tileMirrors()
                val paths = Array(origins.size) { index ->
                    mainRibbonPath(
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                        originX = origins[index],
                        mirrored = mirrored[index],
                    )
                }
                val brushes = Array(origins.size) { index ->
                    mainRibbonBrush(
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                        originX = origins[index],
                        mirrored = mirrored[index],
                        accentColor = accentColor,
                        highlightColor = highlightColor,
                    )
                }

                onDrawBehind {
                    for (index in paths.indices) {
                        drawPath(path = paths[index], brush = brushes[index])
                    }
                }
            },
    )
}

@Composable
private fun PurpleHighlightLayer(
    viewportWidthDp: Dp,
    viewportWidthPx: Float,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
    accentColor: Color,
    highlightColor: Color,
) {
    Spacer(
        modifier = Modifier
            .repeatingWallpaperLayer(
                viewportWidthDp = viewportWidthDp,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                rate = WallpaperParallax.HIGHLIGHT_RATE,
            )
            .drawWithCache {
                val tileWidth = viewportWidthPx
                val tileHeight = size.height
                val origins = tileOrigins(tileWidth)
                val mirrored = tileMirrors()
                val paths = Array(origins.size) { index ->
                    highlightRibbonPath(
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                        originX = origins[index],
                        mirrored = mirrored[index],
                    )
                }
                val brushes = Array(origins.size) { index ->
                    highlightRibbonBrush(
                        tileWidth = tileWidth,
                        tileHeight = tileHeight,
                        originX = origins[index],
                        mirrored = mirrored[index],
                        accentColor = accentColor,
                        highlightColor = highlightColor,
                    )
                }

                onDrawBehind {
                    for (index in paths.indices) {
                        drawPath(path = paths[index], brush = brushes[index])
                    }
                }
            },
    )
}

/**
 * One fixed full-screen shader draw. MIRROR provides an infinite, pixel-continuous horizontal
 * repeat. Only the local shader matrix changes with parallax, so even very long scrolls cannot
 * reveal an empty layer.
 */
@Composable
private fun BitmapWallpaper(
    bitmap: Bitmap,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val shader = BitmapShader(
                    bitmap,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.CLAMP,
                )
                val paint = Paint(
                    Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG,
                ).apply {
                    style = Paint.Style.FILL
                    this.shader = shader
                }
                val shaderMatrix = Matrix()
                val matrixValues = FloatArray(9)

                onDrawBehind {
                    val viewportWidth = size.width.coerceAtLeast(1f)
                    val viewportHeight = size.height.coerceAtLeast(1f)
                    val sourceWidth = bitmap.width.toFloat().coerceAtLeast(1f)
                    val sourceHeight = bitmap.height.toFloat().coerceAtLeast(1f)

                    // Aspect-preserving cover: never distort the 4:3 Windows artwork on portrait.
                    val scale = max(viewportWidth / sourceWidth, viewportHeight / sourceHeight)
                    val renderedWidth = sourceWidth * scale
                    val renderedHeight = sourceHeight * scale
                    val centerX = (viewportWidth - renderedWidth) / 2f
                    val centerY = (viewportHeight - renderedHeight) / 2f

                    val rawTranslation = if (enabled) {
                        WallpaperParallax.translationX(
                            scrollOffsetPx = scrollOffsetPx(),
                            rate = WallpaperParallax.IMAGE_RATE,
                        )
                    } else {
                        0f
                    }
                    // A MIRROR shader repeats exactly every two rendered source widths. Wrapping
                    // by that exact period is visually identical but keeps the matrix numerically
                    // small forever.
                    val phaseTranslation = WallpaperParallax.wrapTranslationX(
                        linearTranslationPx = rawTranslation,
                        repeatPeriodPx = (renderedWidth * 2f).coerceAtLeast(1f),
                    )

                    matrixValues[0] = scale
                    matrixValues[1] = 0f
                    matrixValues[2] = centerX + phaseTranslation
                    matrixValues[3] = 0f
                    matrixValues[4] = scale
                    matrixValues[5] = centerY
                    matrixValues[6] = 0f
                    matrixValues[7] = 0f
                    matrixValues[8] = 1f
                    shaderMatrix.setValues(matrixValues)
                    shader.setLocalMatrix(shaderMatrix)

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            0f,
                            0f,
                            viewportWidth,
                            viewportHeight,
                            paint,
                        )
                    }
                }
            },
    )
}

private fun Modifier.repeatingWallpaperLayer(
    viewportWidthDp: Dp,
    viewportWidthPx: Float,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
    rate: Float,
): Modifier = requiredWidth(viewportWidthDp * REPEATED_LAYER_WIDTHS)
    .fillMaxHeight()
    .offset { IntOffset(-viewportWidthPx.roundToInt(), 0) }
    .graphicsLayer {
        translationX = if (enabled) {
            WallpaperParallax.repeatingTranslationX(
                scrollOffsetPx = scrollOffsetPx(),
                rate = rate,
                repeatPeriodPx = viewportWidthPx * 2f,
            )
        } else {
            0f
        }
    }

private fun tileOrigins(tileWidth: Float): FloatArray = floatArrayOf(
    -tileWidth,
    0f,
    tileWidth,
    tileWidth * 2f,
)

private fun tileMirrors(): BooleanArray = booleanArrayOf(false, true, false, true)

private fun tileX(
    originX: Float,
    tileWidth: Float,
    fraction: Float,
    mirrored: Boolean,
): Float = originX + (if (mirrored) 1f - fraction else fraction) * tileWidth

private fun mainRibbonPath(
    tileWidth: Float,
    tileHeight: Float,
    originX: Float,
    mirrored: Boolean,
): Path = Path().apply {
    moveTo(tileX(originX, tileWidth, 0.35f, mirrored), 0f)
    cubicTo(
        tileX(originX, tileWidth, 0.45f, mirrored), tileHeight * 0.30f,
        tileX(originX, tileWidth, 0.75f, mirrored), tileHeight * 0.50f,
        tileX(originX, tileWidth, 0.60f, mirrored), tileHeight,
    )
    lineTo(tileX(originX, tileWidth, 0.90f, mirrored), tileHeight)
    cubicTo(
        tileX(originX, tileWidth, 0.95f, mirrored), tileHeight * 0.45f,
        tileX(originX, tileWidth, 0.65f, mirrored), tileHeight * 0.20f,
        tileX(originX, tileWidth, 0.55f, mirrored), 0f,
    )
    close()
}

private fun mainRibbonBrush(
    tileWidth: Float,
    tileHeight: Float,
    originX: Float,
    mirrored: Boolean,
    accentColor: Color,
    highlightColor: Color,
): Brush {
    val startFraction = if (mirrored) 0.65f else 0.35f
    val endFraction = if (mirrored) 0.25f else 0.75f
    return Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.45f),
            highlightColor.copy(alpha = 0.35f),
            Color(0xFF32094D).copy(alpha = 0.20f),
        ),
        start = Offset(originX + tileWidth * startFraction, 0f),
        end = Offset(originX + tileWidth * endFraction, tileHeight),
    )
}

private fun highlightRibbonPath(
    tileWidth: Float,
    tileHeight: Float,
    originX: Float,
    mirrored: Boolean,
): Path = Path().apply {
    moveTo(tileX(originX, tileWidth, 0.48f, mirrored), 0f)
    cubicTo(
        tileX(originX, tileWidth, 0.58f, mirrored), tileHeight * 0.25f,
        tileX(originX, tileWidth, 0.82f, mirrored), tileHeight * 0.45f,
        tileX(originX, tileWidth, 0.72f, mirrored), tileHeight,
    )
    lineTo(tileX(originX, tileWidth, 1f, mirrored), tileHeight)
    lineTo(tileX(originX, tileWidth, 1f, mirrored), 0f)
    close()
}

private fun highlightRibbonBrush(
    tileWidth: Float,
    tileHeight: Float,
    originX: Float,
    mirrored: Boolean,
    accentColor: Color,
    highlightColor: Color,
): Brush {
    val startFraction = 0.50f
    val endFraction = if (mirrored) 0f else 1f
    return Brush.linearGradient(
        colors = listOf(
            highlightColor.copy(alpha = 0.25f),
            accentColor.copy(alpha = 0.15f),
            Color.Transparent,
        ),
        start = Offset(originX + tileWidth * startFraction, 0f),
        end = Offset(originX + tileWidth * endFraction, tileHeight),
    )
}

private fun wallpaperResourceId(style: Int): Int = when (style) {
    1 -> R.drawable.start_wallpaper_1
    2 -> R.drawable.start_wallpaper_2
    3 -> R.drawable.start_wallpaper_3
    4 -> R.drawable.start_wallpaper_4
    5 -> R.drawable.start_wallpaper_5
    6 -> R.drawable.start_wallpaper_6
    7 -> R.drawable.start_wallpaper_7
    8 -> R.drawable.start_wallpaper_8
    9 -> R.drawable.start_wallpaper_9
    else -> 0
}

@Composable
private fun rememberWallpaperBitmap(resourceId: Int): Bitmap? {
    val resources = LocalContext.current.resources
    val cachedBitmap = WallpaperBitmapCache.peek(resourceId)
    return produceState<Bitmap?>(cachedBitmap, resources, resourceId) {
        value = withContext(Dispatchers.IO) {
            WallpaperBitmapCache.getOrDecode(resources, resourceId)
        }
    }.value
}

/** Keeps decoded full-size wallpaper bitmaps bounded while avoiding decode on the UI thread. */
private object WallpaperBitmapCache {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024

    private val cache = object : LruCache<Int, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    fun peek(resourceId: Int): Bitmap? = synchronized(cache) {
        cache.get(resourceId)
    }

    fun getOrDecode(resources: Resources, resourceId: Int): Bitmap? {
        peek(resourceId)?.let { return it }

        val decoded = BitmapFactory.decodeResource(
            resources,
            resourceId,
            BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null

        return synchronized(cache) {
            val racedBitmap = cache.get(resourceId)
            if (racedBitmap != null) {
                decoded.recycle()
                racedBitmap
            } else {
                cache.put(resourceId, decoded)
                decoded
            }
        }
    }
}
