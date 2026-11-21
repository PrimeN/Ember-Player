# Changelog

## [1.0.0]

- Reuse saved-folder listings for paging, search and sorting during the current visit.

- Add mpv program and quality choices, explain automatic core and renderer decisions, and prepare reusable native resources off the main thread.
- Search and naturally sort entire saved folders, show durations before videos are played, and improve compact phone headers and TV accessibility labels.
- Fix background embedded artwork, unknown-length WebDAV playback, unreadable folder-history writes and subtitle ZIP handling.
- Prefer gpu-next for automatic mpv rendering, with one Stable GPU fallback and an explicit manual renderer choice.
- Offer explicit Media3 and mpv playback-core choices in settings and the player menu.
- Update to mpv 0.42.0 and the Android wrapper 1.1.0, with matching FFmpeg 9.0.2 filters and rebuilt custom native components.
- Handle remote Menu presses safely while native playback resources are loading, and use consistent playback-core names.
- Provide smaller ARM64 and ARM32 APKs alongside the universal APK, plus an Android App Bundle for store distribution. Trim unused crypto code, add privacy-policy access in About, and refresh the screenshot gallery and release documentation.

## [0.8.5]

A broad update to playback, the media library and the phone and TV interfaces.

- **Library and playlists:** automatic background folder scans that resume after interruptions, natural sorting, better recent and Continue watching shelves, improved search including partial Chinese and Japanese titles, and playlist imports from the queue with entry reordering.
- **Phone and TV controls:** clearer navigation, consistent buttons and settings, stable artwork and card layouts, readable warnings, better accessibility, remote-scrollable licenses and seek buttons that follow your chosen interval.
- **Background playback and PiP:** Media3 system media controls, Android Auto support, PiP playback buttons and sizing, smoother transitions and improved screen-off handoff.
- **Playback and subtitles:** local and authenticated-stream chapters, MP4 chapter support, chapter-based intro and credits skipping, improved Compatibility audio timing and captions, Traditional Chinese decoding and better imported-font names.
- **Cast and network files:** saved receiver progress, restored volume after sleep-timer fading, more reliable TV discovery, SFTP fingerprint confirmation and key sign-in, and separate SMB options for guest and older servers.
- **Clips and screenshots:** lossless, keyframe-aligned MP4 clips using the selected audio track, an explicit video-only fallback, broader document-provider support, and screenshots with subtitles up to 4K with an HDR colour warning.
- **Privacy and saved data:** stronger PIN and backup protection, current-PIN checks for protection changes, safeguards and recovery for unreadable lists, safer backup restores, and activity clearing that includes bookmarks and remembered tracks. Saved bookmarks no longer share a title limit across profiles.
- **Maintenance:** Android 16 compatibility, refreshed dependencies, a verified Gradle wrapper, isolated release signing and removal of unused integrations.

## [0.7.5]

The first public Ember Player release for Android phones, tablets and TVs.

### Playback

- Standard playback through AndroidX Media3/Nextlib and a Compatibility engine
  using libmpv, with automatic recovery and remembered working decoder choices.
- Local files, streams, saved folders and playlists; SMB, SFTP, WebDAV, FTP and
  FTPS connections, mounted storage and supported read-only FAT32 USB access.
- Resume, watched-state tracking, chapters, scene bookmarks, queues, shuffle,
  repeat, A–B repeat, exact seeking, custom playback speeds and frame inspection
  in Compatibility playback.
- Touch gestures and remote controls, sleep timers, picture-in-picture and
  optional background audio with system media controls.

### Sound, picture and subtitles

- Bundled software audio decoding for formats including AC-3, E-AC-3, DTS and
  TrueHD, plus supported MPEG-H and IAMF playback paths.
- Track selection, supported audio passthrough, timing and Bluetooth lip-sync
  controls, equalizer presets and optional night listening.
- Picture profiles, GPU upscaling, framing, deinterlacing and configurable
  HDR-to-SDR tone mapping; experimental SDR-to-HDR expansion is optional.
- Embedded and external subtitles, timing and appearance controls, custom fonts,
  style previews, saved presets and dual subtitles in Compatibility playback.
- Optional subtitle translation and read-aloud using installed on-device
  services and language resources; availability depends on the device.

### Library, devices and privacy

- Adaptive phone, tablet and TV layouts with Ember branding, media shelves,
  Continue watching, searchable settings and four playback-control layouts.
- Encrypted local media indexing, playlists, saved connections and
  password-encrypted settings backups.
- PIN-protected private media, configurable history and resume preferences.
- Optional Google Cast and DLNA, direct-file downloads, screenshots and MP4 clip
  export, with limitations documented in the feature guide.

### Requirements and downloads

- Android 10 or newer, with ARM64 or ARMv7 processors.
- Install `ember-player-0.7.5.apk`; `SHA256SUMS.txt` provides its checksum.
- Local playback needs no account or API key. Network services and receiver
  integrations remain optional.
- Codec profiles, HDR/Dolby Vision output, surround passthrough and receiver
  playback depend on the engine, device and connected equipment. Software AC-4,
  encrypted/AAC-based IAMF and universal Dolby Vision FEL support are not included.

[Feature details](docs/features.md) · [Privacy](docs/privacy.md) · [Build and signing](docs/building.md)
