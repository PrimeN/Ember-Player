# Build Ember Player

## Requirements

| Component | Version |
| --- | --- |
| Java | JDK 17 |
| Gradle | 9.8.2 |
| Android compile platform | API 37 |
| Minimum Android version | Android 10 / API 29 |
| Packaged processors | `arm64-v8a` and `armeabi-v7a` |

The project uses Android Gradle Plugin 9.4.0, Kotlin 2.4.21 and Compose BOM
2026.09.00. Install Gradle separately; the repository does not contain a Gradle
wrapper. Configure the Android SDK with `ANDROID_HOME` or an untracked
`local.properties` file containing `sdk.dir`.

## Build and install

```sh
git clone --branch beta https://github.com/PrimeN/Ember-Player.git
cd Ember-Player
gradle testDebugUnitTest assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`. Its application ID
is `app.ember.player.debug`, allowing it to coexist with a release installation.
The release application ID is `app.ember.player`.

For the optimized build and static checks:

```sh
gradle testDebugUnitTest assembleRelease lintRelease
python3 scripts/verify-player-native.py app/build/isolated-mpv/libmpv-1.0.0.aar
```

The release build is unsigned. Sign distribution APKs with your own keystore
outside the repository. Keep signing files, passwords and generated build output
out of source control. Subsequent updates to an installed release must use the
same signing identity.

## GitHub Actions

The [Android workflow](../.github/workflows/android.yml) runs functional tests,
optimized release compilation, lint and native dependency/alignment checks on
pushes and pull requests. Repository maintainers can also dispatch it manually
to create an installable debug APK artifact, retained for seven days. Ordinary
pushes do not publish downloadable releases.

Development takes place on `beta`. Published versions, when available, appear
under [Releases](https://github.com/PrimeN/Ember-Player/releases).

## Native components

Ordinary Gradle builds use the checked-in ARM native components and pinned Maven
dependencies; no NDK or separately obtained decoder SDK is required. Rebuilding
the native components requires their documented toolchains, including NDK
29.0.14206865 where specified.

Follow [Playback dependencies and source](../player-dependencies.md) for the exact
source revisions, checksums, licenses, patches and rebuild commands. Retain all
applicable notices when distributing a modified build.
