package app.ember.tv

import android.app.Application
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.ember.tv.player.*
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackEngineTransitionTest {
    private fun intercept(player:ExoPlayer,callback:(String)->Any?):ExoPlayer = Proxy.newProxyInstance(
        ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)) {_,method,args->
        val answer=callback(method.name)
        if(answer!==Unit)answer else try {method.invoke(player,*(args?:emptyArray()))}catch(e:InvocationTargetException){throw e.targetException}
    } as ExoPlayer

    @Test fun failedPrepareReleasesCandidateAndRestoresOriginalPositionAndPause() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build()
        val candidate=ExoPlayer.Builder(context).build()
        var released=false
        val failing=intercept(candidate) {method->when(method){
            "prepare"->throw IllegalStateException("Cannot prepare backend")
            "release"->{released=true;Unit}
            else->Unit
        }}
        val engine=PlaybackEngine(context,original,{PlaybackConfig(rememberDecoderChoices=false)},compatibilityFactory={_,_,_->failing}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"));engine.player.seekTo(42000)
            engine.player.playWhenReady=false
            assertFalse(engine.switch());assertTrue(released)
            assertEquals(42000,engine.player.currentPosition);assertFalse(engine.player.playWhenReady)
            assertEquals("content://media/123",engine.player.currentMediaItem?.localConfiguration?.uri.toString())
        } finally {engine.player.release()}
    }

    @Test fun audioWithoutACompatibilityDecoderNeverDestroysWorkingStandardEngine() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build()
        val standard=intercept(original) {method->if(method=="getAudioFormat")Format.Builder().setSampleMimeType("audio/mhm1").build() else Unit}
        var constructed=false
        val engine=PlaybackEngine(context,standard,{PlaybackConfig(rememberDecoderChoices=false)},compatibilityFactory={_,_,_->constructed=true;error("Should not switch")}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"));engine.player.seekTo(42000)
            assertFalse(engine.recover(4003));assertFalse(constructed);assertEquals(42000,original.currentPosition)
        } finally {engine.player.release()}
    }
    @Test fun pendingKeySeeksAccumulateButExplicitJumpsAndSourceChangesCancelThem() {
        val context=RuntimeEnvironment.getApplication()
        val actual=ExoPlayer.Builder(context).build()
        val engine=PlaybackEngine(context,actual,{PlaybackConfig(rememberDecoderChoices=false)}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"));engine.player.seekTo(10000)
            engine.navigate(20000,true);engine.navigate(30000,true)
            assertEquals(30000L,engine.player.currentPosition);assertEquals(10000L,actual.currentPosition)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60))
            assertEquals(30000L,actual.currentPosition)
            engine.navigate(90000,true);engine.navigate(12345,false,coalesce=false)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60))
            assertEquals(12345L,actual.currentPosition)
            engine.navigate(90000,true);engine.player.seekTo(7000)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60))
            assertEquals(7000L,actual.currentPosition)
            engine.navigate(90000,true)
            engine.configure(EngineSource(listOf("content://media/456"),"Next","next"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/456"));engine.player.seekTo(5000)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(60))
            assertEquals(5000L,actual.currentPosition)
        }finally{engine.player.release()}
    }

    @Test fun unavailableFrameInspectionKeepsWorkingAudioAndPlaybackState() {
        val activity=org.robolectric.Robolectric.buildActivity(PlayerActivity::class.java).get()
        val actual=ExoPlayer.Builder(activity).build()
        val standard=intercept(actual){name->if(name=="getAudioFormat")Format.Builder().setSampleMimeType("audio/mhm1").build() else Unit}
        val engine=PlaybackEngine(activity,standard,{PlaybackConfig(rememberDecoderChoices=false)}) {}
        engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
        activity.playbackEngine=engine;activity.player=engine.player
        try {
            actual.setMediaItem(MediaItem.fromUri("content://media/123"));actual.seekTo(42000);actual.playWhenReady=true
            activity.inspectFrames()
            assertTrue(actual.playWhenReady);assertEquals(42000L,actual.currentPosition)
            assertNull(activity.actionDialog);assertNull(engine.compatibility)
        }finally{activity.player=null;activity.playbackEngine=null;engine.player.release()}
    }

}
