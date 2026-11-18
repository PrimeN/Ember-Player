package app.ember.tv.player

import android.app.Application
import android.content.Intent
import android.os.Looper
import app.ember.tv.data.Prefs
import androidx.media3.common.Player
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
            activity.player=fake.player;activity.positions=PositionStore(activity);activity.prefs=Prefs(activity)
            try {test(activity,fake)}finally {activity.pipActions?.close();activity.handler.removeCallbacksAndMessages(null)}
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
    @Test fun pipActionsTogglePlaybackAndSeekWithoutOpeningTheActivity()=fixture {activity,fake->
        activity.pictureInPictureEntered=true
        val actions=activity.pictureInPictureParams().actions
        assertEquals(3,actions.size);assertEquals("Pause",actions[1].title.toString())
        actions[1].actionIntent.send();shadowOf(Looper.getMainLooper()).idle()
        assertFalse(fake.playing)
        val paused=activity.pictureInPictureParams().actions
        assertEquals("Play",paused[1].title.toString())
        paused[1].actionIntent.send();shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.playing)
        paused[0].actionIntent.send();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(110_000L,fake.position)
        paused[2].actionIntent.send();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(120_000L,fake.position)
    }
    @Test fun unsupportedSeekingIsDisabledAndOldOrForgedActionsCannotAffectPlayback()=fixture {activity,fake->
        activity.pictureInPictureEntered=true;fake.seekable=false
        val actions=activity.pictureInPictureParams().actions
        assertFalse(actions[0].isEnabled);assertFalse(actions[2].isEnabled)
        val valid=Intent(shadowOf(actions[1].actionIntent).savedIntent)
        activity.sendBroadcast(Intent(valid).putExtra("token","wrong"));shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.playing)
        activity.playbackVisit++
        activity.sendBroadcast(valid);shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.playing)
        activity.pictureInPictureParams()
        try {actions[1].actionIntent.send();fail("Old action should be cancelled")}catch(_:android.app.PendingIntent.CanceledException){}
        val newAction=activity.pictureInPictureParams().actions[1].actionIntent
        activity.pictureInPictureEntered=false
        newAction.send();shadowOf(Looper.getMainLooper()).idle();assertTrue(fake.playing)
        activity.pictureInPictureEntered=true;activity.privatePlayback=true
        newAction.send();shadowOf(Looper.getMainLooper()).idle();assertTrue(fake.playing)
        assertTrue(activity.pictureInPictureParams().actions.isEmpty())
    }
    @Test fun screenOffPipCommitsBackgroundAudioButPausedPipStillStops()=fixture {activity,fake->
        val actual=ExoPlayer.Builder(activity).build()
        val engine=PlaybackEngine(activity,actual,{activity.config.copy(rememberDecoderChoices=false)}) {}
        engine.configure(EngineSource(listOf("https://example.test/video.mp4"),"Video","video"))
        activity.playbackEngine=engine
        ReflectionHelpers.setField(shadowOf(activity),"isInPictureInPictureMode",true)
        activity.pictureInPictureEntered=true
        val power=shadowOf(activity.getSystemService(android.os.PowerManager::class.java))
        power.setIsInteractive(false)
        try {
            assertNull(activity.backgroundRequest())
            assertNotNull(activity.backgroundRequest(true))
            ReflectionHelpers.callInstanceMethod<Unit>(activity,"onStop")
            assertTrue(activity.backgroundQueueTransferred)
            assertNull(activity.player);assertEquals(1,fake.released)
            val pending=ReflectionHelpers.getStaticField<java.util.concurrent.ConcurrentHashMap<String,BackgroundHandoff<BackgroundAudioRequest>>>(BackgroundAudioService::class.java,"pending")
            pending.values.forEach {it.close()};pending.clear()
        }finally{power.setIsInteractive(true);engine.player.release();activity.playbackEngine=null}
    }
    @Test fun unchangedPipStateDoesNotRebuildWindowParameters()=fixture {activity,fake->
        activity.updatePictureInPicture()
        val before=activity.lastPipState
        repeat(20){activity.updatePictureInPicture()}
        assertSame(before,activity.lastPipState)
        fake.playing=false;activity.updatePictureInPicture()
        assertNotSame(before,activity.lastPipState)
    }
    private class FakeVideo {
        var playing=true;var size=VideoSize(1920,1080);var released=0;var position=120_000L;var seekable=true
        val player=Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)){_,method,args->when(method.name) {
            "isPlaying","getPlayWhenReady"->playing
            "getVideoSize"->size
            "getDuration"->600_000L
            "getCurrentPosition"->position
            "isCommandAvailable"->args?.firstOrNull()!=Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM || seekable
            "pause"->{playing=false;null}
            "play"->{playing=true;null}
            "seekTo"->{position=args!!.last() as Long;null}
            "getMediaMetadata"->androidx.media3.common.MediaMetadata.EMPTY
            "getPlaybackParameters"->androidx.media3.common.PlaybackParameters.DEFAULT
            "getSeekParameters"->androidx.media3.exoplayer.SeekParameters.EXACT
            "release"->{released++;null}
            "hashCode"->System.identityHashCode(this)
            else->when(method.returnType){java.lang.Boolean.TYPE->false;java.lang.Integer.TYPE->0;java.lang.Long.TYPE->0L;java.lang.Float.TYPE->1f;else->null}
        }} as ExoPlayer
    }
}
