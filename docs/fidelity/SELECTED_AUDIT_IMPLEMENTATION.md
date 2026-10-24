# Selected launcher fidelity corrections

Scope: audit sections **5, 6, 7, 9, 10, 11, 12** only. Baseline `e89a4583cff7a8b4210e5d39faef16cc9dcb7c34`. The approved Windows 10 Mobile Start Back choreography, WP8.1 launch durations, default desktop palette and custom stronger overscroll remain separate from this work.

## Changes and evidence

| Section | Implementation | Evidence and limits |
|---|---|---|
| 5 — Mobile All Apps | Route real rows through `MobileAppsMotion`; stationary search/chrome fade; selected-row press feedback; 400 ms exit / 320 ms Back completion | Original Lumia 950/XL hardware footage below. These are fitted timings, not recovered Microsoft constants. Start Back is unchanged. |
| 6 — Desktop return | Exclude repeated interior poses from the monotone fit; retain 600 ms return duration, unique measured poses, band geometry and delayed header | `test.mp4`, 56.033–56.633 s. Other return events also contain capture repeats. The 2800 ms startup fit comes from an edited comparison; its true native duration remains unverified and has not been guessed. |
| 7 — Quality gates | All 60 story/mode/origin combinations are independent tests; unit tests and both emulator render jobs gate the final APK artifact; retain failure images, test reports and R8 frame statistics | A green APK build alone is insufficient. Candidate APKs are separate from validated output. Emulator frame statistics do not certify physical-device smoothness. |
| 9 — Phone interactions | Ripple-free Mobile row press state; measured popup placement clamped to usable viewport; accessible international alphabet bucket; no dismissal from disabled alphabet cells; dialer/SMS role-based glyph selection | Native footage and source behavior support the selected-row treatment. Exact menu reveal timings and a full cross-build light-theme comparison are not established by the current references. Existing visual geometry is retained where evidence is insufficient. |
| 10 — Features | Inline folders in the scrolling Start grid, rename/reorder/remove, previews attached to the animated face, live notification counts, and current child launch bounds; route phone Settings into phone presentation with advanced launcher settings retained; label sample weather; locale-aware ordering and Android dialer/SMS roles | Microsoft's 2014 Live Folders article shows expanded folders within Start. The 280 ms folder interpolation is a launcher fit, not an authenticated native timing. Android system settings open their actual Android destinations. |
| 11 — Desktop input | Pinch, Ctrl+wheel and keyboard semantic zoom; type-to-search; Escape; keyboard focus/activation/context menu; right-click; wheel scrolling | Microsoft documents pinch and Ctrl+wheel semantic zoom. Existing touch scrolling remains on its existing physics. |
| 12 — Continuity / performance | Share the real selected source display list with optional launch stories; retain artwork and avoid a stationary duplicate; validate return and internal-window lifetime; gather optimized frame stats for all optional stories | No GPU bitmap readback on the production launch path. These custom stories are launcher additions, not historical Windows animations. The source list must follow child RenderNode invalidation. |

## Native footage reinspection

Original publisher: [WinFuture Lumia 950 / Lumia 950 XL unboxing and hands-on](https://winfuture.de/videos/Hardware/Microsoft-Lumia-950-Lumia-950-XL-ausgepackt-und-ausprobiert-15394.html). Direct reference stream: `https://winfuturevideo.fra1.digitaloceanspaces.com/15394.mp4`, 970×544, 25 fps, duration 1688.88 s.

- 1023.16–1023.72 s: All Apps launch. Upper rows leave first; the selected Continuum row persists longer with gray selection feedback. Search stays anchored while fading.
- 1072.40–1072.88 s: All Apps return. Lower rows return first from an enlarged pose.
- 1441.40–1442.00 s: Start Back, inspected only as a scope boundary; section 4 was not selected for modification.

Desktop `test.mp4`: 1920×1080, 30 fps. Adjacent full-frame mean pixel differences near 56 s fall to approximately 0.027 and 0.076 during otherwise moving sequences, consistent with near-repeated lossy-encoded images. At another return near 47 s, a fixed 1500×950 crop downscaled to 750×475 has mean differences 0.010 at 47.233 s and 0.080 at 47.400 s, versus surrounding multi-level differences. Repeated encoded poses must not become deliberate zero-velocity segments. Equal rounded coordinates are also not evidence of a subpixel pause.

**Temporal resolution:** the native recordings provide observations every 40 ms and 33.33 ms respectively. Ten-millisecond model/renderer samples evaluate continuity between those observations; they are not newly observed native frames or proof of 100 fps rendering. Perspective, video compression, unknown OS build and device refresh differences limit pixel-exact claims.

## Primary references

- [Microsoft: Windows Phone folders, 5 August 2014](https://blogs.windows.com/windowsexperience/2014/08/05/windows-phone-folders-a-new-way-to-organize-your-start-screen/)
- [Microsoft: XAML control guidance, 22 July 2014](https://blogs.windows.com/windowsdeveloper/2014/07/22/tips-and-tricks-for-using-xaml-controls-in-your-universal-windows-apps/)
- [Microsoft MSDN Magazine: semantic zoom input, July 2013](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/july/modern-apps-mastering-controls-and-settings-in-windows-store-apps-built-with-javascript)
- [Android: graphics layers and recording](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
- [Android: default dialer package](https://developer.android.com/reference/android/telecom/TelecomManager#getDefaultDialerPackage())
- [Android: default SMS package](https://developer.android.com/reference/android/provider/Telephony.Sms#getDefaultSmsPackage(android.content.Context))

## Validation status

Checkpoint `57667edff0bac04bdb04e1e1093bfe5ac9ba4602` passed **192 unit tests** and **98 of 99 instrumented tests on both API 29 and API 34**. All 60 story/mode/origin cases, the native phone motion captures, Settings routes, and optimized startup/ten-story smoke checks passed. The remaining inline-folder check queried a structural container through merged semantics; the next change removes the idle grid's disabled clickable wrapper, targets the actual text/container nodes, and exports a screenshot plus semantics tree on failure. It also checks right-click without accidental launch.

The previous workflow then stalled installing OS preview dependencies. Preview encoding now uses pinned Python wheels with bounded setup/render time, and cancelled runs skip postprocessing. The full unit/render matrix still gates the validated APK; the early folder probe does not replace it.

Latest results and tested APK commit are recorded in [PR #46](https://github.com/AdityaKumar-009/Til/pull/46) and its [Actions checks](https://github.com/AdityaKumar-009/Til/actions/workflows/android.yml). A passing screenshot matrix establishes continuity/behavior on those emulators, not native pixel identity or physical-device smoothness. The optimized software-emulator frame logs include slow frames; no controlled physical-device before/after trace is available to establish a frame-time regression budget.

Remaining exact-match evidence: an identified Windows build for menu/Settings details, matching-resolution native captures for theme/picker calibration, and physical Android traces at the device's actual refresh rate. Current footage cannot resolve native timing to 10 ms.
