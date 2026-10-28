package com.flivoro.tile8auncher.ui.phone

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager

internal data class PhoneAppRoles(val dialer: String? = null, val messages: String? = null) {
    fun glyph(packageName: String?, label: String): String? = when {
        packageName != null && packageName == dialer -> "wp81_phone"
        packageName != null && packageName == messages -> "wp81_messages"
        dialer == null && (label.equals("Phone", true) || label.equals("Dialer", true)) -> "wp81_phone"
        messages == null && (label.equals("Messages", true) || label.equals("Messaging", true)) -> "wp81_messages"
        else -> null
    }
}

/** Read system roles without call/SMS contents, runtime permissions or OEM package guesses. */
internal fun readPhoneAppRoles(context: Context): PhoneAppRoles = PhoneAppRoles(
    dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
    messages = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
)
