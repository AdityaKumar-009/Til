package com.flivoro.tile8auncher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import com.flivoro.tile8auncher.ui.theme.WindowsColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AppsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tile8_launcher_prefs_v2", Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager

    /**
     * Package icons can be surprisingly large (adaptive icons are often 512px or more), so
     * bound this cache by its approximate ARGB byte cost instead of by item count. Access to
     * Android's LruCache is guarded because icons are read and populated from IO coroutines
     * while the launcher may read the cache on the main thread.
     */
    private val iconCache = object : LruCache<String, ImageBitmap>(ICON_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * BYTES_PER_PIXEL
            return bytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
    private val iconCacheLock = Any()
    private val monochromeIconCache = object : LruCache<String, ImageBitmap>(ICON_CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            val bytes = value.width.toLong() * value.height.toLong() * BYTES_PER_PIXEL
            return bytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
    }
    private val monochromeIconCacheLock = Any()

    /**
     * All Apps deliberately uses display-sized thumbnails instead of the launch-resolution cache.
     * A 40dp list icon should not consume the same ~0.5MB entry as a launch overlay icon. Keeping
     * these thumbnails in a separate, larger LRU prevents horizontal LazyRow recycling from
     * evicting/re-decoding the icons the user just saw.
     */
    private val allAppsThumbnailCache =
        object : LruCache<String, ImageBitmap>(ALL_APPS_ICON_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: ImageBitmap): Int {
                val bytes = value.width.toLong() * value.height.toLong() * BYTES_PER_PIXEL
                return bytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            }
        }
    private val allAppsThumbnailCacheLock = Any()
    private val inFlightAllAppsThumbnailLoads =
        ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val failedAllAppsThumbnailKeys = ConcurrentHashMap.newKeySet<String>()

    private val appAccentCache = ConcurrentHashMap<String, Long>()
    private val failedIcons = ConcurrentHashMap.newKeySet<String>()
    private val iconLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightIconLoads = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val inFlightMonochromeLoads = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    // PackageManager and bitmap decoding are both CPU/Binder-heavy. A screenful of Compose
    // icon requests should not fan out into dozens of simultaneous decodes on low-end phones.
    private val iconDecodePermits = Semaphore(ICON_DECODE_CONCURRENCY)
    private val allAppsDecodePermits = Semaphore(ALL_APPS_ICON_DECODE_CONCURRENCY)

    // The largest app icon currently rendered by the launcher is ~104 dp during the launch
    // overlay. Keeping a small guard above that preserves visual fidelity while preventing a
    // 512/1024 px source icon from consuming megabytes in the cache and causing GC churn.
    private val maxCachedIconPx: Int =
        (ICON_CACHE_MAX_DP * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceIn(ICON_CACHE_MIN_PX, ICON_CACHE_MAX_PX)

    @Volatile
    private var installedAppsCache: List<AppInfo>? = null
    private val installedAppsCacheLock = Any()

    @Volatile
    private var categorizedAppsCache: List<AppSection>? = null
    private val categorizedAppsCacheLock = Any()

    /**
     * Returns the launcher-visible apps from one process-local PackageManager scan.
     *
     * Previously startup could run this scan concurrently from repository initialization,
     * All Apps loading and default-tile creation. On slower devices those Binder/resource calls
     * competed with first-frame work. The immutable result is now shared for this repository.
     */
    fun getInstalledApps(): List<AppInfo> {
        installedAppsCache?.let { return it }

        return synchronized(installedAppsCacheLock) {
            installedAppsCache?.let { return@synchronized it }

            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val locale = Locale.getDefault()
            val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
            val apps = resolveInfoList.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(packageManager).toString()
                val activityName = resolveInfo.activityInfo.name

                // firstInstallTime is not consumed by the launcher UI. Avoid an additional
                // getPackageInfo() Binder call for every installed app during cold start.
                AppInfo(
                    label = label,
                    packageName = pkg,
                    activityName = activityName,
                    firstInstallTime = 0L,
                )
            }.sortedBy { it.label.lowercase(locale) }

            installedAppsCache = apps
            apps
        }
    }

    fun getCategorizedApps(): List<AppSection> {
        categorizedAppsCache?.let { return it }

        return synchronized(categorizedAppsCacheLock) {
            categorizedAppsCache?.let { return@synchronized it }

            val locale = Locale.getDefault()
            val groups = getInstalledApps().groupBy { app ->
                val firstChar = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
                if (firstChar in 'A'..'Z') firstChar.toString() else "#"
            }
            val sections = groups.map { (letter, sectionApps) ->
                AppSection(
                    letter = letter,
                    apps = sectionApps.sortedBy { it.label.lowercase(locale) },
                )
            }.sortedWith { a, b ->
                when {
                    a.letter == "#" -> 1
                    b.letter == "#" -> -1
                    else -> a.letter.compareTo(b.letter)
                }
            }

            categorizedAppsCache = sections
            sections
        }
    }

    /** Returns an icon only when it is already in memory; this never touches PackageManager. */
    fun getCachedAppIcon(packageName: String): ImageBitmap? {
        synchronized(iconCacheLock) {
            return iconCache.get(packageName)
        }
    }

    /**
     * Loads and caches an icon without blocking the caller's thread. Concurrent requests for
     * the same package share one decode, while total decode concurrency is deliberately bounded
     * to protect animation frames from CPU, Binder and GC bursts on slower devices.
     */
    suspend fun loadAppIcon(packageName: String): ImageBitmap? {
        getCachedAppIcon(packageName)?.let { return it }
        if (failedIcons.contains(packageName)) return null

        val candidate = iconLoadScope.async(start = CoroutineStart.LAZY) {
            try {
                iconDecodePermits.withPermit {
                    decodeAndCacheAppIcon(packageName)
                }
            } finally {
                inFlightIconLoads.remove(packageName)
            }
        }
        val active = inFlightIconLoads.putIfAbsent(packageName, candidate)
        if (active == null) {
            candidate.start()
        } else {
            candidate.cancel()
        }
        return (active ?: candidate).await()
    }

    /**
     * Synchronous callers (notably a tile/app tap starting the launch overlay) must never perform
     * PackageManager or bitmap work on the main thread. A cache miss simply uses the existing
     * launcher fallback icon while the normal composable loader fills the cache asynchronously.
     */
    fun getAppIcon(packageName: String): ImageBitmap? = getCachedAppIcon(packageName)

    fun getCachedMonochromeAppIcon(packageName: String): ImageBitmap? {
        synchronized(monochromeIconCacheLock) {
            return monochromeIconCache.get(packageName)
        }
    }

    suspend fun loadMonochromeAppIcon(packageName: String): ImageBitmap? {
        getCachedMonochromeAppIcon(packageName)?.let { return it }

        val candidate = iconLoadScope.async(start = CoroutineStart.LAZY) {
            try {
                iconDecodePermits.withPermit {
                    decodeAndCacheMonochromeAppIcon(packageName)
                }
            } finally {
                inFlightMonochromeLoads.remove(packageName)
            }
        }
        val active = inFlightMonochromeLoads.putIfAbsent(packageName, candidate)
        if (active == null) candidate.start() else candidate.cancel()
        return (active ?: candidate).await()
    }

    fun getCachedAllAppsIcon(
        packageName: String,
        maxPx: Int,
        monochrome: Boolean,
    ): ImageBitmap? {
        val safePx = maxPx.coerceIn(ALL_APPS_ICON_MIN_PX, ALL_APPS_ICON_MAX_PX)
        val key = allAppsThumbnailKey(packageName, safePx, monochrome)
        synchronized(allAppsThumbnailCacheLock) {
            return allAppsThumbnailCache.get(key)
        }
    }

    suspend fun loadAllAppsIcon(
        packageName: String,
        maxPx: Int,
        monochrome: Boolean,
    ): ImageBitmap? {
        val safePx = maxPx.coerceIn(ALL_APPS_ICON_MIN_PX, ALL_APPS_ICON_MAX_PX)
        getCachedAllAppsIcon(packageName, safePx, monochrome)?.let { return it }

        val key = allAppsThumbnailKey(packageName, safePx, monochrome)
        if (failedAllAppsThumbnailKeys.contains(key)) return null

        val candidate = iconLoadScope.async(start = CoroutineStart.LAZY) {
            try {
                allAppsDecodePermits.withPermit {
                    getCachedAllAppsIcon(packageName, safePx, monochrome)
                        ?: decodeAndCacheAllAppsIcon(packageName, safePx, monochrome)
                }
            } finally {
                inFlightAllAppsThumbnailLoads.remove(key)
            }
        }
        val active = inFlightAllAppsThumbnailLoads.putIfAbsent(key, candidate)
        if (active == null) candidate.start() else candidate.cancel()
        return (active ?: candidate).await()
    }

    private fun decodeAndCacheAllAppsIcon(
        packageName: String,
        maxPx: Int,
        monochrome: Boolean,
    ): ImageBitmap? {
        val key = allAppsThumbnailKey(packageName, maxPx, monochrome)
        val image = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val bitmap = if (monochrome) {
                // Match the older All Apps monochrome path that produced clean glyphs: resolve the
                // high-resolution themed/smart glyph first, then downscale. Crucially, the UI does
                // not recolor a normal-icon fallback anymore, so an extraction miss can never turn
                // the entire adaptive plate into a white square/circle.
                drawableToMonochromeBitmap(drawable).downscaleToMaxPx(maxPx)
            } else {
                drawableToBitmap(drawable, maxPx)
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }

        if (image != null) {
            failedAllAppsThumbnailKeys.remove(key)
            synchronized(allAppsThumbnailCacheLock) {
                allAppsThumbnailCache.put(key, image)
            }
        } else {
            failedAllAppsThumbnailKeys.add(key)
        }
        return image
    }

    private fun allAppsThumbnailKey(
        packageName: String,
        maxPx: Int,
        monochrome: Boolean,
    ): String = "${if (monochrome) "mono" else "normal"}:$packageName:$maxPx"

    fun getCachedAppAccentColor(packageName: String): Long? = appAccentCache[packageName]

    suspend fun loadAppAccentColor(packageName: String): Long {
        appAccentCache[packageName]?.let { return it }
        loadAppIcon(packageName)
        return appAccentCache[packageName] ?: WindowsColors.Purple
    }

    private fun decodeAndCacheAppIcon(packageName: String): ImageBitmap? {
        getCachedAppIcon(packageName)?.let { return it }
        if (failedIcons.contains(packageName)) return null

        val bitmap = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val androidBitmap = drawableToBitmap(drawable)
            appAccentCache[packageName] = extractAppAccentColor(androidBitmap)
            androidBitmap.asImageBitmap()
        } catch (e: Exception) {
            failedIcons.add(packageName)
            null
        }
        if (bitmap != null) {
            synchronized(iconCacheLock) {
                iconCache.put(packageName, bitmap)
            }
        } else {
            failedIcons.add(packageName)
        }
        return bitmap
    }

    private fun decodeAndCacheMonochromeAppIcon(packageName: String): ImageBitmap? {
        getCachedMonochromeAppIcon(packageName)?.let { return it }
        return try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToMonochromeBitmap(drawable).asImageBitmap()
            synchronized(monochromeIconCacheLock) {
                monochromeIconCache.put(packageName, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToMonochromeBitmap(drawable: Drawable): Bitmap =
        drawableToMonochromeBitmap(drawable, maxCachedIconPx)

    private fun Bitmap.downscaleToMaxPx(targetPx: Int): Bitmap {
        val safeTarget = targetPx.coerceAtLeast(1)
        val longestSide = max(width, height).coerceAtLeast(1)
        if (longestSide <= safeTarget) return this

        val scale = safeTarget.toFloat() / longestSide.toFloat()
        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
        if (scaled !== this && !isRecycled) recycle()
        return scaled
    }

    private fun drawableToMonochromeBitmap(
        drawable: Drawable,
        targetPx: Int,
    ): Bitmap {
        // Android 13+ exposes the exact monochrome layer an app designed for themed icons.
        // Use it whenever the app actually supplies one.
        if (drawable is AdaptiveIconDrawable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawable.monochrome?.let { monochrome ->
                // Render the app-supplied monochrome artwork itself. Wrapping this layer back
                // inside a new AdaptiveIconDrawable applies the adaptive outer mask as visible
                // alpha and turns many themed icons into rounded/square plates. The raw
                // monochrome layer is already the intended glyph mask, with vector AA intact.
                return renderTintedDrawable(monochrome, Color.WHITE, targetPx)
            }
        }

        // Many apps (and several OEM-packaged apps) do not publish a monochrome layer. Tinting
        // their adaptive foreground blindly turns the complete icon plate into a white square or
        // circle. Instead render the normal icon, identify its dominant plate/background, remove
        // that plate, and keep only contrasting logo/detail pixels as the white glyph.
        val original = renderDrawableAtResolution(drawable, targetPx)
        val glyph = createSmartWhiteGlyph(original)
        if (glyph != null) {
            if (glyph !== original && !original.isRecycled) original.recycle()
            return glyph
        }
        return original
    }

    private fun renderTintedDrawable(
        drawable: Drawable,
        tint: Int,
        targetPx: Int = maxCachedIconPx,
    ): Bitmap {
        val safeTarget = targetPx.coerceAtLeast(1)
        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else safeTarget
        val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else safeTarget
        val longestSide = maxOf(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
        // Always render vector monochrome artwork at the requested cache resolution. This keeps
        // the app-authored curves anti-aliased and avoids reconstructing a border from the
        // adaptive mask.
        val scale = safeTarget.toFloat() / longestSide.toFloat()
        val width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val tinted = (drawable.constantState?.newDrawable() ?: drawable).mutate()
            tinted.setTint(tint)
            tinted.setBounds(0, 0, width, height)
            tinted.draw(canvas)
        }
    }

    private fun renderDrawableHighResolution(drawable: Drawable): Bitmap =
        renderDrawableAtResolution(drawable, maxCachedIconPx)

    private fun renderDrawableAtResolution(
        drawable: Drawable,
        targetPx: Int,
    ): Bitmap {
        val safeTarget = targetPx.coerceAtLeast(1)
        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else safeTarget
        val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else safeTarget
        val longestSide = maxOf(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
        val scale = safeTarget.toFloat() / longestSide.toFloat()
        val width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val copy = drawable.mutate()
            copy.setBounds(0, 0, width, height)
            copy.draw(canvas)
        }
    }

    private fun refineWhiteGlyphMask(mask: Bitmap): Bitmap? {
        val width = mask.width
        val height = mask.height
        if (width <= 2 || height <= 2) return mask

        val area = width * height
        val pixels = IntArray(area)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        val labels = IntArray(area)
        val queue = IntArray(area)
        data class Component(
            var count: Int = 0,
            var minX: Int = Int.MAX_VALUE,
            var minY: Int = Int.MAX_VALUE,
            var maxX: Int = Int.MIN_VALUE,
            var maxY: Int = Int.MIN_VALUE,
        )
        val components = mutableListOf<Component>()
        var nextLabel = 1

        fun pushIfEligible(index: Int, label: Int, tailRef: IntArray): Int {
            if (index !in pixels.indices || labels[index] != 0 || Color.alpha(pixels[index]) < 28) {
                return tailRef[0]
            }
            labels[index] = label
            queue[tailRef[0]] = index
            tailRef[0]++
            return tailRef[0]
        }

        for (start in pixels.indices) {
            if (labels[start] != 0 || Color.alpha(pixels[start]) < 28) continue
            val component = Component()
            var head = 0
            val tailRef = intArrayOf(0)
            labels[start] = nextLabel
            queue[tailRef[0]++] = start

            while (head < tailRef[0]) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                component.count++
                component.minX = min(component.minX, x)
                component.maxX = max(component.maxX, x)
                component.minY = min(component.minY, y)
                component.maxY = max(component.maxY, y)

                if (x > 0) pushIfEligible(index - 1, nextLabel, tailRef)
                if (x + 1 < width) pushIfEligible(index + 1, nextLabel, tailRef)
                if (y > 0) pushIfEligible(index - width, nextLabel, tailRef)
                if (y + 1 < height) pushIfEligible(index + width, nextLabel, tailRef)
            }
            components += component
            nextLabel++
        }

        if (components.isEmpty()) return null
        val edgeMarginX = max(1, width / 32)
        val edgeMarginY = max(1, height / 32)
        val remove = BooleanArray(components.size + 1)

        components.forEachIndexed { index, component ->
            val componentWidth = (component.maxX - component.minX + 1).coerceAtLeast(1)
            val componentHeight = (component.maxY - component.minY + 1).coerceAtLeast(1)
            val longSide = max(componentWidth, componentHeight)
            val shortSide = min(componentWidth, componentHeight)
            val aspect = longSide.toFloat() / shortSide.toFloat()
            val touchesOuterEdge =
                component.minX <= edgeMarginX ||
                    component.maxX >= width - 1 - edgeMarginX ||
                    component.minY <= edgeMarginY ||
                    component.maxY >= height - 1 - edgeMarginY

            // OEM/adaptive plates often leave a faint rim or highlight after color separation.
            // Long thin strips and small edge-connected fragments are plate residue, not the logo.
            val longThinResidue =
                aspect >= 4.0f &&
                    shortSide <= max(3, min(width, height) / 10) &&
                    component.count < area / 4
            val edgeResidue = touchesOuterEdge && component.count < area / 10
            remove[index + 1] = longThinResidue || edgeResidue
        }

        val filteredAlpha = IntArray(area)
        var kept = 0
        for (index in pixels.indices) {
            val label = labels[index]
            val alpha = if (label == 0 || remove[label]) 0 else Color.alpha(pixels[index])
            filteredAlpha[index] = alpha
            if (alpha >= 48) kept++
        }
        if (kept < area / 120) return null

        // One light separable-style 3x3 weighted blur on alpha removes the stair-step edge created
        // by color-distance thresholding. It is small enough to keep the Metro glyph crisp.
        val output = IntArray(area)
        val kernel = intArrayOf(1, 2, 1, 2, 4, 2, 1, 2, 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var weight = 0
                var k = 0
                for (dy in -1..1) {
                    val sy = (y + dy).coerceIn(0, height - 1)
                    for (dx in -1..1) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        val w = kernel[k++]
                        sum += filteredAlpha[sy * width + sx] * w
                        weight += w
                    }
                }
                val alpha = (sum / weight).coerceIn(0, 255)
                output[y * width + x] = if (alpha == 0) 0 else Color.argb(alpha, 255, 255, 255)
            }
        }

        val refined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        refined.setPixels(output, 0, width, 0, 0, width, height)
        return refined
    }

    private fun createSmartWhiteGlyph(source: Bitmap): Bitmap? {
        if (source.width <= 1 || source.height <= 1) return null

        // Quantized-color accumulation keeps this inexpensive even with hundreds of installed apps.
        fun addPixel(map: MutableMap<Int, LongArray>, pixel: Int) {
            val alpha = Color.alpha(pixel)
            if (alpha < 96) return
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val acc = map.getOrPut(key) { LongArray(4) }
            acc[0] += 1L
            acc[1] += r.toLong()
            acc[2] += g.toLong()
            acc[3] += b.toLong()
        }

        fun dominantColor(map: Map<Int, LongArray>): IntArray? {
            val best = map.values.maxByOrNull { it[0] } ?: return null
            val count = best[0].coerceAtLeast(1L)
            return intArrayOf(
                (best[1] / count).toInt().coerceIn(0, 255),
                (best[2] / count).toInt().coerceIn(0, 255),
                (best[3] / count).toInt().coerceIn(0, 255),
            )
        }

        val step = max(1, min(source.width, source.height) / 72)
        val borderX = max(1, source.width / 7)
        val borderY = max(1, source.height / 7)
        val borderBins = HashMap<Int, LongArray>()
        val allBins = HashMap<Int, LongArray>()

        var y = 0
        while (y < source.height) {
            var x = 0
            while (x < source.width) {
                val pixel = source.getPixel(x, y)
                addPixel(allBins, pixel)
                if (
                    x < borderX || x >= source.width - borderX ||
                    y < borderY || y >= source.height - borderY
                ) {
                    addPixel(borderBins, pixel)
                }
                x += step
            }
            y += step
        }

        // Prefer the color touching the icon perimeter. Transparent adaptive corners can leave too
        // few usable border samples, in which case the dominant color of the whole rendered icon
        // is normally the plate color.
        val borderSamples = borderBins.values.sumOf { it[0] }
        val background = if (borderSamples >= 8L) {
            dominantColor(borderBins)
        } else {
            dominantColor(allBins)
        } ?: return null

        fun colorDistance(pixel: Int): Int {
            val dr = Color.red(pixel) - background[0]
            val dg = Color.green(pixel) - background[1]
            val db = Color.blue(pixel) - background[2]
            return sqrt((dr * dr + dg * dg + db * db).toDouble()).roundToInt()
        }

        val distances = ArrayList<Int>()
        y = 0
        while (y < source.height) {
            var x = 0
            while (x < source.width) {
                val pixel = source.getPixel(x, y)
                if (Color.alpha(pixel) >= 96) distances += colorDistance(pixel)
                x += step
            }
            y += step
        }
        if (distances.size < 6) return null
        distances.sort()

        fun percentile(fraction: Float): Int {
            val index = ((distances.lastIndex) * fraction.coerceIn(0f, 1f))
                .roundToInt()
                .coerceIn(0, distances.lastIndex)
            return distances[index]
        }

        fun buildMask(threshold: Int): Pair<Bitmap, Float> {
            val softness = 34f
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            var kept = 0
            var considered = 0

            var py = 0
            while (py < source.height) {
                var px = 0
                while (px < source.width) {
                    val pixel = source.getPixel(px, py)
                    val alpha = Color.alpha(pixel)
                    if (alpha >= 32) {
                        considered++
                        val distance = colorDistance(pixel)
                        val signal = ((distance - threshold) / softness).coerceIn(0f, 1f)
                        val outAlpha = (alpha * signal).roundToInt().coerceIn(0, 255)
                        if (outAlpha >= 48) kept++
                        if (outAlpha > 0) {
                            output.setPixel(px, py, Color.argb(outAlpha, 255, 255, 255))
                        }
                    }
                    px++
                }
                py++
            }
            return output to if (considered == 0) 0f else kept.toFloat() / considered.toFloat()
        }

        var threshold = max(38, percentile(0.58f))
        var result = buildMask(threshold)

        // If too much of the plate survived, aggressively isolate the most distinctive pixels.
        if (result.second > 0.50f) {
            result.first.recycle()
            threshold = max(threshold, percentile(0.76f))
            result = buildMask(threshold)
        }

        // If almost nothing survived, relax the separation once. This catches low-contrast logos.
        if (result.second < 0.018f) {
            result.first.recycle()
            threshold = max(20, percentile(0.30f))
            result = buildMask(threshold)
        }

        // A mask covering most of the icon is still just a white plate; a near-empty mask is not
        // identifiable. Preserve the ordinary colored icon in either case rather than showing a
        // misleading white square/circle.
        return if (result.second in 0.018f..0.50f) {
            val refined = refineWhiteGlyphMask(result.first)
            if (refined !== result.first) result.first.recycle()
            refined
        } else {
            result.first.recycle()
            null
        }
    }

    private fun extractAppAccentColor(bitmap: Bitmap): Long {
        if (bitmap.width <= 0 || bitmap.height <= 0) return WindowsColors.Purple

        val hueBins = 24
        val hueCoverage = DoubleArray(hueBins)
        val hueWeight = DoubleArray(hueBins)
        val hueR = DoubleArray(hueBins)
        val hueG = DoubleArray(hueBins)
        val hueB = DoubleArray(hueBins)

        var totalWeight = 0.0
        var chromaticCoverage = 0.0

        var darkNeutralWeight = 0.0
        var darkNeutralR = 0.0
        var darkNeutralG = 0.0
        var darkNeutralB = 0.0

        var neutralWeight = 0.0
        var neutralR = 0.0
        var neutralG = 0.0
        var neutralB = 0.0

        var allR = 0.0
        var allG = 0.0
        var allB = 0.0

        val hsv = FloatArray(3)
        val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 48)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha >= 64) {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val baseWeight = alpha / 255.0

                    totalWeight += baseWeight
                    allR += r * baseWeight
                    allG += g * baseWeight
                    allB += b * baseWeight

                    Color.RGBToHSV(r, g, b, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]

                    if (saturation >= 0.18f && value >= 0.10f) {
                        chromaticCoverage += baseWeight
                        val bin = ((hsv[0] / 360f) * hueBins).toInt().coerceIn(0, hueBins - 1)
                        // Coverage decides whether a hue is truly present; this stronger weight
                        // then favours the clean brand color over antialias/desaturated fringes.
                        val weight = baseWeight * (0.55 + saturation * saturation * 1.45)
                        hueCoverage[bin] += baseWeight
                        hueWeight[bin] += weight
                        hueR[bin] += r * weight
                        hueG[bin] += g * weight
                        hueB[bin] += b * weight
                    } else {
                        neutralWeight += baseWeight
                        neutralR += r * baseWeight
                        neutralG += g * baseWeight
                        neutralB += b * baseWeight

                        // For genuinely monochrome brands, prefer the dark mark/plate even when
                        // it sits on a larger white background. This is what keeps Uber, ChatGPT,
                        // Notion-style icons, etc. black instead of averaging to gray/white.
                        if (value <= 0.58f) {
                            darkNeutralWeight += baseWeight
                            darkNeutralR += r * baseWeight
                            darkNeutralG += g * baseWeight
                            darkNeutralB += b * baseWeight
                        }
                    }
                }
                x += step
            }
            y += step
        }

        if (totalWeight <= 0.0) return WindowsColors.Purple

        val bestHue = hueCoverage.indices.maxByOrNull { hueCoverage[it] } ?: 0
        val bestHueCoverage = hueCoverage[bestHue] / totalWeight
        val overallChromaticCoverage = chromaticCoverage / totalWeight

        // A meaningful chromatic region owns the tile accent even if a white/black icon plate is
        // larger. This preserves actual brand colors for Chrome/Brave/Telegram/WhatsApp/etc.
        if (
            hueWeight[bestHue] > 0.0 &&
            overallChromaticCoverage >= 0.075 &&
            bestHueCoverage >= 0.035
        ) {
            val weight = hueWeight[bestHue]
            val r = (hueR[bestHue] / weight).roundToInt().coerceIn(0, 255)
            val g = (hueG[bestHue] / weight).roundToInt().coerceIn(0, 255)
            val b = (hueB[bestHue] / weight).roundToInt().coerceIn(0, 255)
            return Color.rgb(r, g, b).toLong() and 0xFFFFFFFFL
        }

        // No real chromatic brand color: preserve the neutral identity instead of forcing a
        // Metro mid-brightness. Prefer a meaningful dark neutral component over a white plate.
        if (darkNeutralWeight / totalWeight >= 0.035) {
            var r = (darkNeutralR / darkNeutralWeight).roundToInt().coerceIn(0, 255)
            var g = (darkNeutralG / darkNeutralWeight).roundToInt().coerceIn(0, 255)
            var b = (darkNeutralB / darkNeutralWeight).roundToInt().coerceIn(0, 255)

            val maxChannel = maxOf(r, g, b)
            val minChannel = minOf(r, g, b)
            if (maxChannel - minChannel <= 18 && maxChannel <= 52) {
                // Near-black branding should visually be black, not a muddy charcoal caused by
                // antialiasing or a tiny gray highlight.
                r = 0
                g = 0
                b = 0
            }
            return Color.rgb(r, g, b).toLong() and 0xFFFFFFFFL
        }

        if (neutralWeight > 0.0) {
            val r = (neutralR / neutralWeight).roundToInt().coerceIn(0, 255)
            val g = (neutralG / neutralWeight).roundToInt().coerceIn(0, 255)
            val b = (neutralB / neutralWeight).roundToInt().coerceIn(0, 255)
            return Color.rgb(r, g, b).toLong() and 0xFFFFFFFFL
        }

        val r = (allR / totalWeight).roundToInt().coerceIn(0, 255)
        val g = (allG / totalWeight).roundToInt().coerceIn(0, 255)
        val b = (allB / totalWeight).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b).toLong() and 0xFFFFFFFFL
    }

    /**
     * Exact pre-v2 accent algorithm. Kept only for one-time migration so existing automatically
     * colored pins can be recognized without touching tiles the user manually recolored.
     */
    private fun extractLegacyAppAccentColor(bitmap: Bitmap): Long {
        if (bitmap.width <= 0 || bitmap.height <= 0) return WindowsColors.Purple

        val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 32)
        var satR = 0.0
        var satG = 0.0
        var satB = 0.0
        var satWeight = 0.0
        var allR = 0.0
        var allG = 0.0
        var allB = 0.0
        var allWeight = 0.0

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha >= 96) {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val chroma = max - min
                    val baseWeight = alpha / 255.0
                    allR += r * baseWeight
                    allG += g * baseWeight
                    allB += b * baseWeight
                    allWeight += baseWeight

                    if (chroma >= 24 && max >= 48) {
                        val weight = baseWeight * (chroma / 255.0) * (0.55 + max / 510.0)
                        satR += r * weight
                        satG += g * weight
                        satB += b * weight
                        satWeight += weight
                    }
                }
                x += step
            }
            y += step
        }

        val useSaturated = satWeight >= 0.75
        val weight = if (useSaturated) satWeight else allWeight
        if (weight <= 0.0) return WindowsColors.Purple

        val r = ((if (useSaturated) satR else allR) / weight).roundToInt().coerceIn(0, 255)
        val g = ((if (useSaturated) satG else allG) / weight).roundToInt().coerceIn(0, 255)
        val b = ((if (useSaturated) satB else allB) / weight).roundToInt().coerceIn(0, 255)

        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        if (hsv[1] >= 0.10f) hsv[1] = hsv[1].coerceAtLeast(0.52f)
        hsv[2] = hsv[2].coerceIn(0.46f, 0.86f)
        return Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
    }

    /**
     * Refreshes every tile explicitly owned by automatic app accents and performs a narrow one-time
     * migration for pins created by the old extractor. A pre-v2 tile is migrated only when its
     * saved color exactly matches the old algorithm, so arbitrary/manual colors remain untouched.
     *
     * This method performs PackageManager/bitmap work and must be called off the main thread.
     */
    fun refreshAutomaticAppAccentTiles(tiles: List<TileModel>): List<TileModel> {
        val previousVersion = prefs.getInt(APP_ACCENT_ALGORITHM_VERSION_KEY, 0)
        val allowLegacyRecognition = previousVersion < APP_ACCENT_ALGORITHM_VERSION
        val accentPairs = mutableMapOf<String, Pair<Long, Long>>()
        var changed = false

        fun accentsFor(packageName: String): Pair<Long, Long>? =
            accentPairs.getOrPut(packageName) {
                val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
                    ?: return null
                val bitmap = drawableToBitmap(drawable)
                val current = extractAppAccentColor(bitmap)
                val legacy = extractLegacyAppAccentColor(bitmap)
                appAccentCache[packageName] = current
                current to legacy
            }

        val refreshed = tiles.map { tile ->
            val packageName = tile.packageName
            if (packageName.isNullOrBlank() || tile.tileType != TileType.APP) {
                return@map tile
            }

            val pair = accentsFor(packageName) ?: return@map tile
            val currentAccent = pair.first
            val legacyAccent = pair.second
            val ownsAutomaticAccent =
                tile.usesAppAccent ||
                    (allowLegacyRecognition && tile.colorValue == legacyAccent)

            if (ownsAutomaticAccent) {
                if (!tile.usesAppAccent || tile.colorValue != currentAccent) {
                    changed = true
                    tile.copy(
                        colorValue = currentAccent,
                        usesAppAccent = true,
                    )
                } else {
                    tile
                }
            } else {
                tile
            }
        }

        if (allowLegacyRecognition) {
            prefs.edit { putInt(APP_ACCENT_ALGORITHM_VERSION_KEY, APP_ACCENT_ALGORITHM_VERSION) }
        }
        if (changed) savePinnedTiles(refreshed)
        return refreshed
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap =
        drawableToBitmap(drawable, maxCachedIconPx)

    private fun drawableToBitmap(
        drawable: Drawable,
        targetPx: Int,
    ): Bitmap {
        val safeTarget = targetPx.coerceAtLeast(1)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return downsampleBitmapIfNeeded(drawable.bitmap, safeTarget)
        }

        val intrinsicWidth = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else safeTarget
        val intrinsicHeight = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else safeTarget
        val longestSide = maxOf(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
        val scale = minOf(1f, safeTarget.toFloat() / longestSide.toFloat())
        val width = (intrinsicWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (intrinsicHeight * scale).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun downsampleBitmapIfNeeded(bitmap: Bitmap): Bitmap =
        downsampleBitmapIfNeeded(bitmap, maxCachedIconPx)

    private fun downsampleBitmapIfNeeded(
        bitmap: Bitmap,
        targetPx: Int,
    ): Bitmap {
        val safeTarget = targetPx.coerceAtLeast(1)
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= safeTarget) return bitmap

        val scale = safeTarget.toFloat() / longestSide.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    fun findAppForKeywords(installed: List<AppInfo>, vararg keywords: String): AppInfo? {
        for (keyword in keywords) {
            val lower = keyword.lowercase(Locale.getDefault())
            val match = installed.firstOrNull {
                it.packageName.lowercase().contains(lower) || it.label.lowercase().contains(lower)
            }
            if (match != null) return match
        }
        return null
    }

    private fun upgradeLegacyStockStartIfNeeded(tiles: List<TileModel>): List<TileModel> {
        val generation = prefs.getInt(DEFAULT_LAYOUT_GENERATION_KEY, 0)
        if (generation >= DEFAULT_LAYOUT_GENERATION) return tiles

        val ids = tiles.mapTo(linkedSetOf()) { it.id }
        val isLegacyStockSet =
            ids == LEGACY_DEFAULT_TILE_IDS ||
                ids == STOCK_DEFAULT_TILE_IDS

        val result = if (isLegacyStockSet) {
            // One-time migration for the old 18-tile demo set. The exact stock ID set is narrow
            // enough to avoid touching layouts where the user pinned or unpinned anything, while
            // still upgrading testers who tried rearranging those stock tiles on earlier builds.
            getDefaultTiles()
        } else {
            tiles
        }

        prefs.edit { putInt(DEFAULT_LAYOUT_GENERATION_KEY, DEFAULT_LAYOUT_GENERATION) }
        if (result !== tiles) savePinnedTiles(result)
        return result
    }

    fun loadPinnedTiles(): List<TileModel> {
        val savedJson = prefs.getString("pinned_tiles_json", null)
        if (!savedJson.isNullOrEmpty()) {
            try {
                val list = mutableListOf<TileModel>()
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    var colorVal = obj.optLong("colorValue", 0xFF0078D7L)
                    // Ensure alpha is 100% opaque
                    if ((colorVal and 0xFF000000L) == 0L || colorVal == 0L) {
                        colorVal = (colorVal and 0x00FFFFFFL) or 0xFF000000L
                        if (colorVal == 0xFF000000L) colorVal = 0xFF0078D7L
                    }

                    list.add(
                        TileModel(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
                            activityName = obj.optString("activityName").takeIf { it.isNotEmpty() },
                            size = TileSize.valueOf(obj.optString("size", TileSize.MEDIUM.name)),
                            colorValue = colorVal,
                            usesAppAccent = obj.optBoolean("usesAppAccent", false),
                            tileType = TileType.valueOf(obj.optString("tileType", TileType.APP.name)),
                            iconGlyph = obj.optString("iconGlyph", ""),
                            groupId = obj.optString("groupId", "").takeIf { it.isNotBlank() }
                                ?: "legacy:${obj.optString("groupName", "Start").trim().ifEmpty { "Start" }}",
                            groupName = obj.optString("groupName", "Start"),
                            order = obj.optInt("order", i),
                            startBand = obj.optInt("startBand", -1).takeIf { it >= 0 },
                            startColumn = obj.optInt("startColumn", -1).takeIf { it >= 0 },
                            startRow = obj.optInt("startRow", -1).takeIf { it >= 0 },
                        )
                    )
                }
                if (list.isNotEmpty()) return upgradeLegacyStockStartIfNeeded(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val defaultTiles = getDefaultTiles()
        savePinnedTiles(defaultTiles)
        prefs.edit { putInt(DEFAULT_LAYOUT_GENERATION_KEY, DEFAULT_LAYOUT_GENERATION) }
        return defaultTiles
    }

    fun savePinnedTiles(tiles: List<TileModel>) {
        val array = JSONArray()
        tiles.forEachIndexed { index, tile ->
            val obj = JSONObject().apply {
                put("id", tile.id)
                put("title", tile.title)
                put("packageName", tile.packageName ?: "")
                put("activityName", tile.activityName ?: "")
                put("size", tile.size.name)
                put("colorValue", tile.colorValue)
                put("usesAppAccent", tile.usesAppAccent)
                put("tileType", tile.tileType.name)
                put("iconGlyph", tile.iconGlyph)
                put(
                    "groupId",
                    tile.groupId.takeIf(String::isNotBlank)
                        ?: "legacy:${tile.groupName.trim().ifEmpty { "Start" }}",
                )
                put("groupName", tile.groupName)
                put("order", index)
                tile.startBand?.let { put("startBand", it) }
                tile.startColumn?.let { put("startColumn", it) }
                tile.startRow?.let { put("startRow", it) }
            }
            array.put(obj)
        }
        prefs.edit { putString("pinned_tiles_json", array.toString()) }
    }

    fun getDefaultTiles(): List<TileModel> {
        val installed = getInstalledApps()

        // Prefer Android platform semantics/defaults first; package-name keyword matching is
        // only a fallback for OEM apps that do not advertise the standard category.
        val mailApp = resolveDefaultTileApp(context, "tile_mail")
            ?: findAppForKeywords(installed, "mail", "gmail", "outlook")
        val calendarApp = resolveDefaultTileApp(context, "tile_calendar")
            ?: findAppForKeywords(installed, "calendar")
        val peopleApp = resolveDefaultTileApp(context, "tile_people")
            ?: findAppForKeywords(installed, "contact", "people", "dialer")
        val chatApp = resolveDefaultTileApp(context, "tile_skype")
            ?: findAppForKeywords(installed, "message", "messages", "whatsapp", "telegram", "skype")
        val weatherApp = resolveDefaultTileApp(context, "tile_weather")
            ?: findAppForKeywords(installed, "weather")
        val browserApp = resolveDefaultTileApp(context, "tile_ie")
            ?: findAppForKeywords(installed, "chrome", "browser", "firefox", "edge")
        val storeApp = resolveDefaultTileApp(context, "tile_store")
            ?: findAppForKeywords(installed, "vending", "store", "play")
        val photosApp = resolveDefaultTileApp(context, "tile_photos")
            ?: findAppForKeywords(installed, "gallery", "photos", "photo")
        val settingsApp = resolveDefaultTileApp(context, "tile_settings")
            ?: findAppForKeywords(installed, "settings")
        val cameraApp = resolveDefaultTileApp(context, "tile_camera")
            ?: findAppForKeywords(installed, "camera")
        val clockApp = resolveDefaultTileApp(context, "tile_clock")
            ?: findAppForKeywords(installed, "clock", "deskclock")
        val driveApp = resolveDefaultTileApp(context, "tile_onedrive")
            ?: findAppForKeywords(installed, "files", "file manager", "drive", "onedrive")
        val mapsApp = resolveDefaultTileApp(context, "tile_maps")
            ?: findAppForKeywords(installed, "maps", "map")
        val musicApp = resolveDefaultTileApp(context, "tile_music")
            ?: findAppForKeywords(installed, "music", "spotify", "audio")
        val videoApp = resolveDefaultTileApp(context, "tile_video")
            ?: findAppForKeywords(installed, "youtube", "video", "movies")
        val gamesApp = findAppForKeywords(installed, "play games", "games", "gaming")
        val newsApp = findAppForKeywords(installed, "news")

        fun tile(
            id: String,
            title: String,
            groupId: String,
            size: TileSize,
            color: Long,
            type: TileType = TileType.APP,
            glyph: String = "app",
            app: AppInfo? = null,
        ) = TileModel(
            id = id,
            title = title,
            packageName = app?.packageName,
            activityName = app?.activityName,
            size = size,
            colorValue = color,
            tileType = type,
            iconGlyph = glyph,
            groupId = groupId,
            // Microsoft's own 8.1 product-guide Start example uses separated unnamed clusters.
            // Keep categories as stable identities while leaving the labels blank by default.
            groupName = "",
        )

        val connect = "default:connect"
        val windows = "default:windows"
        val explore = "default:explore"

        return listOf(
            // Cluster 1 — the at-a-glance block from the period Windows 8.1 Start layout.
            // It intentionally spans more than one four-cell band, with ordinary 8dp tile spacing
            // inside the cluster and no artificial empty category row.
            tile(
                "tile_mail", "Mail", connect, TileSize.WIDE, WindowsColors.MailBlue,
                TileType.MAIL, "mail", mailApp,
            ),
            tile(
                "tile_calendar", "Calendar", connect, TileSize.WIDE, WindowsColors.CalendarPurple,
                TileType.CALENDAR, "calendar", calendarApp,
            ),
            tile(
                "tile_people", "People", connect, TileSize.MEDIUM, WindowsColors.PeopleOrange,
                TileType.APP, "people", peopleApp,
            ),
            tile(
                "tile_skype", "Skype", connect, TileSize.MEDIUM, WindowsColors.SkypeCyan,
                TileType.APP, "skype", chatApp,
            ),
            tile(
                "tile_desktop", "Desktop", connect, TileSize.MEDIUM, WindowsColors.DesktopBlue,
                TileType.DESKTOP, "desktop",
            ),
            tile(
                "tile_weather", "Weather", connect, TileSize.WIDE, WindowsColors.WeatherCyan,
                TileType.WEATHER, "weather", weatherApp,
            ),
            tile(
                "tile_settings", "PC settings", connect, TileSize.SMALL, WindowsColors.SettingsPurple,
                TileType.SETTINGS, "settings", settingsApp,
            ),
            tile(
                "tile_clock", "Alarms & Clock", connect, TileSize.SMALL, WindowsColors.SportsPurple,
                TileType.CLOCK, "clock", clockApp,
            ),

            // Cluster 2 — browser/cloud/photos plus the four small media tiles. This follows the
            // visual rhythm of Microsoft's 8.1 Product Guide screenshot, where IE and four small
            // media tiles sit beside Help+Tips/OneDrive/Photos.
            tile(
                "tile_ie", "Internet Explorer", windows, TileSize.MEDIUM,
                WindowsColors.InternetExplorerBlue, TileType.INTERNET_EXPLORER, "ie", browserApp,
            ),
            tile(
                "tile_video", "Video", windows, TileSize.SMALL, WindowsColors.NewsRed,
                TileType.APP, "video", videoApp,
            ),
            tile(
                "tile_music", "Music", windows, TileSize.SMALL, WindowsColors.MusicOrange,
                TileType.APP, "music", musicApp,
            ),
            tile(
                "tile_games", "Games", windows, TileSize.SMALL, WindowsColors.StoreGreen,
                TileType.APP, "games", gamesApp,
            ),
            tile(
                "tile_camera", "Camera", windows, TileSize.SMALL, WindowsColors.CameraPink,
                TileType.APP, "camera", cameraApp,
            ),
            tile(
                "tile_help", "Help+Tips", windows, TileSize.MEDIUM, WindowsColors.HelpOrange,
                TileType.APP, "help",
            ),
            tile(
                "tile_onedrive", "OneDrive", windows, TileSize.MEDIUM, WindowsColors.InternetExplorerBlue,
                TileType.APP, "cloud", driveApp,
            ),
            tile(
                "tile_photos", "Photos", windows, TileSize.WIDE, WindowsColors.Teal,
                TileType.PHOTOS, "photos", photosApp,
            ),

            // Cluster 3 — information/discovery. At the common six-row phone layout this fills a
            // single band rather than leaving a mostly empty continuation column.
            tile(
                "tile_news", "News", explore, TileSize.WIDE, WindowsColors.NewsRed,
                TileType.APP, "news", newsApp,
            ),
            tile(
                "tile_money", "Money", explore, TileSize.MEDIUM, WindowsColors.MoneyGreen,
                TileType.MONEY, "money",
            ),
            tile(
                "tile_maps", "Maps", explore, TileSize.MEDIUM, WindowsColors.Purple,
                TileType.APP, "maps", mapsApp,
            ),
            tile(
                "tile_reading_list", "Reading List", explore, TileSize.MEDIUM,
                WindowsColors.ReadingListCrimson, TileType.READING_LIST, "reading_list",
            ),
            tile(
                "tile_store", "Store", explore, TileSize.MEDIUM, WindowsColors.StoreGreen,
                TileType.STORE, "store", storeApp,
            ),
        ).mapIndexed { index, tile -> tile.copy(order = index) }
    }

    fun getFlipAnimationMode(): FlipAnimationMode {
        val modeStr = prefs.getString("flip_animation_mode", FlipAnimationMode.CLASSIC.name)
        return try {
            FlipAnimationMode.valueOf(modeStr ?: FlipAnimationMode.CLASSIC.name)
        } catch (e: Exception) {
            FlipAnimationMode.CLASSIC
        }
    }

    fun setFlipAnimationMode(mode: FlipAnimationMode) {
        prefs.edit { putString("flip_animation_mode", mode.name) }
    }

    fun getWallpaperStyle(): Int = prefs.getInt("wallpaper_style", 0).coerceIn(0, 9)

    fun setWallpaperStyle(style: Int) {
        prefs.edit().putInt("wallpaper_style", style.coerceIn(0, 9)).apply()
    }

    fun getWallpaperParallaxEnabled(): Boolean =
        prefs.getBoolean(WALLPAPER_PARALLAX_ENABLED, true)

    fun setWallpaperParallaxEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(WALLPAPER_PARALLAX_ENABLED, enabled) }
    }

    fun getStartWallpaperScrollPx(): Float {
        // Older builds persisted only the wallpaper coordinate, not the matching LazyRow state.
        // Treat that orphaned value as stale on first upgrade; otherwise Start would paint the old
        // parallax position and then snap back to item 0 as soon as its layout becomes available.
        if (!prefs.contains(START_SCROLL_INDEX)) return 0f
        return prefs.getFloat(START_WALLPAPER_SCROLL_PX, 0f)
            .takeIf(Float::isFinite)
            ?.coerceAtLeast(0f)
            ?: 0f
    }

    fun getStartScrollIndex(): Int =
        prefs.getInt(START_SCROLL_INDEX, 0).coerceAtLeast(0)

    fun getStartScrollOffsetPx(): Int =
        prefs.getInt(START_SCROLL_OFFSET_PX, 0).coerceAtLeast(0)

    fun setStartViewState(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        wallpaperScrollPx: Float,
    ) {
        val safeWallpaper =
            wallpaperScrollPx.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        prefs.edit {
            putInt(START_SCROLL_INDEX, firstVisibleItemIndex.coerceAtLeast(0))
            putInt(START_SCROLL_OFFSET_PX, firstVisibleItemScrollOffset.coerceAtLeast(0))
            putFloat(START_WALLPAPER_SCROLL_PX, safeWallpaper)
        }
    }

    fun getLaunchTiming(allApps: Boolean = false): LaunchTiming {
        fun key(name: String) = if (allApps) "all_apps_$name" else name
        val defaults = LaunchTiming()
        val curveName = readPreference(defaults.curve.name) {
            prefs.getString(key(LAUNCH_TIMING_CURVE), defaults.curve.name) ?: defaults.curve.name
        }
        val curve = TimeCurve.values().firstOrNull { it.name == curveName } ?: defaults.curve

        return LaunchTiming(
            durationMillis = readPreference(defaults.durationMillis) {
                prefs.getInt(key(LAUNCH_TIMING_DURATION), defaults.durationMillis)
            },
            curve = curve,
            customX1 = readPreference(defaults.customX1) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_X1), defaults.customX1)
            },
            customY1 = readPreference(defaults.customY1) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_Y1), defaults.customY1)
            },
            customX2 = readPreference(defaults.customX2) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_X2), defaults.customX2)
            },
            customY2 = readPreference(defaults.customY2) {
                prefs.getFloat(key(LAUNCH_TIMING_CUSTOM_Y2), defaults.customY2)
            },
            strength = readPreference(defaults.strength) {
                prefs.getFloat(key(LAUNCH_TIMING_STRENGTH), defaults.strength)
            },
            steps = readPreference(defaults.steps) {
                prefs.getInt(key(LAUNCH_TIMING_STEPS), defaults.steps)
            },
        ).sanitized()
    }

    fun setLaunchTiming(timing: LaunchTiming, allApps: Boolean = false) {
        fun key(name: String) = if (allApps) "all_apps_$name" else name
        val safeTiming = timing.sanitized()
        prefs.edit {
            putInt(key(LAUNCH_TIMING_DURATION), safeTiming.durationMillis)
            putString(key(LAUNCH_TIMING_CURVE), safeTiming.curve.name)
            putFloat(key(LAUNCH_TIMING_CUSTOM_X1), safeTiming.customX1)
            putFloat(key(LAUNCH_TIMING_CUSTOM_Y1), safeTiming.customY1)
            putFloat(key(LAUNCH_TIMING_CUSTOM_X2), safeTiming.customX2)
            putFloat(key(LAUNCH_TIMING_CUSTOM_Y2), safeTiming.customY2)
            putFloat(key(LAUNCH_TIMING_STRENGTH), safeTiming.strength)
            putInt(key(LAUNCH_TIMING_STEPS), safeTiming.steps)
        }
    }

    private fun <T> readPreference(default: T, read: () -> T): T = try {
        read()
    } catch (_: Exception) {
        default
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4L
        const val ICON_CACHE_MAX_BYTES = 8 * 1024 * 1024
        const val ALL_APPS_ICON_CACHE_MAX_BYTES = 32 * 1024 * 1024
        const val ALL_APPS_ICON_MIN_PX = 72
        const val ALL_APPS_ICON_MAX_PX = 192
        const val ICON_DECODE_CONCURRENCY = 2
        const val ALL_APPS_ICON_DECODE_CONCURRENCY = 4
        const val ICON_CACHE_MAX_DP = 112f
        const val ICON_CACHE_MIN_PX = 96
        const val ICON_CACHE_MAX_PX = 384
        const val APP_ACCENT_ALGORITHM_VERSION_KEY = "app_accent_algorithm_version"
        const val APP_ACCENT_ALGORITHM_VERSION = 2
        const val LAUNCH_TIMING_DURATION = "launch_timing_duration_millis"
        const val LAUNCH_TIMING_CURVE = "launch_timing_curve"
        const val LAUNCH_TIMING_CUSTOM_X1 = "launch_timing_custom_x1"
        const val LAUNCH_TIMING_CUSTOM_Y1 = "launch_timing_custom_y1"
        const val LAUNCH_TIMING_CUSTOM_X2 = "launch_timing_custom_x2"
        const val LAUNCH_TIMING_CUSTOM_Y2 = "launch_timing_custom_y2"
        const val LAUNCH_TIMING_STRENGTH = "launch_timing_strength"
        const val LAUNCH_TIMING_STEPS = "launch_timing_steps"
        const val WALLPAPER_PARALLAX_ENABLED = "wallpaper_parallax_enabled"
        const val START_WALLPAPER_SCROLL_PX = "start_wallpaper_scroll_px"
        const val START_SCROLL_INDEX = "start_scroll_index"
        const val START_SCROLL_OFFSET_PX = "start_scroll_offset_px"
        const val DEFAULT_LAYOUT_GENERATION_KEY = "default_start_layout_generation"
        const val DEFAULT_LAYOUT_GENERATION = 3

        val LEGACY_DEFAULT_TILE_IDS = linkedSetOf(
            "tile_mail",
            "tile_weather",
            "tile_store",
            "tile_clock",
            "tile_calendar",
            "tile_people",
            "tile_skype",
            "tile_ie",
            "tile_music",
            "tile_camera",
            "tile_settings",
            "tile_desktop",
            "tile_help",
            "tile_reading_list",
            "tile_maps",
            "tile_photos",
            "tile_money",
            "tile_news",
        )
    }
}
