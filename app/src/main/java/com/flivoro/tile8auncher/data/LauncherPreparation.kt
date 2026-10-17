package com.flivoro.tile8auncher.data

import android.content.Context
import android.util.Log
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.ui.components.preloadAllAppsIcons
import com.flivoro.tile8auncher.ui.components.preloadStartIcons
import com.flivoro.tile8auncher.ui.phone.phoneAppsInDisplayOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PreparedLauncher(
    val sections: List<AppSection>,
    val tiles: List<TileModel>,
    val recentPhoneApps: List<AppInfo>?,
)

/** Restore saved data first. Artwork and package verification never hold this result hostage. */
internal suspend fun prepareLauncher(
    context: Context,
    repository: AppsRepository,
    packageRevision: Int,
): PreparedLauncher = withContext(Dispatchers.IO) {
    val sections = startupFallback("restore app catalog", emptyList()) { repository.loadInitialCatalog(packageRevision) }
    val saved = startupFallback("restore saved tiles", emptyList()) { repository.loadPinnedTiles() }
    val pinned = startupFallback("share pinned apps", saved) {
        SharedStartWorkspace.migrate(context, repository, saved, sections.flatMap(AppSection::apps))
    }
    PreparedLauncher(sections, pinned, null)
}

internal suspend fun warmLauncherArtwork(
    context: Context,
    repository: AppsRepository,
    mode: LauncherUiMode,
    iconsRevision: Int,
    prepared: PreparedLauncher,
) = coroutineScope {
    val style = LauncherFeatureStore.appIconStyle(context)
    val excluded = LauncherFeatureStore.hiddenPackages(context) + LauncherFeatureStore.privatePackages(context)
    val apps = prepared.sections.flatMap(AppSection::apps).distinctBy(AppInfo::packageName)
    val startPackages = prepared.tiles.mapNotNull(TileModel::packageName)
    val recent = prepared.recentPhoneApps
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
    // Only the opening viewport is proactively warmed. Lazy rows request subsequent icons.
}

private inline fun <T> startupFallback(label: String, fallback: T, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Log.w("MosaicStartup", "Failed to $label", error)
    fallback
}
