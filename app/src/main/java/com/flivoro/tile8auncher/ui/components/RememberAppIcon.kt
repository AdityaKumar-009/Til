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
import com.flivoro.tile8auncher.features.IconPackManager
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
    var icon by remember(repository, packageName) {
        mutableStateOf(packageName?.let(repository::getCachedAppIcon))
    }

    LaunchedEffect(repository, packageName) {
        val name = packageName ?: return@LaunchedEffect
        val override = withContext(Dispatchers.IO) {
            IconPackManager.loadOverride(context.applicationContext, name)
        }
        icon = override ?: repository.loadAppIcon(name)
    }

    return icon
}
