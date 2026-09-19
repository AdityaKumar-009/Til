package com.flivoro.tile8auncher.features

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android notification content projected into a Windows 8.1-style live tile update.
 *
 * Windows 8.1 could queue up to five tile notifications. Android apps do not expose their native
 * tile payloads to third-party launchers, so Tile8 uses active Android notifications as the nearest
 * equivalent data source while preserving the Windows queue/presentation behavior.
 */
data class LiveTileNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val count: Int,
    val postTime: Long,
    val key: String,
)

object LiveTileRuntime {
    var listenerConnected by mutableStateOf(false)
        private set

    fun hasNotificationAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun requestReconnect(context: Context) {
        if (!hasNotificationAccess(context) || listenerConnected) return
        NotificationListenerService.requestRebind(
            ComponentName(context, MosaicNotificationListenerService::class.java),
        )
    }

    internal fun setConnected(connected: Boolean) {
        listenerConnected = connected
    }
}

object LiveTileNotificationStore {
    private const val SNAPSHOT_PREFIX = "feature_live_notification_"
    private const val MAX_QUEUE_SIZE = 5

    private val live = mutableStateMapOf<String, List<LiveTileNotification>>()

    fun notifications(context: Context, packageName: String?): List<LiveTileNotification> {
        if (packageName.isNullOrBlank()) return emptyList()
        live[packageName]?.let { return it }

        val prefs = context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val raw = prefs.getString(SNAPSHOT_PREFIX + packageName, null) ?: return emptyList()
        val parsed = parseSnapshot(packageName, raw)
        if (parsed.isNotEmpty()) live[packageName] = parsed
        return parsed
    }

    fun latest(context: Context, packageName: String?): LiveTileNotification? =
        notifications(context, packageName).firstOrNull()

    fun current(): List<LiveTileNotification> =
        live.values.flatten().sortedByDescending(LiveTileNotification::postTime)

    internal fun replacePackage(
        context: Context,
        packageName: String,
        values: List<LiveTileNotification>,
    ) {
        val normalized = values
            .sortedByDescending(LiveTileNotification::postTime)
            .distinctBy { it.key }
            .take(MAX_QUEUE_SIZE)

        if (normalized.isEmpty()) {
            clear(context, packageName)
            return
        }

        live[packageName] = normalized
        val array = JSONArray()
        normalized.forEach { value ->
            array.put(JSONObject().apply {
                put("title", value.title)
                put("text", value.text)
                put("count", value.count)
                put("postTime", value.postTime)
                put("key", value.key)
            })
        }
        context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        ).edit().putString(SNAPSHOT_PREFIX + packageName, array.toString()).apply()
    }

    internal fun replaceAll(
        context: Context,
        values: Map<String, List<LiveTileNotification>>,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val persistedPackages = prefs.all.keys
            .asSequence()
            .filter { it.startsWith(SNAPSHOT_PREFIX) }
            .map { it.removePrefix(SNAPSHOT_PREFIX) }
            .toSet()
        val stalePackages = (persistedPackages + live.keys) - values.keys
        stalePackages.forEach { clear(context, it) }
        values.forEach { (packageName, notifications) ->
            replacePackage(context, packageName, notifications)
        }
    }

    internal fun clear(context: Context, packageName: String) {
        live.remove(packageName)
        context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        ).edit().remove(SNAPSHOT_PREFIX + packageName).apply()
    }

    private fun parseSnapshot(
        packageName: String,
        raw: String,
    ): List<LiveTileNotification> = runCatching {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    parseItem(packageName, array.optJSONObject(index))?.let(::add)
                }
            }
        } else {
            // Migration from the original single-notification snapshot format.
            listOfNotNull(parseItem(packageName, JSONObject(raw)))
        }
    }.getOrDefault(emptyList())
        .sortedByDescending(LiveTileNotification::postTime)
        .take(MAX_QUEUE_SIZE)

    private fun parseItem(
        packageName: String,
        json: JSONObject?,
    ): LiveTileNotification? {
        if (json == null) return null
        val key = json.optString("key").takeIf(String::isNotBlank)
            ?: "snapshot:${json.optLong("postTime", 0L)}"
        return LiveTileNotification(
            packageName = packageName,
            title = json.optString("title"),
            text = json.optString("text"),
            count = json.optInt("count", 1).coerceIn(1, 99),
            postTime = json.optLong("postTime", 0L),
            key = key,
        )
    }
}

class MosaicNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        LiveTileRuntime.setConnected(true)
        publishAllActiveNotifications()
    }

    override fun onListenerDisconnected() {
        LiveTileRuntime.setConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        publishPackage(sbn.packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        publishPackage(sbn.packageName)
    }

    private fun publishAllActiveNotifications() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filterNot { it.packageName == packageName }

        val projected = active
            .groupBy(StatusBarNotification::getPackageName)
            .mapValues { (_, notifications) -> projectPackage(notifications) }
            .filterValues(List<LiveTileNotification>::isNotEmpty)

        LiveTileNotificationStore.replaceAll(this, projected)
    }

    private fun publishPackage(packageName: String) {
        val active = runCatching {
            activeNotifications
                ?.filter { it.packageName == packageName }
                .orEmpty()
        }.getOrDefault(emptyList())
        LiveTileNotificationStore.replacePackage(
            context = this,
            packageName = packageName,
            values = projectPackage(active),
        )
    }

    private fun projectPackage(
        items: List<StatusBarNotification>,
    ): List<LiveTileNotification> {
        if (items.isEmpty()) return emptyList()

        val projected = items.mapNotNull { sbn ->
            projectNotification(sbn)?.let { value ->
                ProjectedNotification(
                    value = value,
                    isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                )
            }
        }
        if (projected.isEmpty()) return emptyList()

        // Android group summaries frequently duplicate their child messages. Prefer the real child
        // notifications, but retain a summary when it is the only meaningful payload available.
        val contentItems = projected.filterNot(ProjectedNotification::isGroupSummary)
            .ifEmpty { projected }

        val badgeCount = maxOf(
            contentItems.size,
            items.maxOfOrNull { it.notification.number.coerceAtLeast(0) } ?: 0,
        ).coerceIn(1, 99)

        return contentItems
            .sortedByDescending { it.value.postTime }
            .distinctBy { it.value.key }
            .take(5)
            .map { it.value.copy(count = badgeCount) }
    }

    private fun projectNotification(
        sbn: StatusBarNotification,
    ): LiveTileNotification? {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle.EMPTY

        val latestMessage = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.lastOrNull()
            as? Bundle

        val messageText = latestMessage
            ?.getCharSequence("text")
            ?.toString()
            ?.trim()
            .orEmpty()
        val messageSender = latestMessage
            ?.getCharSequence("sender")
            ?.toString()
            ?.trim()
            .orEmpty()

        val title = firstUsefulText(
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
            messageSender,
        )

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { line -> line?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()

        val text = firstUsefulText(
            messageText,
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            textLines.lastOrNull(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
        )

        // Ongoing/background service notifications with no human-readable payload should never
        // replace a real Mail/Chat/News update with a visually blank live tile.
        if (title.isBlank() && text.isBlank()) return null

        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        }.getOrDefault(sbn.packageName)

        val safeTitle = title.ifBlank { appLabel }
        val safeText = text.takeUnless { it.equals(safeTitle, ignoreCase = true) }.orEmpty()

        return LiveTileNotification(
            packageName = sbn.packageName,
            title = safeTitle,
            text = safeText,
            count = 1,
            postTime = sbn.postTime,
            key = sbn.key,
        )
    }

    private fun firstUsefulText(vararg values: CharSequence?): String =
        values.asSequence()
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            .orEmpty()

    private data class ProjectedNotification(
        val value: LiveTileNotification,
        val isGroupSummary: Boolean,
    )
}
