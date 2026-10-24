package com.flivoro.tile8auncher.ui.components

import android.graphics.Bitmap
import android.util.LruCache

/** Bounded bitmap storage; concurrent requests for one image share its decode. */
internal class BitmapDecodeCache<K : Any>(maxBytes: Int) {
    private val memory = object : LruCache<K, Bitmap>(maxBytes) {
        override fun sizeOf(key: K, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)
    }

    // A fixed number of locks avoids retaining every wallpaper URI ever selected.
    // Never hold the memory lock while decoding: the UI must be able to peek immediately.
    private val decodeLocks = Array(16) { Any() }

    fun peek(key: K): Bitmap? = synchronized(memory) { memory.get(key) }

    fun getOrDecode(key: K, decode: () -> Bitmap?): Bitmap? {
        peek(key)?.let { return it }
        val lock = decodeLocks[(key.hashCode() and Int.MAX_VALUE) % decodeLocks.size]
        return synchronized(lock) {
            peek(key) ?: decode()?.also { bitmap ->
                synchronized(memory) { memory.put(key, bitmap) }
            }
        }
    }

    fun remove(key: K) {
        synchronized(memory) { memory.remove(key) }
    }
}
