# Google Play preparation

The release workflow prepares a signed Android App Bundle alongside the GitHub
APKs. It does not upload to Google Play or complete Play Console declarations.
Publishing requires the account owner to complete the following in the Console.

## App bundle and signing

Upload `ember-player-1.0.0.aab` from the approved release build. The application ID
is `app.ember.player`, version name is 1.0.0 and version code is 1000. Confirm
that Play App Signing and its upload certificate match the intended existing
signing identity before enrolling. Do not replace the key used by installed APKs.
Use an internal testing track to verify installation and updates first.

## Foreground-service declarations

Declare the service types requested by the manifest, with the required
explanation and a demonstration of each user-visible workflow:

- **Media playback:** background audio and user-started Cast/DLNA playback. The
  media notification provides playback controls and a way to stop playback.
- **Data sync:** user-requested media downloads. Describe the download notification
  and its stop action. Folder scans use JobScheduler rather than this service.

Review the current Play Console eligibility and declaration requirements when
submitting. Manifest permissions alone do not complete these declarations.

## Network traffic, privacy and app content

Settings → About links to the privacy policy. Use this public URL in the Console:
https://github.com/PrimeN/Ember-Player/blob/main/docs/privacy.md.

Explain that cleartext traffic supports local HTTP media, DLNA and the playback
relay, as well as HTTP streams and WebDAV addresses explicitly chosen by the
user. Prefer encrypted connections where supported; cleartext is not restricted
to local addresses by the manifest.

Complete Data safety from the actual release behavior, including optional Cast
and subtitle-service integrations and any SDK disclosures. The player does not
require an account. Private-media PIN protection is created locally by the user;
provide review instructions if demonstrating this optional feature.

Add current screenshots, store descriptions, content rating, target audience,
app-access instructions and the developer contact required by the Console.
The repository screenshot gallery contains illustrative media; review its
suitability and the current store image-size requirements before uploading.

[Build and signing](building.md) · [Privacy policy](privacy.md)
