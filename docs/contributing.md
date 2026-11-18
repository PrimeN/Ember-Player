# Support and contributing

## Before opening an issue

Search [existing issues](https://github.com/PrimeN/Ember-Player/issues) first. Note
the app version, Android version, device model and whether the problem occurs in
Media3, mpv or automatic playback.

For playback problems, compare the two internal engines if the media permits it.
For stuttering, try turning off picture enhancement or lowering the performance
level. HDR, passthrough and casting also depend on the display, receiver and
device decoders; a successful build cannot establish those capabilities.

## Report a bug

Open a [bug report](https://github.com/PrimeN/Ember-Player/issues/new?title=%5BBug%5D%20).
Describe what you did, what you expected and what happened. Include:

- App version and installation source.
- Device model, Android version and input method: touch, remote or controller.
- A short sequence of steps that reproduces the problem.
- Relevant engine, renderer, output and performance settings.
- For a media problem, container, video/audio codecs and subtitle format, if known.
- A screenshot, short recording or shareable sample when useful and permitted.

Remove credentials, personal filenames, private server addresses and signed URLs
from screenshots and logs before posting them. Please do not upload commercial
media or files you cannot redistribute.

## Suggest a feature

Open a [feature request](https://github.com/PrimeN/Ember-Player/issues/new?title=%5BFeature%5D%20).
Explain the task you want to accomplish, where the current interface gets in the
way and which devices would benefit. A concrete example is more useful than a
long list of unrelated requests.

## Contribute a change

Open pull requests against **beta**. Link the relevant issue and explain the
user-visible behavior and reason for the change. Keep unrelated changes separate.
For a layout change, include phone/tablet/TV captures where applicable; account
for remote focus, enlarged text, day/night colors and constrained devices.

Follow the [build guide](building.md) and run checks appropriate to the change.
Preserve useful functional regression tests. For playback or storage changes,
include a regression case for the actual failure rather than a test that repeats
the implementation.

Keep credentials, signing material, generated APKs, local paths and temporary
working files out of commits. Retain dependency licenses and rebuild information.
New optional online services should state what they connect to and how the user
enables them.

Ember Player is distributed under [GPL v3](../LICENSE). See the
[dependency inventory](../player-dependencies.md) for component-specific terms.
