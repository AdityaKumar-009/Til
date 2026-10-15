package com.flivoro.tile8auncher.ui.phone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Recreates DiscoLauncher src/styles/pages/tileList.scss's icon placement.
 *
 * Both source icons are 42px circular controls containing a font glyph in a
 * 30px-wide, 16px-high clipped <p> positioned at left:11px / top:13px.
 * Unlike a font glyph, these MDI outlines render consistently across devices.
 * Rotate the clipped glyph region at the source's 35%/47% transform origin.
 */
@Composable
internal fun DiscoSourceArrowGlyph(
    @DrawableRes drawableId: Int,
    classicUnit: Float,
    rotationDegrees: Float = 0f,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Box(
            modifier = Modifier
                .offset(x = (11f * classicUnit).dp, y = (13f * classicUnit).dp)
                .size(width = (30f * classicUnit).dp, height = (16f * classicUnit).dp)
                .graphicsLayer {
                    rotationZ = rotationDegrees
                    transformOrigin = TransformOrigin(.35f, .47f)
                }
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawableId),
                contentDescription = null,
                modifier = Modifier.size((30f * classicUnit).dp),
            )
        }
    }
}
