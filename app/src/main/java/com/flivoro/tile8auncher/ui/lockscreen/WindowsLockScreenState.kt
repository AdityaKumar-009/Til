package com.flivoro.tile8auncher.ui.lockscreen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.flivoro.tile8auncher.ui.components.LauncherImageCache

object WindowsLockScreenPreferences {
    private const val PREFS_NAME = "tile8_launcher_prefs_v2"
    private const val LOCK_SCREEN_ENABLED = "windows_81_lock_screen_enabled"
    private const val CAMERA_GESTURE_ENABLED = "windows_81_lock_screen_camera_gesture_enabled"
    private const val WALLPAPER_URI = "windows_81_lock_screen_wallpaper_uri"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(LOCK_SCREEN_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(LOCK_SCREEN_ENABLED, enabled) }
        if (!enabled) WindowsLockScreenRuntime.dismiss()
    }

    fun isCameraGestureEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(CAMERA_GESTURE_ENABLED, true)

    fun setCameraGestureEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(CAMERA_GESTURE_ENABLED, enabled) }
    }


    fun wallpaperUri(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(WALLPAPER_URI, null)
            ?.takeIf(String::isNotBlank)

    fun setWallpaperUri(context: Context, uri: String?) {
        val previous = wallpaperUri(context)
        val normalized = uri?.takeIf(String::isNotBlank)
        if (normalized != null) LauncherImageCache.preload(context, normalized, maxSide = 2560)
        if (previous != null && previous != normalized) LauncherImageCache.forget(previous)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                if (normalized == null) remove(WALLPAPER_URI)
                else putString(WALLPAPER_URI, normalized)
            }
    }
}

object WindowsLockScreenRuntime {
    var pending by mutableStateOf(false)
        private set

    fun arm(context: Context) {
        pending = WindowsLockScreenPreferences.isEnabled(context)
    }

    fun preview() {
        pending = true
    }

    fun dismiss() {
        pending = false
    }
}
