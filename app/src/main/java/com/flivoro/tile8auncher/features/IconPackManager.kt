package com.flivoro.tile8auncher.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Resolves per-app custom images first, then common ADW/Nova-style icon packs.
 * The default application icon remains AppsRepository's responsibility.
 */
object IconPackManager {
    private data class ParsedPack(val packageName: String, val components: Map<String, String>)

    @Volatile
    private var parsedPack: ParsedPack? = null
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()

    fun clearCaches() {
        parsedPack = null
        bitmapCache.clear()
    }

    fun loadOverride(context: Context, targetPackage: String, maxPx: Int = 256): ImageBitmap? {
        val customUri = LauncherFeatureStore.customIconUri(context, targetPackage)
        if (!customUri.isNullOrBlank()) {
            val key = "uri:$customUri:$maxPx"
            bitmapCache[key]?.let { return it }
            decodeUri(context, Uri.parse(customUri), maxPx)?.let {
                bitmapCache[key] = it
                return it
            }
        }

        val packPackage = LauncherFeatureStore.selectedIconPack(context) ?: return null
        val component = context.packageManager.getLaunchIntentForPackage(targetPackage)?.component
        val componentKey = component?.flattenToString()
        val shortComponentKey = component?.flattenToShortString()
        val pack = parsePack(context, packPackage)
        val drawableName = sequenceOf(
            componentKey,
            shortComponentKey,
            component?.let { "ComponentInfo{${it.flattenToString()}}" },
            component?.let { "ComponentInfo{${it.flattenToShortString()}}" },
            targetPackage,
        ).mapNotNull { key -> key?.let(pack.components::get) }.firstOrNull() ?: return null

        val cacheKey = "pack:$packPackage:$drawableName:$maxPx"
        bitmapCache[cacheKey]?.let { return it }
        val resources = runCatching { context.packageManager.getResourcesForApplication(packPackage) }.getOrNull()
            ?: return null
        val id = resources.getIdentifier(drawableName, "drawable", packPackage)
            .takeIf { it != 0 }
            ?: resources.getIdentifier(drawableName, "mipmap", packPackage).takeIf { it != 0 }
            ?: return null
        val drawable = runCatching { resources.getDrawable(id, null) }.getOrNull() ?: return null
        return drawableToImageBitmap(drawable, maxPx)?.also { bitmapCache[cacheKey] = it }
    }

    fun discoverIconPacks(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val candidates = linkedMapOf<String, String>()
        listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME").forEach { action ->
            val intent = android.content.Intent(action)
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val pkg = info.activityInfo.packageName
                val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                candidates[pkg] = label
            }
        }
        // Some packs expose no activity filter. A conservative fallback only includes packages that
        // actually contain an appfilter XML resource.
        pm.getInstalledApplications(0).forEach { app ->
            if (app.packageName in candidates) return@forEach
            val resources = runCatching { pm.getResourcesForApplication(app.packageName) }.getOrNull()
                ?: return@forEach
            val id = resources.getIdentifier("appfilter", "xml", app.packageName)
            if (id != 0) {
                candidates[app.packageName] = runCatching { pm.getApplicationLabel(app).toString() }
                    .getOrDefault(app.packageName)
            }
        }
        return candidates.entries.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

    private fun parsePack(context: Context, packPackage: String): ParsedPack {
        parsedPack?.takeIf { it.packageName == packPackage }?.let { return it }
        val pm = context.packageManager
        val resources = runCatching { pm.getResourcesForApplication(packPackage) }.getOrNull()
        val map = linkedMapOf<String, String>()
        if (resources != null) {
            val xmlId = resources.getIdentifier("appfilter", "xml", packPackage)
            if (xmlId != 0) {
                runCatching {
                    val parser = resources.getXml(xmlId)
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                            val component = parser.getAttributeValue(null, "component")?.trim()
                            val drawable = parser.getAttributeValue(null, "drawable")?.trim()
                            if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                                map[component] = drawable
                                val normalized = component
                                    .removePrefix("ComponentInfo{")
                                    .removeSuffix("}")
                                map.putIfAbsent(normalized, drawable)
                                val pkg = normalized.substringBefore('/').takeIf(String::isNotBlank)
                                if (pkg != null) map.putIfAbsent(pkg, drawable)
                            }
                        }
                        event = parser.next()
                    }
                }
            }
        }
        return ParsedPack(packPackage, map).also { parsedPack = it }
    }

    private fun decodeUri(context: Context, uri: Uri, maxPx: Int): ImageBitmap? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                if (longest > maxPx) {
                    val scale = maxPx.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val safe = if (maxOf(bitmap.width, bitmap.height) > maxPx) {
            val scale = maxPx.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else bitmap
        safe.asImageBitmap()
    }.getOrNull()

    private fun drawableToImageBitmap(drawable: Drawable, maxPx: Int): ImageBitmap? = runCatching {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bitmap = drawable.bitmap
            val longest = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            if (longest <= maxPx) return@runCatching bitmap.asImageBitmap()
            val scale = maxPx.toFloat() / longest
            return@runCatching Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            ).asImageBitmap()
        }

        val intrinsicW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
        val intrinsicH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
        val longest = maxOf(intrinsicW, intrinsicH).coerceAtLeast(1)
        val scale = minOf(1f, maxPx.toFloat() / longest)
        val width = (intrinsicW * scale).roundToInt().coerceAtLeast(1)
        val height = (intrinsicH * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }.getOrNull()
}
