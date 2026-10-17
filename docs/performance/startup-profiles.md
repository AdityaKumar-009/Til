# Launcher startup profiles

Til captures app-specific ART rules for Desktop, Windows Phone 8.1, and Windows 10
Mobile. `app/src/main/baselineProfiles/startup-prof.txt` contains fresh and saved-state launch paths;
`baseline-prof.txt` also includes opening and scrolling All Apps. These supplement
the profiles from AndroidX. They do not alter launcher animations or pinned apps.

R8 consumes the Startup Profile to group startup code into the first DEX files.
The Baseline Profile is rewritten by R8 and packaged in the APK for Android's
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

Download `launcher-startup-profiles`, review the generated source profiles and
`capture.json`, then commit both `*-prof.txt` files together. Normal builds use
the checked-in profiles without running an emulator. Regenerate after meaningful
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
commit message. It builds the same revision twice, removing only the app-specific
profiles in a disposable control checkout. AndroidX library profiles remain in
both APKs. Each compilation condition uses a fresh software-rendered emulator
with Vulkan disabled. Commands have time limits and each sample is saved as it
finishes, so an emulator failure preserves completed measurements and fails
promptly. Incomplete runs must not be reported as a measured improvement.

`verify-startup-profiles.py` checks the APK's binary ART profile and size limit,
then verifies the bundle's R8 metadata identifies a startup DEX with a matching
checksum. This catches misplaced or unconsumed Startup Profile source files.

References:

- https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure
- https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations
- https://developer.android.com/topic/performance/baselineprofiles/confirm-startup-profiles
