package com.flivoro.tile8auncher.features

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri

private const val MOSAIC_NATIVE_SETTINGS = "mosaic.launcher.settings"

/**
 * Only ordinary user-installed packages may be removed through the launcher.
 * Android system apps and launcher-native entries remain manageable via App info.
 */
fun canRequestAppUninstall(context: Context, packageName: String): Boolean {
    if (packageName.isBlank() ||
        packageName == context.packageName ||
        packageName == MOSAIC_NATIVE_SETTINGS
    ) return false

    val info = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
    }.getOrNull() ?: return false
    return info.flags and (ApplicationInfo.FLAG_SYSTEM or
        ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
}

/** Invokes Android's user-confirmed system uninstaller; never deletes silently. */
fun userConfirmedAppUninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", packageName, null)).apply {
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
