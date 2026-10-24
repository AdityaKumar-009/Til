package com.flivoro.tile8auncher

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flivoro.tile8auncher.ui.components.BitmapDecodeCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BitmapDecodeCacheTest {
    @Test fun concurrentTilesShareOneDecodeWithoutBlockingPeeksOrOtherImages() {
        val cache = BitmapDecodeCache<Int>(4096)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val other = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requestersReady = CountDownLatch(8)
        val startTogether = CountDownLatch(1)
        val decodes = AtomicInteger()
        val workers = Executors.newFixedThreadPool(10)
        try {
            val tiles = (1..8).map {
                workers.submit<Bitmap?> {
                    requestersReady.countDown()
                    check(startTogether.await(5, SECONDS))
                    cache.getOrDecode(0) {
                        decodes.incrementAndGet()
                        started.countDown()
                        check(release.await(10, SECONDS))
                        bitmap
                    }
                }
            }
            assertTrue(requestersReady.await(5, SECONDS))
            startTogether.countDown()
            assertTrue(started.await(5, SECONDS))
            // A slow content provider must not lock the UI's memory-cache lookup.
            assertNull(workers.submit<Bitmap?> { cache.peek(0) }.get(2, SECONDS))
            assertSame(other, workers.submit<Bitmap?> {
                cache.getOrDecode(1) { other }
            }.get(2, SECONDS))
            release.countDown()
            tiles.forEach { assertSame(bitmap, it.get(5, SECONDS)) }
            assertEquals("Every tile decoded the same wallpaper", 1, decodes.get())
            assertSame(bitmap, cache.peek(0))
        } finally {
            startTogether.countDown()
            release.countDown()
            workers.shutdownNow()
        }
    }

    @Test fun evictionAndFailedLoadsAllowFreshDecodes() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val cache = BitmapDecodeCache<String>(bitmap.allocationByteCount)
        assertNull(cache.getOrDecode("first") { null })
        assertSame(bitmap, cache.getOrDecode("first") { bitmap })
        val second = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        assertSame(second, cache.getOrDecode("second") { second })
        assertNull(cache.peek("first"))
        cache.remove("second")
        assertNull(cache.peek("second"))
        assertSame(bitmap, cache.getOrDecode("first") { bitmap })
        assertFalse("Eviction recycled a bitmap still owned by a visible tile", bitmap.isRecycled)
    }
}
