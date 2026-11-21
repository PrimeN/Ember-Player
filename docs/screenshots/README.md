# Screenshots

The library captures show an earlier Ember Player first-launch layout before folders or media have been selected. The playback
capture shows real transport controls over an illustrative backdrop.

| View | Preview |
| --- | --- |
| [Playback](player.png) | Glass transport controls, timeline and playback actions |
| [Phone](mobile.png) | First-launch library with browsing tabs and Search/Settings in the top bar |
| [Tablet](tablet.png) | Top actions and browsing tabs |
| [TV](tv.png) | Remote navigation with a compact sidebar |
| [Settings](settings.png) | Search and eight settings categories |

Images are captured from app views. Device size, media availability, palette and
system text settings can change the number of cards visible.

To regenerate the gallery with JDK 21 and the configured Android SDK:

```sh
EMBER_SCREENSHOTS_DIR="$PWD/docs/screenshots" ./gradlew testDebugUnitTest --tests 'app.ember.player.Release*ScreenshotsTest'
```

These opt-in captures render app views through Robolectric native graphics. They
show the layout and controls; they do not validate hardware video output.

[Back to Ember Player](../../README.md)
