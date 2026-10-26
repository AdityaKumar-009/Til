package com.flivoro.tile8auncher.ui.phone

/**
 * Windows 10 Mobile Live Folders expand in the Start grid, not into a dialog.
 *
 * One normalized animation clock is used by the insertion space and by these
 * child layers. This keeps interruptions, reversals, and surrounding tile
 * displacement coherent even if the user taps while a folder is moving.
 *
 * Durations/easing live in PhoneStartGrid; these values only describe the
 * relative reveal order. They are not presented as measured Microsoft constants.
 */
internal object PhoneFolderMotion {
    internal data class Frame(
        val headerOpacity: Float,
        val headerOffsetDp: Float,
        val childrenOpacity: Float,
        val childrenOffsetDp: Float,
        val childrenScale: Float,
    ) {
        companion object {
            val Visible = Frame(1f, 0f, 1f, 0f, 1f)
        }
    }

    private fun segment(progress: Float, start: Float, end: Float): Float =
        ((progress.coerceIn(0f, 1f) - start) / (end - start)).coerceIn(0f, 1f)

    fun frame(progress: Float): Frame {
        val header = segment(progress, .08f, .46f)
        val child = segment(progress, .18f, .82f)
        return Frame(
            headerOpacity = header,
            headerOffsetDp = (1f - header) * 10f,
            childrenOpacity = child,
            childrenOffsetDp = (1f - child) * 16f,
            childrenScale = .96f + .04f * child,
        )
    }
}
