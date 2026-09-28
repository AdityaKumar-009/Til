package com.flivoro.tile8auncher.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.features.AppIconStyle
import com.flivoro.tile8auncher.features.IconPackManager
import com.flivoro.tile8auncher.features.LauncherFeatureRuntime
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Returns the already-resolved icon for the currently selected global style.
 *
 * Launch animations must use this instead of AppsRepository.getAppIcon(), otherwise the tile can
 * show a themed/monochrome icon but the first flip frame falls back to the original Android icon.
 * This function never performs PackageManager, disk or bitmap work.
 */
fun cachedAppIconForCurrentStyle(
    context: Context,
    repository: AppsRepository,
    packageName: String?,
): ImageBitmap? {
    val name = packageName ?: return null
    return when (LauncherFeatureStore.appIconStyle(context)) {
        AppIconStyle.DEFAULT ->
            IconPackManager.peekOverride(name) ?: repository.getCachedAppIcon(name)
        AppIconStyle.ANDROID_ADAPTIVE ->
            repository.getCachedAppIcon(name)
        AppIconStyle.WHITE_MONOCHROME ->
            repository.getCachedMonochromeAppIcon(name) ?: repository.getCachedAppIcon(name)
    }
}

private fun allAppsIconTargetPx(context: Context): Int =
    (40f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceIn(72, 160)

private fun cachedAllAppsIconForStyle(
    context: Context,
    repository: AppsRepository,
    packageName: String,
    style: AppIconStyle,
    maxPx: Int,
): ImageBitmap? = when (style) {
    AppIconStyle.DEFAULT ->
        IconPackManager.peekOverride(packageName, maxPx)
            ?: repository.getCachedAllAppsIcon(packageName, maxPx, monochrome = false)
            ?: repository.getCachedAppIcon(packageName)

    AppIconStyle.ANDROID_ADAPTIVE ->
        repository.getCachedAllAppsIcon(packageName, maxPx, monochrome = false)
            ?: repository.getCachedAppIcon(packageName)

    AppIconStyle.WHITE_MONOCHROME ->
        repository.getCachedAllAppsIcon(packageName, maxPx, monochrome = true)
            ?: repository.getCachedMonochromeAppIcon(packageName)
            ?: repository.getCachedAllAppsIcon(packageName, maxPx, monochrome = false)
            ?: repository.getCachedAppIcon(packageName)
}

private suspend fun resolveAllAppsIcon(
    context: Context,
    repository: AppsRepository,
    packageName: String,
    style: AppIconStyle,
    maxPx: Int,
): ImageBitmap? {
    cachedAllAppsIconForStyle(context, repository, packageName, style, maxPx)?.let { return it }

    return when (style) {
        AppIconStyle.DEFAULT -> {
            val override = withContext(Dispatchers.IO) {
                IconPackManager.loadOverride(context.applicationContext, packageName, maxPx)
            }
            override ?: repository.loadAllAppsIcon(packageName, maxPx, monochrome = false)
        }

        AppIconStyle.ANDROID_ADAPTIVE ->
            repository.loadAllAppsIcon(packageName, maxPx, monochrome = false)

        AppIconStyle.WHITE_MONOCHROME ->
            repository.loadAllAppsIcon(packageName, maxPx, monochrome = true)
                ?: repository.loadAllAppsIcon(packageName, maxPx, monochrome = false)
    }
}

/**
 * Display-sized, memory-stable icon path for the Windows 8.1 All Apps surface.
 *
 * This deliberately does not reuse the launch-resolution LRU: horizontally recycled columns
 * should come back from a compact thumbnail cache synchronously instead of briefly showing the
 * fallback glyph and decoding the app again.
 */
@Composable
fun rememberAllAppsIcon(
    repository: AppsRepository,
    packageName: String?,
): ImageBitmap? {
    val context = LocalContext.current
    val iconsRevision = LauncherFeatureRuntime.iconsRevision
    val iconStyle = remember(iconsRevision) {
        LauncherFeatureStore.appIconStyle(context)
    }
    val maxPx = remember(context.resources.displayMetrics.density) {
        allAppsIconTargetPx(context)
    }

    var icon by remember(repository, packageName, iconsRevision, iconStyle, maxPx) {
        mutableStateOf(
            packageName?.let { name ->
                cachedAllAppsIconForStyle(
                    context = context,
                    repository = repository,
                    packageName = name,
                    style = iconStyle,
                    maxPx = maxPx,
                )
            },
        )
    }

    LaunchedEffect(repository, packageName, iconsRevision, iconStyle, maxPx) {
        val name = packageName ?: return@LaunchedEffect
        icon = resolveAllAppsIcon(
            context = context,
            repository = repository,
            packageName = name,
            style = iconStyle,
            maxPx = maxPx,
        )
    }

    return icon
}

/**
 * Warms the entire All Apps thumbnail set off the UI thread. Visible rows still request their
 * own icons immediately, while this sequential pass fills the rest of the compact LRU without
 * causing a PackageManager/bitmap decode burst.
 */
suspend fun preloadAllAppsIcons(
    context: Context,
    repository: AppsRepository,
    packageNames: List<String>,
    style: AppIconStyle,
) {
    val maxPx = allAppsIconTargetPx(context)
    packageNames.distinct().forEach { packageName ->
        resolveAllAppsIcon(
            context = context,
            repository = repository,
            packageName = packageName,
            style = style,
            maxPx = maxPx,
        )
    }
}

/**
 * Returns a cached icon synchronously and schedules a cache miss on bounded background work.
 * User-selected icons/icon packs are resolved first; the existing repository path remains the
 * unchanged fallback, so this feature cannot affect package scanning or launcher motion timing.
 */
@Composable
fun rememberAppIcon(
    repository: AppsRepository,
    packageName: String?,
): ImageBitmap? {
    val context = LocalContext.current
    val iconsRevision = LauncherFeatureRuntime.iconsRevision
    val iconStyle = remember(iconsRevision) {
        LauncherFeatureStore.appIconStyle(context)
    }
    var icon by remember(repository, packageName, iconsRevision, iconStyle) {
        mutableStateOf(
            packageName?.let { name ->
                when (iconStyle) {
                    AppIconStyle.DEFAULT ->
                        IconPackManager.peekOverride(name) ?: repository.getCachedAppIcon(name)
                    AppIconStyle.ANDROID_ADAPTIVE ->
                        repository.getCachedAppIcon(name)
                    AppIconStyle.WHITE_MONOCHROME ->
                        repository.getCachedMonochromeAppIcon(name) ?: repository.getCachedAppIcon(name)
                }
            },
        )
    }

    LaunchedEffect(repository, packageName, iconsRevision, iconStyle) {
        val name = packageName ?: return@LaunchedEffect
        icon = when (iconStyle) {
            AppIconStyle.DEFAULT -> {
                val override = withContext(Dispatchers.IO) {
                    IconPackManager.loadOverride(context.applicationContext, name)
                }
                override ?: repository.loadAppIcon(name)
            }
            AppIconStyle.ANDROID_ADAPTIVE -> repository.loadAppIcon(name)
            AppIconStyle.WHITE_MONOCHROME -> repository.loadMonochromeAppIcon(name)
        }
    }

    return icon
}
