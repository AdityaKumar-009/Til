package com.flivoro.tile8auncher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android's closest equivalent to the Windows live-tile notification channel.
 * No notification content is persisted; the in-memory snapshot disappears with the process.
 */
class LauncherNotificationService : NotificationListenerService() {
    override fun onListenerConnected() {
        publish(activeNotifications?.toList().orEmpty())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publish(activeNotifications?.toList().orEmpty())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publish(activeNotifications?.toList().orEmpty())
    }

    private fun publish(active: List<StatusBarNotification>) {
        val grouped = active
            .filter { it.packageName != packageName }
            .groupBy { it.packageName }
            .mapValues { (_, notifications) ->
                val latest = notifications.maxByOrNull { it.postTime }
                val extras = latest?.notification?.extras
                TileNotificationSummary(
                    count = notifications.sumOf { notificationCount(it.notification) }.coerceAtLeast(1),
                    title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                )
            }
        _notifications.value = grouped
    }

    private fun notificationCount(notification: Notification): Int =
        notification.number.takeIf { it > 0 } ?: 1

    companion object {
        private val _notifications = MutableStateFlow<Map<String, TileNotificationSummary>>(emptyMap())
        val notifications: StateFlow<Map<String, TileNotificationSummary>> = _notifications
    }
}

data class TileNotificationSummary(
    val count: Int,
    val title: String,
    val text: String,
)
