package app.ember.tv

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import app.ember.tv.player.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class EnteredPlaybackTimeTest {
    @Test fun enteredTimeUsesPreciseSeekingWithoutStartingPausedMedia() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val actual=ExoPlayer.Builder(activity).build()
        var toleranceAtSeek:SeekParameters?=null
        val p=Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)) {_,method,args->
            when(method.name) {
                "getDuration"->120_000L
                "isCommandAvailable"->if(args?.firstOrNull()==Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)true else method.invoke(actual,*(args?:emptyArray()))
                else->try {
                    if(method.name=="seekTo")toleranceAtSeek=actual.seekParameters
                    method.invoke(actual,*(args?:emptyArray()))
                }catch(e:InvocationTargetException){throw e.targetException}
            }
        } as ExoPlayer
        activity.player=p
        try {
            actual.setMediaItem(MediaItem.fromUri("content://media/123"))
            actual.setSeekParameters(SeekParameters.CLOSEST_SYNC);actual.playWhenReady=false
            assertNull(activity.seekToEnteredTime("1:30.250",p,activity.playbackVisit))
            assertEquals(90_250L,actual.currentPosition);assertFalse(actual.playWhenReady)
            assertEquals(SeekParameters.EXACT,toleranceAtSeek)
            assertEquals(SeekParameters.CLOSEST_SYNC,actual.seekParameters)
            assertNotNull(activity.seekToEnteredTime("3:00",p,activity.playbackVisit))
            assertEquals(90_250L,actual.currentPosition)
            assertNotNull(activity.seekToEnteredTime("0",p,activity.playbackVisit+1))
            assertEquals(90_250L,actual.currentPosition)
        }finally{activity.player=null;actual.release()}
    }
}
