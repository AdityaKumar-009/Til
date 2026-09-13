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

    @Volatile
    private var hasHorizontalInteraction: Boolean = false

    fun onContentConsumed(consumedX: Float) {
        if (!consumedX.isFinite() || consumedX == 0f) return

        // Nested scroll uses gesture coordinates. Negating consumed X makes the wallpaper world
        // advance positively when the list advances toward content on the right.
        val next = worldOffsetPx - consumedX.toDouble()
        worldOffsetPx = if (next.isFinite()) next else 0.0
        hasHorizontalInteraction = true
    }

    /**
     * Keep the existing pure/entrance offset until the user has actually moved either horizontal
     * list. From that first movement onward, Start and All Apps use exactly one persistent world X.
     * A negative legacy value is an entrance-only displacement and remains honored.
     */
    fun effectiveOffset(legacyOffsetPx: Float): Float {
        val legacy = legacyOffsetPx.takeIf { it.isFinite() } ?: 0f
        if (!hasHorizontalInteraction || legacy < 0f) return legacy
        val shared = worldOffsetPx.toFloat()
        return if (shared.isFinite()) shared else 0f
    }
}
