# Build Ember Player

## Requirements

| Component | Version |
| --- | --- |
| Java | JDK 17 |
| Gradle | 9.8.2 (included wrapper) |
| Android compile platform | API 37 |
| Minimum Android version | Android 10 / API 29 |
| Packaged processors | `arm64-v8a` and `armeabi-v7a` |

The project uses Android Gradle Plugin 9.4.0, Kotlin 2.4.21 and Compose BOM
2026.09.00. The checked-in wrapper downloads Gradle 9.8.2 and verifies its
distribution SHA-256 before use. On Windows, use `gradlew.bat` instead of
`./gradlew`. Configure the Android SDK with `ANDROID_HOME` or an untracked
`local.properties` file containing `sdk.dir`.

## Build and install

```sh
git clone --branch beta https://github.com/PrimeN/Ember-Player.git
cd Ember-Player
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`. Its application ID
is `app.ember.player.debug`, allowing it to coexist with a release installation.
The release application ID is `app.ember.player`.

For the optimized build and static checks:

```sh
./gradlew testDebugUnitTest assembleRelease lintRelease
python3 scripts/verify-player-native.py app/build/isolated-mpv/libmpv-1.0.0.aar
```

Release builds use a PKCS12 signing keystore when `EMBER_KEYSTORE_FILE`,
`EMBER_KEYSTORE_PASSWORD`, `EMBER_KEY_ALIAS` and `EMBER_KEY_PASSWORD` are set.
Without a keystore, Gradle can still validate an unsigned release build.
Keep signing files, passwords and generated build output out of source control.
Subsequent updates to an installed release must use the same signing identity.

`release-signing-certificate.sha256` records only the public certificate fingerprint.
Download preparation verifies signed builds against that identity before distribution.

## GitHub Actions

The [Android workflow](../.github/workflows/android.yml) runs functional tests,
optimized release compilation, lint and native dependency/alignment checks on
pushes and pull requests. Pushes and manual runs provide a release-build artifact,
retained for seven days. When signing secrets are configured, it contains an
installable signed APK; otherwise it is clearly marked as unsigned. Manual runs
also provide a separate debug APK. Pull requests validate without restoring the
release signing key.

Repository Actions secrets for signed builds are `EMBER_KEYSTORE_BASE64` (the
Base64-encoded PKCS12 file), `EMBER_KEYSTORE_PASSWORD`, `EMBER_KEY_ALIAS` and
`EMBER_KEY_PASSWORD`. The key is decoded only into temporary runner storage and
removed after the build. It is never uploaded with the APK artifact.

Ordinary pushes do not publish releases. To publish a requested version:

1. Set `versionName` and a larger `versionCode`, and add a matching section to
   `CHANGELOG.md`.
2. Merge to `main` and wait for its **Android player** workflow to pass.
3. Push a tag matching the version, such as `v0.7.5`, or run **Publish requested
   player release** manually with that tag name.

Publication reuses the successful main-build artifact, verifies its source commit,
version, signing identity and checksum, and publishes the APK with changelog notes.
It does not rebuild or replace an existing version. If an artifact has expired,
rerun the corresponding main build before retrying publication.

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
