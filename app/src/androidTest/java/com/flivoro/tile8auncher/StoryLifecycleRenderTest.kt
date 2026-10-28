package com.flivoro.tile8auncher

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flivoro.tile8auncher.data.AppInfo
import com.flivoro.tile8auncher.data.AppSection
import com.flivoro.tile8auncher.data.AppsRepository
import com.flivoro.tile8auncher.data.TileModel
import com.flivoro.tile8auncher.data.TileSize
import com.flivoro.tile8auncher.features.LauncherFeatureStore
import com.flivoro.tile8auncher.features.LauncherUiMode
import com.flivoro.tile8auncher.features.MasterLaunchAnimation
import com.flivoro.tile8auncher.ui.animation.FlipAnimationState
import com.flivoro.tile8auncher.ui.animation.FlipAnimationDirection
import com.flivoro.tile8auncher.ui.animation.LaunchOrigin
import com.flivoro.tile8auncher.ui.apps.AllAppsScreen
import com.flivoro.tile8auncher.ui.components.LocalStoryLaunchScene
import com.flivoro.tile8auncher.ui.components.StoryLaunchOverlay
import com.flivoro.tile8auncher.ui.components.StoryLaunchScene
import com.flivoro.tile8auncher.ui.phone.PhoneLauncherSurface
import com.flivoro.tile8auncher.ui.phone.PhoneLaunchSourceFace
import com.flivoro.tile8auncher.ui.phone.PhoneLaunchDestinationIcon
import com.flivoro.tile8auncher.ui.start.StartScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class StoryLifecycleRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun internalWindowStaysMountedThroughOpeningAndBack() {
        val state = mutableStateOf(FlipAnimationState(isRunning = true,
            sourceTile = TileModel(id = "settings", title = "Settings"),
            masterLaunchAnimation = MasterLaunchAnimation.LIFT))
        val clock = Animatable(0f)
        val scene = StoryLaunchScene(state.value) { clock.value }
        var mounts = 0
        var disposals = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalStoryLaunchScene provides scene) {
                StoryLaunchOverlay(state.value, clock, {}, {}, destinationContent = {
                    DisposableEffect(Unit) {
                        mounts++
                        onDispose { disposals++ }
                    }
                    Box(Modifier.fillMaxSize().background(Color.Blue))
                })
            }
        }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue("The real window must exist during travel", clock.value in .01f.. .99f)
            assertEquals(1, mounts)
            assertEquals(0, disposals)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertEquals(1f, clock.value, .00001f)
            state.value = state.value.copy(direction = FlipAnimationDirection.REVERSE)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertTrue(clock.value in .01f.. .99f)
            assertEquals("Back must keep the very same window", 1, mounts)
            assertEquals(0, disposals)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertEquals(0f, clock.value, .00001f)
            assertEquals(1, mounts)
            assertEquals(0, disposals)
        }
    }

}
