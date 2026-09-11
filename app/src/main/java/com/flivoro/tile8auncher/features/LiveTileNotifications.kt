package com.flivoro.tile8auncher.features

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/**
 * Minimal notification projection used by Start live tiles and the Windows 8.1 lock screen.
 * It never alters tile launch/navigation state; the surface simply observes these immutable values.
 */
data class LiveTileNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val count: Int,
    val postTime: Long,
    val key: String,
)

object LiveTileNotificationStore {
    private const val SNAPSHOT_PREFIX = "feature_live_notification_"

    private val live = mutableStateMapOf<String, LiveTileNotification>()

    fun latest(context: Context, packageName: String?): LiveTileNotification? {
        if (packageName.isNullOrBlank()) return null
        live[packageName]?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val raw = prefs.getString(SNAPSHOT_PREFIX + packageName, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            LiveTileNotification(
                packageName = packageName,
                title = json.optString("title"),
                text = json.optString("text"),
                count = json.optInt("count", 1).coerceAtLeast(1),
                postTime = json.optLong("postTime", 0L),
                key = json.optString("key"),
            )
        }.getOrNull()?.also { live[packageName] = it }
    }

    fun current(): List<LiveTileNotification> =
        live.values.sortedByDescending(LiveTileNotification::postTime)

    internal fun publish(context: Context, value: LiveTileNotification) {
        live[value.packageName] = value
        val json = JSONObject().apply {
            put("title", value.title)
            put("text", value.text)
            put("count", value.count)
            put("postTime", value.postTime)
            put("key", value.key)
        }
        context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        ).edit().putString(SNAPSHOT_PREFIX + value.packageName, json.toString()).apply()
    }

    internal fun clear(context: Context, packageName: String) {
        live.remove(packageName)
        context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        ).edit().remove(SNAPSHOT_PREFIX + packageName).apply()
    }
}

class MosaicNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications
            ?.groupBy(StatusBarNotification::getPackageName)
            ?.values
            ?.forEach { publishNewest(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishNewest(activeNotifications?.filter { it.packageName == sbn.packageName }.orEmpty())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val remaining = activeNotifications?.filter { it.packageName == sbn.packageName }.orEmpty()
        if (remaining.isEmpty()) {
            LiveTileNotificationStore.clear(this, sbn.packageName)
        } else {
            publishNewest(remaining)
        }
    }

    private fun publishNewest(items: List<StatusBarNotification>) {
        val newest = items.maxByOrNull(StatusBarNotification::getPostTime) ?: return
        val extras = newest.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
        ).mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }.firstOrNull().orEmpty()

        LiveTileNotificationStore.publish(
            this,
            LiveTileNotification(
                packageName = newest.packageName,
                title = title,
                text = text,
                count = items.size.coerceAtLeast(1),
                postTime = newest.postTime,
                key = newest.key,
            ),
        )
    }
}
