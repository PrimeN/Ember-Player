# IAMF software audio

The Media3 engine includes Media3's IAMF renderer and IAMF Tools. Supported
IAMF tracks in MP4 use Opus, FLAC or PCM, with output layouts selected for the
current audio configuration. AAC-based IAMF and encrypted IAMF are unsupported.
For eligible local MP4 files, Compatibility uses the same decoder to prepare
seekable 16-bit PCM before mpv starts. This path supports mono or stereo output
from the selected IAMF mix. Raw IAMF files and Dolby/DTS object rendering are
outside this path. Existing AAC playback outside IAMF is unchanged.

## Sources and rebuilding

- [IAMF Tools](https://github.com/AOMediaCodec/iamf-tools/tree/e552e7d6d8b55530d201fd2764ca7e12d2c66cd6),
  BSD 3-Clause Clear and the accompanying AOMedia patent license. Its pinned
  MODULE.bazel and lockfile identify all dependencies, including Opus, FLAC,
  Abseil, Eigen, Open Binaural Renderer and PFFFT. Dependency source trees are
  fetched by Bazel during rebuilding; Eigen's unmodified source is available
  through the [pinned registry entry](https://github.com/bazelbuild/bazel-central-registry/blob/main/modules/eigen/3.4.0.bcr.3/source.json)
  and its upstream repository.
- [Media3 1.11.1](https://github.com/androidx/media/tree/8c6678b657ede1e7883fc164ef73ed483c7796c3/libraries/decoder_iamf),
  Apache 2.0. The wrapper sources and original copyright notices are retained in
  `jni/` and `app/src/main/java/androidx/media3/decoder/iamf/`.
- Ember modifications: exclude AAC decoder build dependencies with upstream's
  `IAMF_TOOLS_DISABLE_AAC_DECODER`; validate JNI handles and buffer bounds;
  fix the unused creation method's Java/native signature mismatch; release failed
  initializations; reject encrypted tracks; defer native loading until IAMF is
  requested. Native handles are serialized and repeated closes are safe.
- Complete redistribution notices are bundled as `licenses/IAMF-NOTICES.txt`
  and available in Settings > Legal. The NDK runtime notice is also retained.

Use Bazel 8.5.1, NDK 29.0.14206865, CMake 3.22.1, Python and curl on Linux.
The script checks the source archive hash, downloads pinned build dependencies,
and builds only arm64-v8a and armeabi-v7a. No runtime downloads are required.

```sh
scripts/build-iamf-decoder.sh /path/to/ndk/29.0.14206865 /path/to/cmake/3.22.1 /tmp/iamf-build
python3 scripts/verify-iamf-native.py
```

The prebuilt JNI libraries statically contain these dependencies, hide helper
symbols and use 16 KiB ELF alignment. Gradle verifies their hashes and CPU types.

## Decoder checks

`scripts/test-iamf-decoder.py SOURCE_DIRECTORY HOST_STATIC_ARCHIVE BUILD_DIRECTORY`
compiles the same JNI wrapper for Linux and decodes PCM, FLAC and Opus with JDK
17 and g++. Build the source tree's `iamf_decoder_static` target without an Android
config, using `--compilation_mode=opt --copt=-fPIC --copt=-DIAMF_TOOLS_DISABLE_AAC_DECODER`.
Keep the same two build-dependency exclusions used by the rebuild script. Checks
exercise native registration, invalid buffers, stale handles and repeated close.
The legacy PCM fixture's zero samples-per-frame field is replaced with its actual
256-sample value in the temporary build directory; audio data is unchanged.
