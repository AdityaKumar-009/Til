# Launcher startup performance

## Wallpaper work

Stock and custom wallpaper requests now share a bounded `BitmapDecodeCache`.
Concurrent tile requests decode an image once instead of decoding identical
bitmaps and discarding the duplicates. Memory peeks use a separate short lock, so
the UI never waits for a full decode merely to check for a cached image. Different
images can load concurrently, using a fixed pool of 16 locks. The existing 12 MiB
stock and 48 MiB custom bitmap budgets remain; disk previews have a separate 4 MiB
cache so each tile does not reread the same preview during a cold launch.

`BitmapDecodeCacheTest` checks eight simultaneous requests share one decode,
memory peeks and another image remain responsive during that decode, failed loads
can retry, and eviction respects the byte budget without recycling displayed
bitmaps. This removes duplicate work; no whole-launch percentage gain is claimed
for the wallpaper change. It does not change motion timing or shared pins.

## Profile experiments

Production builds retain AndroidX library Baseline Profiles. The experimental
app-specific Baseline and Startup Profiles are disabled because comparisons did
not show a consistent benefit across modes. Two complete API 34 comparisons found
approximately 10% slower precompiled Desktop startup with both app profiles (runs
39066860649 attempt 1 and 39068923256). Without the Startup Profile layout, the
app-specific Baseline Profile alone still measured 6% slower on Desktop and 11%
slower on WP8.1 in run 39069915594. W10M improved in the combined-profile runs, but
that did not justify the regressions elsewhere. The incomplete W10M comparison
in the Baseline-only run is excluded.

The full samples, conditions, and excluded runs are recorded in
[`startup-profile-measurements.json`](startup-profile-measurements.json). Hosted
emulator variability and fixed before/after order limit the precision of these
results. Neither experimental profile is being presented as a measured speedup.
The captured source files remain available in commit `bc70854`; regenerate and
benchmark all three modes before enabling replacements.

When enabled, R8 consumes the Startup Profile to group startup code into the first
DEX files. The Baseline Profile is rewritten by R8 and packaged in the APK for Android's
ahead-of-time compilation. ProfileInstaller also supports sideloads; the full
compilation benefit on a sideloaded APK depends on Android's background dexopt,
so the first launch is not equivalent to a precompiled installation.

## Regenerate and measure

Run the **Launcher startup profiles** workflow manually, or include
`[startup-profile]` in a commit on `feature/windows-phone-launcher-modes`.
It uses a rooted API 34 emulator and a non-debuggable, non-minified
`profileGeneration` APK so ART records original class and method names. Only
the final R8-optimized `performance` APK should be installed for normal use.

The workflow captures actual method usage via Android's documented manual ART
profile protocol. It disables automatic profile bootstrapping during collection,
records three launches per mode, and unions the observed rules. No wildcard
class rules or production mode-selection hooks are needed. Mode preferences
are written only on the disposable rooted emulator.

Download `launcher-startup-profiles`, review the generated source profiles,
`capture.json`, and measured results before committing either profile. Normal builds
use AndroidX's library profiles without running an emulator. Regenerate after meaningful
changes to startup, the three launcher surfaces, or dependencies.

The workflow builds the previous commit and the candidate, then measures five
saved cold starts per mode on the same emulator under two conditions:

- ART compilation reset, without forced AOT compilation.
- Each APK's own bundled profile installed and compiled with `speed-profile`.

Both versions receive the same preparation. Fresh setup is measured separately.
The report includes app-preparation time and `reportFullyDrawn` time (process
startup plus the prepared initial UI, before the entrance animation completes).
It also checks repeated foreground/Back returns retain prepared state and never
show the launcher loading indicator. Emulator timings are not device guarantees.

To isolate the profile benefit from other feature changes, use the Android build
workflow's `benchmark_profiles` input, or `[startup-profile-benchmark]` in the
commit message, with candidate app-specific profiles present. It builds the same
revision twice, removing only the app-specific
profiles in a disposable control checkout. AndroidX library profiles remain in
both APKs. Each mode and compilation condition uses a fresh software-rendered
emulator with Vulkan disabled. Mode isolation reduces exposure to the emulator's
intermittent ColorBuffer failure when switching between cleared-data launcher modes.
Within each comparison, both APKs run on the same emulator. Commands have time
limits and each sample is saved as it finishes, so an emulator failure preserves
completed measurements and fails
promptly. Incomplete runs must not be reported as a measured improvement.
After recording the startup timestamps, the harness waits for the phone mode's
real stable-render confirmation before force-stopping it. This avoids triggering
the production crash-recovery fallback merely because a busy emulator took more
than two seconds to finish settling. It never clears the safety marker itself.

`verify-startup-profiles.py` checks the APK's binary ART profile and size limit.
With a Startup Profile present, it also requires R8 metadata to identify a startup
DEX with a matching checksum. `--without-startup-layout` instead requires no
startup DEX, verifying that the build excludes the rejected layout.

References:

- https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure
- https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations
- https://developer.android.com/topic/performance/baselineprofiles/confirm-startup-profiles
