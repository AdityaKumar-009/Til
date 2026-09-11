from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_one(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected source block not found in {path}: {old[:100]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"Expected exactly one source block in {path}, found {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")


motion = ROOT / "app/src/main/java/com/flivoro/tile8auncher/ui/animation/StartEntranceMotion.kt"
start = ROOT / "app/src/main/java/com/flivoro/tile8auncher/ui/start/StartScreen.kt"
main = ROOT / "app/src/main/java/com/flivoro/tile8auncher/MainActivity.kt"
test = ROOT / "app/src/test/java/com/flivoro/tile8auncher/ui/animation/StartEntranceMotionTest.kt"

replace_one(
    motion,
    "import kotlin.math.pow\n",
    "import kotlin.math.min\nimport kotlin.math.pow\n",
)

replace_one(
    motion,
    " * Motion is stored as viewport fractions so portrait and landscape preserve the measured geometry.\n",
    " * STARTUP remains viewport-normalized. RETURN is normalized to the measured 251 px Start band,\n * because the desktop reference moves the tile group relative to its own width, not relative to the\n * full 1920 px monitor. That preserves the same perceived sweep/zoom when a phone portrait viewport\n * contains roughly one Start band, while landscape keeps the same band-relative movement.\n",
)

old_travel = '''    /*
     * Short Start-return reference, measured from test.mp4.
     *
     * The cyan Mail tile settles at x=120, width=248 in the 1920 px source. Its measured center
     * displacement is 76, 53.5, 35.5, 26.5, 19, 14.5, 10, 8, 5, 3, 2.5, 2, 1, 0 px at the
     * corresponding encoded frames below. Translation is therefore only a few viewport percent;
     * the previous hand fit substantially over-travelled during the visible part of the entrance.
     *
     * The 100/300/467 ms plateaus are retained because those are duplicate/near-duplicate encoded
     * frames in the supplied recording. MotionCurve interpolates continuously between observations.
     */
    private val returnTime = floatArrayOf(
        0f, 33.333f, 66.667f, 100f, 133.333f, 166.667f, 200f, 233.333f, 266.667f,
        300f, 333.333f, 366.667f, 400f, 433.333f, 466.667f, 500f, 533.333f, 566.667f, 600f,
    )
    private val returnTravel = MotionCurve(
        returnTime,
        floatArrayOf(
            // t=0 is inferred from the measured deceleration immediately before the first visible
            // 33 ms frame; alpha is zero there, so it cannot introduce a visible jump.
            .049300f,
            76f / 1920f,
            53.5f / 1920f,
            53.5f / 1920f,
            35.5f / 1920f,
            26.5f / 1920f,
            19f / 1920f,
            14.5f / 1920f,
            10f / 1920f,
            10f / 1920f,
            8f / 1920f,
            5f / 1920f,
            3f / 1920f,
            2.5f / 1920f,
            2.5f / 1920f,
            2f / 1920f,
            1f / 1920f,
            0f,
            0f,
        ),
    )
    private val returnGrowth = MotionCurve(
        returnTime,
        floatArrayOf(
            .680000f,
            192f / 248f,
            225f / 248f,
            225f / 248f,
            237f / 248f,
            241f / 248f,
            244f / 248f,
            245f / 248f,
            246f / 248f,
            246f / 248f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
    )
    private val returnOpacity = MotionCurve(
        returnTime,
        floatArrayOf(
            0f,
            .029f,
            .377f,
            .384f,
            .741f,
            .996f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
    )
'''
new_travel = '''    /*
     * Short Start-return reference, measured from every encoded frame of test.mp4 after cropping
     * the desktop sequence to a 350x630 portrait window around the first Start band.
     *
     * Blank background is still present at 56.033 s. At 56.067 s the Mail band is first visible at
     * 193/251 of final size, ~6.5% opacity, and its center is 75 px to the right of the settled
     * center. It then follows the recorded 53.5, 53.5, 34.5, 26, 19, 15, 10, 10, 8.5, 4.5,
     * 2.5, 2.5, 2.5, 1.5, .5, 0 px deceleration tail through 56.600 s.
     *
     * Those displacements are divided by the measured final 251 px band width, NOT the 1920 px
     * desktop width. StartScreen multiplies this ratio by its real band width so portrait and
     * landscape preserve the reference's group-relative geometry.
     */
    private const val ReturnReferenceBandWidthPx = 251f
    private val returnTime = floatArrayOf(
        0f, 33.333f, 66.667f, 100f, 133.333f, 166.667f, 200f, 233.333f, 266.667f,
        300f, 333.333f, 366.667f, 400f, 433.333f, 466.667f, 500f, 533.333f, 566.667f, 600f,
    )
    private val returnTravel = MotionCurve(
        returnTime,
        floatArrayOf(
            // t=0 is invisible; 94.656 px extrapolates the measured first-frame deceleration.
            94.656f / ReturnReferenceBandWidthPx,
            75f / ReturnReferenceBandWidthPx,
            53.5f / ReturnReferenceBandWidthPx,
            53.5f / ReturnReferenceBandWidthPx,
            34.5f / ReturnReferenceBandWidthPx,
            26f / ReturnReferenceBandWidthPx,
            19f / ReturnReferenceBandWidthPx,
            15f / ReturnReferenceBandWidthPx,
            10f / ReturnReferenceBandWidthPx,
            10f / ReturnReferenceBandWidthPx,
            8.5f / ReturnReferenceBandWidthPx,
            4.5f / ReturnReferenceBandWidthPx,
            2.5f / ReturnReferenceBandWidthPx,
            2.5f / ReturnReferenceBandWidthPx,
            2.5f / ReturnReferenceBandWidthPx,
            1.5f / ReturnReferenceBandWidthPx,
            .5f / ReturnReferenceBandWidthPx,
            0f,
            0f,
        ),
    )
    private val returnGrowth = MotionCurve(
        returnTime,
        floatArrayOf(
            .680000f,
            193f / ReturnReferenceBandWidthPx,
            226f / ReturnReferenceBandWidthPx,
            226f / ReturnReferenceBandWidthPx,
            238f / ReturnReferenceBandWidthPx,
            243f / ReturnReferenceBandWidthPx,
            247f / ReturnReferenceBandWidthPx,
            249f / ReturnReferenceBandWidthPx,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
    )
    private val returnOpacity = MotionCurve(
        returnTime,
        floatArrayOf(
            0f,
            .064516f,
            .411290f,
            .411290f,
            .755245f,
            .993007f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        ),
    )
'''
replace_one(motion, old_travel, new_travel)

insert_after_duration = '''    fun durationMillis(kind: StartEntranceKind): Int =
        if (UnlockEntranceMotionOverride.resolve(kind) == StartEntranceKind.STARTUP) {
            DurationMillis
        } else {
            ReturnDurationMillis
        }
'''
translation_helper = insert_after_duration + '''

    /**
     * Converts the fitted offset into pixels using the same geometry the reference actually moves.
     * RETURN is relative to one Start band; STARTUP intentionally keeps its existing viewport basis.
     */
    fun translationX(
        frame: EntranceFrame,
        kind: StartEntranceKind,
        viewportWidthPx: Float,
        bandWidthPx: Float,
    ): Float {
        val safeViewport = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeBand = bandWidthPx.takeIf { it.isFinite() && it > 0f } ?: safeViewport
        val basis = if (UnlockEntranceMotionOverride.resolve(kind) == StartEntranceKind.RETURN) {
            safeBand
        } else {
            safeViewport
        }
        return (frame.offsetFraction * basis).takeIf(Float::isFinite) ?: 0f
    }
'''
replace_one(motion, insert_after_duration, translation_helper)

background_anchor = '''    /** Decorative wallpaper anchor; base color itself remains stationary. */
    fun backgroundTravelFraction(
        progress: Float,
        kind: StartEntranceKind = StartEntranceKind.RETURN,
    ): Float {
'''
replace_one(
    motion,
    background_anchor,
    '''    /**
     * Decorative wallpaper anchor; base color itself remains stationary. RETURN uses the same
     * band-relative fit as the tiles. The caller converts it through backgroundEntranceOffsetPx().
     */
    fun backgroundTravelFraction(
        progress: Float,
        kind: StartEntranceKind = StartEntranceKind.RETURN,
    ): Float {
''',
)

background_end = '''        }
    }

    fun viewportBandPosition(
'''
background_helper = '''        }
    }

    /**
     * Synthetic scroll offset used only for entrance parallax. For RETURN the phone's short side is
     * the stable physical motion basis, so rotating the device does not multiply the wallpaper
     * sweep by the landscape width. STARTUP keeps its existing full-width behavior unchanged.
     */
    fun backgroundEntranceOffsetPx(
        progress: Float,
        kind: StartEntranceKind,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): Float {
        val width = viewportWidthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val height = viewportHeightPx.takeIf { it.isFinite() && it > 0f } ?: width
        val basis = if (UnlockEntranceMotionOverride.resolve(kind) == StartEntranceKind.RETURN) {
            min(width, height)
        } else {
            width
        }
        return (backgroundTravelFraction(progress, kind) * basis)
            .takeIf(Float::isFinite) ?: 0f
    }

    fun viewportBandPosition(
'''
replace_one(motion, background_end, background_helper)

replace_one(
    start,
    '''                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val bandExtentPx = with(density) {
                    metrics.bandWidthDp.dp.toPx() + START_BAND_SPACING_DP.dp.toPx()
                }
''',
    '''                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val bandWidthPx = with(density) { metrics.bandWidthDp.dp.toPx() }
                val bandExtentPx = with(density) {
                    metrics.bandWidthDp.dp.toPx() + START_BAND_SPACING_DP.dp.toPx()
                }
''',
)
replace_one(
    start,
    '''                                        translationX = frame.offsetFraction * viewportWidthPx
''',
    '''                                        translationX = StartEntranceMotion.translationX(
                                            frame = frame,
                                            kind = playingKind,
                                            viewportWidthPx = viewportWidthPx,
                                            bandWidthPx = bandWidthPx,
                                        )
''',
)

replace_one(
    main,
    '''                val viewportWidthPx = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                val entranceTravel = if (currentScreen == LauncherScreen.START && activeInAppTile == null) {
                    StartEntranceMotion.backgroundTravelFraction(
                        progress = wallpaperEntrance.value,
                        kind = startEntranceKind,
                    )
                } else {
                    0f
                }
                userScroll - viewportWidthPx * entranceTravel
''',
    '''                val displayMetrics = context.resources.displayMetrics
                val viewportWidthPx = displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                val viewportHeightPx = displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                val entranceOffsetPx = if (currentScreen == LauncherScreen.START && activeInAppTile == null) {
                    StartEntranceMotion.backgroundEntranceOffsetPx(
                        progress = wallpaperEntrance.value,
                        kind = startEntranceKind,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                    )
                } else {
                    0f
                }
                userScroll - entranceOffsetPx
''',
)

replace_one(
    test,
    '''        val at133 = StartEntranceMotion.frame(133.333f / 600f, 0f, StartEntranceKind.RETURN)
        assertEquals(35.5f / 1920f, at133.offsetFraction, .00005f)
        assertEquals(237f / 248f, at133.scale, .0005f)
        assertEquals(.741f, at133.alpha, .002f)

        val at200 = StartEntranceMotion.frame(200f / 600f, 0f, StartEntranceKind.RETURN)
        assertEquals(19f / 1920f, at200.offsetFraction, .00005f)
        assertEquals(244f / 248f, at200.scale, .0005f)
        assertEquals(1f, at200.alpha, .0001f)
''',
    '''        val at133 = StartEntranceMotion.frame(133.333f / 600f, 0f, StartEntranceKind.RETURN)
        assertEquals(34.5f / 251f, at133.offsetFraction, .00005f)
        assertEquals(238f / 251f, at133.scale, .0005f)
        assertEquals(.755245f, at133.alpha, .002f)

        val at200 = StartEntranceMotion.frame(200f / 600f, 0f, StartEntranceKind.RETURN)
        assertEquals(19f / 251f, at200.offsetFraction, .00005f)
        assertEquals(247f / 251f, at200.scale, .0005f)
        assertEquals(1f, at200.alpha, .0001f)
''',
)

anchor = '''    @Test fun returnHeaderUsesItsSlowerMeasuredFade() {
'''
new_tests = '''    @Test fun returnTranslationPreservesBandRelativeMotionAcrossPhoneOrientations() {
        val frame = StartEntranceMotion.frame(33.333f / 600f, 0f, StartEntranceKind.RETURN)
        val portraitBandWidth = 312f
        val landscapeBandWidth = 312f
        val expected = 75f / 251f * portraitBandWidth

        val portrait = StartEntranceMotion.translationX(
            frame, StartEntranceKind.RETURN, viewportWidthPx = 312f, bandWidthPx = portraitBandWidth)
        val landscape = StartEntranceMotion.translationX(
            frame, StartEntranceKind.RETURN, viewportWidthPx = 760f, bandWidthPx = landscapeBandWidth)

        assertEquals(expected, portrait, .001f)
        assertEquals(expected, landscape, .001f)
        assertTrue(landscape < 760f * frame.offsetFraction)
    }

    @Test fun returnWallpaperEntranceUsesThePhysicalShortSide() {
        val progress = 33.333f / 600f
        val portrait = StartEntranceMotion.backgroundEntranceOffsetPx(
            progress, StartEntranceKind.RETURN, viewportWidthPx = 1080f, viewportHeightPx = 2400f)
        val landscape = StartEntranceMotion.backgroundEntranceOffsetPx(
            progress, StartEntranceKind.RETURN, viewportWidthPx = 2400f, viewportHeightPx = 1080f)
        assertEquals(portrait, landscape, .001f)

        val startupPortrait = StartEntranceMotion.backgroundEntranceOffsetPx(
            100f / 2800f, StartEntranceKind.STARTUP, viewportWidthPx = 1080f, viewportHeightPx = 2400f)
        val startupLandscape = StartEntranceMotion.backgroundEntranceOffsetPx(
            100f / 2800f, StartEntranceKind.STARTUP, viewportWidthPx = 2400f, viewportHeightPx = 1080f)
        assertTrue(startupLandscape > startupPortrait)
    }

''' + anchor
replace_one(test, anchor, new_tests)

print("Applied portrait-aware short Start return refinement")
