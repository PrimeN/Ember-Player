# Android surface colour metadata

Ember's small JNI bridge uses Android's public native-window API to set and read
back a SurfaceView buffer data space. Experimental SDR-to-HDR expansion requires
confirmed BT.2020/PQ metadata, a reported HDR10 display and the advanced renderer.
The original data space is restored when conversion ends or the surface detaches.
TextureView paths without a separately signaled HDR surface stay bypassed.
An accepted data space does not prove a television's currently active HDR mode.

The C source is part of Ember and follows the repository GPL v3 license. It links
only platform libraries, adds no decoder and performs no image or network work.
The ordinary app build uses the checked ARM binaries and needs no new NDK setup.
Gradle verifies their hashes, source hash and CPU architectures before packaging.

Rebuild on Linux with the pinned Android NDK 29.0.14206865:

```sh
scripts/build-hdr-surface.sh /path/to/android-ndk-29
```

The API 29 builds contain only arm64-v8a and armeabi-v7a and use 16 KiB ELF page
alignment. They do not install SDK packages or accept license prompts. Existing
Android NDK notices remain in the app's Legal section.
