package com.flivoro.tile8auncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max

/**
 * Shared wallpaper cache for Start and the Windows 8.1 lock surface.
 *
 * The in-memory bitmap prevents a decode every time the launcher is uncovered after screen-off.
 * A small disk preview is also retained so even a cold process can paint the user's selected image
 * immediately instead of briefly flashing the stock/default wallpaper before the full decode lands.
 */
internal object LauncherImageCache {
    private const val MAX_MEMORY_BYTES = 48 * 1024 * 1024
    private const val MAX_PREVIEW_BYTES = 4 * 1024 * 1024
    private const val PREVIEW_MAX_SIDE = 640
    private const val PREVIEW_QUALITY = 86
    private const val FULL_IMAGE_SIDE = 2560
    private const val ACCENT_IMAGE_SIDE = 720
    private const val CACHE_DIR_NAME = "wallpaper_previews"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memory = BitmapDecodeCache<String>(MAX_MEMORY_BYTES)
    private val previews = BitmapDecodeCache<String>(MAX_PREVIEW_BYTES)

    // Cache accent sampling and full wallpaper artwork at distinct resolutions.
    private fun cacheKey(uriString: String, maxSide: Int): String = "$maxSide|$uriString"

    fun peek(uriString: String): Bitmap? =
        memory.peek(cacheKey(uriString, FULL_IMAGE_SIDE))
            ?: memory.peek(cacheKey(uriString, ACCENT_IMAGE_SIDE))

    fun peekOrPreview(context: Context, uriString: String): Bitmap? {
        peek(uriString)?.let { return it }
        return previews.getOrDecode(uriString) {
            runCatching {
                val file = previewFile(context.applicationContext, uriString)
                if (!file.isFile || file.length() <= 0L) return@runCatching null
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                )
            }.getOrNull()
        }
    }

    fun preload(context: Context, uriString: String?, maxSide: Int = 2560) {
        val safe = uriString?.takeIf(String::isNotBlank) ?: return
        if (memory.peek(cacheKey(safe, maxSide)) != null) return
        val appContext = context.applicationContext
        ioScope.launch {
            getOrDecode(appContext, safe, maxSide)
        }
    }

    fun getOrDecode(context: Context, uriString: String, maxSide: Int = 2560): Bitmap? {
        var loadedHere = false
        // A full decode may supply the accent sampler, never the reverse.
        if (maxSide <= ACCENT_IMAGE_SIDE) {
            memory.peek(cacheKey(uriString, FULL_IMAGE_SIDE))?.let { return it }
        }
        val bitmap = memory.getOrDecode(cacheKey(uriString, maxSide)) {
            decodeContentUri(context.applicationContext, uriString, maxSide)?.also { loadedHere = true }
        } ?: return peekOrPreview(context, uriString)
        // Publish the full bitmap before saving its preview; other tiles can already use it.
        if (loadedHere) ensurePreview(context.applicationContext, uriString, bitmap)
        return bitmap
    }

    fun forget(uriString: String?) {
        val safe = uriString?.takeIf(String::isNotBlank) ?: return
        memory.remove(cacheKey(safe, FULL_IMAGE_SIDE))
        memory.remove(cacheKey(safe, ACCENT_IMAGE_SIDE))
        previews.remove(safe)
    }

    /**
     * A user can replace large gallery images repeatedly. Retire only files
     * imported into our own Start-wallpaper directory, never external content.
     * Run cleanup off the UI thread after the replacement has been published.
     */
    fun retireImportedWallpaper(context: Context, uriString: String) {
        forget(uriString)
        val appContext = context.applicationContext
        ioScope.launch {
            runCatching {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    val directory = File(appContext.filesDir, "start_wallpapers").canonicalFile
                    val path = uri.path
                    if (path != null) {
                        val file = File(path).canonicalFile
                        if (file.parentFile == directory &&
                            file.name.startsWith("start-") && file.name.endsWith(".img")) {
                            file.delete()
                        }
                    }
                }
                previewFile(appContext, uriString).delete()
            }
        }
    }

    /**
     * Import a selected gallery/file-provider image into our private persistent
     * files directory. Gallery providers often grant temporary content URIs;
     * storing those directly made wallpapers disappear after an Activity resume,
     * app restart, or provider permission change.
     *
     * Call only from Dispatchers.IO. Never publish the new URI before its bytes
     * are copied and verified as an image.
     */
    fun importWallpaper(context: Context, sourceUri: Uri): String? = runCatching {
        val app = context.applicationContext
        val directory = File(app.filesDir, "start_wallpapers")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create wallpaper directory" }
        val temp = File.createTempFile("import-", ".tmp", directory)
        val target = File(directory, "start-${UUID.randomUUID()}.img")
        try {
            val input = app.contentResolver.openInputStream(sourceUri)
                ?: error("Image provider returned no data")
            input.use { stream ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var copied = 0L
                    while (true) {
                        val size = stream.read(buffer)
                        if (size < 0) break
                        copied += size
                        require(copied <= 48L * 1024L * 1024L) {
                            "Wallpaper image is larger than 48 MB"
                        }
                        output.write(buffer, 0, size)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "Selected file is not a readable image"
            }
            check(temp.renameTo(target)) { "Failed to store wallpaper" }
            Uri.fromFile(target).toString()
        } finally {
            if (temp.exists()) temp.delete()
        }
    }.getOrNull()

    private fun decodeContentUri(
        context: Context,
        uriString: String,
        maxSide: Int,
    ): Bitmap? = runCatching {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > maxSide.coerceAtLeast(512)) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()

    private fun ensurePreview(context: Context, uriString: String, bitmap: Bitmap) {
        val file = previewFile(context, uriString)
        if (file.isFile && file.length() > 0L) return

        runCatching {
            file.parentFile?.mkdirs()
            val longest = max(bitmap.width, bitmap.height).coerceAtLeast(1)
            val preview = if (longest > PREVIEW_MAX_SIDE) {
                val scale = PREVIEW_MAX_SIDE.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }

            FileOutputStream(file).use { output ->
                preview.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, output)
            }
            if (preview !== bitmap && !preview.isRecycled) preview.recycle()
        }
    }

    private fun previewFile(context: Context, uriString: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(File(context.cacheDir, CACHE_DIR_NAME), "$digest.jpg")
    }
}
