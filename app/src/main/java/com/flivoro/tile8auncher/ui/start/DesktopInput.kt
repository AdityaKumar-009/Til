package com.flivoro.tile8auncher.ui.start

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

/** Additional Windows input paths; one-finger scrolling and tile geometry are untouched. */
@Composable
internal fun Modifier.desktopStartInput(
    enabled: Boolean,
    onZoom: (Boolean) -> Unit,
    onEscape: () -> Boolean,
    onSearch: (String) -> Unit,
    onCharms: () -> Unit,
    onScroll: (Float) -> Unit,
): Modifier {
    val zoom = rememberUpdatedState(onZoom)
    val scroll = rememberUpdatedState(onScroll)
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) focus.requestFocus() }
    return this.focusRequester(focus).onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (!enabled || key.action != AndroidKeyEvent.ACTION_DOWN) false
        else when {
            key.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE -> onEscape()
            key.isCtrlPressed && key.keyCode == AndroidKeyEvent.KEYCODE_MINUS -> { onZoom(false); true }
            key.isCtrlPressed && key.keyCode in listOf(AndroidKeyEvent.KEYCODE_EQUALS,
                AndroidKeyEvent.KEYCODE_PLUS, AndroidKeyEvent.KEYCODE_0) -> { onZoom(true); true }
            key.isCtrlPressed && key.keyCode == AndroidKeyEvent.KEYCODE_F -> { onSearch(""); true }
            key.isMetaPressed && key.keyCode == AndroidKeyEvent.KEYCODE_C -> { onCharms(); true }
            !key.isCtrlPressed && !key.isAltPressed && !key.isMetaPressed &&
                key.unicodeChar > 0 && Character.isLetterOrDigit(key.unicodeChar) -> {
                onSearch(String(Character.toChars(key.unicodeChar))); true
            }
            else -> false // Compose focus traversal owns arrows, Tab and Enter.
        }
    }.focusable(enabled).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            var scale = 1f
            var pinching = false
            var committed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) {
                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (delta != 0f) {
                        if (event.keyboardModifiers.isCtrlPressed) zoom.value(delta < 0f)
                        else scroll.value(delta)
                        event.changes.forEach { it.consume() }
                    }
                }
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    scale *= event.calculateZoom()
                    if (!committed && (scale < .82f || scale > 1.18f)) {
                        zoom.value(scale > 1f)
                        committed = true
                    }
                }
                if (pinching) event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) {
                    scale = 1f; pinching = false; committed = false
                }
            }
        }
    }
}
