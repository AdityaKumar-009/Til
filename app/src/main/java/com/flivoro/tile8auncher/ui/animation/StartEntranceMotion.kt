package com.flivoro.tile8auncher.ui.animation

import android.os.SystemClock

/**
 * Windows 8.1 Start entrance motion.
 *
 * Windows distinguishes the pronounced session/sign-in entrance from the
 * smaller transition used when returning to Start later in the session. Tile8
 * mirrors that distinction: RETURN is compact and translation-only so it does
 * not compound into a wobbly scale when Android hands the launcher back its
 * window; SIGN_IN is intentionally longer, softer and slightly dimensional.
 *
 * The sign-in profile is selected for a short window after ACTION_USER_PRESENT
 * (and for the process' first visible Start) by Tile8Application. App-opening
 * FlipLaunchOverlay/WindowsLaunchMotion is completely independent of this.
 */
internal enum class StartEntranceProfile { RETURN, SIGN_IN }

internal object StartEntranceMotion {
    private const val ReturnDurationMillis = 500
    private const val SignInDurationMillis = 880
    private const val SignInSelectionWindowMillis = 2_500L

    private val returnTime = floatArrayOf(0f, 34f, 67f, 100f, 134f, 167f, 220f, 300f, 380f, 450f)
    private val returnTravel = MotionCurve(
        returnTime,
        floatArrayOf(.18f, .125f, .078f, .047f, .029f, .017f, .008f, .003f, .001f, 0f),
    )
    private val returnOpacity = MotionCurve(
        returnTime,
        floatArrayOf(0f, .20f, .52f, .76f, .91f, 1f, 1f, 1f, 1f, 1f),
    )

    private val signInTime = floatArrayOf(0f, 50f, 100f, 150f, 200f, 260f, 330f, 420f, 520f, 650f, 790f)
    private val signInTravel = MotionCurve(
        signInTime,
        floatArrayOf(.32f, .24f, .17f, .115f, .075f, .045f, .026f, .014f, .006f, .002f, 0f),
    )
    private val signInGrowth = MotionCurve(
        signInTime,
        floatArrayOf(.93f, .942f, .954f, .965f, .975f, .983f, .989f, .994f, .998f, 1f, 1f),
    )
    private val signInOpacity = MotionCurve(
        signInTime,
        floatArrayOf(0f, .06f, .18f, .38f, .62f, .82f, .94f, 1f, 1f, 1f, 1f),
    )

    @Volatile
    private var signInUntilElapsedRealtime = 0L

    /** Duration captured by the composable when a new entrance request starts. */
    val DurationMillis: Int
        get() = durationForProfile(activeProfile())

    fun markSignInWindow(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()) {
        signInUntilElapsedRealtime = nowElapsedRealtime + SignInSelectionWindowMillis
    }

    internal fun activeProfile(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): StartEntranceProfile =
        if (nowElapsedRealtime <= signInUntilElapsedRealtime) StartEntranceProfile.SIGN_IN
        else StartEntranceProfile.RETURN

    internal fun durationForProfile(profile: StartEntranceProfile): Int = when (profile) {
        StartEntranceProfile.RETURN -> ReturnDurationMillis
        StartEntranceProfile.SIGN_IN -> SignInDurationMillis
    }

    fun frame(progress: Float, column: Int): EntranceFrame =
        frameForProfile(activeProfile(), progress, column)

    internal fun frameForProfile(
        profile: StartEntranceProfile,
        progress: Float,
        column: Int,
    ): EntranceFrame {
        val clampedProgress = progress.coerceIn(0f, 1f)
        return when (profile) {
            StartEntranceProfile.RETURN -> {
                // A compact return should never make tiles breathe vertically.
                // Keeping scale at 1 also prevents overlap with Android's final
                // window hand-off frames, which was the source of the jiggly feel.
                val milliseconds = (
                    clampedProgress * ReturnDurationMillis - column.coerceIn(0, 3) * 14f
                    ).coerceAtLeast(0f)
                EntranceFrame(
                    offsetFraction = returnTravel.at(milliseconds),
                    scale = 1f,
                    alpha = returnOpacity.at(milliseconds),
                )
            }

            StartEntranceProfile.SIGN_IN -> {
                val milliseconds = (
                    clampedProgress * SignInDurationMillis - column.coerceIn(0, 3) * 26f
                    ).coerceAtLeast(0f)
                EntranceFrame(
                    offsetFraction = signInTravel.at(milliseconds),
                    scale = signInGrowth.at(milliseconds),
                    alpha = signInOpacity.at(milliseconds),
                )
            }
        }
    }
}

internal data class EntranceFrame(val offsetFraction: Float, val scale: Float, val alpha: Float)
