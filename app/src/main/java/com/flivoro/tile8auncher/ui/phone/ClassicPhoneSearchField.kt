package com.flivoro.tile8auncher.ui.phone

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Floating WP8.1 search field. The All Apps list slides underneath it independently. */
@Composable
internal fun ClassicPhoneSearchField(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    classicUnit: Float,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) 400 else 100,
            easing = if (visible) CubicBezierEasing(.5f, 0f, 0f, 1f)
                else CubicBezierEasing(.5f, 0f, 1f, 1f),
        ),
        label = "classic-search-field-slide",
    )
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            delay(75)
            focusRequester.requestFocus()
        }
    }
    if (!visible && progress <= .001f) return

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        enabled = enabled && visible,
        singleLine = true,
        textStyle = TextStyle(
            color = Color.Black,
            fontSize = (23f * classicUnit).sp,
            fontWeight = FontWeight.Light,
        ),
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                translationY = -(1f - progress) * 52f * classicUnit * density
            }
            .border((3f * classicUnit).dp, accent)
            .background(Color.White)
            .focusRequester(focusRequester)
            .testTag("classic-phone-search-field")
            .padding(horizontal = (10f * classicUnit).dp, vertical = (6f * classicUnit).dp),
        decorationBox = { innerField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "search",
                            color = Color(0xFF646464),
                            fontSize = (22f * classicUnit).sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                    innerField()
                }
                Text(
                    "×", color = Color.DarkGray, fontSize = (26f * classicUnit).sp,
                    modifier = Modifier.clickable(enabled = visible, onClick = onClose)
                        .padding(horizontal = (6f * classicUnit).dp),
                )
            }
        },
    )
}
