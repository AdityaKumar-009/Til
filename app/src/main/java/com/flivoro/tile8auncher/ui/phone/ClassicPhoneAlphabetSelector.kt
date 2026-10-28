package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

// Exact Disco letter order, in four columns and seven rows (script.js).
private val discoAlphabet = "#abcdefghijklmnopqrstuvwxyz".toList().chunked(4)
private val letterEnter = CubicBezierEasing(0f, 0f, .58f, 1f)
private val letterExit = CubicBezierEasing(.42f, 0f, 1f, 1f)

@Composable
internal fun DiscoGlobeSymbol(color: Color) {
    // Disco's final private-use glyph is font-dependent on Android. Draw the
    // same globe shape ourselves so the last cell never displays a blank box.
    Canvas(Modifier.size(35.dp)) {
        val stroke = 2.3.dp.toPx()
        val pad = stroke / 2f
        val left = pad
        val top = pad
        val w = size.width - stroke
        val h = size.height - stroke
        drawOval(color, topLeft = Offset(left, top), size = Size(w, h),
            style = Stroke(stroke))
        drawOval(color, topLeft = Offset(size.width * .32f, top),
            size = Size(size.width * .36f, h), style = Stroke(stroke))
        drawLine(color, Offset(left, size.height * .5f),
            Offset(size.width - pad, size.height * .5f), strokeWidth = stroke)
    }
}

@Composable
private fun DiscoLetterRow(
    letters: List<Char>,
    index: Int,
    cellSize: Dp,
    visible: Boolean,
    available: Set<String>,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    val rotation = remember { Animatable(90f) }
    val view = LocalView.current
    LaunchedEffect(visible) {
        if (visible) {
            delay(index * 20L)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            rotation.animateTo(0f, tween(75, easing = letterEnter))
        } else {
            delay(60L + index * 20L)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            rotation.animateTo(-90f, tween(75, easing = letterExit))
        }
    }
    Row(Modifier.graphicsLayer {
        rotationX = rotation.value
        cameraDistance = 1000f * density
        transformOrigin = TransformOrigin(.5f, .5f)
    }.testTag("classic-letter-row-$index")) {
        letters.forEach { letter ->
            val isGlobe = letter == ''
            val label = letter.toString()
            val group = if (isGlobe) "&" else label.uppercase(Locale.ROOT)
            val enabled = group in available
            Box(
                Modifier.size(cellSize).padding(6.dp)
                    .background(if (enabled) accent else Color(0xFF222222))
                    .clickable(enabled = visible && enabled) { onSelect(group) }
                    .testTag("classic-letter-$label")
                    .then(if (isGlobe) Modifier else Modifier.padding(start = 8.dp, bottom = 5.dp)),
                contentAlignment = if (isGlobe) Alignment.Center else Alignment.BottomStart,
            ) {
                if (isGlobe) DiscoGlobeSymbol(Color.White)
                else Text(
                    label,
                    color = if (enabled) Color.White else Color(0xFF696969),
                    fontSize = 45.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun ClassicPhoneAlphabetSelector(
    visible: Boolean,
    availableLetters: Set<String>,
    accent: Color,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) mounted = true
        else {
            delay(500) // Disco keeps the outgoing overlay until the 3D rows finish.
            mounted = false
        }
    }
    val scrim by animateFloatAsState(
        if (visible) .5f else 0f, tween(200), label = "disco-letters-scrim",
    )
    if (!mounted) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
                .clickable(enabled = visible, onClick = onDismiss)
                .testTag("classic-phone-letter-selector"),
            contentAlignment = Alignment.Center,
        ) {
            // CSS original: 4 columns with 6px all-round margins. Limit the
            // seven-row board by BOTH available width and height so every letter
            // tile stays square and the complete board is centrally aligned.
            val usableWidth = minOf(maxWidth, 500.dp) - 36.dp
            val usableHeight = maxHeight - 36.dp
            val cell = minOf(usableWidth / 4f, usableHeight / 7f)
                .coerceAtLeast(32.dp)
            Column {
                discoAlphabet.forEachIndexed { index, letters ->
                    DiscoLetterRow(
                        letters = letters,
                        index = index,
                        cellSize = cell,
                        visible = visible,
                        available = availableLetters,
                        accent = accent,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}
