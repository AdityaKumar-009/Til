package com.flivoro.tile8auncher.ui.components

import android.app.ActivityManager
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val WALLPAPER_STYLE_MIN = 0
private const val WALLPAPER_STYLE_MAX = 9
private val DEFAULT_WALLPAPER_BASE = Color(0xFF23053D)
private val DEFAULT_WALLPAPER_ACCENT = Color(0xFF5A148C)
private val DEFAULT_WALLPAPER_HIGHLIGHT = Color(0xFF8824B8)

/**
 * Windows 8.1 Start background renderer optimized for low-end Android hardware.
 *
 * Styles 1..9 are not redrawn as procedural SVG-like geometry. Their exact supplied artwork pixels
 * are converted once, off the UI thread, into three compact ALPHA_8 masks. Scrolling then costs only
 * three GPU shader draws and matrix updates. Background color is a separate solid plane and Accent
 * recolors only those transparent artwork masks.
 *
 * The shader uses MIRROR tiling horizontally, which makes the infinite world continuous at both
 * texture boundaries without ever moving a finite backing layer off screen. Start and All Apps feed
 * the same Double world coordinate through [StartBackgroundScrollRuntime].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun WindowsWallpaper(
    modifier: Modifier = Modifier,
    baseColor: Color = DEFAULT_WALLPAPER_BASE,
    accentColor: Color = DEFAULT_WALLPAPER_ACCENT,
    highlightColor: Color = DEFAULT_WALLPAPER_HIGHLIGHT,
    enabled: Boolean = true,
    scrollOffsetPx: () -> Float = { 0f },
    wallpaperStyle: Int = 0,
    sceneState: StartBackgroundSceneState? = null,
    trackLauncherScroll: Boolean = true,
    previewMode: Boolean = false,
) {
    val context = LocalContext.current
    LaunchedEffect(context) { StartPersonalization.ensureLoaded(context) }

    val personalizedBase = StartPersonalization.backgroundColor
    val personalizedAccent = StartPersonalization.accentColor
    val effectiveBaseColor = if (baseColor == DEFAULT_WALLPAPER_BASE) personalizedBase else baseColor
    val effectiveAccentColor =
        if (accentColor == DEFAULT_WALLPAPER_ACCENT) personalizedAccent else accentColor
    val effectiveHighlightColor = if (highlightColor == DEFAULT_WALLPAPER_HIGHLIGHT) {
        StartPersonalization.highlightFor(effectiveAccentColor)
    } else {
        highlightColor
    }
    val lowRamDevice = remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }

    val ownedScene = remember { StartBackgroundSceneState() }
    val scene = sceneState ?: ownedScene
    SideEffect { scene.inputEnabled = enabled }

    DisposableEffect(scene, trackLauncherScroll) {
        if (trackLauncherScroll) StartBackgroundScrollRuntime.attach(scene)
        onDispose {
            if (trackLauncherScroll) StartBackgroundScrollRuntime.detach(scene)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val selectedStyle = wallpaperStyle.coerceIn(WALLPAPER_STYLE_MIN, WALLPAPER_STYLE_MAX)
        val motion = rememberWindowsMotionAccentFrame(
            wallpaperStyle = selectedStyle,
            enabled = enabled,
            viewportWidthPx = viewportWidthPx,
            sceneState = scene,
            lowRamMode = lowRamDevice,
        )

        if (selectedStyle == 0) {
            // Style 0 has no supplied stock bitmap. Keep its lightweight cached ribbon geometry.
            val geometry = remember(viewportWidthPx, viewportHeightPx) {
                WindowsBackgroundGeometry(viewportWidthPx, viewportHeightPx)
            }
            val palette = remember(effectiveAccentColor, effectiveHighlightColor) {
                BackgroundPalette.fromAccent(effectiveAccentColor).copy(
                    highlight = effectiveHighlightColor,
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                drawRect(effectiveBaseColor)
                drawWindowsBackgroundArtwork(
                    geometry = geometry,
                    wallpaperStyle = 0,
                    worldX = scene.worldX,
                    palette = palette,
                    motion = motion,
                )
            }
        } else {
            val initial = remember(selectedStyle, previewMode, lowRamDevice) {
                WindowsWallpaperArtCache.peek(selectedStyle, previewMode, lowRamDevice)
            }
            val art = produceState<WallpaperArtMasks?>(
                initialValue = initial,
                selectedStyle,
                previewMode,
                lowRamDevice,
            ) {
                if (value == null) {
                    value = withContext(Dispatchers.Default) {
                        WindowsWallpaperArtCache.load(
                            context = context.applicationContext,
                            style = selectedStyle,
                            preview = previewMode,
                            lowRam = lowRamDevice,
                        )
                    }
                }
            }.value

            NativeMaskedWallpaper(
                modifier = Modifier.fillMaxSize(),
                art = art,
                backgroundColor = effectiveBaseColor,
                accentColor = effectiveAccentColor,
                highlightColor = effectiveHighlightColor,
                scene = scene,
                motion = motion,
            )
        }
    }
}

@Composable
private fun NativeMaskedWallpaper(
    modifier: Modifier,
    art: WallpaperArtMasks?,
    backgroundColor: Color,
    accentColor: Color,
    highlightColor: Color,
    scene: StartBackgroundSceneState,
    motion: WindowsMotionAccentFrame,
) {
    val palette = remember(accentColor, highlightColor) {
        BackgroundPalette.fromAccent(accentColor).copy(highlight = highlightColor)
    }

    Spacer(
        modifier = modifier.drawWithCache {
            val currentArt = art
            if (currentArt == null || currentArt.sourceWidth <= 0 || currentArt.sourceHeight <= 0) {
                onDrawBehind { drawRect(backgroundColor) }
            } else {
                val shadowShader = BitmapShader(
                    currentArt.shadowMask,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.CLAMP,
                )
                val primaryShader = BitmapShader(
                    currentArt.primaryMask,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.CLAMP,
                )
                val highlightShader = BitmapShader(
                    currentArt.highlightMask,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.CLAMP,
                )
                val shadowPaint = maskPaint(shadowShader, palette.shadow.toArgb())
                val primaryPaint = maskPaint(primaryShader, palette.primary.toArgb())
                val highlightPaint = maskPaint(highlightShader, palette.highlight.toArgb())
                val shadowMatrix = Matrix()
                val primaryMatrix = Matrix()
                val highlightMatrix = Matrix()
                val values = FloatArray(9)

                fun updateShader(
                    shader: BitmapShader,
                    matrix: Matrix,
                    scale: Float,
                    translationX: Float,
                    translationY: Float,
                ) {
                    values[0] = scale
                    values[1] = 0f
                    values[2] = translationX
                    values[3] = 0f
                    values[4] = scale
                    values[5] = translationY
                    values[6] = 0f
                    values[7] = 0f
                    values[8] = 1f
                    matrix.setValues(values)
                    shader.setLocalMatrix(matrix)
                }

                onDrawBehind {
                    drawRect(backgroundColor)

                    val height = size.height.coerceAtLeast(1f)
                    val scale = height / currentArt.sourceHeight.toFloat().coerceAtLeast(1f)
                    val scaledWidth = currentArt.sourceWidth * scale
                    // MIRROR repeats over two source widths. Reducing the Double world coordinate
                    // before Float conversion preserves smooth sub-pixel movement after huge scrolls.
                    val repeatPeriod = max(1f, scaledWidth * 2f)
                    val baseWorld = scene.worldX * WallpaperParallax.IMAGE_RATE.toDouble()
                    val phase = BackgroundWorldMath.positiveModulo(
                        baseWorld,
                        repeatPeriod.toDouble(),
                    ).toFloat()
                    val motionX = WindowsMotionAccent.artworkOffsetX(motion, size.width)
                    val motionY = WindowsMotionAccent.artworkOffsetY(motion, height)

                    // Tiny role-specific phase offsets make the recovered tone layers breathe
                    // together without introducing synthetic shapes over the exact source artwork.
                    val shadowMotion = WindowsMotionAccent.maskLayerOffsetX(
                        motion, size.width, layerIndex = 0,
                    )
                    val primaryMotion = WindowsMotionAccent.maskLayerOffsetX(
                        motion, size.width, layerIndex = 1,
                    )
                    val highlightMotion = WindowsMotionAccent.maskLayerOffsetX(
                        motion, size.width, layerIndex = 2,
                    )
                    val verticalMotion = motionY + WindowsMotionAccent.maskLayerOffsetY(motion, height)

                    updateShader(
                        shadowShader,
                        shadowMatrix,
                        scale,
                        -phase + motionX * 0.45f + shadowMotion,
                        verticalMotion * 0.45f,
                    )
                    updateShader(
                        primaryShader,
                        primaryMatrix,
                        scale,
                        -phase + motionX + primaryMotion,
                        verticalMotion,
                    )
                    updateShader(
                        highlightShader,
                        highlightMatrix,
                        scale,
                        -phase + motionX * 1.12f + highlightMotion,
                        verticalMotion * 1.12f,
                    )

                    highlightPaint.alpha = WindowsMotionAccent.highlightLayerAlpha(motion)

                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        native.drawRect(0f, 0f, size.width, size.height, shadowPaint)
                        native.drawRect(0f, 0f, size.width, size.height, primaryPaint)
                        native.drawRect(0f, 0f, size.width, size.height, highlightPaint)
                    }
                }
            }
        },
    )
}

private fun maskPaint(shader: BitmapShader, color: Int): Paint = Paint(
    Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG,
).apply {
    this.shader = shader
    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
}
