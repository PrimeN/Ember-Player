package app.ember.tv.player

import android.app.Application
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class HdrOutputRoutingTest {
    private fun route(config:PlaybackConfig,transfer:Int=C.COLOR_TRANSFER_ST2084,known:Boolean=true,
        audio:String="audio/ac3",drm:Boolean=false):Boolean {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val original=ExoPlayer.Builder(activity).build();val candidate=ExoPlayer.Builder(activity).build()
        val format=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(1920).setHeight(1080)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build()).apply {
                if(drm)setDrmInitData(DrmInitData("cenc",DrmInitData.SchemeData(C.WIDEVINE_UUID,"video/mp4",byteArrayOf(1))))
            }.build()
        val tracks=if(known)Tracks(listOf(Tracks.Group(TrackGroup("v",format),false,intArrayOf(C.FORMAT_HANDLED),booleanArrayOf(true)))) else Tracks.EMPTY
        val standard=Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)){_,method,args->
            when(method.name){
                "getVideoFormat"->format
                "getAudioFormat"->Format.Builder().setSampleMimeType(audio).build()
                "getCurrentTracks"->tracks
                else->try {method.invoke(original,*(args?:emptyArray()))}catch(e:InvocationTargetException){throw e.targetException}
            }
        } as ExoPlayer
        var constructed=false;var initial=0L
        val engine=PlaybackEngine(activity,standard,{config.copy(rememberDecoderChoices=false)},compatibilityFactory={_,at,_->constructed=true;initial=at;candidate}) {}
        activity.player=engine.player;activity.playbackEngine=engine;activity.config=config;activity.resumeKey="media"
        try {
            engine.configure(EngineSource(listOf("content://media/123"),"Film","key"))
            original.setMediaItem(MediaItem.fromUri("content://media/123"));original.seekTo(42000);original.playWhenReady=false
            activity.applyPictureEnhancement(format)
            assertFalse(engine.player.playWhenReady)
            if(constructed)assertEquals(42000L,initial) else assertEquals(42000L,original.currentPosition)
            return constructed
        }finally{activity.player=null;activity.playbackEngine=null;engine.player.release();if(!constructed)candidate.release()}
    }
    @Test fun conversionSwitchesOnlyWhenRequestedAndTheSourceIsKnownAndSupported() {
        val enabled=PlaybackConfig(hdrOutput=HdrOutput(sdr=true),rememberDecoderChoices=false)
        assertTrue(route(enabled))
        assertTrue(route(enabled,transfer=C.COLOR_TRANSFER_HLG))
        assertFalse(route(enabled,known=false))
        assertFalse(route(enabled,transfer=C.COLOR_TRANSFER_SDR))
        assertFalse(route(enabled.copy(engine="standard")))
        assertFalse(route(enabled.copy(hdrOutput=HdrOutput())))
        assertFalse(route(enabled,drm=true))
        assertFalse(route(enabled,audio="audio/mhm1"))
        assertFalse(route(enabled,audio="audio/ac4"))
    }
    @Test fun aMediaSpecificProfileCanRequestConversionWithoutChangingGlobalOutput() {
        val saved=PictureProfiles.save(PlaybackConfig(),"SDR screen",PictureState(hdr=HdrOutput(sdr=true)),id={"sdr"})
        val bound=PictureProfiles.bind(saved,"media","sdr")
        assertFalse(bound.hdrOutput.sdr)
        assertTrue(route(bound))
    }
}
