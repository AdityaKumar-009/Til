package com.flivoro.tile8auncher.ui.components

import android.graphics.Matrix as PlatformMatrix
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.ui.phone.MobileStartMotion
import com.flivoro.tile8auncher.ui.phone.PhoneStartChoreography
import com.flivoro.tile8auncher.ui.phone.PhoneStartMotionFrame
import kotlin.math.roundToInt

/**
 * Borrow a phone compositor for the ACTUAL Desktop Start/All Apps cells.
 * All visible neighbors participate. No second/duplicate tapped tile is ever drawn.
 * Each cell samples the same original motion implementation as PhoneLauncherSurface.
 *
 * Source bounds must be the unmoving, untransformed layout bounds. Geometry is sampled
 * from a single shared launch progress clock, without starting N individual Animatables.
 */
internal fun Modifier.borrowedPhoneScene(
    style: MasterLaunchAnimation?,
    progress: () -> Float,
    source: () -> Rect,
    viewport: () -> Rect,
    selected: Boolean,
    stagger: Float,
    allApps: Boolean,
    density: Float,
): Modifier {
    if (style != MasterLaunchAnimation.PHONE_8 &&
        style != MasterLaunchAnimation.MOBILE_10) return this

    fun sample(): PhoneStartMotionFrame {
        val b = source()
        val vp = viewport()
        val widthCss = (vp.width / density).coerceAtLeast(1f)
        val heightCss = (vp.height / density).coerceAtLeast(1f)
        val duration = SharedLaunchMotion.duration(style, heightCss, allApps)
        val millis = (progress().coerceIn(0f, 1f) * duration).roundToInt()
        val row = ((b.center.y - vp.top) / vp.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return if (style == MasterLaunchAnimation.MOBILE_10) {
            MobileStartMotion.sample(
                exiting = true,
                elapsedMillis = millis,
                row = row,
                centerX = b.center.x,
                centerY = b.center.y,
                viewportCenterX = vp.center.x,
                viewportCenterY = vp.center.y,
                selected = selected,
            )
        } else if (allApps) {
            PhoneStartChoreography.sampleAppListExit(
                elapsedMillis = millis,
                animationIndex = stagger.coerceIn(0f, 1f),
                viewportHeightCssPx = heightCss,
                viewportWidthCssPx = widthCss,
                selected = selected,
                tileLeftCssPx = (b.left - vp.left) / density,
                tileWidthCssPx = (b.width / density).coerceAtLeast(1f),
            )
        } else {
            PhoneStartChoreography.sample(
                mode = LauncherUiMode.PHONE_8,
                exiting = true,
                elapsedMillis = millis,
                mobileRowFraction = row,
                animationIndex = stagger.coerceIn(0f, 1f),
                viewportHeightCssPx = heightCss,
                viewportWidthCssPx = widthCss,
                tileLeftCssPx = (b.left - vp.left) / density,
                tileWidthCssPx = (b.width / density).coerceAtLeast(1f),
                selected = selected,
            )
        }
    }

    if (style == MasterLaunchAnimation.MOBILE_10) {
        return this.graphicsLayer {
            val m = sample()
            alpha = m.alpha
            rotationY = m.rotationY
            translationX = m.translationXPx
            translationY = m.translationYPx
            scaleX = m.scale
            scaleY = m.scale
            cameraDistance = maxOf(900f, 2f * size.width, 2f * size.height)
        }
    }

    // Use the SAME four-corner perspective projection used by real WP8.1 tiles.
    // A generic graphicsLayer rotation misses the shared-page camera and makes
    // the neighboring tiles look independently hinged.
    // The old drawWithContent allocated a Matrix, two FloatArrays and four Pair
    // objects for EVERY tile on EVERY frame. A full Start/All Apps scene can
    // easily allocate hundreds of objects per vsync and stall the UI thread.
    // Cache the transform's scratch storage per physical tile/layer instead.
    return this.graphicsLayer {
        alpha = sample().alpha
    }.drawWithCache {
        val nativeMatrix = PlatformMatrix()
        val from = floatArrayOf(0f, 0f, size.width, 0f,
            size.width, size.height, 0f, size.height)
        val to = FloatArray(8)
        val w = size.width / density
        val h = size.height / density
        onDrawWithContent {
            val b = source()
            val vp = viewport()
            if (b.width <= 1f || b.height <= 1f || vp.width <= 1f || vp.height <= 1f) {
                drawContent()
            } else {
                val m = sample()
                if (m.alpha > .001f) {
                    val l = (b.left - vp.left) / density
                    val t = (b.top - vp.top) / density
                    val pw = vp.width / density
                    val ph = vp.height / density
                    // Manual corner iteration avoids Pair creation and temporary
                    // arrays inside PhoneStartChoreography.projectPlanePoint.
                    for (i in 0..3) {
                        val x = if (i == 1 || i == 2) w else 0f
                        val y = if (i >= 2) h else 0f
                        val point = PhoneStartChoreography.projectPlanePoint(
                            localXPx = x,
                            localYPx = y,
                            elementLeftCssPx = l,
                            elementTopCssPx = t,
                            elementWidthCssPx = w,
                            elementHeightCssPx = h,
                            viewportWidthCssPx = pw,
                            viewportHeightCssPx = ph,
                            motion = m,
                            cameraDistanceCssPx = PhoneStartChoreography.CLASSIC_TILE_PERSPECTIVE_CSS_PX,
                        )
                        to[i * 2] = (point.xCssPx - l) * density
                        to[i * 2 + 1] = (point.yCssPx - t) * density
                    }
                    if (nativeMatrix.setPolyToPoly(from, 0, to, 0, 4)) {
                        val canvas = drawContext.canvas.nativeCanvas
                        val saveCount = canvas.save()
                        canvas.concat(nativeMatrix)
                        drawContent()
                        canvas.restoreToCount(saveCount)
                    } else drawContent()
                }
            }
        }
    }
}
