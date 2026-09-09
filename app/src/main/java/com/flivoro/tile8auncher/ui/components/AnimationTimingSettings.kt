package com.flivoro.tile8auncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flivoro.tile8auncher.data.LaunchTiming
import com.flivoro.tile8auncher.data.TimeCurve
import com.flivoro.tile8auncher.ui.animation.WindowsLaunchMotion
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

private val SettingsPurple = Color(0xFF5133AB)
private val SettingsText = Color(0xFF222222)
private val SettingsMuted = Color(0xFF666666)
private val SettingsBorder = Color(0xFFD0D0D0)
private val SettingsPanel = Color(0xFFFFFFFF)

@Composable
fun AnimationTimingSettings(
    timing: LaunchTiming,
    onTimingChange: (LaunchTiming) -> Unit,
    modifier: Modifier = Modifier,
    referenceProgress: (Float) -> Float = WindowsLaunchMotion::rotationFractionAtProgress,
) {
    val safeTiming = timing.sanitized()
    var curveMenuExpanded by remember { mutableStateOf(false) }

    fun updateTiming(next: LaunchTiming) {
        onTimingChange(next.sanitized())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Motion timing",
            style = WindowsTypography.titleLarge.copy(
                color = SettingsPurple,
                fontSize = 20.sp,
            ),
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "Choose how the launch speeds up, slows down, or holds.",
            style = WindowsTypography.bodyMedium.copy(
                color = SettingsMuted,
                fontSize = 13.sp,
            ),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (curveMenuExpanded) SettingsPurple else SettingsBorder)
                    .background(SettingsPanel)
                    .clickable { curveMenuExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = safeTiming.curve.displayName,
                        style = WindowsTypography.titleMedium.copy(
                            color = SettingsText,
                            fontSize = 15.sp,
                        ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = safeTiming.curve.description,
                        style = WindowsTypography.bodySmall.copy(
                            color = SettingsMuted,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "⌄",
                    style = WindowsTypography.titleLarge.copy(
                        color = SettingsPurple,
                        fontSize = 22.sp,
                    ),
                )
            }

            DropdownMenu(
                expanded = curveMenuExpanded,
                onDismissRequest = { curveMenuExpanded = false },
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 360.dp)
                    .heightIn(max = 420.dp),
            ) {
                var previousGroup: String? = null
                TimeCurve.selectable.forEach { option ->
                    if (option.group != previousGroup) {
                        Text(
                            text = option.group,
                            style = WindowsTypography.labelSmall.copy(
                                color = SettingsPurple,
                                fontSize = 11.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        previousGroup = option.group
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option.displayName,
                                    style = WindowsTypography.bodyMedium.copy(fontSize = 13.sp, color = SettingsText),
                                    modifier = Modifier.weight(1f),
                                )
                                if (option == safeTiming.curve) {
                                    Text(
                                        text = "✓",
                                        color = SettingsPurple,
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                        },
                        onClick = {
                            curveMenuExpanded = false
                            updateTiming(safeTiming.copy(curve = option))
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CurveDiagram(timing = safeTiming, referenceProgress = referenceProgress)

        Spacer(modifier = Modifier.height(16.dp))

        SettingLabel(label = "Total launch time", value = "${safeTiming.durationMillis} ms")
        Slider(
            value = safeTiming.durationMillis.toFloat(),
            onValueChange = { value ->
                updateTiming(
                    safeTiming.copy(
                        durationMillis = value.roundToInt().coerceIn(
                            LaunchTiming.MIN_DURATION_MILLIS,
                            LaunchTiming.MAX_DURATION_MILLIS,
                        ),
                    ),
                )
            },
            valueRange = LaunchTiming.MIN_DURATION_MILLIS.toFloat()..LaunchTiming.MAX_DURATION_MILLIS.toFloat(),
            steps = 189,
        )
        SliderRangeLabels(
            start = "${LaunchTiming.MIN_DURATION_MILLIS} ms",
            end = "${LaunchTiming.MAX_DURATION_MILLIS} ms",
        )

        if (safeTiming.curve.strengthLabel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingLabel(
                label = safeTiming.curve.strengthLabel,
                value = String.format(Locale.US, "%.1f", safeTiming.strength),
            )
            Slider(
                value = safeTiming.strength,
                onValueChange = { value ->
                    updateTiming(
                        safeTiming.copy(
                            strength = value.coerceIn(0f, LaunchTiming.MAX_STRENGTH),
                        ),
                    )
                },
                valueRange = 0f..LaunchTiming.MAX_STRENGTH,
                steps = 39,
            )
            SliderRangeLabels(start = "Subtle", end = "Strong")
            Text(
                text = "Bouncy and reversing curves can intentionally move back or overshoot.",
                style = WindowsTypography.bodySmall.copy(
                    color = SettingsMuted,
                    fontSize = 11.sp,
                ),
            )
        }

        if (safeTiming.curve.hasStepCount) {
            Spacer(modifier = Modifier.height(12.dp))
            SettingLabel(label = "Number of stops", value = safeTiming.steps.toString())
            Slider(
                value = safeTiming.steps.toFloat(),
                onValueChange = { value ->
                    updateTiming(
                        safeTiming.copy(
                            steps = value.roundToInt().coerceIn(LaunchTiming.MIN_STEPS, LaunchTiming.MAX_STEPS),
                        ),
                    )
                },
                valueRange = LaunchTiming.MIN_STEPS.toFloat()..LaunchTiming.MAX_STEPS.toFloat(),
                steps = LaunchTiming.MAX_STEPS - LaunchTiming.MIN_STEPS - 1,
            )
            SliderRangeLabels(start = "Fewer", end = "More")
        }

        if (safeTiming.curve == TimeCurve.CUSTOM_CUBIC_BEZIER) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Shape your curve",
                style = WindowsTypography.titleMedium.copy(
                    color = SettingsText,
                    fontSize = 15.sp,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set the two points that control the motion. Horizontal values stay between 0 and 1; vertical values may overshoot.",
                style = WindowsTypography.bodySmall.copy(
                    color = SettingsMuted,
                    fontSize = 11.sp,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoordinateField(
                    label = "Point 1 X",
                    value = safeTiming.customX1,
                    range = 0f..1f,
                    onValueChange = { value -> updateTiming(safeTiming.copy(customX1 = value)) },
                    modifier = Modifier.weight(1f),
                )
                CoordinateField(
                    label = "Point 1 Y",
                    value = safeTiming.customY1,
                    range = LaunchTiming.MIN_CUSTOM_Y..LaunchTiming.MAX_CUSTOM_Y,
                    onValueChange = { value -> updateTiming(safeTiming.copy(customY1 = value)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoordinateField(
                    label = "Point 2 X",
                    value = safeTiming.customX2,
                    range = 0f..1f,
                    onValueChange = { value -> updateTiming(safeTiming.copy(customX2 = value)) },
                    modifier = Modifier.weight(1f),
                )
                CoordinateField(
                    label = "Point 2 Y",
                    value = safeTiming.customY2,
                    range = LaunchTiming.MIN_CUSTOM_Y..LaunchTiming.MAX_CUSTOM_Y,
                    onValueChange = { value -> updateTiming(safeTiming.copy(customY2 = value)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Start over with the fitted Windows timing.",
                style = WindowsTypography.bodySmall.copy(
                    color = SettingsMuted,
                    fontSize = 11.sp,
                ),
            )
            TextButton(onClick = { onTimingChange(LaunchTiming()) }) {
                Text(
                    text = "Reset",
                    color = SettingsPurple,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingLabel(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = WindowsTypography.bodyMedium.copy(
                color = SettingsText,
                fontSize = 14.sp,
            ),
        )
        Text(
            text = value,
            style = WindowsTypography.titleMedium.copy(
                color = SettingsPurple,
                fontSize = 14.sp,
            ),
        )
    }
}

@Composable
private fun SliderRangeLabels(start: String, end: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = start,
            style = WindowsTypography.labelSmall.copy(color = SettingsMuted, fontSize = 10.sp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = end,
            style = WindowsTypography.labelSmall.copy(color = SettingsMuted, fontSize = 10.sp),
        )
    }
}

@Composable
private fun CurveDiagram(timing: LaunchTiming, referenceProgress: (Float) -> Float) {
    val previewTransition = rememberInfiniteTransition()
    val rawProgress by previewTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = timing.durationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Curve preview",
            style = WindowsTypography.titleMedium.copy(
                color = SettingsText,
                fontSize = 15.sp,
            ),
        )
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .border(1.dp, SettingsBorder)
                .background(SettingsPanel)
                .padding(9.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                val horizontalStep = size.width / 4f
                val verticalStep = size.height / 4f
                for (index in 1..3) {
                    drawLine(
                        color = Color(0xFFECE8F7),
                        start = Offset(horizontalStep * index, 0f),
                        end = Offset(horizontalStep * index, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawLine(
                        color = Color(0xFFECE8F7),
                        start = Offset(0f, verticalStep * index),
                        end = Offset(size.width, verticalStep * index),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                drawLine(
                    color = Color(0xFFBDB5D8),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )

                val path = Path()
                val samples = 80
                for (index in 0..samples) {
                    val x = index / samples.toFloat()
                    val y = previewValue(timing, x, referenceProgress)
                    val point = Offset(x * size.width, (1f - y) * size.height)
                    if (index == 0) {
                        path.moveTo(point.x, point.y)
                    } else {
                        path.lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    path = path,
                    color = SettingsPurple,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )

                val dotProgress = previewValue(timing, rawProgress, referenceProgress)
                drawCircle(
                    color = SettingsPurple,
                    radius = 5.dp.toPx(),
                    center = Offset(rawProgress * size.width, (1f - dotProgress) * size.height),
                )
            }
        }
    }
}

private fun previewValue(timing: LaunchTiming, rawProgress: Float, referenceProgress: (Float) -> Float): Float =
    if (timing.curve == TimeCurve.REFERENCE) {
        referenceProgress(rawProgress)
    } else {
        timing.transform(rawProgress)
    }

@Composable
private fun CoordinateField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(formatCoordinate(value)) }

    LaunchedEffect(value) {
        val parsed = text.toFloatOrNull()
        if (parsed == null || !parsed.isFinite() || abs(parsed - value) > .0001f) {
            text = formatCoordinate(value)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { nextText ->
            text = nextText
            nextText.toFloatOrNull()
                ?.takeIf { it.isFinite() }
                ?.let { parsed -> onValueChange(parsed.coerceIn(range.start, range.endInclusive)) }
        },
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = WindowsTypography.bodyMedium.copy(
            color = SettingsText,
            fontSize = 13.sp,
        ),
        modifier = modifier,
    )
}

private fun formatCoordinate(value: Float): String =
    String.format(Locale.US, "%.2f", value)
