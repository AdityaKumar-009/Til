package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/**
 * DiscoLauncher-style alphabet jump overlay, scoped to the classic phone skin.
 * Seven 3-D rows feather into place and out; the dialog remains mounted for exit.
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
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            reveal.animateTo(1f, tween(250, easing = CubicBezierEasing(.25f, 0f, .25f, 1f)))
        } else if (mounted) {
            reveal.animateTo(0f, tween(230, easing = CubicBezierEasing(.75f, 0f, 1f, 1f)))
            mounted = false
        }
    }
    if (!mounted) return

    val letters = listOf(
        "#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L",
        "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .58f * reveal.value))
                .testTag("classic-phone-letter-selector")
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                letters.chunked(4).forEachIndexed { rowIndex, rowLetters ->
                    val fraction = ((reveal.value - rowIndex * .095f) /
                        (1f - rowIndex * .095f)).coerceIn(0f, 1f)
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer {
                            rotationX = 90f * (1f - fraction)
                            alpha = fraction
                            cameraDistance = 1200f
                            transformOrigin = TransformOrigin(.5f, .5f)
                        },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowLetters.forEach { letter ->
                            val enabled = letter in availableLetters
                            Box(
                                Modifier.weight(1f).height(58.dp)
                                    .background(if (enabled) accent else Color(0xFF292929))
                                    .clickable(enabled = visible && enabled) { onSelect(letter) }
                                    .testTag("classic-letter-$letter")
                                    .padding(start = 7.dp, bottom = 4.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(
                                    text = letter.lowercase(),
                                    color = if (enabled) Color.White else Color(0xFF646464),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Light,
                                )
                            }
                        }
                        repeat(4 - rowLetters.size) {
                            Box(Modifier.weight(1f).height(58.dp))
                        }
                    }
                }
            }
        }
    }
}
