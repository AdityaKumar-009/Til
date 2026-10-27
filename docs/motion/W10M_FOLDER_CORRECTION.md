# W10M Live Folder correction — 27 October 2026

Baseline: `9c416649`, folder captures from Actions run `40443172077` (production change `904e9926`). Inspecting the 16 ms samples shows a shrinking/tilting source plate, fading miniatures, clipped independently fading children, and a separate header chevron. The source settles as an empty colored tile. These captures establish the launcher behavior; they do not establish native Windows timing.

Native sources checked:

- Shaan Haider's original Lumia 950/950 XL/650 tutorial, https://www.youtube.com/watch?v=svVwSYXQasA (2016-03-01). Metadata is available: 200 seconds, 30 fps. Media retrieval returned a 195-byte Site Unavailable HTML response, not a usable video. No native frame measurements are claimed.
- Microsoft's Live Folders demonstration: https://blogs.windows.com/devices/2015/08/14/video-60-second-productivity-hack-live-folders/ and https://blogs.windows.com/windowsexperience/2014/08/05/windows-phone-folders-a-new-way-to-organize-your-start-screen/ . These establish the inline folder structure and persistent tile identity, not exact W10M interpolation constants.
- Original hands-on screenshots and description: https://onmsft.com/how-to/how-to-make-folders-on-your-windows-10-start-menu/ . The phone folder name belongs on the upper divider; the folder tile toggles the contents.

Changes:

- Use the same miniature rectangles for closed preview rendering and child expansion/contraction. Individual children translate and scale between their real preview and expanded positions. The preview/child opacity handoff is complementary.
- Measure children at their final size throughout the motion. The space reserved by Start expands while drawing is clipped to that opening, so traveling children cannot paint over adjacent tiles.
- Keep the source plate, replace its emptied preview with a collapse chevron, and use a divider/title instead of a second header button. Remove the arbitrary 12-degree folder-plane tilt; retain a small tap compression.
- Use one reversible linear timebase with layer easing. Cancelling or reversing retains the current timebase; partial transitions run only for the remaining distance. Dispose closing content after its animation completes rather than after an independent timer.
- Clear enough space for tiles that span the source folder's lower edge, preserving relative positions of displaced tiles.

The existing 330 ms opening / 260 ms closing envelope is retained as a launcher fit. The miniature travel, brief tap compression, and child stagger are inferred implementation choices, not authenticated Microsoft curves. This is a structural/motion correction and must not be described as proven 100% native equivalence. Start/app-launch/return motion functions and WP8.1's folder tween are unchanged.

Verification: unit tests cover coordinate endpoints, monotonic travel, opacity handoff, interruption duration, miniature geometry, and spanning-neighbor clearance. Instrumented tests exercise physical open/close/reopen taps, source collapse accessibility, child contraction toward the source, rename persistence, child launch bounds, and restored neighbor positions. The folder workflow records open/close/reopen at 16 ms virtual-clock intervals for visual review; those intervals are not native-video sampling resolution.

## Overdraw follow-up

In the launcher capture from Actions run `40446809472`, the 64 ms opening frame shows a traveling child painting over a stationary tile beside the folder. The follow-up clips the folder content to its animated opening while preserving its full-size measurement. A pixel regression test samples the stationary neighboring tile through opening and closing. The same test failed on the previous implementation (Actions run `40482750757`, open frame 2) and passed with the correction (run `40482752043`).

The Microsoft Live Folders animation that could be inspected is a Windows Phone 8.1 folder-creation sequence in edit mode, not a Windows 10 Mobile tap-to-open recording. It cannot establish W10M timing or prove this change matches the native animation. A usable W10M recording showing both opening and closing is still needed for a frame-by-frame comparison.
