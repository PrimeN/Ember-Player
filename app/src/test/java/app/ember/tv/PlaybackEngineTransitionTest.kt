package app.ember.tv

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
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

    @Test fun explicitStandardModeRefusesImplicitSwitchButAnExplicitModeChangeWorks() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build();val candidate=ExoPlayer.Builder(context).build()
        var created=0
        val engine=PlaybackEngine(context,original,{PlaybackConfig(engine="standard",rememberDecoderChoices=false)},compatibilityFactory={_,_,_->created++;candidate}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"))
            assertFalse(engine.switch());assertEquals(0,created)
            engine.mode("compatibility");assertEquals(1,created)
        }finally{engine.player.release();if(created==0)candidate.release()}
    }

    @Test fun explicitlyChosenMpvStartsForAnOrdinaryFileWithoutWaitingForFailure() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build();val native=ExoPlayer.Builder(context).build()
        var returnedToMedia3=false
        val candidate=intercept(native){method->if(method=="release")returnedToMedia3=true;Unit}
        var created=0
        val engine=PlaybackEngine(context,original,{PlaybackConfig(engine="compatibility",rememberDecoderChoices=false)},compatibilityFactory={_,_,_->created++;candidate}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film.mp4","key",filename="Film.mp4"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"))
            engine.player.prepare()
            assertEquals(1,created)
            engine.mode("standard")
            assertTrue(returnedToMedia3)
            engine.player.prepare()
            assertEquals(1,created)
        } finally {engine.player.release();if(created==0)candidate.release()}
    }

    @Test fun automaticCoreReasonsDistinguishFormatRoutingAndErrorRecovery() {
        val context=RuntimeEnvironment.getApplication()
        for(nativeFormat in listOf(true,false)) {
            val original=ExoPlayer.Builder(context).build();val candidate=ExoPlayer.Builder(context).build()
            var created=false
            val engine=PlaybackEngine(context,original,{PlaybackConfig(rememberDecoderChoices=false)},compatibilityFactory={_,_,_->created=true;candidate}) {}
            try {
                val name=if(nativeFormat)"Film.wmv" else "Film.mp4"
                engine.configure(EngineSource(listOf("content://media/123"),name,"key",filename=name))
                engine.player.setMediaItem(MediaItem.fromUri("content://media/123"))
                if(nativeFormat)engine.player.prepare() else assertTrue(engine.recover(PlaybackException(null, null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
                assertTrue(created)
                assertEquals(if(nativeFormat)app.ember.tv.R.string.ui_core_format else app.ember.tv.R.string.ui_core_recovery,engine.selectionReason)
                engine.mode("standard")
                assertEquals(app.ember.tv.R.string.ui_core_manual,engine.selectionReason)
            }finally {engine.player.release();if(!created)candidate.release()}
        }
    }
    @Test fun choosingTheAlreadyActiveCoreReplacesItsAutomaticExplanation() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build();val candidate=ExoPlayer.Builder(context).build()
        var created=0
        val engine=PlaybackEngine(context,original,{PlaybackConfig(rememberDecoderChoices=false)},compatibilityFactory={_,_,_->created++;candidate}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film.mp4","key"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"))
            engine.mode("standard");assertEquals(app.ember.tv.R.string.ui_core_manual,engine.selectionReason);assertEquals(0,created)
            engine.mode("auto");assertEquals(app.ember.tv.R.string.ui_core_retained,engine.selectionReason)
            engine.mode("compatibility");assertEquals(1,created)
            engine.mode("auto");assertEquals(app.ember.tv.R.string.ui_core_retained,engine.selectionReason)
            engine.mode("compatibility");assertEquals(app.ember.tv.R.string.ui_core_manual,engine.selectionReason);assertEquals(1,created)
        }finally {engine.player.release();if(created==0)candidate.release()}
    }
    @Test fun anAutomaticBackgroundResumeRestoresItsProgramWithMpv() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build();val candidate=ExoPlayer.Builder(context).build()
        var received:EngineSource?=null
        val engine=PlaybackEngine(context,original,{PlaybackConfig(rememberDecoderChoices=false)},compatibilityFactory={source,_,_->received=source;candidate}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film.mp4","key",edition=2))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"));engine.player.prepare()
            assertEquals(2,received?.edition)
            assertEquals(app.ember.tv.R.string.ui_core_feature,engine.selectionReason)
        }finally {engine.player.release();if(received==null)candidate.release()}
    }
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
            assertFalse(engine.recover(PlaybackException(null, null, PlaybackException.ERROR_CODE_DECODING_FAILED)));assertFalse(constructed);assertEquals(42000,original.currentPosition)
        } finally {engine.player.release()}
    }
    @Test fun automaticCoreRoutingHonoursTunnelingAndExplicitDecoderModes() {
        val context = RuntimeEnvironment.getApplication()
        for (settings in listOf(PlaybackConfig(tunneling = true), PlaybackConfig(ffmpegVideo = "always"),
                PlaybackConfig(ffmpeg = "off"))) {
            val standard = ExoPlayer.Builder(context).build()
            var created = false
            val engine = PlaybackEngine(context, standard, { settings.copy(rememberDecoderChoices = false) },
                compatibilityFactory = { _, _, _ -> created = true; error("Automatic change ignored settings") }) {}
            try {
                engine.configure(EngineSource(listOf("content://media/123"), "Film", "key"))
                assertFalse(engine.recover(PlaybackException(null, null, PlaybackException.ERROR_CODE_DECODING_FAILED)))
                assertFalse(engine.switch(app.ember.tv.R.string.ui_core_styled_subtitles))
                assertFalse(created)
            } finally { engine.player.release() }
        }
    }

    @Test fun requestedAudioTakesPrecedenceOverTheLastDecoderFormat() {
        val context = RuntimeEnvironment.getApplication()
        val original = ExoPlayer.Builder(context).build()
        val aac = Format.Builder().setSampleMimeType("audio/mp4a-latm").build()
        val ac4 = Format.Builder().setSampleMimeType("audio/ac4").build()
        val group = TrackGroup("audio", aac, ac4)
        val tracks = Tracks(listOf(Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(true, false))))
        val standard = intercept(original) { name -> when (name) {
            "getAudioFormat" -> aac
            "getCurrentTracks" -> tracks
            "getTrackSelectionParameters" -> TrackSelectionParameters.DEFAULT.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group, 1)).build()
            else -> Unit
        } }
        val engine = PlaybackEngine(context, standard, { PlaybackConfig(rememberDecoderChoices = false) },
            compatibilityFactory = { _, _, _ -> error("Ineligible audio") }) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"), "Film", "key"))
            assertFalse(engine.switch())
        } finally { engine.player.release() }
    }

    @Test fun returningToStandardKeepsTheCurrentControlsAndPendingSeek() {
        val context = RuntimeEnvironment.getApplication()
        val original = ExoPlayer.Builder(context).build()
        val candidate = ExoPlayer.Builder(context).build()
        val engine = PlaybackEngine(context, original, { PlaybackConfig(rememberDecoderChoices = false) },
            compatibilityFactory = { _, _, _ -> candidate }) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"), "Film", "key"))
            engine.player.setMediaItem(MediaItem.fromUri("content://media/123"))
            assertTrue(engine.switch())
            engine.player.playbackParameters = PlaybackParameters(1.5f, .8f)
            engine.player.volume = .3f
            engine.player.repeatMode = Player.REPEAT_MODE_ONE
            engine.player.playWhenReady = false
            engine.navigate(42000, false)
            engine.mode("standard")
            assertEquals(42000L, engine.player.currentPosition)
            assertEquals(PlaybackParameters(1.5f, .8f), engine.player.playbackParameters)
            assertEquals(.3f, engine.player.volume)
            assertEquals(Player.REPEAT_MODE_ONE, engine.player.repeatMode)
            assertFalse(engine.player.playWhenReady)
        } finally { engine.player.release() }
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

    @Test fun explicitlySelectedLocalIamfCanSwitchToCompatibilityButAutomaticSwitchKeepsStandard() {
        val context = RuntimeEnvironment.getApplication()
        val original = ExoPlayer.Builder(context).build()
        val candidate = ExoPlayer.Builder(context).build()
        val standard = intercept(original) { name ->
            if (name == "getAudioFormat") Format.Builder().setSampleMimeType("audio/iamf").build() else Unit
        }
        var created = 0
        val engine = PlaybackEngine(context, standard, { PlaybackConfig(rememberDecoderChoices = false) },
            compatibilityFactory = { _, _, _ -> created++; candidate }) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"), "Film", "key", filename = "Film.mp4"))
            original.setMediaItem(MediaItem.fromUri("content://media/123"))
            assertFalse(engine.switch())
            assertEquals(0, created)
            engine.mode("compatibility")
            assertEquals(1, created)
        } finally {
            engine.player.release()
            if (created == 0) candidate.release()
        }
    }

    @Test fun unsupportedAudioDoesNotBlockALaterSupportedTrackSwitch() {
        val context=RuntimeEnvironment.getApplication()
        val original=ExoPlayer.Builder(context).build();val candidate=ExoPlayer.Builder(context).build()
        var mime="audio/mhm1";var constructed=false
        val standard=intercept(original){name->if(name=="getAudioFormat")Format.Builder().setSampleMimeType(mime).build() else Unit}
        val engine=PlaybackEngine(context,standard,{PlaybackConfig(rememberDecoderChoices=false)},compatibilityFactory={_,_,_->constructed=true;candidate}) {}
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
            original.setMediaItem(MediaItem.fromUri("content://media/123"));original.seekTo(42000);original.playWhenReady=false
            assertFalse(engine.switch());assertFalse(constructed)
            mime="audio/ac3"
            assertTrue(engine.switch());assertTrue(constructed);assertFalse(engine.player.playWhenReady)
        }finally{engine.player.release();if(!constructed)candidate.release()}
    }

}
