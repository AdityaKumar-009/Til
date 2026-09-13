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
        worldOffsetPx = (worldOffsetPx - consumedX.toDouble()).takeIf(Double::isFinite) ?: 0.0
        hasHorizontalInteraction = true
    }

    /**
     * Before the first horizontal interaction, keep the legacy offset so startup/return wallpaper
     * entrance motion remains unchanged. Afterwards use the one shared world position for both
     * Start and All Apps. Negative legacy values are entrance-only offsets and remain honored.
     */
    fun effectiveOffset(legacyOffsetPx: Float): Float {
        val legacy = legacyOffsetPx.takeIf(Float::isFinite) ?: 0f
        if (!hasHorizontalInteraction || legacy < 0f) return legacy
        return worldOffsetPx.toFloat().takeIf(Float::isFinite) ?: 0f
    }
}
