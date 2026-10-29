package com.flivoro.tile8auncher;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Runs in the standalone test APK process, which does not include target APK libraries. */
public class FixtureWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TAP = "com.flivoro.tile8auncher.TEST_WIDGET_TAP";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, content(context, id, context));
    }

    public static RemoteViews content(Context resourcesContext, int id, Context interactionContext) {
        String pkg = resourcesContext.getPackageName();
        RemoteViews views = new RemoteViews(pkg,
            resourcesContext.getResources().getIdentifier("widget_test_content", "layout", pkg));
        views.setTextViewText(resourcesContext.getResources().getIdentifier("widget_test_title", "id", pkg),
            "Live widget " + id);
        views.setOnClickPendingIntent(resourcesContext.getResources().getIdentifier("widget_test_button", "id", pkg),
            PendingIntent.getBroadcast(interactionContext, id,
                new Intent(ACTION_TAP).setPackage("com.flivoro.tile8auncher"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }
}
