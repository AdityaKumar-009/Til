package com.flivoro.tile8auncher.features

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper for the launcher gesture "double tap to lock". Android requires the user to
 * explicitly enable an accessibility service for third-party launchers to perform the lock action.
 */
class MosaicAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: MosaicAccessibilityService? = null

        fun lockDevice(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
        }

        fun isConnected(): Boolean = instance != null
    }
}
