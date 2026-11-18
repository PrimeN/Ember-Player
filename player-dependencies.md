# Playback dependencies and source

## Direct dependencies

All versions are pinned in `app/build.gradle.kts`.
No new API key, account, repository secret, NDK installation or manually supplied
native binary is required for an ordinary Gradle build.

| Dependency | Version | Purpose |
|---|---|---|
| AndroidX Media3 RTSP | 1.11.1 | RTSP source handling |
| AndroidX Media3 SmoothStreaming | 1.11.1 | SmoothStreaming manifests/segments |
| `dev.jdtech.mpv:libmpv` | 1.0.0 | Internal compatibility engine and native libass captions |
| `com.hierynomus:smbj` | 0.15.0 | Encrypted/signed SMB3 file access |
| `com.rapid7.client:dcerpc` | 0.12.13 | Authenticated SMB share enumeration |
| `commons-io:commons-io` / `org.apache.commons:commons-lang3` | 2.22.0 / 3.22.0 | RPC I/O and value helpers |
| `com.github.mwiede:jsch` | 2.28.8 | SFTP with a pinned host key |
| `commons-net:commons-net` | 3.13.0 | FTP and certificate-verified FTPS |

Existing Media3 core/UI/HLS/DASH stay at **1.11.1** and existing Nextlib stays at
**1.11.1-0.17.0**. Nextlib bundles AV1/dav1d. Media3 includes an AVI extractor; the compatibility
engine provides another route for variants the normal pipeline cannot handle.

SMBJ's runtime dependencies include SLF4J API 2.0.9, Bouncy Castle 1.79, mbassador
1.3.0 and asn-one 0.6.0. Commons IO is pinned to 2.22.0. SMBJ-RPC uses the existing SMBJ 0.15.0 transport; its older transitive dependencies are not imported. Gradle resolves these;
no logging backend that records private requests was added.

### Android SMB RPC adaptation and rebuild

