package com.flivoro.tile8auncher.ui.components

/**
 * One tiny process-local horizontal coordinate shared by Start and All Apps.
 *
 * Both screens already route their LazyRow scrolling through [elasticHorizontalScroll]. That
 * modifier reports only pixels actually consumed by the list, so vertical navigation and edge
 * rubber-banding never move this coordinate. No Compose state, coroutine, animation clock or
 * per-frame allocation is used here; the existing LazyListState invalidation drives wallpaper
 * redraws and the renderer simply samples the latest value.
 */
internal object SharedWallpaperScroll {
    @Volatile
    private var worldOffsetPx: Double = 0.0

    fun onContentConsumed(consumedX: Float) {
        if (!consumedX.isFinite() || consumedX == 0f) return

        // Nested scroll uses gesture coordinates. Negating consumed X makes the wallpaper world
        // advance positively when the list advances toward content on the right.
        val next = worldOffsetPx - consumedX.toDouble()
        worldOffsetPx = if (next.isFinite()) next else 0.0
    }

    /**
     * The normal non-negative list offsets are deliberately ignored: Start and All Apps must look
     * into the same persistent wallpaper world even while the vertical navigator is mid-gesture.
     * A negative legacy value can only be the launcher's existing entrance displacement, so keep
     * that transient effect without allowing either screen to reset the persistent world position.
     */
    fun effectiveOffset(legacyOffsetPx: Float): Float {
        val legacy = legacyOffsetPx.takeIf { it.isFinite() } ?: 0f
        if (legacy < 0f) return legacy
        val shared = worldOffsetPx.toFloat()
        return if (shared.isFinite()) shared else 0f
    }
}
