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
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.roundToInt

private const val REPEATED_LAYER_WIDTHS = 3f
private const val WALLPAPER_STYLE_MIN = 0
private const val WALLPAPER_STYLE_MAX = 9
private val DEFAULT_WALLPAPER_BASE = Color(0xFF23053D)
private val DEFAULT_WALLPAPER_ACCENT = Color(0xFF5A148C)
private val DEFAULT_WALLPAPER_HIGHLIGHT = Color(0xFF8824B8)

/**
 * Windows 8.1-inspired Start wallpaper and Motion Accent compositor.
 *
 * Style 0 is the vector wallpaper. Styles 1..9 keep the supplied stock artwork as the visual base.
 * The bitmap is aspect-preserving and center-cropped for the current viewport, then Windows 8.1
 * Motion Accent behavior is composited as a separate interaction-driven layer for the animated
 * families (robots, city, bubbles/swirls, dragon and gears). The entire decorative plane remains
 * behind Start content and follows one continuous parallax track.
 *
 * Background and accent colors are independent Personalize settings, as on Windows 8.1. Existing
 * callers that explicitly supply colors still win; the persisted Personalize palette is used only
 * for this composable's historical default values.
 */
@Composable
fun WindowsWallpaper(
    modifier: Modifier = Modifier,
    baseColor: Color = DEFAULT_WALLPAPER_BASE,
    accentColor: Color = DEFAULT_WALLPAPER_ACCENT,
    highlightColor: Color = DEFAULT_WALLPAPER_HIGHLIGHT,
    enabled: Boolean = true,
    scrollOffsetPx: () -> Float = { 0f },
    wallpaperStyle: Int = 0,
) {
    val context = LocalContext.current
    LaunchedEffect(context) { StartPersonalization.ensureLoaded(context) }

    val personalizedBase = StartPersonalization.backgroundColor
    val personalizedAccent = StartPersonalization.accentColor
    val effectiveBaseColor = if (baseColor == DEFAULT_WALLPAPER_BASE) personalizedBase else baseColor
    val effectiveAccentColor = if (accentColor == DEFAULT_WALLPAPER_ACCENT) personalizedAccent else accentColor
    val effectiveHighlightColor = if (highlightColor == DEFAULT_WALLPAPER_HIGHLIGHT) {
        StartPersonalization.highlightFor(effectiveAccentColor)
    } else {
        highlightColor
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val selectedStyle = wallpaperStyle.coerceIn(WALLPAPER_STYLE_MIN, WALLPAPER_STYLE_MAX)
        val motionFrame = rememberWindowsMotionAccentFrame(
            wallpaperStyle = selectedStyle,
            enabled = enabled,
            viewportWidthPx = viewportWidthPx,
            scrollOffsetPx = scrollOffsetPx,
        )

        WallpaperBase(baseColor = effectiveBaseColor)

        if (selectedStyle == 0) {
            PurpleGlowLayer(
                viewportWidthDp = maxWidth,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                baseColor = effectiveBaseColor,
            )
            PurpleRibbonLayer(
                viewportWidthDp = maxWidth,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                accentColor = effectiveAccentColor,
                highlightColor = effectiveHighlightColor,
            )
            PurpleHighlightLayer(
                viewportWidthDp = maxWidth,
                viewportWidthPx = viewportWidthPx,
                enabled = enabled,
                scrollOffsetPx = scrollOffsetPx,
                accentColor = effectiveAccentColor,
                highlightColor = effectiveHighlightColor,
            )
        } else {
            val bitmap = rememberWallpaperBitmap(wallpaperResourceId(selectedStyle))
            if (bitmap != null) {
                BitmapWallpaper(
                    bitmap = bitmap,
                    viewportWidthDp = maxWidth,
                    viewportWidthPx = viewportWidthPx,
                    enabled = enabled,
                    scrollOffsetPx = scrollOffsetPx,
                    motionFrame = motionFrame,
                    baseColor = effectiveBaseColor,
                    accentColor = effectiveAccentColor,
                    highlightColor = effectiveHighlightColor,
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
                val defaultPalette = baseColor == DEFAULT_WALLPAPER_BASE
                val middle = if (defaultPalette) Color(0xFF140224) else baseColor.scaledRgb(0.55f)
                val end = if (defaultPalette) Color(0xFF0C0117) else baseColor.scaledRgb(0.30f)
                val baseBrush = Brush.linearGradient(
                    colors = listOf(baseColor, middle, end),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind { drawRect(brush = baseBrush, size = size) }
            },
    )
}

@Composable
private fun PurpleGlowLayer(
    viewportWidthDp: Dp,
    viewportWidthPx: Float,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
    baseColor: Color,
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
                val glowColor = if (baseColor == DEFAULT_WALLPAPER_BASE) {
                    Color(0xFF4A0E72)
                } else {
                    baseColor.lightened(0.18f)
                }
                val brushes = Array(origins.size) { index ->
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.35f), Color.Transparent),
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
                    for (index in paths.indices) drawPath(path = paths[index], brush = brushes[index])
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
                    for (index in paths.indices) drawPath(path = paths[index], brush = brushes[index])
                }
            },
    )
}

