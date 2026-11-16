package app.ember.tv

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import app.ember.tv.player.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackRecoveryLifecycleTest {
    @Test fun audioOutputRetryPreservesPauseAndPosition() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        activity.config=PlaybackConfig()
        val player=ExoPlayer.Builder(activity).build();activity.player=player
        try {
            player.setMediaItem(MediaItem.fromUri("https://example.invalid/film.mp4"));player.seekTo(42000);player.playWhenReady=false
            assertTrue(activity.recover(PlaybackException("Audio output failed",null,PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)))
            assertEquals(42000,player.currentPosition);assertFalse(player.playWhenReady)
        } finally {activity.player=null;player.release()}
    }
    @Test fun delayedRetryCannotSeekOrResumeANewItem() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val player=ExoPlayer.Builder(activity).build();activity.player=player
        try {
            player.setMediaItem(MediaItem.fromUri("https://example.invalid/old.mp4"));player.seekTo(42000)
            assertTrue(activity.recover(PlaybackException("Connection lost",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
            player.setMediaItem(MediaItem.fromUri("https://example.invalid/new.mp4"));player.seekTo(7000);player.playWhenReady=false
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1501))
            assertEquals(7000,player.currentPosition)
            assertFalse(player.playWhenReady)
        } finally {activity.player=null;player.release()}
    }
    @Test fun retryingTheSameItemPreservesAPauseMadeDuringTheDelay() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val player=ExoPlayer.Builder(activity).build();activity.player=player
        try {
            player.setMediaItem(MediaItem.fromUri("https://example.invalid/film.mp4"));player.seekTo(42000);player.playWhenReady=true
            assertTrue(activity.recover(PlaybackException("Connection lost",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
            player.playWhenReady=false
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1501))
            assertEquals(42000,player.currentPosition);assertFalse(player.playWhenReady)
        } finally {activity.cancelRecovery();activity.player=null;player.release()}
    }

    @Test fun pictureFailureBypassesEffectsOnceAndKeepsThePausedPosition() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val actual=ExoPlayer.Builder(activity).build()
        activity.player=actual
        activity.playbackEngine=PlaybackEngine(activity,actual,{PlaybackConfig(rememberDecoderChoices=false)}) {}
        activity.config=PlaybackConfig();activity.pictureEffectsActive=true
        try {
            actual.setMediaItem(MediaItem.fromUri("content://media/123"));actual.seekTo(42000);actual.playWhenReady=false
            val error=PlaybackException("GPU unavailable",null,PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED)
            assertTrue(activity.recoverPicture(error));assertTrue(activity.pictureBypassed)
            assertFalse(activity.pictureEffectsActive)
            assertEquals(42000L,actual.currentPosition);assertFalse(actual.playWhenReady)
            assertFalse(activity.recoverPicture(error))
            assertFalse(activity.recoverPicture(PlaybackException("Network",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        }finally{activity.player=null;activity.playbackEngine=null;actual.release()}
    }

}
