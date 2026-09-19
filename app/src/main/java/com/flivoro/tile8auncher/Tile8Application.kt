package com.flivoro.tile8auncher

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.ui.animation.UnlockEntranceMotionOverride
import com.flivoro.tile8auncher.ui.components.StartPersonalization
import com.flivoro.tile8auncher.ui.components.LauncherImageCache
import com.flivoro.tile8auncher.ui.lockscreen.WindowsLockScreenPreferences
import com.flivoro.tile8auncher.ui.lockscreen.WindowsLockScreenRuntime

/**
 * Process-wide revision for the installed launcher app catalog.
 *
 * It changes only when Android reports a real package add/remove/change. Compose surfaces can use
 * it as a refresh key while ordinary launcher navigation keeps reusing the already-loaded catalog.
 */
object PackageCatalogUpdates {
    var revision by mutableIntStateOf(0)
        private set

    internal fun notifyPackageChanged() {
        revision++
    }
}

/**
 * Process application for Mosaic Launcher.
 *
 * Unlock rendering itself deliberately stays in MainActivity's single Compose/window surface.
 * This class only tags a real screen-off -> unlock cycle so StartEntranceMotion can reuse the
 * existing short Home/Back RETURN fit for that one entrance. It creates no additional View/surface.
 */
class Tile8Application : Application() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var unlockGeneration = 0
    private var mainActivityResumed = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    unlockGeneration++
                    UnlockEntranceMotionOverride.arm()
                    WindowsLockScreenRuntime.arm(context)
                }

                Intent.ACTION_SCREEN_ON -> {
                    // Devices without a secure/visible keyguard may resume directly from SCREEN_ON.
                    // Keep the override alive through the 680 ms entrance, then clear it.
                    if (!deviceRequiresUnlockGate()) {
                        scheduleOverrideRelease()
                        scheduleUnusedLockScreenRelease()
                    }
                }

                Intent.ACTION_USER_PRESENT -> {
                    // Keyguard has gone away. The MainActivity gate starts the same entrance; keep
                    // the override active long enough for both tile and wallpaper clocks to finish.
                    scheduleOverrideRelease()
                    scheduleUnusedLockScreenRelease()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Android can emit remove/add around an in-place package replacement. Ignore those two
            // duplicate replacement legs because ACTION_PACKAGE_REPLACED follows with the final state.
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (replacing &&
                (intent.action == Intent.ACTION_PACKAGE_ADDED ||
                    intent.action == Intent.ACTION_PACKAGE_REMOVED)
            ) {
                return
            }

            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED -> PackageCatalogUpdates.notifyPackageChanged()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Load the tiny saved Accent preference once for the process. This only updates the
        // All Apps icon-container purple; wallpapers remain the original stock artwork.
        StartPersonalization.ensureLoaded(this)
        LauncherImageCache.preload(
            this,
            WindowsLockScreenPreferences.wallpaperUri(this),
            maxSide = 2560,
        )
        LauncherFeatureStore.lockSlideshowUris(this).forEach { uri ->
            LauncherImageCache.preload(this, uri, maxSide = 2560)
        }

        // If the process itself is created while the phone is asleep/locked, treat the eventual
        // reveal as an unlock even though this process missed the earlier SCREEN_OFF broadcast.
        if (deviceRequiresUnlockGate()) {
            UnlockEntranceMotionOverride.arm()
            WindowsLockScreenRuntime.arm(this)
        }

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

        // Keep the fast process-local app cache, but refresh it only when Android says the package
        // set actually changed. This avoids polling/re-scanning during normal Start <-> All Apps use.
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity && deviceRequiresUnlockGate()) {
                    UnlockEntranceMotionOverride.arm()
                    WindowsLockScreenRuntime.arm(this@Tile8Application)
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) mainActivityResumed = true
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) mainActivityResumed = false
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity is MainActivity && activity.isChangingConfigurations) {
                    // Rotation/configuration recreation must retain the existing long STARTUP fit,
                    // even if it happens immediately after an unlock.
                    unlockGeneration++
                    UnlockEntranceMotionOverride.cancel()
                    WindowsLockScreenRuntime.dismiss()
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    private fun scheduleOverrideRelease() {
        val generation = unlockGeneration
        mainHandler.postDelayed({
            if (generation == unlockGeneration && !deviceRequiresUnlockGate()) {
                UnlockEntranceMotionOverride.cancel()
            }
        }, UNLOCK_OVERRIDE_HOLD_MILLIS)
    }

    /**
     * A launcher cannot cover another foreground app after Android unlock. If Home does not resume
     * promptly, drop the pending visual layer instead of surprising the user on a much later Home tap.
     */
    private fun scheduleUnusedLockScreenRelease() {
        val generation = unlockGeneration
        mainHandler.postDelayed({
            if (generation == unlockGeneration && !mainActivityResumed) {
                WindowsLockScreenRuntime.dismiss()
            }
        }, LOCK_SCREEN_HOME_GRACE_MILLIS)
    }

    private fun deviceRequiresUnlockGate(): Boolean {
        val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        return keyguardLocked || !interactive
    }

    companion object {
        // RETURN motion is 680 ms. This is only a cleanup guard; the visual timing remains exactly
        // the existing RETURN fit and no animation curve/duration is changed here.
        private const val UNLOCK_OVERRIDE_HOLD_MILLIS = 1_200L
        private const val LOCK_SCREEN_HOME_GRACE_MILLIS = 3_000L
    }
}
