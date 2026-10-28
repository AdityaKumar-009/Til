# Windows Phone home-screen feature audit

Research date: 10 October 2026

## Reference and validation limits

Microsoft's archived developer requirements say Windows Phone 8.1 development uses
Windows, and its emulator needs 64-bit Windows 8.1 Professional or later plus Client
Hyper-V and SLAT. The Windows 10 Mobile emulator likewise depends on the Windows
Visual Studio toolchain and Hyper-V. This checkout runs Ubuntu 24.04 and has neither a
Windows runtime nor a Hyper-V/KVM device, so those kits cannot be installed or run here.

The only source video in the checkout is `test.mp4`, a Windows 8.1 desktop capture.
It is not a Windows Phone or Windows 10 Mobile recording. The motion PNGs under
`wp81-preview/evidence` are Tile8 renderer output, so they are not independent phone
reference frames. This audit uses Microsoft's historical feature material and the
existing DiscoLauncher source notes; it does not claim a new emulator or source-video
frame match.

## Home-screen feature map

| Category | Windows Phone 8.1 | Windows 10 Mobile | Tile8 status |
| --- | --- | --- | --- |
| Start layout | Vertical Start; users can pin, move, resize and remove tiles; two or three medium-tile columns | Similar vertical Start, with adjustable tile density | Phone modes have independent order, size and color; the column setting selects two or three medium tiles across. Long-press opens phone tile editing. |
| Tile sizes | Small, medium and wide; large desktop tile is not a phone size | Small, medium and wide | Implemented for phone Start. |
| Folders | Live folders are available on updated Windows Phone 8.1 devices | Live folders are available | Phone-mode folder creation/opening is not wired to the desktop Start folder implementation yet. |
| Background and accent | User picture, stock backgrounds and accent color; the picture appears through transparent tiles | Background picture plus a tile-transparency control; Microsoft demonstrated full-screen background treatment | Stock/custom wallpaper, overlay and accent controls already exist. Windows 10 Mobile has a per-tile-opacity setting. Tile8 does not yet expose separate `tile picture` and `full-screen picture` treatments. |
| Live tiles | Tiles show app-provided changing content and badges | Same glanceable tile concept | Android notifications can be projected into tiles after notification access is granted. This is a compatibility bridge, not Microsoft's Live Tile feed/update system. |
| App list | Alphabetical list with letter navigation and device search | App list with search; recently installed apps can be promoted to its top | Phone modes have search and alphabetical letter navigation. Windows 10 Mobile now optionally puts newly installed apps in a `Recently installed` group at the top; the toggle is in launcher settings and defaults on for that mode. |
| Lock and system surfaces | Lock-screen status, notifications and quick actions are OS services | Lock screen and Action Center are OS services | Tile8 has optional launcher lock-screen presentation and status slots. Android's real lock, notifications and quick settings remain system-owned. |

## Launcher settings categories

The launcher feature screen groups the existing controls under **Phone Start screen**,
**Tiles and live content**, **App list and search**, **Start shortcuts**,
**Windows 8.1 lock screen**, and **Backup and restore**. The background and accent
picker remains in the Start personalization section of the Settings app. Windows 8.1
Desktop remains the selected default and keeps its independent tile layout and rendering.

## Sources

- Microsoft, [Windows Phone 8.1 system requirements for Visual Studio 2013](https://learn.microsoft.com/en-us/visualstudio/releases/2013/vs2013-sysrequirements-vs).
- Microsoft, [Visual Studio 2015 system requirements](https://learn.microsoft.com/en-us/visualstudio/releases/2015/vs2015-sysrequirements-vs).
- Microsoft, [Windows Phone 8.1 Start-screen tips](https://blogs.windows.com/windowsexperience/2014/08/11/4-tips-for-a-standout-start-screen-on-your-windows-phone/).
- Microsoft Devices, [Lumia Cyan and Windows Phone 8.1 features](https://blogs.windows.com/devices/2014/07/15/lumia-cyan-update/).
- Microsoft, [Windows 10 phone Start demonstration](https://news.microsoft.com/speeches/satya-nadella-terry-myerson-joe-belfiore-and-phil-spencer-windows-10-briefing/).
- Microsoft Learn, [Windows Phone 8.1 secondary-tile behavior](https://learn.microsoft.com/en-us/uwp/api/windows.ui.startscreen.secondarytile).
- Tile motion implementation reference: [DiscoLauncher frame review](motion/WP81_DISCOLAUNCHER_FRAME_REVIEW.md).
