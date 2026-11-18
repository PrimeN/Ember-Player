package app.ember.tv.player

import android.app.Application
import android.content.res.Configuration
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import app.ember.player.PlayerUiStoreFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerPictureInPictureTest {
    private fun fixture(test:(PlayerActivity,FakeVideo)->Unit) {
        val app:Application=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(app).use {
            shadowOf(app.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,true)
            val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
            val fake=FakeVideo()
            activity.config=PlaybackConfig(pip=true,backgroundAudio=true,rememberTracks=false)
            activity.player=fake.player;activity.positions=PositionStore(activity)
            try {test(activity,fake)}finally {activity.handler.removeCallbacksAndMessages(null)}
        }
    }
    @Test fun gestureAutoEntryTracksPlaybackPrivacyAndSidecarPicking()=fixture {activity,fake->
        assertTrue(activity.pictureInPictureParams().isAutoEnterEnabled)
        fake.playing=false;activity.updatePictureInPicture()
        assertFalse(activity.pictureInPictureParams().isAutoEnterEnabled)
        fake.playing=true;activity.pickingSidecar=true
        assertFalse(activity.pictureInPictureParams().isAutoEnterEnabled)
        activity.pickingSidecar=false;activity.privatePlayback=true
        assertFalse(activity.pictureInPictureParams().isAutoEnterEnabled)
        activity.privatePlayback=false;activity.config=activity.config.copy(pip=false)
        assertFalse(activity.pictureInPictureParams().isAutoEnterEnabled)
    }
    @Test fun aspectRatioFollowsVideoChangesIncludingAnamorphicAndExtremeSizes()=fixture {activity,fake->
        assertEquals(16f/9,activity.pictureInPictureParams().aspectRatio!!.toFloat(),.001f)
        fake.size=VideoSize(1080,1920);activity.updatePictureInPicture()
        assertEquals(9f/16,activity.pictureInPictureParams().aspectRatio!!.toFloat(),.001f)
        assertEquals(2f,pictureInPictureRatio(VideoSize(720,576,0,1.6f)).toFloat(),.001f)
        assertEquals(2.38f,pictureInPictureRatio(VideoSize(10000,1)).toFloat(),.001f)
        assertEquals(.42f,pictureInPictureRatio(VideoSize(1,10000)).toFloat(),.001f)
    }
    @Test fun closingAPausedPipWindowReleasesPlaybackInsteadOfKeepingItInvisible()=fixture {activity,fake->
        fake.playing=false
        activity.onPictureInPictureModeChanged(true,Configuration())
        ReflectionHelpers.callInstanceMethod<Unit>(activity,"onStop")
        assertNull(activity.player);assertEquals(1,fake.released)
        assertFalse(activity.keptAlive)
    }
    @Test fun pipExitAfterTheActivityStopsAlsoEndsPlayback()=fixture {activity,fake->
        fake.playing=false
        activity.onPictureInPictureModeChanged(true,Configuration())
        activity.onPictureInPictureModeChanged(false,Configuration())
        assertNull(activity.player);assertEquals(1,fake.released)
    }
    private class FakeVideo {
        var playing=true;var size=VideoSize(1920,1080);var released=0
        val player=Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)){_,method,_->when(method.name) {
            "isPlaying","getPlayWhenReady"->playing
            "getVideoSize"->size
            "getDuration"->600_000L
            "getCurrentPosition"->120_000L
            "release"->{released++;null}
            "hashCode"->System.identityHashCode(this)
            else->when(method.returnType){java.lang.Boolean.TYPE->false;java.lang.Integer.TYPE->0;java.lang.Long.TYPE->0L;java.lang.Float.TYPE->1f;else->null}
        }} as ExoPlayer
    }
}
