# Changelog

## Unreleased

- Update SQLite, Commons IO, JSON serialization and the JVM test framework.
- Refuse incomplete encrypted backups and preserve the local library index key on restore.
- Validate restored privacy settings, require the current PIN and keep protection markers consistent.
- Keep repeated queue entries distinct when editing and remembering playlist progress.
- Snapshot clip-export inputs on the player thread and clean up cancelled exports safely.
- Prevent duplicate download submissions and disable pending form controls.
- Accept subtitle language script tags and align non-Latin indexed searches with voice search.

- Resume interrupted library scans from committed folder cursors.
- Export keyframe-aligned clips without re-encoding, using the selected audio track.
- Include captions in screenshots from both engines, up to 4K, and warn when HDR screenshot colours may be inaccurate.
- Read MP4 chapter metadata and handle caption, audio, stop, percentage-seek and frame-step keys.
- Offer confirmed bookmark cleanup and fade timed playback during its final minute.
- Update Android 16 navigation, edge-to-edge layouts and phone/TV controls.
- Add system-following themes, natural sorting, playlist queue imports and reordering,
  richer file browsers and translatable interface resources.
- Refresh saved-folder indexes automatically in background jobs and preserve library
  pages and scroll positions when returning from playback.
- Preserve receiver progress, provide PiP playback actions and retain session brightness.
- Load local and authenticated-stream chapters, decode Traditional Chinese subtitles
  with Big5, and improve imported font names and Compatibility settings.
- Derive skip markers from named chapters and keep enabled background audio playing
  when PiP is stopped by screen-off.
- Offer separate SMB signing and encryption choices for guest and older servers.
- Use consistent word matching for indexed media, recent items and voice searches.
- Protect unreadable saved lists, provide confirmed recovery, improve folder permission
  cleanup and require the current PIN to change private-media protection.
- Strengthen backup/PIN key derivation, SFTP host verification and key authentication,
  and keep sensitive playback details out of retained crash logs.
- Verify Gradle builds, isolate signing from ordinary CI and remove unused integrations.

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
