package com.flivoro.tile8auncher.features

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Android layouts can contain fixed dp heights. Measuring those inside an arbitrary WIDE
 * tile clips their footer. Measure a complete responsive layout, then fit it as one view.
 * Android's view transform also maps widget touches back to the unscaled coordinates.
 */
internal class WidgetHostViewport(
    context: Context,
    private val widget: AppWidgetHostView,
    private val provider: AppWidgetProviderInfo,
) : FrameLayout(context) {
    private var geometry = WidgetHostGeometry(1, 1, 1f)
    private var reportedSize: Pair<Int, Int>? = null

    init {
        clipChildren = true
        clipToPadding = true
        addView(widget)
        tag = "widget-host-${widget.appWidgetId}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        geometry = fitHostedWidget(width, height,
            provider.minWidth + widget.paddingLeft + widget.paddingRight,
            provider.minHeight + widget.paddingTop + widget.paddingBottom)
        widget.measure(MeasureSpec.makeMeasureSpec(geometry.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(geometry.height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val (width, height, scale) = geometry
        widget.pivotX = 0f
        widget.pivotY = 0f
        widget.scaleX = scale
        widget.scaleY = scale
        val x = ((measuredWidth - width * scale) / 2f).toInt()
        val y = ((measuredHeight - height * scale) / 2f).toInt()
        widget.layout(x, y, x + width, y + height)

        // Update only when measured dimensions change, never on every recomposition/frame.
        val size = width to height
        if (reportedSize != size) {
            reportedSize = size
            val density = resources.displayMetrics.density
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= 31) {
                    widget.updateAppWidgetSize(options,
                        listOf(SizeF(width / density, height / density)))
                } else {
                    @Suppress("DEPRECATION")
                    widget.updateAppWidgetSize(options, (width / density).toInt(),
                        (height / density).toInt(), (width / density).toInt(), (height / density).toInt())
                }
            }
        }
    }
}

/** Preview RemoteViews also need their native layout space before being reduced. */
internal class WidgetPreviewViewport(
    context: Context,
    private val nativeWidth: Int,
    private val nativeHeight: Int,
) : FrameLayout(context) {
    private var geometry = WidgetHostGeometry(1, 1, 1f)

    // Compose owns selection and press feedback; provider PendingIntents never run here.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        geometry = fitHostedWidget(width, height, nativeWidth, nativeHeight)
        for (i in 0 until childCount) getChildAt(i).measure(
            MeasureSpec.makeMeasureSpec(geometry.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(geometry.height, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (i in 0 until childCount) getChildAt(i).apply {
            pivotX = 0f
            pivotY = 0f
            scaleX = geometry.scale
            scaleY = geometry.scale
            layout(0, 0, geometry.width, geometry.height)
        }
    }
}
