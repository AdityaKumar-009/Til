package com.flivoro.tile8auncher.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flivoro.tile8auncher.ui.theme.WindowsTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Windows 8.1-style visual lock surface for Mosaic Launcher.
 *
 * The panel follows the finger one-for-one. Releasing past the historical swipe direction settles
 * the panel off the top edge; a downward gesture optionally opens the camera, matching Windows 8.1.
 */
@Composable
fun Windows81LockScreen(
    cameraGestureEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableIntStateOf(1) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Windows 8.1's lock screen is an immersive full-window surface. Hide Android chrome only while
    // this visual layer is present, then restore it immediately when Start is revealed.
    val activity = context as? Activity
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L - (nowMillis % 1_000L))
        }
    }

    val locale = Locale.getDefault()
    val timePattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    val timeText = remember(nowMillis / 1_000L, locale, timePattern) {
        SimpleDateFormat(timePattern, locale).format(Date(nowMillis))
    }
    val dateText = remember(nowMillis / 60_000L, locale) {
        SimpleDateFormat("EEEE, MMMM d", locale).format(Date(nowMillis))
    }
    val batteryLevel = remember(nowMillis / 15_000L) { readBatteryLevel(context) }
    val networkConnected = remember(nowMillis / 5_000L) { isNetworkConnected(context) }

    suspend fun settleTo(target: Float, durationMillis: Int) {
        val start = offsetY
        animate(
            initialValue = start,
            targetValue = target,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        ) { value, _ ->
            offsetY = value
        }
    }

    fun dismissUp() {
        scope.launch {
            settleTo(-heightPx.toFloat(), 245)
            onDismiss()
        }
    }

    fun openCameraDown() {
        scope.launch {
            settleTo(heightPx.toFloat(), 245)
            val launched = launchCamera(context)
            if (!launched) {
                settleTo(0f, 210)
            } else {
                // Reset behind the external camera so returning to Mosaic presents a complete panel.
                delay(320)
                offsetY = 0f
            }
        }
    }

    val dragState = rememberDraggableState { delta ->
        val min = -heightPx.toFloat()
        val max = if (cameraGestureEnabled) heightPx.toFloat() else 0f
        offsetY = (offsetY + delta).coerceIn(min, max)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val h = heightPx.toFloat()
                    when {
                        offsetY <= -h * 0.16f || velocity <= -1_100f -> dismissUp()
                        cameraGestureEnabled &&
                            (offsetY >= h * 0.16f || velocity >= 1_100f) -> openCameraDown()
                        else -> scope.launch { settleTo(0f, 210) }
                    }
                },
            )
            .clickable(onClick = ::dismissUp)
            .semantics {
                contentDescription = if (cameraGestureEnabled) {
                    "Windows 8.1 lock screen. Swipe up to open Start or swipe down for camera."
                } else {
                    "Windows 8.1 lock screen. Swipe up to open Start."
                }
            },
    ) {
        Windows81DefaultLockArtwork(Modifier.fillMaxSize())

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val portrait = maxHeight > maxWidth
            val timeSize = if (portrait) 70.sp else 82.sp
            val dateSize = if (portrait) 23.sp else 27.sp
            val horizontalPadding = if (portrait) 28.dp else 46.dp
            val bottomPadding = if (portrait) 62.dp else 48.dp

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = horizontalPadding, bottom = bottomPadding),
            ) {
                TextWithWindowsLockStyle(
                    text = timeText,
                    sizeSp = timeSize.value,
                    weight = FontWeight.Light,
                )
                TextWithWindowsLockStyle(
                    text = dateText,
                    sizeSp = dateSize.value,
                    weight = FontWeight.Light,
                )
                Spacer(Modifier.height(13.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NetworkStatusGlyph(connected = networkConnected)
                    BatteryStatusGlyph(level = batteryLevel)
                }
            }
        }
    }
}

@Composable
private fun TextWithWindowsLockStyle(
    text: String,
    sizeSp: Float,
    weight: FontWeight,
) {
    androidx.compose.material3.Text(
        text = text,
        style = WindowsTypography.displayLarge.copy(
            color = Color.White,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            letterSpacing = (-0.55).sp,
        ),
    )
}

