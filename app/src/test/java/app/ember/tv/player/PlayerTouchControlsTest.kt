package app.ember.tv.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1000dp-h600dp-land-mdpi")
class PlayerTouchControlsTest {
    private class Host : TouchPlaybackHost {
        override var position = 60_000L
        override var duration = 120_000L
        override var seekable = true
        override var playing = true
        override var speed = 1.25f
        override var controlsVisible = false
        override var menuVisible = false
        var zoom = 1f
        val seeks = mutableListOf<Long>()
        override fun seek(position: Long) { seeks += position; this.position = position }
        override fun togglePlayback() { playing = !playing }
        override fun toggleControls() { controlsVisible = !controlsVisible }
        override fun hideControls() { controlsVisible = false }
        override fun magnify(scale: Float) { zoom = scale }
    }

    private fun fixture(config: TouchControlsConfig = TouchControlsConfig(), block: (Activity, Host, PlayerTouchControls) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.layout(0, 0, 1000, 600)
        val host = Host()
        val touch = PlayerTouchControls(activity, root, host) { config }
        try { block(activity, host, touch) } finally { touch.close(); controller.pause().stop().destroy() }
    }

    private fun event(touch: PlayerTouchControls, action: Int, x: Float, y: Float, start: Long, offset: Long = 0): Boolean {
        val event = MotionEvent.obtain(start, start + offset, action, x, y, 0)
        return try { touch.dispatch(event) } finally { event.recycle() }
    }

    @Test fun swipePreviewsUntilReleaseAndCancellationNeverSeeks() = fixture { _, host, touch ->
        val now = SystemClock.uptimeMillis()
        assertTrue(event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, now))
        event(touch, MotionEvent.ACTION_MOVE, 750f, 300f, now, 80)
        assertTrue(host.seeks.isEmpty())
        event(touch, MotionEvent.ACTION_UP, 750f, 300f, now, 100)
        assertEquals(listOf(90_000L), host.seeks)
        event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, now + 1000)
        event(touch, MotionEvent.ACTION_MOVE, 100f, 300f, now + 1000, 80)
        event(touch, MotionEvent.ACTION_CANCEL, 100f, 300f, now + 1000, 100)
        assertEquals(listOf(90_000L), host.seeks)
    }

    @Test fun longPressRestoresThePreviousSpeedWhenTheWindowLosesInput() = fixture { _, host, touch ->
        event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, SystemClock.uptimeMillis())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700))
        assertEquals(2f, host.speed, .001f)
        touch.cancel()
        assertEquals(1.25f, host.speed, .001f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertFalse(host.controlsVisible)
    }

    @Test fun menusAndVisibleControlButtonsKeepTheirTouches() = fixture { _, host, touch ->
        host.menuVisible = true
        assertFalse(event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, SystemClock.uptimeMillis()))
        host.menuVisible = false; host.controlsVisible = true
        assertFalse(event(touch, MotionEvent.ACTION_DOWN, 500f, 550f, SystemClock.uptimeMillis()))
        assertFalse(event(touch, MotionEvent.ACTION_DOWN, 500f, 20f, SystemClock.uptimeMillis()))
        assertTrue(event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, SystemClock.uptimeMillis()))
    }

    @Test fun disabledGesturesLeaveTheNormalPlayerInputUntouched() = fixture(TouchControlsConfig(enabled = false)) { _, _, touch ->
        assertFalse(event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, SystemClock.uptimeMillis()))
    }

    @Test fun playbackLockBlocksPlaybackKeysButBackUnlocksWithoutExiting() = fixture { _, host, touch ->
        touch.setLocked(true)
        assertTrue(touch.locked)
        assertFalse(host.controlsVisible)
        assertTrue(touch.dispatchKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)))
        assertFalse(touch.dispatchKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
        assertTrue(touch.dispatchKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(touch.locked)
        assertTrue(touch.dispatchKey(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertFalse(touch.locked)
        assertTrue(host.controlsVisible)
    }

    @Test fun orientationUsesTheSelectedSensorMode() = fixture(TouchControlsConfig(orientation = "landscape")) { activity, _, _ ->
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
    }

    @Test @Config(qualifiers = "w960dp-h540dp-land-television-mdpi")
    fun televisionRemoteNavigationIsNotReplacedByTouchGestures() = fixture(TouchControlsConfig(orientation = "portrait")) { activity, _, touch ->
        assertTrue(PlayerTouchControls.isTelevision(activity))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        assertFalse(event(touch, MotionEvent.ACTION_DOWN, 500f, 300f, SystemClock.uptimeMillis()))
    }

    @Test fun calculationsBoundUntrustedSettingsAndUnknownDurations() {
        assertEquals(0L, TouchPlaybackMath.seek(1000, Long.MIN_VALUE, 2000))
        assertEquals(2000L, TouchPlaybackMath.seek(1000, Long.MAX_VALUE, 2000))
        assertEquals(1000L, TouchPlaybackMath.seek(1000, 3000, -1))
        assertEquals(1000L, TouchPlaybackMath.swipeSeek(1000, Float.NaN, 10000))
        assertEquals(0, TouchPlaybackMath.level(2, -10f, 15))
        assertEquals(15, TouchPlaybackMath.level(2, 10f, 15))
        assertEquals(4f, TouchPlaybackMath.zoom(3f, 2f), 0f)
        assertEquals(1f, TouchPlaybackMath.zoom(1f, Float.NaN), 0f)
        assertEquals(5000L, TouchPlaybackMath.step(-500))
        assertEquals(60000L, TouchPlaybackMath.step(Int.MAX_VALUE))
    }

    @Test fun touchPreferencesRoundTripAndOlderSettingsKeepUsableDefaults() {
        val value = PlaybackConfig(touch = TouchControlsConfig(false, 30, false, true, false, true, "sensor"))
        assertEquals(value, Json.decodeFromString<PlaybackConfig>(Json.encodeToString(value)))
        assertEquals(TouchControlsConfig(), Json.decodeFromString<PlaybackConfig>("{}").touch)
    }
}
