package com.flivoro.tile8auncher.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
 * Recreates DiscoLauncher src/styles/pages/tileList.scss's icon placement.
 *
 * Both source icons are 42px circular controls containing a font glyph in a
 * 30px-wide, 16px-high <p> positioned at left:11px / top:13px.
 * Disco displays a 30px font glyph within this control. The MDI vector must
 * remain 30px tall even though its parent layout region is 16px tall: clipping
 * it to the parent height removes most of its visible arrow, especially after
 * the 45/90/225-degree resize rotations. Compose must allow that overdraw.
 * Rotate the source-sized parent at the original 35%/47% transform origin.
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        // Render the complete MDI 30x30 outline on its own 30x30 layer.
        // A 30x16 graphics layer loses arrow pixels on Android even if explicit
        // clipToBounds() is removed. Disco's <p> is 30x16 at (11,13); a 30px
        // glyph centered inside it starts at y=6. The rotation pivot is the
        // original CSS origin (35% of 30, 47% of 16), transformed into the
        // 30x30 vector layer's local coordinates.
        Image(
            painter = painterResource(drawableId),
            contentDescription = null,
            modifier = Modifier
                .offset(x = (11f * classicUnit).dp, y = (6f * classicUnit).dp)
                .size((30f * classicUnit).dp)
                .graphicsLayer {
                    rotationZ = rotated
                    transformOrigin = TransformOrigin(.35f, (13f + 16f * .47f - 6f) / 30f)
                },
        )
    }
}
