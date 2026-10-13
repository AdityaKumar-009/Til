package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

// Direct port of DiscoLauncher src/script.js and styles/pages/appList.scss:
// # + a..z + the symbols tile, laid out in seven 4-letter flex rows.
// Each row starts at +90 degrees, enters in 75ms with 20ms successive delays,
// and exits to -90 degrees with a 60ms lead-in and the same feather.
private val discoAlphabet = "#abcdefghijklmnopqrstuvwxyz".toList().chunked(4)
private val discoLetterEnter = CubicBezierEasing(0f, 0f, .58f, 1f)
private val discoLetterExit = CubicBezierEasing(.42f, 0f, 1f, 1f)

@Composable
private fun DiscoLetterRow(
    row: List<Char>,
    rowIndex: Int,
    visible: Boolean,
    availableLetters: Set<String>,
    accent: Color,
    onSelect: (String) -> Unit,
) {
    val rotation = remember { Animatable(90f) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(rowIndex * 20L)
            rotation.animateTo(0f, tween(75, easing = discoLetterEnter))
        } else {
            delay(60L + rowIndex * 20L)
            rotation.animateTo(-90f, tween(75, easing = discoLetterExit))
        }
    }
    Row(
        Modifier.fillMaxWidth().fillMaxHeight()
            .graphicsLayer {
                rotationX = rotation.value
                cameraDistance = 1000f * density
                transformOrigin = TransformOrigin(.5f, .5f)
            }
            .testTag("classic-letter-row-$rowIndex"),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        row.forEach { char ->
            val raw = char.toString()
            val category = if (char == '') "#" else raw.uppercase(Locale.ROOT)
            val available = category in availableLetters
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .padding(6.dp)
                    .background(if (available) accent else Color(0xFF272727))
                    .clickable(enabled = visible && available) { onSelect(category) }
                    .testTag("classic-letter-$raw")
                    .padding(start = 8.dp, bottom = 5.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    raw,
                    color = if (available) Color.White else Color(0xFF656565),
                    fontSize = 45.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The overlay is kept mounted for the source's 500ms outgoing state so
 * Back and an alphabet selection both play the actual 7-row exit.
 */
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
            delay(500)
            mounted = false
        }
    }
    val scrim by animateFloatAsState(
        targetValue = if (visible) .5f else 0f,
        animationSpec = tween(200),
        label = "disco-letter-selector-scrim",
    )
    if (!mounted) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(enabled = visible, onClick = onDismiss)
                .testTag("classic-phone-letter-selector"),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 500.dp).fillMaxWidth().fillMaxHeight()
                    .padding(18.dp),
            ) {
                discoAlphabet.forEachIndexed { index, row ->
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        DiscoLetterRow(
                            row = row,
                            rowIndex = index,
                            visible = visible,
                            availableLetters = availableLetters,
                            accent = accent,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}
