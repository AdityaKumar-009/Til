package com.flivoro.tile8auncher.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs

/**
 * One persistent Windows Start background world shared by Start and All Apps.
 *
 * Horizontal lists report the distance they actually consumed through [StartBackgroundScrollRuntime].
 * The world coordinate is intentionally a Double: the renderer can reduce it to a local repeating
 * phase at draw time without losing sub-pixel precision after very long launcher sessions.
 */
@Stable
internal class StartBackgroundSceneState {
    private val worldXState = mutableStateOf(0.0)
    private val interactionSerialState = mutableIntStateOf(0)
    private val lastScrollDeltaState = mutableFloatStateOf(0f)

    /** Logical artwork-world position. It is never reset by Start <-> All Apps navigation. */
    val worldX: Double get() = worldXState.value

    /** Monotonic interaction token observed by Motion Accent animation code. */
    val interactionSerial: Int get() = interactionSerialState.intValue

    /** Last consumed horizontal content delta in pixels. */
    val lastScrollDeltaPx: Float get() = lastScrollDeltaState.floatValue

    /**
     * Updated by the currently mounted horizontal list. This flag is controlled by the wallpaper
     * preference so disabling parallax also stops Motion Accent input work.
     */
    @Volatile
    var inputEnabled: Boolean = true

    fun onHorizontalScroll(deltaPx: Float) {
        if (!inputEnabled || !deltaPx.isFinite() || abs(deltaPx) < 0.01f) return
        worldXState.value += deltaPx.toDouble()
        lastScrollDeltaState.floatValue = deltaPx
        interactionSerialState.intValue++
    }
}

/**
 * Very small process-local bridge between the shared horizontal-scroll modifier and the single
 * wallpaper scene behind Start/All Apps.
 *
 * Main launcher wallpaper attaches; Personalize thumbnails explicitly do not. No list reference or
 * Android View is retained, so this cannot keep a screen alive.
 */
internal object StartBackgroundScrollRuntime {
    @Volatile
    private var attachedScene: StartBackgroundSceneState? = null

    fun attach(scene: StartBackgroundSceneState) {
        attachedScene = scene
    }

    fun detach(scene: StartBackgroundSceneState) {
        if (attachedScene === scene) attachedScene = null
    }

    /**
     * NestedScroll reports content movement in gesture coordinates. Negating the consumed X makes
     * worldX increase when the list advances toward content on the right, matching Windows parallax.
     */
    fun onListConsumedScroll(consumedX: Float) {
        if (!consumedX.isFinite() || consumedX == 0f) return
        attachedScene?.onHorizontalScroll(-consumedX)
    }
}
