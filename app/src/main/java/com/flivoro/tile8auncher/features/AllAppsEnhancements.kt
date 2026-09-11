package com.flivoro.tile8auncher.features

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.flivoro.tile8auncher.data.AppInfo
import java.util.Locale
import kotlin.math.pow

enum class AppSortMode(val label: String) {
    NAME("by name"),
    INSTALL_DATE("by date installed"),
    MOST_USED("by most used"),
    CATEGORY("by category"),
}

data class EnhancedAppInfo(
    val app: AppInfo,
    val firstInstallTime: Long,
    val lastTimeUsed: Long,
    val totalForegroundTime: Long,
    val category: String,
    val isNew: Boolean,
)

suspend fun loadEnhancedApps(context: Context, apps: List<AppInfo>): List<EnhancedAppInfo> {
    val pm = context.packageManager
    val now = System.currentTimeMillis()
    val usage = usageStats(context, now)
    return apps.map { app ->
        val packageInfo = runCatching { pm.getPackageInfo(app.packageName, 0) }.getOrNull()
        val appInfo = runCatching { pm.getApplicationInfo(app.packageName, 0) }.getOrNull()
        val firstInstall = packageInfo?.firstInstallTime ?: app.firstInstallTime
        val stat = usage[app.packageName]
        EnhancedAppInfo(
            app = app,
            firstInstallTime = firstInstall,
            lastTimeUsed = stat?.lastTimeUsed ?: 0L,
            totalForegroundTime = stat?.totalTimeInForeground ?: 0L,
            category = categoryLabel(appInfo),
            isNew = firstInstall > 0L && now - firstInstall <= NEW_APP_WINDOW_MILLIS &&
                (stat?.lastTimeUsed ?: 0L) <= firstInstall,
        )
    }
}

fun sortEnhancedApps(items: List<EnhancedAppInfo>, mode: AppSortMode): List<EnhancedAppInfo> {
    val locale = Locale.getDefault()
    return when (mode) {
        AppSortMode.NAME -> items.sortedBy { it.app.label.lowercase(locale) }
        AppSortMode.INSTALL_DATE -> items.sortedWith(
            compareByDescending<EnhancedAppInfo> { it.firstInstallTime }
                .thenBy { it.app.label.lowercase(locale) },
        )
        AppSortMode.MOST_USED -> items.sortedWith(
            compareByDescending<EnhancedAppInfo> { it.totalForegroundTime }
                .thenByDescending { it.lastTimeUsed }
                .thenBy { it.app.label.lowercase(locale) },
        )
        AppSortMode.CATEGORY -> items.sortedWith(
            compareBy<EnhancedAppInfo> { it.category.lowercase(locale) }
                .thenBy { it.app.label.lowercase(locale) },
        )
    }
}

fun sectionLabel(item: EnhancedAppInfo, mode: AppSortMode): String = when (mode) {
    AppSortMode.NAME -> item.app.label.trim().firstOrNull()?.uppercaseChar()
        ?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
    AppSortMode.CATEGORY -> item.category
    AppSortMode.INSTALL_DATE -> "Installed"
    AppSortMode.MOST_USED -> "Most used"
}

fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

private fun usageStats(context: Context, now: Long) = if (hasUsageAccess(context)) {
    val manager = context.getSystemService(UsageStatsManager::class.java)
    manager?.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        now - 30L * 24L * 60L * 60L * 1000L,
        now,
    )?.associateBy { it.packageName }.orEmpty()
} else {
    emptyMap()
}

private fun categoryLabel(info: ApplicationInfo?): String = when (info?.category) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO -> "Music & audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps & navigation"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    else -> "Other"
}

sealed interface UniversalSearchResult {
    val title: String
    val subtitle: String

    data class Contact(
        override val title: String,
        override val subtitle: String,
        val phone: String,
    ) : UniversalSearchResult

    data class SystemSetting(
        override val title: String,
        override val subtitle: String,
        val intent: Intent,
    ) : UniversalSearchResult

    data class Calculation(
        override val title: String,
        override val subtitle: String,
    ) : UniversalSearchResult

    data class Web(
        override val title: String,
        override val subtitle: String,
        val query: String,
    ) : UniversalSearchResult
}

