# Startup profiles and folder browsing

## Baseline and startup profiles

The optional `benchmark` module records app startup, populated library shelves,
scrolling, remote focus and Settings navigation. It seeds 60 synthetic media
entries through a signature-protected fixture receiver in the profiling variant.
The fixture provider and media asset are absent from production builds.

The Android startup profiles workflow collects on a phone emulator and an Android
TV emulator. Its dependency downloads use Google's Maven Central mirror through
`-PuseMavenCentralMirror=true`. CI validation and release builds use the same
mirror with Maven Central as a fallback. Download both `Ember-Player-profile-*` artifacts into one directory,
then merge the generated rules:

```sh
python3 scripts/merge-startup-profiles.py /path/to/captures
```

The generated `app/src/main/baseline-prof.txt` and `startup-prof.txt` are compiler
inputs. Commit these rules after collecting a representative journey. Android
Gradle Plugin compiles the baseline rules for ProfileInstaller and uses startup
rules for DEX layout. Recollect after substantial UI or dependency changes.
Emulator timing is not a measure of startup speed on a physical TV box.

To collect locally on an accelerated x86 emulator:

```sh
./gradlew -PincludePerformanceTests=true :app:assembleBenchmark :benchmark:assembleBenchmark
python3 scripts/collect-startup-profiles.py --television
```

For a dedicated ARM64 device with USB debugging enabled:

```sh
./gradlew -PincludePerformanceTests=true -PprofileAbi=arm64-v8a :benchmark:connectedBenchmarkAndroidTest
python3 scripts/merge-startup-profiles.py benchmark/build/outputs
```

Use API 33 or newer for collection without root. Older devices need root access.
The profiling app uses `app.ember.player.benchmark`, its own data and debug
signing. It is configured for UI profiling; use a normal ARM build for playback
validation. Production release tasks reject the profiling flag. Ordinary builds
exclude the benchmark module and retain both supported ARM architectures.

Playback, decoding, HDR and physical-device responsiveness still need manual
validation. No emulator playback smoke test is part of this change.

## Folder visit cache

The saved-folder browser keeps one visit's metadata and sorted results in RAM.
Next and previous pages reuse it, and search or sort changes use the same source
snapshot. File operations, refresh, directory changes and privacy state changes
end the visit. Ending a visit also prevents an outstanding read from restoring
its cached data.

The cache allows up to 20,000 entries within an estimated 16 MiB metadata budget.
Larger listings use the existing bounded-memory scan. Neither cache stores file
contents or writes filenames to disk. Folder thumbnails keep their independent
bounded cache.

[Build instructions](building.md) · [Translations](localization.md)
