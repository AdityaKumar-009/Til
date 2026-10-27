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

    /**
     * A Live Folder expands below its own Start row. When its contents would
     * be mostly below the viewport, pan just far enough to reveal its header
     * and the first child rows, rather than jumping the folder to screen top.
     * Kept pure so actual device/density calculations can be regression tested.
     */
    fun focusScrollTarget(
        currentScrollPx: Int,
        headerTopPx: Float,
        expandedHeightPx: Float,
        viewportHeightPx: Float,
    ): Int {
        if (viewportHeightPx <= 0f || expandedHeightPx <= 0f) return currentScrollPx
        val visibleContents = minOf(expandedHeightPx, viewportHeightPx * .68f)
        val margin = minOf(viewportHeightPx * .04f, 24f)
        val desired = (headerTopPx + visibleContents - viewportHeightPx + margin)
            .toInt().coerceAtLeast(0)
        return maxOf(currentScrollPx, desired)
    }

    /** Each child cascades into view from the folder edge, not one rigid group. */
    fun childFrame(progress: Float, index: Int): Frame {
        val start = (.12f + index.coerceAtLeast(0).coerceAtMost(8) * .028f)
            .coerceAtMost(.35f)
        val part = segment(progress, start, .86f)
        return Frame(
            headerOpacity = 1f,
            headerOffsetDp = 0f,
            childrenOpacity = part,
            childrenOffsetDp = (1f - part) * 14f,
            childrenScale = .94f + .06f * part,
        )
    }

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
