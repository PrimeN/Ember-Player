# Privacy and local data

Local playback requires no account or API key. Ember Player has no advertising
interface. The app uses Android's file picker for selected media and folders;
mounted-drive browsing can request media-library access when you open that tool.

## Data on the device

- Recent media addresses, saved folder grants and network credentials use
  Android Keystore-backed encrypted storage. Sensitive values are not saved
  through a plaintext fallback if that storage is unavailable.
- The filename search index uses SQLCipher and can be rebuilt from your selected
  folders. Adding a saved folder schedules a background scan, and startup
  refreshes stale indexes. Other storage is not scanned automatically.
- Playback positions, bookmarks, playlists and preferences stay in the app's
  private storage. Device-video thumbnails use a bounded memory cache.
- **Clear disposable caches** preserves settings, positions and credentials.
- **Clear all saved activity** removes recent media, resume positions, bookmarks and remembered audio/subtitle selections across profiles after confirmation. Playlists, saved folders and connection credentials are kept.

## Connections you choose

Opening a stream or a network folder connects to its specified server. Receiver
discovery, Google Cast and online subtitle search are optional tools.
Skip markers are derived locally from chapter names; no title lookup is sent to a marker service.
Google Cast initializes when enabled from the receiver screen and requires
compatible Google Play services. DLNA works independently.

Subtitle translation uses supported Android on-device translation services and
already installed models. Speech assistance uses installed offline voices.
Availability depends on the device; Ember Player does not automatically download
translation packs or send subtitle text to a cloud translation provider.

## Private media

The private-media PIN protects access inside Ember Player. It does **not** encrypt
the original videos or prevent other apps from accessing their own granted files.
Private playback is excluded from background audio, picture-in-picture and system
media controls. Screenshot/export restrictions apply while private media is in use.

## Backups

Exported backups use password-derived AES-256-GCM encryption. The password is not
saved and cannot be recovered. Backups include settings, positions, bookmarks,
playlists and connection details; they do not include video files or imported
font files. Android folder permissions may need to be granted again after a
restore on another device.

Wrong passwords, damaged files and invalid contents are rejected before settings
are replaced. Keep a backup password somewhere separate from its file.

Public bug reports should contain no passwords, private server addresses,
access tokens or signed media URLs. See [reporting a bug](contributing.md#report-a-bug).
