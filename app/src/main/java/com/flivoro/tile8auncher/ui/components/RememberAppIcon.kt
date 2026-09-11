package com.flivoro.tile8auncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.flivoro.tile8auncher.data.AppsRepository

/**
 * Returns a cached icon synchronously and schedules a cache miss on the repository's bounded IO
 * loader. A missing package is deliberately a no-op so placeholder tiles never query the
 * PackageManager during composition.
 */
@Composable
fun rememberAppIcon(
    repository: AppsRepository,
    packageName: String?,
): ImageBitmap? {
    var icon by remember(repository, packageName) {
        mutableStateOf(packageName?.let(repository::getCachedAppIcon))
    }

    LaunchedEffect(repository, packageName) {
        val name = packageName ?: return@LaunchedEffect
        // loadAppIcon owns its IO dispatcher and decode throttling. Awaiting it here suspends this
        // effect without blocking the main thread, and avoids an unnecessary extra dispatcher hop.
        icon = repository.loadAppIcon(name)
    }

    return icon
}
