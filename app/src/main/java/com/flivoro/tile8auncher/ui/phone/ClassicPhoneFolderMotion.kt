package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Windows Phone 8.1 folder is a separate presentation from W10M.
 * Microsoft's folder-creation GIF shows the folder unfolding over roughly
 * 350 ms, but does not establish exact tap-to-open or close timings. These
 * values are a launcher fit, kept independent from the W10M motion.
 */
internal object ClassicPhoneFolderMotion {
    const val OPEN_MS = 350
    const val CLOSE_MS = 350
    val clockEasing = FastOutSlowInEasing

    fun remainingDuration(clock: Float, opening: Boolean): Int =
        ((if (opening) OPEN_MS else CLOSE_MS) *
            abs((if (opening) 1f else 0f) - clock.coerceIn(0f, 1f))).roundToInt()

    private fun segment(value: Float, start: Float, end: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun previewOpacity(progress: Float): Float = 1f - segment(progress, .05f, .38f)
    fun emptyGridOpacity(progress: Float): Float = segment(progress, .12f, .48f)

    fun frame(progress: Float): PhoneFolderMotion.Frame {
        val header = segment(progress, .12f, .55f)
        val children = segment(progress, .20f, .70f)
        return PhoneFolderMotion.Frame(
            headerOpacity = header,
            headerOffsetDp = (1f - header) * 6f,
            childrenOpacity = children,
            childrenOffsetDp = (1f - children) * 12f,
            childrenScale = .96f + .04f * children,
        )
    }
}
