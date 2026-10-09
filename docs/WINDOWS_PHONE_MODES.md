# Windows Phone modes — implementation notes

The existing **Windows 8.1 Desktop** surface is the default and remains in its previous
horizontal Start / All Apps implementation. The two new modes run through their own
vertical phone renderer, without changing the desktop tile coordinates or on-device desktop
tile data.

## Select a mode

Open PC settings → Launcher features → **Windows interface** and choose:

- Windows 8.1 Desktop (unchanged default).
- Windows Phone 8 (2012 Metro-style vertical Start and alphabetical app list).
- Windows 10 Mobile (2015/2016 phone Start, background, tile transparency and revised motion).

When a phone mode is selected, **Phone tile columns** selects a classic 4-small-cell
or dense 6-small-cell layout. Windows 10 Mobile also has **Live tile opacity**.
Phone-specific pin order, tile sizes and colors are persisted independently in the
launcher feature preferences, including existing feature JSON backups.

## Behavioral mapping

| Behavior | Windows Phone 8 | Windows 10 Mobile | Android launcher implementation |
| --- | --- | --- | --- |
| Start orientation | Vertical | Vertical | Vertical first-fit packed tile canvas |
| Tile sizes | Small / medium / wide | Small / medium / wide | Small 1×1, medium 2×2, wide 4×2 |
| App list | Swipe left; letters | Swipe left; search and letters | App-list swipe, search, alphabet jump |
| Motion | Reverse-indexed 3D tile turnstile | Faster, flatter transition | WP8.1 follows DiscoLauncher choreography; W10M uses its separate fitted Start track |
| Tile backgrounds | Solid | Background picture and tile opacity | Per-mode tile opacity, reused wallpaper |
| Tile updates | Windows Push/Live Tile APIs | Windows Push/Live Tile APIs | Android notification listener projection, **not** Windows Push |
| Customization | Pin/unpin, resize, colors | Pin/unpin, resize, colors | Per-mode persistence, phone edit options |
| Back, Start, Search | Hardware phone keys | Software bar | In-app simulated button row |
| Action Center | Not present in original WP8 release | Pull-down notifications / quick actions | Simplified Android-settings links, **not** a system replacement |

The **full Windows Phone OS is not recreated**: Lumia-specific system apps, the native
phone dialer/call service, original Windows lock screen/unlock choreography, native
notification shade, Continuum, universal Windows app frames and true Microsoft Live Tile
push providers cannot be supplied by an ordinary Android launcher. W10 action center is a
visual approximation; use the Android notification shade for real notifications. Native
Android app transitions may vary with Android/OEM policy.

The W10M Start animation is fitted to the supplied recordings; the WP8.1 app
launch and return choreography now follows the timing and transforms in
[DiscoLauncher](motion/WP81_DISCOLAUNCHER_FRAME_REVIEW.md). The remaining phone
surfaces still use their separate approximations.

## Research used as references

Primary historical documentation:

1. Microsoft, [Windows Phone 8 reveal](https://news.microsoft.com/source/2012/10/29/microsoft-unveils-windows-phone-8/) — three tile sizes, live tiles, Start personalization.
2. Microsoft MSDN, [Windows Phone 8 tile APIs](https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/june/windows-8-building-apps-for-windows-8-and-windows-phone-8) — small/medium/wide, flip/cycle/iconic tiles.
3. Microsoft Lumia 950 guide, [Explore your tiles, apps, and settings](https://microsoft-lumia-950.helpdoc.net/en-us/your-first-lumia/explore-your-tiles-apps-and-settings/) — swipe left/right, search and alphabet jumps.
4. Microsoft Lumia 950 guide, [Personalize Start](https://microsoft-lumia-950.helpdoc.net/en-us/basics/personalize-your-phone/personalize-the-start-screen/) — background, tile translucency, pin/unpin and move/resize.
5. Ars Technica, [Windows Phone 8 review](https://arstechnica.com/gadgets/2012/11/windows-phone-8-review-microsoft-lays-a-foundation-for-success/) — full-width tile screen, arrow below tiles, original sizing.

Period UI walkthroughs and motion / speed comparisons to consult during manual testing:

- [Nokia Lumia 920 — Windows Phone 8 review and walkthrough](https://www.youtube.com/watch?v=xCbd3NwT9Iw).
- [Nokia Lumia 920 UI demo](https://www.youtube.com/watch?v=_EB0Uc8gqFM).
- [Top new Windows 10 Mobile features, Lumia 950XL](https://www.youtube.com/watch?v=d_sQwCe9POw).
- [Windows 10 Mobile vs Windows Phone 8.1 animations](https://www.youtube.com/watch?v=POIE3hIpwhQ).
- [Windows 10 Mobile vs Phone 8.1 speed comparison](https://allaboutwindowsphone.com/features/item/21045_Speed_testing_Windows_10_Mobil.php).

## Validation

Phone tile packing has unit tests for four and six columns, empty packing, and unsupported
grid counts. CI runs JUnit tests and builds a performance APK independently, then fails
the workflow if any unit tests failed. Validate on a real Android phone before merging
for touch hitboxes, OS gesture interaction, animation frame pacing and wallpaper crop.
