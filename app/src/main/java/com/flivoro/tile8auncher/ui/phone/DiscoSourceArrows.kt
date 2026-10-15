package com.flivoro.tile8auncher.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * The two MDI arrow vectors used by Disco's WP8.1 round controls.
 *
 * The SVG paths match the Disco icon glyphs, but CSS font text positioning
 * must NOT be applied to Compose Images: it placed the glyph to the right
 * of the button's true center. Keep the original 30-unit glyph scale in a
 * 42-unit button, with its pivot at the glyph center through every rotation.
 */
@Composable
internal fun DiscoSourceArrowGlyph(
    @DrawableRes drawableId: Int,
    classicUnit: Float,
    rotationDegrees: Float = 0f,
) {
    // Disco CSS: .disco-tile-menu-resize-button p {
    //   transition: transform .25s ease-in-out;
    // }
    val rotated by animateFloatAsState(
        targetValue = rotationDegrees,
        animationSpec = tween(
            durationMillis = 250,
            easing = CubicBezierEasing(.42f, 0f, .58f, 1f),
        ),
        label = "disco-source-arrow-rotation",
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawableId),
            contentDescription = null,
            modifier = Modifier
                .size((30f * classicUnit).dp)
                .graphicsLayer {
                    rotationZ = rotated
                    transformOrigin = TransformOrigin.Center
                },
        )
    }
}
