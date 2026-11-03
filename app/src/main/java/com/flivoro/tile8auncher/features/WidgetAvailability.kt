package com.flivoro.tile8auncher.features

import android.appwidget.AppWidgetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.flivoro.tile8auncher.PackageCatalogUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Query only for an open app menu, off the scrolling/composition thread. */
@Composable
internal fun rememberAppHasWidgets(packageName: String): Boolean {
    val context = LocalContext.current.applicationContext
    val packageRevision = PackageCatalogUpdates.revision
    val providerRevision = LauncherWidgetHost.providersRevision
    return key(context, packageName, packageRevision, providerRevision) {
        val available by produceState(false) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    AppWidgetManager.getInstance(context).installedProviders.any {
                        it.provider.packageName == packageName
                    }
                }.getOrDefault(false)
            }
        }
        available
    }
}
