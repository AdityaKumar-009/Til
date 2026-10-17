package com.flivoro.tile8auncher.ui.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.ui.components.rememberAppIcon

/**
 * One WP8.1 source of truth for app artwork. The icon, optional painted
 * adaptive background and accent are resolved by the same repository that
 * supplies All Apps thumbnails; both Start and the alphabetical list use it.
 */
internal data class Wp81TileVisual(
    val icon: ImageBitmap?,
    val richBackground: ImageBitmap?,
    val accent: Long?,
)

@Composable
internal fun rememberWp81TileVisual(
    repository: AppsRepository,
    packageName: String?,
    enabled: Boolean,
): Wp81TileVisual {
    val fallback = rememberAppIcon(repository, packageName)
    val cachedIcon = remember(repository, packageName, fallback, enabled) {
        if (enabled && packageName != null)
            repository.getCachedWp81NormalizedIcon(packageName)
        else null
    }
    var fittedIcon by remember(repository, packageName, enabled) {
        mutableStateOf(cachedIcon)
    }
    var richBackground by remember(repository, packageName, enabled) {
        mutableStateOf(
            if (enabled && packageName != null)
                repository.getCachedRichDiscoBackground(packageName)
            else null,
        )
    }
    // A successful decode changes fallback's state; then normalize once,
    // off the UI thread. Full legacy icons and sparse foreground glyphs get
    // the same *visible* artwork size.
    LaunchedEffect(repository, packageName, fallback, enabled) {
        if (enabled && packageName != null) {
            fittedIcon = repository.loadWp81NormalizedIcon(packageName)
            richBackground = repository.loadRichDiscoBackground(packageName)
        }
    }
    return Wp81TileVisual(
        icon = if (enabled) fittedIcon ?: fallback else fallback,
        richBackground = if (enabled) richBackground else null,
        accent = packageName?.let(repository::getDiscoTileAccentColor),
    )
}
