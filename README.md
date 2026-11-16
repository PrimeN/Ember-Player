# Ember Player

A media player for Android phones, tablets and Android TV, with touch and remote controls. No ads.

**Development happens on [`beta`](https://github.com/PrimeN/Ember-Player/tree/beta).** The initial version is 0.1.0. No production APK release is published automatically.

## Playback

- **Two internal engines:** AndroidX Media3/Nextlib for normal playback and libmpv for compatibility playback, with automatic recovery where available.
- **Software audio:** AC-3, E-AC-3, DTS, TrueHD and other common formats are bundled. Standard also supports supported MPEG-H streams as stereo PCM and IAMF tracks in MP4 using Opus, FLAC or PCM. AAC-based IAMF and encrypted IAMF are excluded. HDR output, immersive passthrough and device-only codecs depend on the device and selected engine.
- **Automatic engine choices:** known filenames guide opaque file URLs, and successful playback choices are remembered privately for seven days. Reset them in Settings with Reset decoder memory. Explicit engine selections take priority. Supported RTMPS and SRTP streams open directly in Compatibility.
- **Player interfaces:** Glass, Ember, Basic and Streaming controls, independent of the device's home screen.
- **Files and USB:** select media through Android's document picker, including multiple files for a playback queue.
- **Device folders:** remember chosen folders, browse video/audio files, search and sort, select multiple files, share, rename, move or delete where the storage provider permits it. Each folder remembers its last-played file; rename/move preserves playback positions, track choices and scene bookmarks.
- **Network folders:** SMB, SFTP, WebDAV, FTP and FTPS, with on-demand SMB discovery and authenticated share browsing.
- **Stream URLs and Open with:** play supported network URLs or open media supplied by another application.
- **Resume and bookmarks:** saved positions, named scene bookmarks, queue ordering, repeat modes and chapter navigation.
- **Saved playlists:** keep reusable file, stream and network-folder queues, with shuffle and repeat. Saved network items reconnect through their account and file references when reopened.
- **Encrypted backups:** export and restore player settings, positions, bookmarks, playlists and connection details using a password. Backups include no video files; Android storage permissions may need to be renewed on another device.
- **Subtitles:** embedded tracks and manual text/bitmap subtitles, including SRT, VTT, ASS/SSA, TTML, SAMI and paired VobSub files. Timing, speed, custom fonts, colours, outlines, spacing and encoding controls are available. Find matching subtitle files beside a video or in a chosen folder; manual file choices are remembered privately for each video. Advanced layout and dual subtitles use Compatibility playback. Imported font files need to be installed again after restoring a backup on another device.
- **Offline subtitle assistance:** optional translation through Android’s installed on-device language models (Android 12+) and read-aloud through installed offline speech voices. No cloud translation, API keys or automatic model downloads. Availability depends on the device; original subtitles remain available.
- **Private media:** hide selected files and folders behind a PIN, lock the private area, and disable or clear recent activity and resume positions. Private playback stays out of background audio, picture-in-picture and system media controls. This protects access inside the app; original files are not encrypted or hidden from other apps. Encrypted backups include privacy settings.
- **USB browsing:** browse permitted audio and video on removable drives Android has mounted and indexed, even without a document picker. Media access is requested when you open a drive; supported filesystems and indexing depend on Android. Drive removal stops playback and releases its file handles. The system picker remains available for other files.

- **Sound and picture:** track selection, HDMI passthrough, lip sync, separate Bluetooth delay, ten-band equalizer with saved presets, bass/treble, stereo width, picture fit, frame-rate matching and optional deinterlacing. Sound effects apply to decoded audio; passthrough keeps the original audio.
- **Device-aware performance:** automatic, low, balanced and high settings; optional glass surfaces and remembered working video decoders.
- **Precise playback controls:** enter an exact position in seconds, MM:SS or HH:MM:SS, including milliseconds. Choose a custom speed from 0.25× to 4× alongside the presets. Entered timestamps use precise seeking and preserve pause state.
- **Touch controls:** choose independent left, centre and right double-tap actions and edge widths; swap brightness/volume sides or turn horizontal seeking off. By default, double-tap to seek or pause, swipe to seek or change volume/brightness, pinch to zoom, and hold for a configurable temporary playback speed. Gestures and rotation are configurable; playback lock has an explicit unlock button and Back-key escape.
- **Picture enhancement:** GPU upscaling is on by default, with a master off switch and individual controls in Settings > Picture enhancement and Player options > Video. Standard uses Lanczos; Compatibility also offers a bundled fast enlargement shader. Brightness, contrast, saturation, gamma, hue and sharpness start neutral, with optional presets. HDR and protected playback keep their supported output path.
- **Frame inspection:** move forward or backward through individual frames while paused in Compatibility playback. The player reports the resulting timestamp rather than estimating a frame count. Switching preserves working audio; unsupported tracks keep their current engine.
- **Responsive seeking:** repeated seek inputs combine over a short fixed window to reduce decoder work. The displayed target updates immediately; entered times, resume positions and chapter jumps remain precise.
- **Optional subtitle gestures:** with controls hidden and subtitles selected, start in the lower third to adjust timing horizontally, position vertically or text size with a pinch. Changes apply when released, with size and position remembered; cancellation restores the preview. System caption styling keeps priority. Remote users retain the subtitle menu controls.
- **Optional background audio:** keep sound playing with the screen off or while using another app, with notification, lock-screen, headset and Bluetooth controls. Return through the notification or the Playing entry on the media browser. Playback hands ownership to a foreground media service; returning to video reopens it at the current position. Picture in picture takes priority when enabled.

Format support depends on the engine, device decoders, audio output and display. HDR10+, Dolby Vision and encoded surround output require compatible hardware. There is no promise that every format or profile works on every device.

## Privacy and permissions

Local playback needs no account or API key. No developer credentials are included. Online subtitle search and IntroDB are optional; online subtitle search is off initially.

Recent media addresses and network credentials use Android Keystore-backed encrypted storage. If encrypted storage is unavailable, these values are not saved through a plaintext fallback. Resume positions and scene bookmarks remain in the application's private storage. Clearing disposable caches preserves saved media settings and credentials.

Chosen folders and their recent-file history are also encrypted. Device-video thumbnails stay in a small memory cache and are removed through Clear disposable caches. Deleting files requires confirmation. A file move completes and checks the copy before removing the original.

Backups use password-derived AES-256-GCM encryption. The password is not saved. Wrong passwords, damaged files and invalid backup contents are rejected before settings are replaced; failed writes attempt to restore and verify the previous configuration. Keep the backup password separately, since it cannot be recovered.

The app requests access to selected files through Android's document picker. It does not replace the home screen, run a home-button accessibility service, start at boot, request all-files storage access or install application updates.


### Receivers and USB drives

- Send the selected media to a Google Cast or DLNA receiver on the same IPv4 local network. Open Playback options → Cast to a receiver. Google Cast setup starts only when enabled on that screen and requires compatible Google Play services; DLNA discovery works independently.
- Remote controls include play/pause, seeking, volume and disconnect, with background playback controls in the media notification. The app relays media through temporary URLs restricted to the chosen receiver. Saved server headers and upstream URLs stay on the device. Private playback, DRM and separately linked audio/video sources cannot be sent.
- The receiver must support the media format. Casting does not provide video transcoding; HDR, audio formats and adaptive-stream support depend on the receiver.
- Browse supported FAT32 USB mass-storage drives directly, including files missing from Android’s index. Access is read-only and requested explicitly. Other filesystems continue to use Android’s mounted-drive or document-provider access.
- Direct USB playback keeps its drive connection until playback ends. Its temporary queues are not saved as playlists. When private media is enabled, use Android’s USB/document browser to preserve protected document identities.

### Library and media tools

- Search across saved media folders using an encrypted local index. Refresh it after changing files; no full scan runs at startup. Private files and forgotten roots are excluded from results.
- Set a sleep timer for a preset or custom duration, or stop at the end of the current item. It applies to video and background audio.
- Save a screenshot to a destination you choose. Captures are limited to 1080p and are unavailable for private playback and protected video surfaces.
- Mark a clip start and end from Playback options, then export an MP4 without changing the source. Export uses the device’s H.264/AAC codecs, so supported input formats and speed depend on the device. Keep playback open during export; progress and cancellation are available in Playback options.
- Download direct HTTP/HTTPS media files for offline use. Downloads are explicitly started, run with a foreground notification, and can be paused or resumed when the server supports byte ranges and validators. Saved URLs and download metadata are encrypted. Adaptive playlists and DRM streams are excluded; downloads interrupted by Android can be resumed manually.

## Installation and builds

- Android 10 or newer; ARM64 or ARM32.
- Touch controls on phones and tablets; remote or controller navigation on TVs. Rotation can follow the device or use a chosen orientation.
- The application ID is `app.ember.player`; debug installations use `app.ember.player.debug`.
- Android’s document picker is used for selected files, saved folders and export destinations. Mounted USB browsing can use Android’s media index with explicit media-library permission; network folders work independently.
- Folder views use pages of 200 provider entries. Library search indexes selected folders and subfolders on request, with encrypted storage and pages of 100 results. Queues and saved playlists support up to 10,000 items; saved playlists share a 16 MiB storage budget.

To build from source, install JDK 17, Gradle 9.8.2 and the Android SDK platform used by `app/build.gradle.kts`:

```sh
gradle testDebugUnitTest assembleDebug
```

An optimized unsigned release can be checked with:

```sh
gradle assembleRelease lintRelease
```

Sign production packages with your own keystore outside the repository. Do not commit keys or signing passwords. The GitHub workflow runs functional regression tests and release checks on pushes and pull requests. A manual workflow run also makes an installable debug artifact available in Actions; ordinary pushes do not publish releases.

## Source and licenses

Ember Player is distributed under the [GNU GPL v3](LICENSE). See [playback dependencies](player-dependencies.md) for pinned components, source locations and reproducible native build instructions. Applicable license texts are bundled with the app and available in Settings → About.

Service names, codec names and any retained logos are trademarks of their respective owners. Their inclusion does not imply endorsement.
