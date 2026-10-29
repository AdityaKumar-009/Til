package com.flivoro.tile8auncher.features

import com.flivoro.tile8auncher.data.TileSize
import org.junit.Assert.*
import org.junit.Test

class WidgetGeometryTest {
    @Test fun providerPixelsBecomeDpAtTheCurrentDensity() {
        assertEquals(WidgetDimensions(180f, 110f), widgetDimensions(540, 330, 3f, 3, 2))
        assertEquals(widgetDimensions(180, 110, 1f), widgetDimensions(540, 330, 3f))
    }

    @Test fun missingLegacyDimensionsUseCellFallbacksWithoutMixingUnits() {
        assertEquals(WidgetDimensions(192f, 128f), widgetDimensions(0, 0, 3f, 3, 2))
        assertEquals(WidgetDimensions(64f, 64f), widgetDimensions(0, 0, 0f))
    }

    @Test fun smallPreviewStaysAtItsNativeSize() {
        assertEquals(WidgetDimensions(60f, 60f), fitWidgetPreview(WidgetDimensions(60f, 60f), 320f))
    }

    @Test fun widePreviewFitsNarrowScreensWithoutChangingShape() {
        val preview = fitWidgetPreview(WidgetDimensions(600f, 120f), 240f)
        assertEquals(240f, preview.width, .01f)
        assertEquals(48f, preview.height, .01f)
    }

    @Test fun tallPreviewIsBoundedAndPreservesItsCompleteShape() {
        val preview = fitWidgetPreview(WidgetDimensions(100f, 400f), 280f)
        assertEquals(164f, preview.height, .01f)
        assertEquals(41f, preview.width, .01f)
    }

    @Test fun tileFootprintDistinguishesSmallWideAndTallWidgets() {
        assertEquals(TileSize.MEDIUM, widgetTileSize(WidgetDimensions(60f, 60f)))
        assertEquals(TileSize.WIDE, widgetTileSize(WidgetDimensions(280f, 100f)))
        assertEquals(TileSize.LARGE, widgetTileSize(WidgetDimensions(180f, 180f)))
        assertEquals(TileSize.LARGE, widgetTileSize(WidgetDimensions(100f, 240f)))
    }

    @Test fun fittingDoesNotCropFixedHeightWidgetContent() {
        val fit = fitHostedWidget(280, 120, 180, 240)
        assertTrue(fit.width >= 180 && fit.height >= 240)
        assertTrue(fit.width * fit.scale <= 280.01f)
        assertTrue(fit.height * fit.scale <= 120.01f)
    }

    @Test fun hostUsesItsActualAvailableAreaWhenTheWidgetFits() {
        assertEquals(WidgetHostGeometry(300, 160, 1f), fitHostedWidget(300, 160, 180, 110))
    }

    @Test fun fittingRemainsInsideBoundsAtFractionalScalesAndAfterResize() {
        for (width in listOf(63, 167, 313)) for (height in listOf(53, 117, 227)) {
            val fit = fitHostedWidget(width, height, 281, 193)
            assertTrue(fit.width >= 281 && fit.height >= 193)
            assertTrue(fit.width * fit.scale <= width + .01f)
            assertTrue(fit.height * fit.scale <= height + .01f)
            assertTrue(fit.scale > 0f && fit.scale <= 1f)
        }
    }
}