@Composable
private fun BitmapWallpaper(
    bitmap: Bitmap,
    viewportWidthDp: Dp,
    viewportWidthPx: Float,
    enabled: Boolean,
    scrollOffsetPx: () -> Float,
    motionFrame: WindowsMotionAccentFrame,
    baseColor: Color,
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
                rate = WallpaperParallax.IMAGE_RATE,
            )
            .drawWithCache {
                val tileWidth = viewportWidthPx.coerceAtLeast(1f)
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
                val customBackground = baseColor != DEFAULT_WALLPAPER_BASE

                onDrawBehind {
                    val tileHeight = size.height.coerceAtLeast(1f)
                    val transform = WallpaperParallax.coverTransform(
                        bitmapWidthPx = bitmap.width.toFloat(),
                        bitmapHeightPx = bitmap.height.toFloat(),
                        viewportWidthPx = tileWidth,
                        viewportHeightPx = tileHeight,
                        viewportCenterXPx = tileWidth * 1.5f,
                    )
                    val motionX = WindowsMotionAccent.artworkOffsetX(motionFrame, tileWidth)
                    val motionY = WindowsMotionAccent.artworkOffsetY(motionFrame, tileHeight)

                    matrixValues[0] = transform.scale
                    matrixValues[1] = 0f
                    matrixValues[2] = transform.offsetX + motionX
                    matrixValues[3] = 0f
                    matrixValues[4] = transform.scale
                    matrixValues[5] = transform.offsetY + motionY
                    matrixValues[6] = 0f
                    matrixValues[7] = 0f
                    matrixValues[8] = 1f
                    shaderMatrix.setValues(matrixValues)
                    shader.setLocalMatrix(shaderMatrix)

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                    }

                    // The repository carries flattened stock captures rather than Microsoft's
                    // original tintable layer masks. Preserve the untouched stock pixels for the
                    // default palette; when the user changes Background color, apply only the
                    // restrained color wash that a flattened asset can support without destroying
                    // its original shading/detail.
                    if (customBackground) {
                        drawRect(color = baseColor.copy(alpha = 0.18f), size = size)
                    }

                    drawWindowsMotionAccent(
                        frame = motionFrame,
                        viewportWidthPx = tileWidth,
                        accentColor = accentColor,
                        highlightColor = highlightColor,
                    )
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
            WallpaperParallax.translationX(
                scrollOffsetPx = scrollOffsetPx(),
                rate = rate,
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
    val defaultPalette = accentColor == DEFAULT_WALLPAPER_ACCENT && highlightColor == DEFAULT_WALLPAPER_HIGHLIGHT
    val shadow = if (defaultPalette) Color(0xFF32094D) else accentColor.scaledRgb(0.50f)
    return Brush.linearGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.45f),
            highlightColor.copy(alpha = 0.35f),
            shadow.copy(alpha = 0.20f),
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

private fun Color.scaledRgb(scale: Float): Color = Color(
    red = (red * scale).coerceIn(0f, 1f),
    green = (green * scale).coerceIn(0f, 1f),
    blue = (blue * scale).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.lightened(amount: Float): Color = Color(
    red = (red + (1f - red) * amount).coerceIn(0f, 1f),
    green = (green + (1f - green) * amount).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
    alpha = alpha,
)

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

/** Keeps decoded full-size wallpaper bitmaps bounded while avoiding any decode on the UI thread. */
private object WallpaperBitmapCache {
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024

    private val cache = object : LruCache<Int, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
    }

    fun peek(resourceId: Int): Bitmap? = synchronized(cache) { cache.get(resourceId) }

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
