# Ember Player

A media player for Android phones, tablets and Android TV, with touch and remote controls. No ads.

**Development happens on [`beta`](https://github.com/PrimeN/Ember-Player/tree/beta).** The initial version is 0.1.0. No production APK release is published automatically.

## Playback

- **Two internal engines:** AndroidX Media3/Nextlib for normal playback and libmpv for compatibility playback, with automatic recovery where available.
- **Player interfaces:** Glass, Ember, Basic and Streaming controls, independent of the device's home screen.
- **Files and USB:** select media through Android's document picker, including multiple files for a playback queue.
- **Device folders:** remember chosen folders, browse video/audio files, search and sort, select multiple files, share, rename, move or delete where the storage provider permits it. Each folder remembers its last-played file; rename/move preserves playback positions, track choices and scene bookmarks.
- **Network folders:** SMB, SFTP, WebDAV, FTP and FTPS, with on-demand SMB discovery and authenticated share browsing.
- **Stream URLs and Open with:** play supported network URLs or open media supplied by another application.
- **Resume and bookmarks:** saved positions, named scene bookmarks, queue ordering, repeat modes and chapter navigation.
- **Subtitles:** embedded tracks and manual text/bitmap subtitles, including SRT, VTT, ASS/SSA, TTML, SAMI and paired VobSub files. Timing, speed and appearance controls are available in the player.
- **Sound and picture:** track selection, HDMI passthrough, lip sync, separate Bluetooth delay, ten-band equalizer with saved presets, bass/treble, stereo width, picture fit, frame-rate matching and optional deinterlacing. Sound effects apply to decoded audio; passthrough keeps the original audio.
- **Device-aware performance:** automatic, low, balanced and high settings; optional glass surfaces and remembered working video decoders.
- **Touch controls:** double-tap to seek or pause, swipe to seek or change volume/brightness, pinch to zoom, and hold for temporary 2× playback. Gestures and rotation are configurable; playback lock has an explicit unlock button and Back-key escape.

Format support depends on the engine, device decoders, audio output and display. HDR10+, Dolby Vision and encoded surround output require compatible hardware. There is no promise that every format or profile works on every device.

## Privacy and permissions

Local playback needs no account or API key. No developer credentials are included. Online subtitle search and IntroDB are optional; online subtitle search is off initially.

Recent media addresses and network credentials use Android Keystore-backed encrypted storage. If encrypted storage is unavailable, these values are not saved through a plaintext fallback. Resume positions and scene bookmarks remain in the application's private storage. Clearing disposable caches preserves saved media settings and credentials.

Chosen folders and their recent-file history are also encrypted. Device-video thumbnails stay in a small memory cache and are removed through Clear disposable caches. Deleting files requires confirmation. A file move completes and checks the copy before removing the original.

The app requests access to selected files through Android's document picker. It does not replace the home screen, run a home-button accessibility service, start at boot, request broad storage access or install application updates.

## Installation and builds

- Android 10 or newer; ARM64 or ARM32.
- Touch controls on phones and tablets; remote or controller navigation on TVs. Rotation can follow the device or use a chosen orientation.
- The application ID is `app.ember.player`; debug installations use `app.ember.player.debug`.
- A file provider/document picker must be available for selecting local or USB files. Network folders remain available independently.
- Folder browsing currently shows up to 2,000 media entries per directory; playback queues hold up to 200 files.

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
