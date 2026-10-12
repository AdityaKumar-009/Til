# W10M Back and All apps review

## Native source and reproducible observations

Primary footage: [WinFuture: Microsoft Lumia 950 & Lumia 950 XL ausgepackt und ausprobiert](https://winfuture.de/videos/Hardware/Microsoft-Lumia-950-Lumia-950-XL-ausgepackt-und-ausprobiert-15394.html).
The publisher's MP4 is `https://winfuturevideo.fra1.digitaloceanspaces.com/15394.mp4` (970 x 544, 25 fps).

Every decoded source frame was inspected in the following intervals; no interpolated native frames were used. The input button remains visible: the left navigation Back key, not the central Windows/Home key.

| Phase | Source time (seconds) | Observation |
| --- | --- | --- |
| All apps press | 1023.20–1023.28 | Continuum receives a rectangular gray row highlight. |
| All apps launch | 1023.28–1023.48 | Upper rows leave first, lower rows follow. The selected row holds longer than neighboring unselected rows. |
| Selected row exit | 1023.48–1023.56 | Continuum is the last visible row in this sample. |
| Search/wallpaper exit | 1023.56–1023.64 | Search remains stationary, then fades. The external app splash appears later; its load time is not a shell animation constant. |
| Continuum Back | 1072.24–1072.44 | Outgoing app/window handoff; do not include it as a launcher delay. |
| All apps Back | 1072.48–1072.80 | Lower rows reveal first; upper rows follow. Rows approach their resting positions from outside the layout. No selected-row hold on return. |
| Camera Back to Start | 1441.16–1441.60 | Visible Back press, outgoing camera, then window handoff. |
| Start reveal | 1441.64–1441.92 | Lower tiles are visible first. Stabilized glyphs start smaller than rest and expand into place; this is opposite to All apps Back. |

The older `1000197577.mp4` reference remains the source for Start launch and explicit Home entry. Its yellow marker presses Home; it is not evidence for Back.

## Changes

- All apps has its own shorter launch envelope: 160 ms row motion, 100 ms visible-row stagger, an additive 80 ms selected-row hold, and stationary search/wallpaper fade at 320–400 ms.
- Back has separate entries for each pane. All apps uses 200 ms per row plus 120 ms stagger, approaching from above unit scale. Start uses a 450 ms below-unit scale track and a tighter 60 ms stagger, with row opacity at local 80–160 ms. Neither reverses the entire outgoing timeline: the old reversal imposed 151 ms with all tiles hidden and reused the selected launch delay.
- Start Home and Back both expand smaller tiles, but have different stagger and opacity tracks. All apps Back shrinks its rows into place. The exit and Home fits already checked against the older Start footage are retained.
- Press feedback is a rectangular gray row highlight with no Material ripple. It remains latched through the selected launch hold and disappears on return.
- Header geometry uses untransformed bounds, including when the list is scrolled. App-list clipping continues to prevent offscreen Start tiles leaking into the left gutter.
- One display clock still controls the visual layers and final app handoff. Existing lifecycle/crash guards remain in place.

These durations and curves are fits, not extracted Microsoft shell constants. At 25 fps, source timing is resolved in 40 ms intervals, and camera movement, exposure and display refresh prevent a claim of pixel-exact or 100% identity across builds/devices. The two return directions are checked independently; fine timing and opacity remain constrained by source frame rate and exposure.

## Camera registration and direction check

Start frames were registered to 1441.92s using status/navigation marks that do not participate in tile animation. Pyramidal Lucas–Kanade tracking and a RANSAC similarity transform remove most hand/camera translation, rotation and scale. The Microsoft Mail, Edge and Shazam glyphs were then template-matched across scales; low-confidence or missing glyphs were excluded. This showed Mail at approximately 0.90 scale at 1441.68s and 0.96 at 1441.76s, and Shazam at approximately 0.92 at 1441.64s. Their positions move away from the screen center toward rest, establishing below-unit Start entry. A reversal of the outgoing zoom would produce the opposite direction.

See `w10m-start-back-observed.csv` for the retained measurements. Coordinate tolerances include camera-registration error and one 40 ms source interval; the motion origin (~1441.52s) is a fit, not the physical button timestamp. All apps retains an independent above-unit return based on its stabilized icon positions.

## Verification

- Unit regression checks use the observed phase ordering and visibility windows, preserve earlier Start/Home geometry checks, and cover selected, dense and scrolled rows.
- Instrumentation captures actual Compose output, including Back every 10 ms, and verifies early visibility, settled geometry, stationary search, press feedback, no Start bleed, and one held handoff.
- Existing mode-switch/settings/repeated Home-and-Back tests and the optimized saved-mode cold-start smoke remain enabled on Android API 29 and 34.
- `.github/scripts/render-w10m-review.py` creates compact timestamped contact sheets and videos on API 34. Full raw captures remain manual opt-in with one-day retention; the APK keeps the user's two-day retention policy.

Build/run results and visual comparison notes are recorded after verification, rather than assumed from code inspection.
