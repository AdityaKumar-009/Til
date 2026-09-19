package com.flivoro.tile8auncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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
    private const val PREVIEW_MAX_SIDE = 640
    private const val PREVIEW_QUALITY = 86
    private const val CACHE_DIR_NAME = "wallpaper_previews"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val memory = object : LruCache<String, Bitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
    }

    fun peek(uriString: String): Bitmap? = synchronized(memory) {
        memory.get(uriString)
    }

    fun peekOrPreview(context: Context, uriString: String): Bitmap? {
        peek(uriString)?.let { return it }
        return runCatching {
            val file = previewFile(context.applicationContext, uriString)
            if (!file.isFile || file.length() <= 0L) return@runCatching null
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
            )
        }.getOrNull()
    }

    fun preload(context: Context, uriString: String?, maxSide: Int = 2560) {
        val safe = uriString?.takeIf(String::isNotBlank) ?: return
        if (peek(safe) != null) return
        val appContext = context.applicationContext
        ioScope.launch {
            getOrDecode(appContext, safe, maxSide)
        }
    }

    fun getOrDecode(context: Context, uriString: String, maxSide: Int = 2560): Bitmap? {
        peek(uriString)?.let { return it }

        val decoded = decodeContentUri(context.applicationContext, uriString, maxSide)
            ?: return peekOrPreview(context, uriString)

        val finalBitmap = synchronized(memory) {
            memory.get(uriString)?.also {
                if (it !== decoded && !decoded.isRecycled) decoded.recycle()
            } ?: decoded.also { memory.put(uriString, it) }
        }

        ensurePreview(context.applicationContext, uriString, finalBitmap)
        return finalBitmap
    }

    fun forget(uriString: String?) {
        val safe = uriString?.takeIf(String::isNotBlank) ?: return
        synchronized(memory) { memory.remove(safe) }
    }

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
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(File(context.cacheDir, CACHE_DIR_NAME), "$digest.jpg")
    }
}
