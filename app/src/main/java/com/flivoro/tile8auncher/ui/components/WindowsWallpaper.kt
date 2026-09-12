package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

private const val WALLPAPER_STYLE_MIN = 0
private const val WALLPAPER_STYLE_MAX = 9
private val DEFAULT_WALLPAPER_BASE = Color(0xFF23053D)
private val DEFAULT_WALLPAPER_ACCENT = Color(0xFF5A148C)
private val DEFAULT_WALLPAPER_HIGHLIGHT = Color(0xFF8824B8)

/**
 * Fixed-viewport Windows 8.1 Start background renderer.
 *
 * There is deliberately no full-screen bitmap and no translated three-screen backing layer.
 * Background color is painted first as a true solid plane; transparent vector artwork is then
 * sampled from an infinite Double-coordinate world. Start and All Apps feed the same world through
 * [StartBackgroundScrollRuntime], so vertical navigation cannot reset or cross-fade wallpaper state.
 *
 * [scrollOffsetPx] is retained as a source-compatible legacy argument for callers from the previous
 * renderer. It is intentionally not sampled: that value blended two independent list positions
 * during Start <-> Apps navigation, which was the source of background jumps and state resets.
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
        val geometry = remember(viewportWidthPx, viewportHeightPx) {
            WindowsBackgroundGeometry(viewportWidthPx, viewportHeightPx)
        }
        val palette = remember(effectiveAccentColor, effectiveHighlightColor) {
            BackgroundPalette.fromAccent(effectiveAccentColor).copy(
                highlight = effectiveHighlightColor,
            )
        }
        val motion = rememberWindowsMotionAccentFrame(
            wallpaperStyle = selectedStyle,
            enabled = enabled,
            viewportWidthPx = viewportWidthPx,
            sceneState = scene,
        )

        Canvas(Modifier.fillMaxSize()) {
            // Windows Personalize background is a separate color plane, not a tint over artwork.
            drawRect(effectiveBaseColor)
            drawWindowsBackgroundArtwork(
                geometry = geometry,
                wallpaperStyle = selectedStyle,
                worldX = scene.worldX,
                palette = palette,
                motion = motion,
            )
        }
    }
}