`PrepareSmbRpc` in `app/build.gradle.kts` downloads the unmodified Maven Central
`com.rapid7.client:dcerpc:0.12.13` JAR, verifies SHA256
`ea3f970d4b6f5bedbca843551b9c4fe250de34942aa02e02a7f957a402f5cbb8`,
then rewrites constant-pool references from desktop-only
`java.rmi.UnmarshalException` and `java.rmi.MarshalException` to Ember's
`RpcParseException` (`IOException`).
Both constructor signatures, protocol instructions and upstream resource files
remain intact. This is an Ember modification, not an upstream Android release.
It does not suppress missing-class warnings or bundle a desktop RMI runtime.
The source for the exact dependency is available from
[SMBJ-RPC tag dcerpc-0.12.13](https://github.com/rapid7/smbj-rpc/tree/dcerpc-0.12.13)
and the matching Maven `-sources.jar`; its complete license is bundled in
`app/src/main/assets/licenses/SMBJ-RPC-BSD.txt` and shown in Legal.

An ordinary `gradle :app:assembleRelease` rebuilds the adapted JAR automatically
under `app/build/android-smb-rpc/`. No generated JAR is committed. Removing that
directory and rebuilding reproduces it from the pinned artifact. A unit test
exercises a malformed RPC response through the adapted exception path.

## Two FFmpeg ABIs must remain separate

Nextlib uses `LIBAVCODEC_63` / `LIBAVUTIL_61`; libmpv 1.0.0 uses
`LIBAVCODEC_62` / `LIBAVUTIL_60`. Selecting one duplicate `.so` with `pickFirst`
would break one engine. `IsolateMpvNative` instead produces a deterministic AAR in
`app/build/isolated-mpv/`, changing only same-length native library-name strings,
JNI loader constants, and the JNI verbose log format. Symbol ABI versions, offsets,
JNI class/method names and executable instructions are preserved.

- Upstream AAR SHA256:
  `df146592480fc8418415a06b1f1a1d6318b0088e21f52254b0e9a82b61ca8fa2`
- Native library filenames/SONAME/DT_NEEDED strings get private `libem…` names.
- Java's `mpv` / `player` loader constants become `emv` / `emplay`.
- The JNI verbose log literal no longer emits arbitrary mpv message text, which can
  contain signed URLs. No raw log observer is registered. Diagnostics show format,
  engine, decoder and audio-output fields, never transport URLs or account fields.
- The task refuses an unexpected artifact hash, loader layout or log format. A future
  dependency upgrade requires reviewing those transformations again.
- `scripts/verify-player-native.py` checks loader names, dependencies, ABI versions,
  both ARM architectures, and **64-bit** 16 KiB ELF load-segment alignment. 32-bit
  libraries are packaged but are not claimed to have 16 KiB alignment.
- `app/proguard-rules.pro` preserves JNI observers/classes, reflective SFTP/crypto
  implementations and SMB event handlers for release shrinking. Optional desktop
  Kerberos/agent/logging/EL references are scoped out; SMB explicitly uses NTLM
  password authentication. An APK/device load remains part of later acceptance.

Example checks (Python 3 / `readelf` for the native audit):

```sh
gradle :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:mergeDebugNativeLibs
python3 scripts/verify-player-native.py app/build/isolated-mpv/libmpv-1.0.0.aar
```

These commands do not assemble an APK. Inspect lint's XML error severities too:
this repository's existing configuration does not abort automatically on lint errors.

## Source and license inventory

The Android wrapper is MIT; that does not describe the whole native stack. The
pinned build enables FFmpeg GPL/version3 and carries GPL obligations. License texts
are bundled in `app/src/main/assets/licenses/`, and Settings → Legal links this
inventory. When distributing binaries, provide the corresponding source, upstream
sources/build scripts and Ember's local transformations together; an MIT label on
the wrapper does not remove that requirement.

- [libmpv Android v1.0.0 source](https://github.com/jarnedemeulemeester/libmpv-android/tree/fcf6745703dc1265bca88f12fee8fc355ddf251e),
  including `buildscripts/include/depinfo.sh` and the native build scripts/patches.
  The source ref is the release tag, not the newer repository main branch.
- Release build inventory: mpv 0.41.0 (GPL), FFmpeg 8.1 (GPL/version3 enabled),
  libass 0.17.4 (ISC), dav1d 1.5.3 (BSD), libplacebo 7.360.1 (LGPL), mbedTLS 3.6.6
  (Apache), Lua 5.2.4 (MIT), FreeType 2.14.3 (FTL/GPL), fontconfig 2.17.1 (MIT),
  FriBidi 1.0.16 (LGPL), HarfBuzz 14.1.0 (MIT style), libunibreak 6.1 (zlib),
  libxml2 2.15.2 (MIT), and the Android NDK C++ runtime (Apache/LLVM exception).
  Exact patches/configuration are in that release's build scripts.
- [Nextlib source matching 1.11.1-0.17.0](https://github.com/anilbeesetti/nextlib/tree/a4c1e634278b7c918c3801e08c9d0e3eaee4f7ae), GPLv3.
- [Media3](https://github.com/androidx/media), Apache 2.0;
  [SMBJ](https://github.com/hierynomus/smbj), Apache 2.0;
  [JSch](https://github.com/mwiede/jsch), BSD style;
  [Commons Net](https://github.com/apache/commons-net), Apache 2.0.

## Limits that still require device acceptance

IntroDB provides optional read-only skip markers. VVC/H.266 routing uses the
bundled compatibility decoder. Support and output still require device testing.

Compilation, JVM fixtures and ELF inspection do not prove actual hardware decoding,
HDR/color accuracy, HDMI passthrough, surface recreation, D-pad behavior or projector
performance. Test the matrix on both capable and constrained TV devices, including
server multipart transitions, receiver changes, USB providers and real network shares.
There is no “all formats” guarantee: DRM/provider licenses, unsupported Dolby Vision
layers, HDMI limits and CPU/GPU capacity remain real boundaries.

Standard playback integrates Media3's matching 1.11.1 Java/JNI extension and
Fraunhofer r3.0.2 for ARM64/ARM32 only. No new Maven artifact is required. The
complete unmodified Fraunhofer source archive, full license, native checksums,
rebuild steps and wrapper changes are recorded in
[the MPEG-H native directory](app/native/mpegh/README.md). The Standard player
can decode supported unencrypted MPEG-H to stereo PCM; Compatibility/libmpv does
not use this extension. Software preference changes renderer priority instead of
forcing Nextlib's FFmpeg-only gate, so it retains MPEG-H and device-only AC-4
fallbacks. TV decoders only continues to exclude software extensions. AC-4 still
requires device support; no software AC-4 decoder is bundled. Standard also
supports unencrypted IAMF tracks in MP4 using Opus, FLAC or PCM. AAC-based IAMF,
encrypted IAMF and Compatibility IAMF decoding are excluded. Source, licenses
and rebuild instructions are in [the IAMF native directory](app/native/iamf/README.md).

Software HDR
preservation/tone mapping is not advertised as equivalent to the TV's HDR pipeline.
Ember now replaces the pinned libmpv filter library with a selective FFmpeg 8.1
build containing BWDIF/YADIF, scale/format, audio compression and the existing buffer filters. Both ARM
ABIs retain private SONAMEs, symbol compatibility and 16 KiB alignment. Source,
checksums and rebuild/verification steps are in
[the native filter directory](app/native/ffmpeg-filters/README.md). Compatibility
playback exposes Off (default), Automatic and Always deinterlacing. Automatic uses
frame flags; the decoder-copy path makes CPU filter inputs readable. Progressive
frames bypass the automatic filter, although enabled copying can still add cost.
Host tests cover field order/frame counts and combing; real HDR/interlaced and
device performance acceptance is still required.
Playback uses foreground video and picture-in-picture.

Library indexing uses [SQLCipher for Android 4.20.0](https://github.com/sqlcipher/sqlcipher-android)
with AndroidX SQLite 2.7.0. Its BSD license is included in About. The database key stays
in the Keystore-backed secret store; the index can be rebuilt from saved folder grants.
Clip export uses Media3 Transformer 1.11.1, under the same Apache 2.0 license as Media3.
Release packaging continues to include ARM64 and ARM32 only.

Direct FAT32 USB browsing uses [libaums core 0.10.0](https://github.com/magnusja/libaums),
under Apache 2.0. No raw NTFS/exFAT driver is bundled.
Optional Google Cast uses `com.google.android.gms:play-services-cast-framework:23.0.0`
and its matching Cast dependency. It is initialized only from the receiver screen;
saved-session resume, automatic reconnection and the SDK media notification are disabled.
The app owns the selected source, temporary relay, media notification and cleanup.
See [Google Cast terms](https://developers.google.com/cast/docs/terms) and
[Android SDK terms](https://developer.android.com/studio/terms.html).
DLNA discovery and controls use the app’s own local UDP/HTTP/XML implementation.

## GPU picture processing

Media3's existing `media3-effect` dependency (1.11.1, already included through
`media3-transformer`) supplies the Standard engine's Lanczos resampler and colour
adjustments. Ember's small gamma/detail pass is in `StandardPictureEffects.kt`.
These effects use the existing GPU renderer; they do not add another decoder.

The Compatibility engine bundles the unmodified MIT-licensed
[Anime4K CNN x2 small shader](https://github.com/bloc97/Anime4K/blob/28535a4aff3deb55707a36c2f2d05009e0cb7fb3/glsl/Upscale/Anime4K_Upscale_CNN_x2_S.glsl).
It is stored as `app/src/main/assets/shaders/fast-upscale.glsl`, SHA256
`4c53ec2e287908f7ee7bcb266b0170421626d663576468b7d7dafc62962649a4`.
The complete upstream copyright and MIT license remain in its header and in
`app/src/main/assets/licenses/Anime4K-MIT.txt`, also available in the app's Legal section.
The source was not modified. An ordinary app build packages the shader without
an external toolchain, account or runtime download. Its upstream condition only
runs the enlargement passes above 1.2× in both output dimensions.

Picture enhancement is enabled by default and can be disabled in Settings >
Picture enhancement or Player options > Video. The master switch bypasses the
extra processing; colour controls start neutral. HDR/Dolby Vision, protected and
tunneled Standard playback retain their supported renderer path. A Standard GPU
processing failure retries the current video without effects, keeping the saved
preference and playback position.


Advanced decoder profiles and HDR-to-SDR output use the existing mpv 0.41.0
`gpu` renderer and decoder options. No additional native library, external shader
pack, model download or service is required. Tone curves and gamut choices are
limited to supported modes. Compatibility playback also offers the bundled
`gpu-next` renderer explicitly, with one automatic fallback to `gpu` after failure. HDR conversion uses the renderer's colour pipeline, with automatic
output settings restored when conversion is disabled, bypassed or not applicable.
SDR colour/detail effects remain separate from HDR output conversion.


## Output metadata and night listening

Experimental SDR-to-HDR expansion uses the already bundled `gpu-next` renderer
and libplacebo inverse tone mapping. It adds no AI model, runtime service or
shader download and is disabled initially. It requires a reported HDR10 display
and confirmed Android BT.2020/PQ producer metadata on a separately signaled
surface. Unsupported displays and TextureView output bypass conversion. Output
metadata does not establish a television's active HDR mode, panel luminance,
10-bit framebuffer or reconstruction of source detail. Dolby Vision FEL and RPU
processing are not advertised as verified by Ember.

Ember's small `libember_surface.so` bridge uses public Android native-window APIs.
It saves and restores each surface's original data space. Its GPL source, pinned
NDK/API settings, checksums and rebuild instructions are in
[the surface metadata directory](app/native/hdr-surface/README.md). Only ARM64 and
ARM32 binaries are packaged, both with 16 KiB ELF load-segment alignment. Gradle
checks source/binary hashes and architectures before packaging; an ordinary
build uses the included binaries and requires no new NDK installation.

Night listening uses Ember's linked-channel PCM compressor on Standard playback
and the existing FFmpeg `acompressor` filter on Compatibility playback. The
selective filter build now enables that filter; its complete source, provenance
and rebuild instructions remain in the native filter directory. No Maven
version changes are required. Encoded passthrough is not compressed.


## Adaptive application interface

The existing Compose BOM 2026.09.00 pins AndroidX Material 3 to 1.4.0. Touch
screens use its buttons, switches, text fields, tabs, state layers and color roles.
The existing TV Material 1.1.0 remains the remote-control component family.
Both use Ember Player's shared palette. These are local UI libraries under the
existing AndroidX Apache 2.0 license; they add no runtime service or account.
The application mark and banner are original Ember vector artwork.
