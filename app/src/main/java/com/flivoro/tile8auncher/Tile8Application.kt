package com.flivoro.tile8auncher

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import com.flivoro.tile8auncher.ui.animation.StartEntranceKind
import com.flivoro.tile8auncher.ui.animation.StartEntranceMotion
import com.flivoro.tile8auncher.ui.components.WindowsWallpaper
import java.util.WeakHashMap

/**
 * Keeps a pre-rendered wallpaper-only surface above the launcher while the display is asleep.
 *
 * Android/OEM compositors are allowed to reuse the last submitted app buffer for the first frame
 * after keyguard dismissal. Merely changing Compose state in ACTION_SCREEN_OFF is therefore not a
 * strong enough guarantee: the old settled Start buffer can be flashed before Compose submits the
 * hidden frame. This application-owned curtain is a separate, always-laid-out hardware layer. Its
 * alpha is changed directly on the View at screen-off, so the buffer visible at unlock contains
 * only the Start wallpaper.
 *
 * USER_PRESENT and Activity resume do not have a guaranteed order across Android/OEM builds. The
 * curtain therefore remains held until MainActivity is actually RESUMED and keyguard is gone, then
 * waits two display frames: one hidden frame for Compose to submit Start progress zero and one frame
 * for the measured entrance to begin. The release is compositor-only (no fade), so no extra motion
 * is introduced into the Windows animation itself.
 */
class Tile8Application : Application(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: SharedPreferences
    private val curtains = WeakHashMap<Activity, ComposeView>()
    private val resumedActivities = WeakHashMap<Activity, Boolean>()
    private var wallpaperStyle by mutableIntStateOf(0)
    private var holdCurtain = false
    private var curtainGeneration = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON -> showCurtainImmediately()

                Intent.ACTION_USER_PRESENT -> {
                    // MainActivity may not be resumed yet. releaseCurtainAfterPreparedFrames()
                    // only schedules resumed activities; onActivityResumed retries if necessary.
                    releaseCurtainAfterPreparedFrames()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        wallpaperStyle = prefs.getInt(KEY_WALLPAPER_STYLE, 0).coerceIn(0, 9)
        prefs.registerOnSharedPreferenceChangeListener(this)
        holdCurtain = deviceRequiresCurtain()

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity) attachCurtain(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity) return
                resumedActivities[activity] = true
                attachCurtain(activity)
                when {
                    deviceRequiresCurtain() -> showCurtainImmediately()
                    holdCurtain -> releaseCurtainAfterPreparedFrames()
                    else -> hideCurtain(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities.remove(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                resumedActivities.remove(activity)
                curtains.remove(activity)?.disposeComposition()
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == KEY_WALLPAPER_STYLE) {
            wallpaperStyle = sharedPreferences.getInt(KEY_WALLPAPER_STYLE, 0).coerceIn(0, 9)
        }
    }

    private fun attachCurtain(activity: MainActivity) {
        if (curtains.containsKey(activity)) return

        val curtain = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.VISIBLE
            alpha = if (holdCurtain || deviceRequiresCurtain()) 1f else 0f
            isClickable = alpha > 0f
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            // Keep the wallpaper buffer resident so screen-off can switch compositor alpha without
            // depending on a new Compose draw landing before the display powers down.
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setContent {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val initialTravel = StartEntranceMotion.backgroundTravelFraction(
                        progress = 0f,
                        kind = StartEntranceKind.STARTUP,
                    )
                    WindowsWallpaper(
                        modifier = Modifier.fillMaxSize(),
                        wallpaperStyle = wallpaperStyle,
                        enabled = true,
                        // WindowsWallpaper converts scroll to art translation using each layer's
                        // depth rate. A negative synthetic scroll places the art at the same
                        // parallax-depth start pose used by the unlock entrance while its base
                        // color remains stationary.
                        scrollOffsetPx = { -viewportWidthPx * initialTravel },
                    )
                }
            }
        }

        activity.addContentView(
            curtain,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        curtain.bringToFront()
        curtains[activity] = curtain
    }

    private fun showCurtainImmediately() {
        holdCurtain = true
        curtainGeneration++
        curtains.forEach { (_, curtain) ->
            curtain.animate().cancel()
            curtain.visibility = View.VISIBLE
            curtain.alpha = 1f
            curtain.isClickable = true
            curtain.bringToFront()
            curtain.invalidate()
        }
    }

    private fun releaseCurtainAfterPreparedFrames() {
        if (deviceRequiresCurtain()) return
        val generation = ++curtainGeneration
        val resumed = resumedActivities.keys.toList()
        if (resumed.isEmpty()) return
        resumed.forEach { activity ->
            val curtain = curtains[activity] ?: return@forEach
            scheduleReleaseFrame(activity, curtain, generation, framesRemaining = PREPARE_FRAMES)
        }
    }

    private fun scheduleReleaseFrame(
        activity: Activity,
        curtain: ComposeView,
        generation: Int,
        framesRemaining: Int,
    ) {
        curtain.postOnAnimation releaseFrame@{
            if (generation != curtainGeneration) return@releaseFrame
            if (activity !in resumedActivities) return@releaseFrame
            if (deviceRequiresCurtain()) {
                // Some keyguards report locked for a frame or two after USER_PRESENT. Keep the
                // wallpaper up and retry instead of risking either a flash or a permanently stuck
                // curtain because one early release callback happened to lose the race.
                scheduleReleaseFrame(
                    activity = activity,
                    curtain = curtain,
                    generation = generation,
                    framesRemaining = framesRemaining.coerceAtLeast(1),
                )
                return@releaseFrame
            }
            if (framesRemaining > 1) {
                scheduleReleaseFrame(activity, curtain, generation, framesRemaining - 1)
                return@releaseFrame
            }

            holdCurtain = false
            hideCurtain(activity)
        }
    }

    private fun hideCurtain(activity: Activity) {
        val curtain = curtains[activity] ?: return
        curtain.animate().cancel()
        curtain.alpha = 0f
        curtain.isClickable = false
    }

    private fun deviceRequiresCurtain(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return power?.isInteractive == false || keyguard?.isKeyguardLocked == true
    }

    companion object {
        private const val PREFS_NAME = "tile8_launcher_prefs_v2"
        private const val KEY_WALLPAPER_STYLE = "wallpaper_style"
        private const val PREPARE_FRAMES = 2
    }
}
