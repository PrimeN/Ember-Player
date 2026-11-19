# Build Ember Player

## Requirements

| Component | Version |
| --- | --- |
| Java | JDK 21 |
| Gradle | 9.8.2 (included wrapper) |
| Android compile platform | API 37 |
| Android target | Android 16 / API 36 |
| Minimum Android version | Android 10 / API 29 |
| Packaged processors | `arm64-v8a` and `armeabi-v7a` |

The project uses Android Gradle Plugin 9.4.0, Kotlin 2.4.21 and Compose BOM
2026.09.00. The checked-in wrapper downloads Gradle 9.8.2 and verifies its
distribution SHA-256 before use. On Windows, use `gradlew.bat` instead of
`./gradlew`. Configure the Android SDK with `ANDROID_HOME` or an untracked
`local.properties` file containing `sdk.dir`.

Java 21 is needed for the Android 16 Robolectric tests. The app's Java/Kotlin
bytecode target remains 17.

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
python3 scripts/verify-player-native.py app/build/isolated-mpv/libmpv-1.1.0.aar
```

Release builds use a PKCS12 signing keystore when `EMBER_KEYSTORE_FILE`,
`EMBER_KEYSTORE_PASSWORD`, `EMBER_KEY_ALIAS` and `EMBER_KEY_PASSWORD` are set.
Without a keystore, Gradle can still validate an unsigned release build.
Keep signing files, passwords and generated build output out of source control.
Subsequent updates to an installed release must use the same signing identity.

`release-signing-certificate.sha256` records only the public certificate fingerprint.
Download preparation verifies signed builds against that identity before distribution.

## Distribution packages

```sh
./gradlew bundleRelease
./gradlew assembleRelease -PsplitApks=true
python3 scripts/prepare-release.py --require-signed
```

Build the bundle before enabling APK splits: Android resource shrinking does not
support building both in one invocation.

With the signing variables above, this prepares four verified packages in `dist/`:

- `ember-player-1.0.0.apk`: universal ARM64 and ARM32 APK.
- `ember-player-1.0.0-arm64-v8a.apk`: ARM64 only.
- `ember-player-1.0.0-armeabi-v7a.apk`: ARM32 only.
- `ember-player-1.0.0.aab`: Android App Bundle for Google Play.

All share version code 1000 and the release signing identity. ABI APKs are for
GitHub downloads; Google Play receives the bundle and delivers the appropriate
architecture itself. Without `-PsplitApks=true`, APK builds remain universal.
An AAB cannot be installed directly. See [Google Play preparation](google-play.md)
for the Console declarations and privacy-policy link.

## GitHub Actions

The [Android workflow](../.github/workflows/android.yml) runs functional tests,
optimized APK and app-bundle compilation, lint and native dependency/alignment checks on
pushes and pull requests. Pushes and manual runs provide an unsigned universal and per-architecture APKs plus an app-bundle
artifact, retained for seven days. Manual runs also provide an installable debug
APK. Validation jobs have no signing-secret references. All workflow actions use
full commit SHAs.

Configure the GitHub Environment **release-signing** before publishing:

1. Require reviewer approval, and disable bypassing protection rules where available.
2. Allow deployments only from `main` and version tags matching `v*`. The workflow
   also verifies that the tagged commit belongs to `main` and passed its checks.
3. Use secrets `EMBER_KEYSTORE_BASE64` (the original PKCS12 file, Base64
   encoded), `EMBER_KEYSTORE_PASSWORD`, `EMBER_KEY_ALIAS` and `EMBER_KEY_PASSWORD`.
   Existing repository secrets with these names are supported. To restrict access
   through Environment protection rules, configure the same names as Environment secrets.
4. After moving the secrets into the Environment, remove their repository copies.

Only the explicit release-signing job references these secrets; ordinary validation
builds do not use them. The key is decoded only into temporary runner storage and
removed after the build. It is never uploaded with the release artifacts. The signing
job stops if any required secret is missing.

Gradle caching is disabled in the signing job; it does not restore or save the
Gradle caches used by unsigned validation builds.

Ordinary pushes do not publish releases. To publish a requested version:

1. Set `versionName` and a larger `versionCode`, and add a matching section to
   `CHANGELOG.md`.
2. Merge to `main` and wait for its **Android player** workflow to pass.
3. Push a tag matching the version, such as `v1.0.0`, or run **Publish requested
   player release** manually with that tag name.
4. Review and approve the **release-signing** Environment deployment.

After approval, the signing job builds the exact validated main commit and
verifies its signing identity. Publication consumes that job's signed artifact
from the same workflow run, verifies its source commit, version and checksum,
and publishes the universal and per-architecture APKs plus the app bundle with
changelog notes. It does not replace an existing version.

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
