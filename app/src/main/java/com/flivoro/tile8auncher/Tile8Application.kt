package com.flivoro.tile8auncher

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.view.MotionEvent
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
 * Pre-rendered wallpaper-only guard used while the display/keyguard owns the screen.
 *
 * The guard stays allocated and hardware-backed so ACTION_SCREEN_OFF / ACTION_SCREEN_ON only need
 * an alpha/visibility property change; the launcher does not wait for a new Compose frame before
 * hiding Start content. It may never expose an uninitialised white surface: a Windows-purple base
 * is installed on the View itself before Compose renders the full selected wallpaper.
 *
 * USER_PRESENT, keyguard dismissal and Activity resume can arrive in different orders on OEM
 * builds. Release is therefore state-driven rather than one-shot: once unlock was requested, a
 * resumed activity keeps checking until the device is genuinely interactive and unlocked, waits
 * two prepared display frames, then removes the guard with no fade. A subsequent screen-off bumps
 * the generation and cancels every pending release callback.
 */
class Tile8Application : Application(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: SharedPreferences
    private val curtains = WeakHashMap<Activity, ComposeView>()
    private val resumedActivities = WeakHashMap<Activity, Boolean>()
    private var wallpaperStyle by mutableIntStateOf(0)
    private var holdCurtain = false
    private var releaseRequested = false
    private var curtainGeneration = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON -> {
                    releaseRequested = false
                    showCurtainImmediately()
                }

                Intent.ACTION_USER_PRESENT -> {
                    releaseRequested = true
                    requestCurtainRelease()
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
                    !holdCurtain -> hideCurtain(activity)
                    releaseRequested || !deviceRequiresCurtain() -> requestCurtainRelease()
                    else -> showCurtainImmediately()
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

        val activeInitially = holdCurtain || deviceRequiresCurtain()
        val curtain = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // This is intentionally set before setContent. Even if the Compose wallpaper has not
            // produced its first buffer yet, the guard can only display purple, never window white.
            setBackgroundColor(WINDOWS_PURPLE_FALLBACK)
            visibility = if (activeInitially) View.VISIBLE else View.INVISIBLE
            alpha = if (activeInitially) 1f else 0f
            isClickable = activeInitially
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // A visible unlock guard must never pass taps to invisible Start tiles underneath.
            setOnTouchListener { view, _: MotionEvent ->
                view.visibility == View.VISIBLE && view.alpha > ACTIVE_ALPHA_THRESHOLD
            }

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
            curtain.setBackgroundColor(WINDOWS_PURPLE_FALLBACK)
            curtain.visibility = View.VISIBLE
            curtain.alpha = 1f
            curtain.isClickable = true
            curtain.bringToFront()
            curtain.invalidate()
        }
    }

    private fun requestCurtainRelease() {
        if (!holdCurtain) return
        val resumed = resumedActivities.keys.toList()
        if (resumed.isEmpty()) return

        val generation = ++curtainGeneration
        resumed.forEach { activity ->
            val curtain = curtains[activity] ?: return@forEach
            scheduleReleaseFrame(
                activity = activity,
                curtain = curtain,
                generation = generation,
                preparedFramesRemaining = PREPARE_FRAMES,
            )
        }
    }

    private fun scheduleReleaseFrame(
        activity: Activity,
        curtain: ComposeView,
        generation: Int,
        preparedFramesRemaining: Int,
    ) {
        curtain.postOnAnimation releaseFrame@{
            if (generation != curtainGeneration) return@releaseFrame
            if (activity !in resumedActivities) return@releaseFrame

            if (deviceRequiresCurtain()) {
                // Do not abandon the release because the OEM reports keyguard locked for an extra
                // frame after USER_PRESENT. Keep checking until it really clears. The next
                // SCREEN_OFF/SCREEN_ON generation cancels this loop immediately.
                scheduleReleaseFrame(
                    activity = activity,
                    curtain = curtain,
                    generation = generation,
                    preparedFramesRemaining = PREPARE_FRAMES,
                )
                return@releaseFrame
            }

            if (preparedFramesRemaining > 1) {
                scheduleReleaseFrame(
                    activity = activity,
                    curtain = curtain,
                    generation = generation,
                    preparedFramesRemaining = preparedFramesRemaining - 1,
                )
                return@releaseFrame
            }

            holdCurtain = false
            releaseRequested = false
            hideCurtain(activity)
        }
    }

    private fun hideCurtain(activity: Activity) {
        val curtain = curtains[activity] ?: return
        curtain.animate().cancel()
        curtain.isClickable = false
        curtain.alpha = 0f
        curtain.visibility = View.INVISIBLE
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
        private const val ACTIVE_ALPHA_THRESHOLD = 0.5f
        private val WINDOWS_PURPLE_FALLBACK = Color.rgb(35, 5, 61)
    }
}
