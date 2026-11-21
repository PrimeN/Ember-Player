# Library and settings

## Browse your media

On phones and tablets, the floating navigation dock contains **Home**, **Files**,
**Settings** and **Search**. Use **Open file** in the header to pick media, or
choose a destination from the selector in **Files**.

TV puts **Browse library**, **Search** and **More options** in the header. Browse
library opens Home, Folders, Network, Playlists, Downloads and Private media.
Open files and Settings from More options. Focus is visible when navigating with
a remote or controller.

Home groups your media into Continue watching, a video shelf, saved-folder
shortcuts and Unwatched. Selecting a folder shortcut opens that folder in Files.
The arrow beside a shelf opens its full view. Use Back to return to the shelves.
Card density adjusts to the available width and system text size.

Open **More options → Show media** to filter by All media, Continue watching,
Unwatched or Watched. A saved position at
95% of the known duration counts as watched. A file without a known duration is
not assumed to be finished. Continue watching opens the saved media item with its
existing resume behavior.

Use **More options → Sort media** to choose **Date modified** or **Name A–Z**.
Modification dates come from the saved folder index. Choose **List view** or
**Grid view** from the same menu. Filter, sort and layout choices are saved and
included in encrypted settings backups.

Library results are filtered and sorted before pagination, with duplicate items
removed. Search matches filenames across saved
folders through an encrypted index. Adding a folder schedules a background scan,
and startup refreshes an index older than 15 minutes or changed saved roots.
Use **Folders → Saved folder library → Refresh library** for an immediate scan.
Use **More options → Reload displayed media** to reload the visible catalog.

When you browse inside a saved folder, paging, search and sorting reuse the same
listing for that visit. Refreshing, changing folders, changing files or changing
privacy settings discards the listing. The cache holds metadata in memory for up
to 20,000 entries within an estimated 16 MiB. Larger folders use the existing
bounded-memory scan.

Thumbnails come from local video frames. The app does not automatically download
movie posters or create an online catalog for local files. The screenshot gallery
uses representative media to illustrate the layout.

## Find a setting

Open Settings from the dock or from **More options** on TV. Search by a feature
name such as `subtitles`, `timeout`, `equalizer`, `HDR` or `backup`. A result opens
the relevant category and section. Back returns to your search results.

| Category | Main controls |
| --- | --- |
| Playback | Engine and control layout, resume, seeking, gestures, rotation, sleep timer, background audio and chapter-based skip markers |
| Picture & HDR | Picture adjustments, upscaling, profiles, renderer, tone mapping, decoding and deinterlacing |
| Audio | Languages, tracks, output, passthrough, delay, equalizer and night listening |
| Subtitles | Languages, timing, fonts, appearance, saved styles and optional assistance |
| Network & cache | Connection/read timeouts, memory buffers and disposable cache clearing |
| Appearance | Palette, day/night mode, glass surfaces and performance level |
| Privacy | Private-media PIN, history and encrypted backup/restore |
| About | Version, legal information and dependency licenses |

Glass, Ember, Basic and Streaming are playback-control layouts. Glass and Ember
keep Back at the top and group the timeline, transport and other actions at the
bottom. Transport and other playback actions stack in narrower windows.
**Appearance** sets the app's palette and theme. Dark is the default on every
device. Light and Follow system are also available.

Choose **Playback → Playback core** to select Automatic, Media3 or mpv as your
default. The player menu offers the same choices during playback. Automatic can
choose between the cores; either manual choice takes priority. The player menu explains whether the active core was selected by format, a remembered choice, a requested feature or recovery. With mpv active, Video → Program or quality lists source-provided programs, variants or editions. Seekable video retains its position; live playback follows the selected stream.

Start with automatic core and performance selection. For a constrained device,
try Low performance and turn off picture enhancement before forcing software
decoding. Hardware, display and receiver capabilities still determine output.

[All features](features.md) · [Troubleshooting](contributing.md#report-a-bug)
