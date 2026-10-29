package com.flivoro.tile8auncher.features

import com.flivoro.tile8auncher.data.TileSize
import kotlin.math.ceil
import kotlin.math.min

internal data class WidgetDimensions(val width: Float, val height: Float)

/** AppWidgetProviderInfo resolves XML dimensions to pixels; Compose and options use dp. */
internal fun widgetDimensions(
    minWidthPx: Int,
    minHeightPx: Int,
    density: Float,
    columns: Int = 0,
    rows: Int = 0,
): WidgetDimensions {
    val scale = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return WidgetDimensions(
        if (minWidthPx > 0) minWidthPx / scale else columns.coerceAtLeast(1) * 64f,
        if (minHeightPx > 0) minHeightPx / scale else rows.coerceAtLeast(1) * 64f,
    )
}

/** One physical scale for both axes; small previews are never enlarged to fill a card. */
internal fun fitWidgetPreview(
    dimensions: WidgetDimensions,
    maxWidth: Float,
    maxHeight: Float = 164f,
): WidgetDimensions {
    val width = dimensions.width.coerceAtLeast(1f)
    val height = dimensions.height.coerceAtLeast(1f)
    val scale = min(1f, min(maxWidth.coerceAtLeast(1f) / width,
        maxHeight.coerceAtLeast(1f) / height))
    return WidgetDimensions(width * scale, height * scale)
}

internal fun widgetTileSize(dimensions: WidgetDimensions): TileSize = when {
    dimensions.width <= 120f && dimensions.height <= 120f -> TileSize.MEDIUM
    dimensions.width / dimensions.height.coerceAtLeast(1f) >= 1.6f -> TileSize.WIDE
    else -> TileSize.LARGE
}

internal data class WidgetHostGeometry(val width: Int, val height: Int, val scale: Float)

/** Lay out at a provider's minimum before fitting the complete view into a Metro tile. */
internal fun fitHostedWidget(
    viewportWidth: Int,
    viewportHeight: Int,
    minimumWidth: Int,
    minimumHeight: Int,
): WidgetHostGeometry {
    val width = viewportWidth.coerceAtLeast(1)
    val height = viewportHeight.coerceAtLeast(1)
    val scale = min(1f, min(width.toFloat() / minimumWidth.coerceAtLeast(1),
        height.toFloat() / minimumHeight.coerceAtLeast(1)))
    val contentWidth = ceil(width / scale).toInt()
    val contentHeight = ceil(height / scale).toInt()
    return WidgetHostGeometry(contentWidth, contentHeight,
        min(width.toFloat() / contentWidth, height.toFloat() / contentHeight))
}
