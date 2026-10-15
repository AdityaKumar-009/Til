package com.flivoro.tile8auncher.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.loadEnhancedApps
import com.flivoro.tile8auncher.features.recentlyInstalledApps
import com.flivoro.tile8auncher.ui.components.preloadAllAppsIcons
import com.flivoro.tile8auncher.ui.components.preloadStartIcons
import com.flivoro.tile8auncher.ui.phone.phoneAppsInDisplayOrder
import com.flivoro.tile8auncher.ui.phone.phoneStartupPackages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PreparedLauncher(
    val sections: List<AppSection>,
    val tiles: List<TileModel>,
    val recentPhoneApps: List<AppInfo>?,
)

/** Keep all PackageManager and bitmap work outside the large launcher composable. */
internal suspend fun prepareLauncher(
    context: Context,
    repository: AppsRepository,
    mode: LauncherUiMode,
    iconsRevision: Int,
    onProgress: (String) -> Unit,
): PreparedLauncher = coroutineScope {
    val startedAt = SystemClock.elapsedRealtime()
    val style = LauncherFeatureStore.appIconStyle(context)
    val excluded = LauncherFeatureStore.hiddenPackages(context) +
        LauncherFeatureStore.privatePackages(context)
    onProgress("Loading installed apps")
    // Catalog and saved layout are independent on subsequent launches. A first-run default
    // layout shares the repository's single catalog query instead of issuing a second scan.
    val catalogTask = async(Dispatchers.IO) {
        startupFallback("enumerate installed apps", emptyList()) { repository.getCategorizedApps() }
    }
    val tilesTask = async(Dispatchers.IO) {
        val saved = startupFallback("restore saved tiles", emptyList()) { repository.loadPinnedTiles() }
        startupFallback("refresh tile accents", saved) { repository.refreshAutomaticAppAccentTiles(saved) }
    }
    val sections = catalogTask.await()
    val apps = sections.flatMap(AppSection::apps).distinctBy(AppInfo::packageName)
    val recentTask = async(Dispatchers.IO) {
        if (mode == LauncherUiMode.MOBILE_10 &&
            LauncherFeatureStore.showRecentlyInstalledPhoneApps(context)) {
            try {
                recentlyInstalledApps(loadEnhancedApps(context, apps.filter { it.packageName !in excluded }))
                    .map { it.app }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("MosaicStartup", "Unable to load recent apps", error)
                emptyList()
            }
        } else null
    }
    val pinned = tilesTask.await()
    val startPackages = withContext(Dispatchers.IO) {
        if (mode == LauncherUiMode.DESKTOP) pinned.mapNotNull(TileModel::packageName)
        else phoneStartupPackages(context, mode, pinned, apps)
    }
    val recent = recentTask.await()
    val metadataMs = SystemClock.elapsedRealtime() - startedAt
    val config = context.resources.configuration
    // A screenful plus a scroll-ahead margin, scaled for tablets/landscape. This cost depends
    // on screen size, never on the number of apps installed on the device.
    val rows = (config.screenHeightDp / 40 + 4).coerceAtLeast(20)
    val columns = if (mode == LauncherUiMode.DESKTOP)
        (config.screenWidthDp / 160 + 2).coerceAtLeast(2) else 1
    val plan = startupIconPlan(
        startPackages, if (mode == LauncherUiMode.DESKTOP) apps else phoneAppsInDisplayOrder(apps),
        excluded, recent.orEmpty(), viewportIconCount = rows * columns,
    )
    onProgress("Preparing Start and All Apps")
    coroutineScope {
        if (mode == LauncherUiMode.DESKTOP) {
            launch { preloadStartIcons(context, repository, plan.startPackages, style, iconsRevision) }
            launch { preloadAllAppsIcons(context, repository, plan.allAppsPackages, style, iconsRevision) }
        } else {
            // Phone rows use the same artwork as Start. Do not also decode unused Desktop
            // thumbnails; finish WP8.1 fitted artwork in the same bounded worker batch.
            preloadStartIcons(context, repository, plan.startPackages + plan.allAppsPackages,
                style, iconsRevision, normalizeWp81 = mode == LauncherUiMode.PHONE_8)
        }
    }
    Log.i("MosaicStartup", "Prepared $mode: catalog=${apps.size}, startIcons=${plan.startPackages.size}, " +
        "drawerIcons=${plan.allAppsPackages.size}, metadata=${metadataMs}ms, " +
        "artwork=${SystemClock.elapsedRealtime() - startedAt - metadataMs}ms")
    PreparedLauncher(sections, pinned, recent)
}

private inline fun <T> startupFallback(label: String, fallback: T, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Log.w("MosaicStartup", "Failed to $label", error)
    fallback
}
