package app.ember.tv.player

import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.Format
import app.ember.tv.data.SleepTimerState
import app.ember.tv.data.SleepVolume
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class RemainingPlaybackRegressionTest {
    @Test fun clipSelectionHonorsTheChosenLanguageAndRejectsAmbiguousAudio() {
        val tracks=listOf(LosslessClip.Track(1,2,"audio/mp4a-latm","eng",2,48000),LosslessClip.Track(2,3,"audio/mp4a-latm","fra",2,48000))
        val french=Format.Builder().setLanguage("fr").setSampleMimeType("audio/mp4a-latm").build()
        assertEquals(2,LosslessClip.chooseAudio(tracks,french,false))
        assertNull(LosslessClip.chooseAudio(tracks,french,true))
        assertEquals(1,LosslessClip.chooseAudio(tracks,Format.Builder().setId("2").build(),false))
        try {LosslessClip.chooseAudio(tracks,null,false);fail("Must not silently choose the first audio")}catch(_:IllegalStateException){}
    }
    @Test fun keyboardDigitsAndDedicatedRemoteKeysUsePlaybackActions() {
        assertEquals(PlaybackKeys.Action.CAPTIONS,PlaybackKeys.action(KeyEvent.KEYCODE_CAPTIONS))
        assertEquals(PlaybackKeys.Action.AUDIO,PlaybackKeys.action(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK))
        assertEquals(PlaybackKeys.Action.STOP,PlaybackKeys.action(KeyEvent.KEYCODE_MEDIA_STOP))
        assertEquals(PlaybackKeys.Action.TOGGLE,PlaybackKeys.action(KeyEvent.KEYCODE_SPACE))
        assertEquals(PlaybackKeys.Action.FORWARD_FRAME,PlaybackKeys.action(KeyEvent.KEYCODE_PERIOD))
        assertEquals(PlaybackKeys.Action.BACK_FRAME,PlaybackKeys.action(KeyEvent.KEYCODE_COMMA))
        for(digit in 0..9){assertEquals(digit,PlaybackKeys.digit(KeyEvent.KEYCODE_NUMPAD_0+digit));assertEquals(digit*1000L,PlaybackKeys.position(10000,digit))}
        assertNull(PlaybackKeys.position(C.TIME_UNSET,3));assertNull(PlaybackKeys.position(1000,10));assertNull(PlaybackKeys.action(KeyEvent.KEYCODE_A))
    }
    @Test fun screenshotDetectsHdrAndCompositesOnlyTheCaptionOverlay() {
        assertTrue(PlayerExports.hdr(Format.Builder().setSampleMimeType("video/dolby-vision").build()))
        assertFalse(PlayerExports.hdr(Format.Builder().setSampleMimeType("video/avc").build()))
        val app=RuntimeEnvironment.getApplication()
        val surface=android.view.View(app).apply {layout(0,0,200,100)}
        val caption=android.view.View(app).apply {layout(0,0,200,100);setBackgroundColor(android.graphics.Color.RED)}
        val bitmap=android.graphics.Bitmap.createBitmap(100,50,android.graphics.Bitmap.Config.ARGB_8888)
        PlayerExports.composite(bitmap,surface,caption)
        assertEquals(android.graphics.Color.RED,bitmap.getPixel(50,25));bitmap.recycle()
    }
    @Test fun hdrWarningDoesNotBlockTheCaptureAttempt() {
        val activity=org.robolectric.Robolectric.buildActivity(PlayerActivity::class.java).get()
        val hdr=Format.Builder().setSampleMimeType("video/dolby-vision").build()
        activity.player=java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,arrayOf(androidx.media3.exoplayer.ExoPlayer::class.java)) {_,method,_->
            when(method.name){"getVideoFormat"->hdr;else->null}
        } as androidx.media3.exoplayer.ExoPlayer
        activity.view=androidx.media3.ui.PlayerView(activity)
        org.robolectric.shadows.ShadowToast.reset()
        try {
            activity.exports.screenshot()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            // The JVM has no readable video surface. Both the HDR warning and the
            // capture error must appear: returning after the warning would skip capture.
            assertEquals(2,org.robolectric.shadows.ShadowToast.shownToastCount())
            assertEquals(activity.getString(app.ember.tv.R.string.ui_this_video_surface_cannot_be_captured),org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        } finally {activity.exports.close();activity.handler.removeCallbacksAndMessages(null)}
    }
    @Test fun bookmarkLimitHasAnExplicitRecoveryThatKeepsOtherProfiles() {
        val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("bookmarks",0).edit().clear().commit()
        val shared=PlaybackBookmarks(app,"shared");val other=PlaybackBookmarks(app,"local:other")
        assertTrue(other.add("other",1000,"Keep"))
        repeat(511){assertTrue(shared.add("video$it",1000,"Mark"))}
        assertFalse(shared.add("overflow",1000,"Mark"))
        assertTrue(shared.clearScope());assertTrue(shared.add("overflow",1000,"Mark"))
        assertEquals("Keep",other.read("other").single().name)
    }
    @Test fun sleepFadePreservesUserVolumeAcrossCancellationAndHandoff() {
        val state=SleepTimerState(deadline=120000)
        assertEquals(1f,SleepVolume.gain(state,0),0f)
        assertEquals(.5f,SleepVolume.gain(state,90000),0f)
        assertEquals(0f,SleepVolume.gain(state,120000),0f)
        assertEquals(1f,SleepVolume.gain(SleepTimerState(endOfItem=true),0),0f)
        val volume=SleepVolume();val halfway=volume.apply(.8f,.5f)
        assertEquals(.4f,halfway,0.0001f);assertEquals(.8f,volume.original(halfway),0.0001f)
        assertEquals(.8f,volume.reset(halfway),0.0001f)
        assertEquals(.3f,volume.apply(.6f,.5f),0.0001f)
        assertEquals(.2f,volume.apply(.4f,.5f),0.0001f)
    }
}
