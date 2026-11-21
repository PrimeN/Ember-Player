<p align="center">
  <img src="assets/ember-player-banner.svg" width="100%" alt="Ember Player — Your media. Every screen." />
</p>

<p align="center">
  A media player for Android phones, tablets and TVs.<br />
  Local files, network libraries and streams. Touch and remote controls. No ads.
</p>

<p align="center">
  <a href="https://github.com/PrimeN/Ember-Player/actions/workflows/android.yml?query=branch%3Amain"><img src="https://github.com/PrimeN/Ember-Player/actions/workflows/android.yml/badge.svg?branch=main" alt="Android build and checks" /></a>
  <a href="https://github.com/PrimeN/Ember-Player/releases/latest"><img src="https://img.shields.io/github/v/release/PrimeN/Ember-Player?color=ffb575" alt="Latest release" /></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 10 or newer" />
  <img src="https://img.shields.io/badge/ARM-32%20%26%2064%20bit-ffb575" alt="ARM 32-bit and 64-bit" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPLv3-ffb575" alt="GNU GPL v3" /></a>
</p>

<p align="center">
  <a href="https://github.com/PrimeN/Ember-Player/stargazers"><img src="https://img.shields.io/github/stars/PrimeN/Ember-Player?style=flat&amp;color=ffb575" alt="GitHub stars" /></a>
  <a href="https://github.com/PrimeN/Ember-Player/network/members"><img src="https://img.shields.io/github/forks/PrimeN/Ember-Player?style=flat&amp;color=ffb575" alt="GitHub forks" /></a>
  <a href="https://github.com/PrimeN/Ember-Player/issues"><img src="https://img.shields.io/github/issues/PrimeN/Ember-Player?color=ffb575" alt="Open issues" /></a>
  <a href="https://github.com/PrimeN/Ember-Player/commits/main/"><img src="https://img.shields.io/github/last-commit/PrimeN/Ember-Player/main?color=ffb575" alt="Latest main commit" /></a>
</p>

<p align="center">
  <a href="https://github.com/PrimeN/Ember-Player/releases/latest">Download</a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="docs/features.md">Features</a> ·
  <a href="docs/building.md">Build</a> ·
  <a href="docs/README.md">Guides</a> ·
  <a href="https://github.com/PrimeN/Ember-Player/issues/new?title=%5BBug%5D%20">Report a bug</a>
</p>

## Made for your media

Ember Player uses Ember Launcher's warm charcoal-and-orange palette. Pick
up where you stopped, browse saved folders or open a network stream. The library
uses the same Home shelves, library tools and searchable Settings across devices.
Phones and tablets use a bottom dock. TVs use a remote-friendly toolbar with
visible D-pad focus. Dark is the default, with Light and Follow system available
under Appearance.

Local playback needs **no account or API key**. Optional network and subtitle tools
are enabled when you choose to use them. See [privacy and local data](docs/privacy.md).

| | What is included |
| --- | --- |
| **Playback** | Media3/Nextlib and mpv playback cores, automatic routing and remembered working decoder choices |
| **Your library** | Continue watching, compact media shelves, saved folders, filename search, playlists, watched-state filters and grid/list views |
| **Files and servers** | Android file picker, mounted USB, read-only FAT32 USB browsing, SMB, SFTP, WebDAV, FTP and FTPS |
| **Sound and picture** | Track selection, passthrough, delay controls, equalizer, night listening, picture profiles, optional upscaling and tone mapping |
| **Subtitles** | Embedded and external captions, custom fonts and styles, timing controls, dual subtitles in mpv and optional on-device assistance |
| **Playback tools** | Exact seeking, custom speed, gestures, chapters, bookmarks, A–B repeat, queues, sleep timer, picture-in-picture and optional background audio |
| **Beyond the screen** | Optional Google Cast and DLNA, resumable direct-file downloads, screenshots and MP4 clip export |
| **Privacy** | PIN-protected private area, encrypted sensitive storage and library index, password-encrypted settings backups |

[Explore the full feature guide →](docs/features.md)

## Screenshots

