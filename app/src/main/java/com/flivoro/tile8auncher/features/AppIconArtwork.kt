package com.flivoro.tile8auncher.features

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.util.UUID

/** App-owned artwork survives restarts, photo deletion and document-provider permission changes. */
internal object AppIconArtwork {
    internal const val CHOICE_PREFIX = "feature_app_icon_choice_"
    const val DEFAULT = "default"
    const val CUSTOM = "custom"

    fun selected(context: Context, packageName: String): String {
        if (LauncherFeatureStore.customIconUri(context, packageName) == null) return DEFAULT
        return context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CHOICE_PREFIX + packageName, CUSTOM) ?: CUSTOM
    }

    /** Call off the UI thread. Publish only after the entire PNG has been written successfully. */
    fun apply(context: Context, packageName: String, choice: String, bitmap: ImageBitmap?) {
        require(packageName.isNotBlank())
        val previous = LauncherFeatureStore.customIconUri(context, packageName)
        val directory = File(context.filesDir, "app-icons").apply { mkdirs() }
        val file = if (choice == DEFAULT) null else {
            requireNotNull(bitmap) { "Choose an available image" }
            File(directory, "${UUID.randomUUID()}.png").also { output ->
                try {
                    output.outputStream().use {
                        check(bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                } catch (error: Exception) {
                    output.delete()
                    throw error
                }
            }
        }
        try {
            check(context.getSharedPreferences(LauncherFeatureStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .apply {
                    if (file == null) {
                        remove(LauncherFeatureStore.CUSTOM_ICON_PREFIX + packageName)
                        remove(CHOICE_PREFIX + packageName)
                    } else {
                        putString(LauncherFeatureStore.CUSTOM_ICON_PREFIX + packageName, Uri.fromFile(file).toString())
                        putString(CHOICE_PREFIX + packageName, choice)
                    }
                }.commit()) { "Could not save the icon" }
        } catch (error: Exception) {
            file?.delete()
            throw error
        }
        // Never delete user documents or files outside the managed icon directory.
        previous?.let(Uri::parse)?.takeIf { it.scheme == "file" }?.path?.let(::File)?.let {
            if (it.canonicalFile.parentFile == directory.canonicalFile) it.delete()
        }
    }
}
