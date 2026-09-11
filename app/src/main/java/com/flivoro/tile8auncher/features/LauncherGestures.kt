package com.flivoro.tile8auncher.features

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

/**
 * Observes taps at the Final pointer pass and never consumes them. This lets existing tile press,
 * horizontal scroll, Start/All Apps vertical navigation and Charms gestures keep ownership of the
 * exact same input stream while still enabling an optional launcher-level double-tap shortcut.
 */
fun Modifier.passiveDoubleTap(onDoubleTap: () -> Unit): Modifier = pointerInput(onDoubleTap) {
    var previousTapTime = 0L
    var previousTapPosition = Offset.Unspecified
    while (true) {
        val downEvent = awaitPointerEventScope { awaitPointerEvent(PointerEventPass.Final) }
        val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
        val start = down.position
        val startTime = down.uptimeMillis
        var maxTravel = 0f
        var upTime = startTime
        var completed = false
        awaitPointerEventScope {
            while (!completed) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull() ?: continue
                maxTravel = maxOf(
                    maxTravel,
                    hypot(change.position.x - start.x, change.position.y - start.y),
                )
                if (!change.pressed) {
                    upTime = change.uptimeMillis
                    completed = true
                }
            }
        }
        val duration = upTime - startTime
        if (duration > 220L || maxTravel > 28f) continue
        val hasPreviousPosition = previousTapPosition != Offset.Unspecified
        val isSecond = previousTapTime > 0L &&
            startTime - previousTapTime in 40L..360L &&
            hasPreviousPosition &&
            hypot(start.x - previousTapPosition.x, start.y - previousTapPosition.y) <= 56f
        if (isSecond) {
            previousTapTime = 0L
            previousTapPosition = Offset.Unspecified
            onDoubleTap()
        } else {
            previousTapTime = upTime
            previousTapPosition = start
        }
    }
}

fun performStartDoubleTapAction(
    context: Context,
    action: StartDoubleTapAction,
    onSearch: () -> Unit,
    onAllApps: () -> Unit,
    onCharms: () -> Unit,
) {
    when (action) {
        StartDoubleTapAction.NONE -> Unit
        StartDoubleTapAction.SEARCH -> onSearch()
        StartDoubleTapAction.ALL_APPS -> onAllApps()
        StartDoubleTapAction.CHARMS -> onCharms()
        StartDoubleTapAction.LOCK_DEVICE -> {
            if (!MosaicAccessibilityService.lockDevice()) {
                Toast.makeText(
                    context,
                    "Enable Mosaic launcher gestures in Accessibility to use double-tap lock.",
                    Toast.LENGTH_LONG,
                ).show()
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
        }
    }
}