The library screenshots show the same sample collection on phone, tablet and TV.
The playback preview shows the controls over an illustrative backdrop.

<p align="center">
  <img src="docs/screenshots/player.png" width="100%" alt="Ember Player Glass playback controls" />
</p>

<table>
  <tr>
    <th align="center">Phone library</th>
    <th align="center">Settings</th>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/mobile.png" width="320" alt="Phone Home with Continue watching, Open file, More options and a bottom navigation dock" /></td>
    <td align="center"><img src="docs/screenshots/settings.png" width="320" alt="Searchable Settings organized into eight categories" /></td>
  </tr>
</table>

<details open>
  <summary><strong>Tablet library</strong></summary>
  <p><img src="docs/screenshots/tablet.png" width="100%" alt="Tablet Home with wide media shelves and a centered bottom dock" /></p>
</details>

<details open>
  <summary><strong>TV library</strong></summary>
  <p><img src="docs/screenshots/tv.png" width="100%" alt="TV Home with Browse, Search and More options above the media shelves" /></p>
</details>

Sample frames from *Big Buck Bunny*, © 2008, Blender Foundation / www.bigbuckbunny.org. [Gallery and image credits](docs/screenshots/README.md)

## Get started

Ember Player supports **Android 10 or newer** on **ARM64 and ARM32**. Phones and
tablets use touch controls; TVs support remotes and controllers.

Download the APK from the [latest release](https://github.com/PrimeN/Ember-Player/releases/latest)
and install it on your device. Choose **arm64-v8a** for modern ARM64 devices,
**armeabi-v7a** for ARM32 devices, or the universal APK if you are unsure.
Use the `.aab` for store distribution. See the changelog for features, fixes
and compatibility details by version. [Changelog](CHANGELOG.md)

Development happens on [`beta`](https://github.com/PrimeN/Ember-Player/tree/beta).
Code pushes build and validate the app, with unsigned APK artifacts. Publishing
a signed release requires an explicit version tag or manual request and approval
through the protected release-signing Environment.

To build the app with JDK 21 and Android SDK platform 37:

```sh
git clone --branch beta https://github.com/PrimeN/Ember-Player.git
cd Ember-Player
./gradlew testDebugUnitTest assembleDebug
```

[Build instructions and native components →](docs/building.md)

## Format and device support

Ember combines Android's decoders with bundled software playback. Common
containers include MP4, MKV, WebM, AVI, MOV and MPEG-TS. Software audio includes
AC-3, E-AC-3, DTS and TrueHD. Media3 also supports selected MPEG-H streams as
stereo PCM and unencrypted IAMF in MP4 using Opus, FLAC or PCM. mpv playback can
also decode supported tracks to PCM for eligible local MP4-family files.

HDR10+, Dolby Vision, encoded surround output and device-only codecs depend on
the hardware, output path and selected core. There is no bundled software AC-4
decoder. AAC-based/encrypted IAMF and Dolby Vision full enhancement-layer playback
are not advertised as universally supported. Casting requires a receiver that
supports the media; it does not transcode video.

[Playback details and limits →](docs/features.md) ·
[Dependency and source inventory →](player-dependencies.md)

## Help and contribute

Found a problem? [Report a bug](https://github.com/PrimeN/Ember-Player/issues/new?title=%5BBug%5D%20)
with the app version, device, Android version and steps to reproduce it. Include
engine and codec details for playback problems. Remove credentials and private
addresses from logs and screenshots.

[Suggest a feature](https://github.com/PrimeN/Ember-Player/issues/new?title=%5BFeature%5D%20)
or contribute a focused pull request against `beta`. The Android workflow checks
functional regressions, the optimized build, lint and native packaging.

[Support and contribution guide →](docs/contributing.md)

## License

Ember Player is distributed under the [GNU GPL v3](LICENSE). Dependency license
texts are included in the app under **Settings → About**. Pinned source revisions,
modifications and native rebuild instructions are documented in
[Playback dependencies and source](player-dependencies.md).

Service names, codec names and logos are trademarks of their respective owners.
Their inclusion does not imply endorsement.
