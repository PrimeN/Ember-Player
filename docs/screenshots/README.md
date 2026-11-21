# Screenshots

The library captures show the same sample collection on phone, tablet and TV.
The playback capture shows the Glass controls over an illustrative backdrop.

| View | Preview |
| --- | --- |
| [Playback](player.png) | Back above the picture, with the timeline, transport and playback actions below |
| [Phone](mobile.png) | Continue watching, a two-button header and the floating navigation dock |
| [Tablet](tablet.png) | Wider shelves, library shortcuts and a centered navigation dock |
| [TV](tv.png) | Browse, Search and More options, with visible remote focus on a media card |
| [Settings](settings.png) | Search and eight settings categories |

Images are captured from app views. Device size, media availability, palette and
system text settings can change the number of cards visible.

The sample thumbnails use cropped frames from *Big Buck Bunny*, © 2008,
Blender Foundation / www.bigbuckbunny.org, under Creative Commons Attribution 3.0.
[Film credits and license](https://peach.blender.org/about/)

To capture the first-launch library, playback and Settings with JDK 21 and the
configured Android SDK:

```sh
EMBER_SCREENSHOTS_DIR="$PWD/app/build/screenshots" ./gradlew testDebugUnitTest --tests 'app.ember.player.Release*ScreenshotsTest'
```

These opt-in captures render app views through Robolectric native graphics. They
exercise the layout and controls without an attached Android device.

[Back to Ember Player](../../README.md)
