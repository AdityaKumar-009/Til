package com.flivoro.tile8auncher.features

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flivoro.tile8auncher.PackageCatalogUpdates
import java.util.concurrent.Executors

/** One service connection per visible Activity; scrolling is not a host lifecycle. */
object LauncherWidgetHost {
    const val HOST_ID = 0x4D4F53
    private var host: RetainedWidgetHost? = null
    private val observers = mutableMapOf<Lifecycle, LifecycleEventObserver>()
    private val started = mutableSetOf<Lifecycle>()
    internal var providersRevision by mutableIntStateOf(0)
        private set

    @Synchronized fun get(context: Context): AppWidgetHost =
        host ?: RetainedWidgetHost(context.applicationContext).also { host = it }

    internal fun observe(context: Context, lifecycle: Lifecycle) {
        if (lifecycle in observers || lifecycle.currentState == Lifecycle.State.DESTROYED) return
        val widgetHost = get(context)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (started.isEmpty()) runCatching { widgetHost.startListening() }
                    started += lifecycle
                }
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                    if (started.remove(lifecycle) && started.isEmpty()) {
                        runCatching { widgetHost.stopListening() }
                    }
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        observers.remove(lifecycle)?.let(lifecycle::removeObserver)
                    }
                }
                else -> Unit
            }
        }
        observers[lifecycle] = observer
        // addObserver catches up with an already-started owner immediately.
        lifecycle.addObserver(observer)
    }

    internal fun view(context: Context, id: Int, info: AppWidgetProviderInfo): RetainedWidgetView =
        (get(context) as RetainedWidgetHost).view(context, id, info)

    internal fun provider(context: Context, id: Int): AppWidgetProviderInfo? =
        (get(context) as RetainedWidgetHost).provider(context, id)

    private class RetainedWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        private data class Entry(val configuration: Configuration, val view: RetainedWidgetView)
        private val views = mutableMapOf<Int, Entry>()
        private val providers = mutableMapOf<Int, AppWidgetProviderInfo?>()
        private var metadataRevision: Pair<Int, Int>? = null
        // RemoteViews inflation/reapplication must not block the scrolling UI thread.
        private val inflater = Executors.newFixedThreadPool(2)

        override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
            RetainedWidgetView(context).apply { setExecutor(inflater) }

        fun provider(context: Context, id: Int): AppWidgetProviderInfo? {
            val revision = PackageCatalogUpdates.revision to providersRevision
            if (metadataRevision != revision) {
                providers.clear()
                metadataRevision = revision
            }
            if (!providers.containsKey(id)) {
                providers[id] = runCatching {
                    AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
                }.getOrNull()
            }
            return providers[id]
        }

        fun view(context: Context, id: Int, info: AppWidgetProviderInfo): RetainedWidgetView {
            val configuration = context.resources.configuration
            val entry = views[id]
            if (entry != null && entry.configuration == configuration &&
                entry.view.appWidgetInfo?.provider == info.provider) return entry.view
            // Application context prevents retained views from retaining a destroyed Activity.
            val view = createView(context.applicationContext, id, info) as RetainedWidgetView
            views[id] = Entry(Configuration(configuration), view)
            return view
        }

        override fun deleteAppWidgetId(id: Int) {
            views.remove(id)
            providers.remove(id)
            super.deleteAppWidgetId(id)
        }

        override fun onProvidersChanged() { providersRevision++ }
    }
}

internal class RetainedWidgetView(context: Context) : AppWidgetHostView(context) {
    internal var reportedSize: Pair<Int, Int>? = null
}
