# Changelog

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
