# Folder behavior by launcher mode

## Historical references

- Microsoft introduced Live Folders in Windows Phone 8.1 Update: dragging one
  Start tile over another creates a folder, and its children remain Live Tiles.
  https://blogs.windows.com/windowsexperience/2014/07/30/windows-phone-8-1-update-brings-cortana-to-new-markets-new-features/
- Microsoft's 2014 Windows Phone folder-creation GIF shows an inline folder
  with accent-colored upper and lower rules and an empty grid in the source
  tile when the children are open. The illustrated creation transition spans
  roughly 350 ms at the GIF's 70 ms sampling interval. This is creation in edit
  mode, not a recording of tap-to-open or tap-to-close; it does **not** establish
  the exact timing or easing for those gestures.
  https://blogs.windows.com/windowsexperience/2014/08/05/windows-phone-folders-a-new-way-to-organize-your-start-screen/
- Microsoft's Lumia 430 reviewer guide says the folder name is edited by
  tapping the text field above the open folder.
  https://news.microsoft.com/wp-content/uploads/prod/2016/04/Lumia_430-ReviewersGuide.pdf
- Windows 8/8.1 Start organizes tiles into named groups. Microsoft describes
  desktop tile folders as new to Windows 10's Creators Update.
  https://download.microsoft.com/download/6/F/5/6F512314-780B-42D7-9D56-BA501352B78A/W8_brochure_download.pdf
  https://blogs.windows.com/windowsexperience/2017/06/05/windows-10-tip-organize-start-files-new-tile-folders/

## Implementation

The two phone skins keep the shared pinned apps and folder contents, but their
folder presentation and transition clocks are independent. WP8.1 uses the
accent rules, empty source grid, tap-to-rename, and its own interruptible
350 ms open/close fit; its children, header and Start displacement share the
same clock. W10M keeps its white 1 dp rules and separate 380/420 ms fit. Both
sets of folder rules participate in their selected Start launch and return
scene. None of those fitted times should be described as a measured native
tap-to-open or tap-to-close timeline.

The desktop launcher's pre-existing package-folder implementation is exposed
through **Create folder** after selecting at least two app tiles. This is a
launcher extension: the native Windows 8.1 drag gesture and group layout stay
unchanged. The Desktop folder's apps remain available to both phone skins via
the canonical pin workspace; no existing pin or phone folder is discarded.
