# Start entrance and horizontal interaction

The supplied recording's Start reveal occurs at **56.033–56.633 seconds**, after the desktop appears around 55 seconds. The source contains 30 frames per second. Sampling it every 10 ms cannot reveal additional recorded frames; the implementation interpolates continuous tracks between the observations.

The cyan Mail tile settles at approximately `(120, 210, 248, 120)` in the 1920×1080 recording. Its observed bounds are `(162,230,235,114)` at 56.167 s, `(150,222,241,116)` at 56.200 s, `(142,217,243,117)` at 56.233 s, and `(132,213,246,119)` at 56.300 s. The final few pixels settle by 56.633 s. The entrance combines a leftward translation, scale recovery, opacity recovery, and trailing columns. Some captured frames are duplicates.

`StartEntranceMotion` adapts these tracks to each responsive tile band. It uses a 600 ms movement and up to 72 ms of column delay. The initial almost-invisible portion and column delays are estimates, not directly measured subframes. Tile placement and both horizontal list states remain separate from the visual transforms; the entrance does not scroll back to the first column or repack tiles.

## Wallpaper artwork

Microsoft documented [19 built-in background designs in Windows 8.1](https://blogs.windows.com/windowsexperience/2013/10/28/windows-themes-and-wallpapers-now-on-your-start-screen-too/), plus the desktop-wallpaper option. This launcher provides ten choices: its existing purple ribbon reconstruction and nine downloaded Start-screen designs.

The JPEG artwork comes from the public [Vivswan/Windows-8.1-Start collection](https://github.com/Vivswan/Windows-8.1-Start/tree/main/images/start_screen). Its appearance was inspected before inclusion. It is a third-party collection, not an independently authenticated Microsoft asset package. The descriptive picker names are launcher labels, not official Microsoft names. Microsoft retains rights in the original Windows artwork.

| Picker choice | Source file | Packaged resource |
|---|---|---|
| Purple ribbons | Existing code reconstruction | Vector paths |
| Robots | `2.jpg` | `start_wallpaper_1.jpg` |
| Pixel city | `3.jpg` | `start_wallpaper_2.jpg` |
| Swirls | `4.jpg` | `start_wallpaper_3.jpg` |
| Blossom | `7.jpg` | `start_wallpaper_4.jpg` |
| Garden | `8.jpg` | `start_wallpaper_5.jpg` |
| Facets | `13.jpg` | `start_wallpaper_6.jpg` |
| Night mountains | `14.jpg` | `start_wallpaper_7.jpg` |
| Dragon | `16.jpg` | `start_wallpaper_8.jpg` |
| Gears | `18.jpg` | `start_wallpaper_9.jpg` |

The downloaded images are flattened artwork. Their scrolling implementation is not Microsoft's original animated dragon/robot mechanism. The parallax implementation and portrait adaptation are launcher-specific; these should not be described as a pixel-exact reproduction of all Windows motion accents.
