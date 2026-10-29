package com.flivoro.tile8auncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

open class FixtureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, content(context, id))
    }

    companion object {
        const val ACTION_TAP = "com.flivoro.tile8auncher.TEST_WIDGET_TAP"
        fun content(context: Context, id: Int, interactionContext: Context = context): RemoteViews {
            val res = context.resources
            val pkg = context.packageName
            return RemoteViews(pkg, res.getIdentifier("widget_test_content", "layout", pkg)).apply {
                setTextViewText(res.getIdentifier("widget_test_title", "id", pkg), "Live widget $id")
                setOnClickPendingIntent(res.getIdentifier("widget_test_button", "id", pkg),
                    PendingIntent.getBroadcast(interactionContext, id,
                        Intent(ACTION_TAP).setPackage("com.flivoro.tile8auncher"),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
        }
    }
}

class FixtureSmallWidgetProvider : FixtureWidgetProvider()
class FixtureTallWidgetProvider : FixtureWidgetProvider()
