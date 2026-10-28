package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Windows 10 Mobile Live Folders expand in the Start grid, not into a dialog.
 *
 * One normalized animation clock is used by the insertion space and by these
 * child layers. This keeps interruptions, reversals, and surrounding tile
 * displacement coherent even if the user taps while a folder is moving.
 *
 * The timebase eases in and out in either direction; each layer derives its
 * travel from that same interruptible clock. These fits are not measured
 * Microsoft constants.
 */
internal object PhoneFolderMotion {
    const val OPEN_MS = 380
    const val CLOSE_MS = 420
    val clockEasing = CubicBezierEasing(.25f, 0f, .25f, 1f)

    // The clock itself is eased toward either endpoint. Easing this value a
    // second time would make a reversed collapse accelerate into its end.
    fun spaceProgress(clock: Float): Float = clock.coerceIn(0f, 1f)

    // An interrupted quarter-open folder must not take a whole closing duration.
    fun remainingDuration(clock: Float, opening: Boolean): Int =
        ((if (opening) OPEN_MS else CLOSE_MS) *
            abs((if (opening) 1f else 0f) - clock.coerceIn(0f, 1f))).roundToInt()

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

    /** Brief source-plate compression, followed by an upright collapse target. */
    internal data class SourceFrame(
        val alpha: Float,
        val scale: Float,
        val rotationX: Float,
        val offsetDp: Float,
    )

    fun sourceFrame(progress: Float): SourceFrame {
        val p = progress.coerceIn(0f, 1f)
        // The actual folder plate remains in Start. Its MINIATURE content
        // vanishes separately while the physical plate gives a brief press
        // response. Returning or interrupting retraces the same curve.
        val pulse = smoothSegment(p, 0f, .12f) * (1f - smoothSegment(p, .12f, .38f))
        return SourceFrame(
            alpha = 1f,
            scale = 1f - .04f * pulse,
            rotationX = 0f,
            offsetDp = 0f,
        )
    }

    fun previewOpacity(clock: Float): Float = 1f - smoothSegment(clock, .05f, .32f)
    fun collapseOpacity(clock: Float): Float = smoothSegment(clock, .25f, .60f)

    /** Shared by the miniature renderer and the outgoing/returning child tiles. */
    fun previewBounds(slots: List<PhoneTileSlot>, width: Float, height: Float): List<Rect> {
        val rows = (slots.maxOfOrNull { it.row + it.rows } ?: 1).coerceAtLeast(1)
        val unit = minOf((width - 12f) / 4f, (height - 12f) / rows).coerceAtLeast(1f)
        val gap = minOf(unit * .07f, 2f)
        val left = (width - unit * 4f) / 2f
        val top = (height - unit * rows) / 2f
        return slots.map { slot ->
            val x = left + unit * slot.column
            val y = top + unit * slot.row
            Rect(x, y, x + (unit * slot.columns - gap).coerceAtLeast(1f),
                y + (unit * slot.rows - gap).coerceAtLeast(1f))
        }
    }

    data class ChildTransform(
        val alpha: Float,
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
    )

    /** Travel between the miniature's real rectangle and the child's full tile. */
    fun childTransform(clock: Float, index: Int, origin: Rect, target: Rect): ChildTransform {
        val delay = index.coerceIn(0, 8) * .018f
        val travel = spaceProgress(segment(clock, delay, 1f))
        val remaining = 1f - travel
        return ChildTransform(
            alpha = 1f - previewOpacity(clock),
            scaleX = 1f + (origin.width / target.width.coerceAtLeast(1f) - 1f) * remaining,
            scaleY = 1f + (origin.height / target.height.coerceAtLeast(1f) - 1f) * remaining,
            translationX = (origin.left - target.left) * remaining,
            translationY = (origin.top - target.top) * remaining,
        )
    }

    /** Keep the open slot immediately below its tile, even beside a tall neighbor. */
    fun needsInlineDisplacement(
        slotRow: Int, slotRows: Int, boundaryRow: Int,
        isFolderSource: Boolean,
    ): Boolean = !isFolderSource && (slotRow >= boundaryRow ||
        (slotRow < boundaryRow && slotRow + slotRows > boundaryRow))

    fun overlappingRows(slots: List<PhoneTileSlot>, sourceIndex: Int, boundaryRow: Int): Int =
        slots.mapIndexedNotNull { index, slot ->
            if (index != sourceIndex && slot.row < boundaryRow &&
                slot.row + slot.rows > boundaryRow) boundaryRow - slot.row else null
        }.maxOrNull() ?: 0

    private fun segment(progress: Float, start: Float, end: Float): Float =
        ((progress.coerceIn(0f, 1f) - start) / (end - start)).coerceIn(0f, 1f)

    private fun smoothSegment(progress: Float, start: Float, end: Float): Float {
        val fraction = segment(progress, start, end)
        return fraction * fraction * (3f - 2f * fraction)
    }

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

    fun frame(progress: Float): Frame {
        val header = smoothSegment(progress, .08f, .46f)
        val child = smoothSegment(progress, .18f, .82f)
        return Frame(
            headerOpacity = header,
            headerOffsetDp = (1f - header) * 10f,
            childrenOpacity = child,
            childrenOffsetDp = (1f - child) * 16f,
            childrenScale = .96f + .04f * child,
        )
    }
}