fun universalNonAppResults(context: Context, query: String): List<UniversalSearchResult> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val lower = trimmed.lowercase(Locale.getDefault())
    val results = mutableListOf<UniversalSearchResult>()

    evaluateExpression(trimmed)?.let { value ->
        results += UniversalSearchResult.Calculation(
            title = formatCalculation(value),
            subtitle = trimmed,
        )
    }

    val settings = listOf(
        Triple("Wi-Fi", "Network settings", Settings.ACTION_WIFI_SETTINGS),
        Triple("Bluetooth", "Bluetooth settings", Settings.ACTION_BLUETOOTH_SETTINGS),
        Triple("Display", "Display settings", Settings.ACTION_DISPLAY_SETTINGS),
        Triple("Sound", "Sound settings", Settings.ACTION_SOUND_SETTINGS),
        Triple("Battery", "Battery settings", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        Triple("Apps", "Installed app settings", Settings.ACTION_APPLICATION_SETTINGS),
        Triple("Accessibility", "Accessibility settings", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Triple("Notifications", "Notification settings", "android.settings.NOTIFICATION_SETTINGS"),
    )
    settings.filter { (name, description, _) ->
        name.lowercase(Locale.getDefault()).contains(lower) ||
            description.lowercase(Locale.getDefault()).contains(lower)
    }.take(4).forEach { (name, description, action) ->
        results += UniversalSearchResult.SystemSetting(name, description, Intent(action))
    }

    results += contactResults(context, trimmed)
    results += UniversalSearchResult.Web(
        title = "Search the web for ‘$trimmed’",
        subtitle = "Open in your browser",
        query = trimmed,
    )
    return results
}

private fun contactResults(context: Context, query: String): List<UniversalSearchResult.Contact> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
        PackageManager.PERMISSION_GRANTED
    ) return emptyList()

    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    val args = arrayOf("%$query%")
    return runCatching {
        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            buildList<UniversalSearchResult.Contact> {
                while (cursor.moveToNext() && size < 6) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    val number = cursor.getString(numberIndex).orEmpty()
                    if (name.isNotBlank() && number.isNotBlank() && none { it.phone == number }) {
                        add(UniversalSearchResult.Contact(name, number, number))
                    }
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

fun launchUniversalResult(context: Context, result: UniversalSearchResult) {
    val intent = when (result) {
        is UniversalSearchResult.Contact -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(result.phone)}"))
        is UniversalSearchResult.SystemSetting -> result.intent
        is UniversalSearchResult.Calculation -> return
        is UniversalSearchResult.Web -> Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(result.query)}"),
        )
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun formatCalculation(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')
}

private fun evaluateExpression(raw: String): Double? {
    if (raw.none { it in "+-*/^()" } && raw.toDoubleOrNull() == null) return null
    return runCatching { ExpressionParser(raw).parse() }.getOrNull()?.takeIf(Double::isFinite)
}

private class ExpressionParser(private val input: String) {
    private var index = 0

    fun parse(): Double {
        val value = expression()
        skipSpaces()
        require(index == input.length)
        return value
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipSpaces()
            value = when {
                take('+') -> value + term()
                take('-') -> value - term()
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = power()
        while (true) {
            skipSpaces()
            value = when {
                take('*') -> value * power()
                take('/') -> value / power()
                else -> return value
            }
        }
    }

    private fun power(): Double {
        var value = unary()
        skipSpaces()
        if (take('^')) value = value.pow(power())
        return value
    }

    private fun unary(): Double {
        skipSpaces()
        return when {
            take('+') -> unary()
            take('-') -> -unary()
            take('(') -> expression().also { skipSpaces(); require(take(')')) }
            else -> number()
        }
    }

    private fun number(): Double {
        skipSpaces()
        val start = index
        while (index < input.length && (input[index].isDigit() || input[index] == '.')) index++
        require(index > start)
        return input.substring(start, index).toDouble()
    }

    private fun take(char: Char): Boolean {
        if (index < input.length && input[index] == char) {
            index++
            return true
        }
        return false
    }

    private fun skipSpaces() {
        while (index < input.length && input[index].isWhitespace()) index++
    }
}

private const val NEW_APP_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L
