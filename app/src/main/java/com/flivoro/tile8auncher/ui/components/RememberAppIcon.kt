package com.flivoro.tile8auncher.ui.components

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
                    AppIconStyle.DEFAULT,
                    AppIconStyle.ANDROID_ADAPTIVE -> repository.getCachedAppIcon(name)
                    AppIconStyle.WHITE_MONOCHROME -> repository.getCachedMonochromeAppIcon(name)
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