/**
 * Programmatic reconstruction of the recognizable Windows 8.1 default lock artwork. Keeping the
 * art vector-like makes it resolution independent and avoids stretching a desktop bitmap on phones.
 */
@Composable
private fun Windows81DefaultLockArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Color(0xFF6D6B65))

        // Soft dark corners visible in the original default artwork.
        drawRect(Color(0x1C000000))

        val colors = listOf(
            Color(0xFF2F8BC4), Color(0xFF1676B7), Color(0xFF185FA9),
            Color(0xFF7843A4), Color(0xFFC12E73), Color(0xFFDE3554),
            Color(0xFFEF4939), Color(0xFFF26B31), Color(0xFFF49A28),
            Color(0xFFF6C72A), Color(0xFFC8D72C), Color(0xFF7DBD38),
            Color(0xFF39A85B), Color(0xFF218B68), Color(0xFF147A75),
            Color(0xFF167C92), Color(0xFF2B729F), Color(0xFF53678B),
        )

        val bandHeight = size.height * 0.031f
        val slope = size.height * 0.52f
        val startY = size.height * 0.05f
        val left = -size.width * 0.34f
        val right = size.width * 1.34f

        colors.forEachIndexed { index, color ->
            val y = startY + bandHeight * index
            val path = Path().apply {
                moveTo(left, y)
                lineTo(right, y + slope)
                lineTo(right, y + slope + bandHeight * 1.34f)
                lineTo(left, y + bandHeight * 1.34f)
                close()
            }
            drawPath(path, color)

            // Fine separators give the layered paper/ribbon texture seen in the original image.
            val separator = Path().apply {
                moveTo(left, y + bandHeight * 1.24f)
                lineTo(right, y + slope + bandHeight * 1.24f)
            }
            drawPath(separator, Color(0x35000000), style = Stroke(width = 1.1f))
        }

        // A subtle bright rim through the center keeps the artwork from reading as flat color bars.
        drawLine(
            color = Color(0x26FFFFFF),
            start = Offset(-size.width * 0.25f, size.height * 0.38f),
            end = Offset(size.width * 1.25f, size.height * 0.90f),
            strokeWidth = size.height * 0.018f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NetworkStatusGlyph(connected: Boolean) {
    Canvas(Modifier.size(width = 18.dp, height = 16.dp)) {
        val alpha = if (connected) 1f else 0.48f
        val barWidth = size.width * 0.13f
        val gap = size.width * 0.09f
        repeat(4) { index ->
            val h = size.height * (0.28f + 0.18f * index)
            drawRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = Offset(index * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
            )
        }
        if (!connected) {
            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = 1.7f,
            )
        }
    }
}

@Composable
private fun BatteryStatusGlyph(level: Int) {
    Canvas(Modifier.size(width = 25.dp, height = 14.dp)) {
        val bodyWidth = size.width * 0.84f
        val bodyHeight = size.height * 0.72f
        val top = (size.height - bodyHeight) / 2f
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, top),
            size = Size(bodyWidth, bodyHeight),
            style = Stroke(width = 1.5f),
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(bodyWidth + 1f, size.height * 0.36f),
            size = Size(size.width - bodyWidth - 1f, size.height * 0.28f),
        )
        val safe = level.coerceIn(0, 100) / 100f
        drawRect(
            color = Color.White,
            topLeft = Offset(2.6f, top + 2.6f),
            size = Size((bodyWidth - 5.2f) * safe, (bodyHeight - 5.2f).coerceAtLeast(1f)),
        )
    }
}

private fun readBatteryLevel(context: Context): Int {
    val manager = context.getSystemService(BatteryManager::class.java)
    return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 } ?: 100
}

private fun isNetworkConnected(context: Context): Boolean = try {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
} catch (_: SecurityException) {
    false
}

private fun launchCamera(context: Context): Boolean {
    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
    if (intent.resolveActivity(context.packageManager) == null) return false
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
