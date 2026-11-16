package app.ember.tv

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.decoder.iamf.IamfAudioRenderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.metadata.MetadataOutput
import app.ember.tv.player.Playback
import app.ember.tv.player.PlaybackConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class IamfRendererTest {
    @Test fun softwareSettingControlsReflectedIamfRendererRegistration() {
        val context=RuntimeEnvironment.getApplication()
        var expectedOrder:List<String>?=null
        for(setting in listOf("off","fallback","prefer")) for(video in listOf("off","fallback")) {
            val renderers=Playback.Renderers(context,PlaybackConfig(ffmpeg=setting,ffmpegVideo=video)).createRenderers(
                Handler(Looper.getMainLooper()),object:VideoRendererEventListener{},object:AudioRendererEventListener{},
                object:TextOutput{override fun onCues(cues:androidx.media3.common.text.CueGroup){}},MetadataOutput{})
            assertTrue(renderers.any {it.name=="IamfAudioRenderer"})
            assertTrue(renderers.any {it.name=="MediaCodecAudioRenderer"})
            assertTrue(renderers.any {it.name=="MediaCodecVideoRenderer"})
            if(setting!="prefer") {
                val names=renderers.map {it.name}
                if(expectedOrder==null)expectedOrder=names else assertEquals("Decoder indexes must remain stable",expectedOrder,names)
            }
            for(renderer in renderers) {
                val disabled=(setting=="off" && renderer.trackType==C.TRACK_TYPE_AUDIO && renderer.name!="MediaCodecAudioRenderer") ||
                    (video=="off" && renderer.trackType==C.TRACK_TYPE_VIDEO && renderer.name!="MediaCodecVideoRenderer")
                if(disabled)assertEquals(C.FORMAT_UNSUPPORTED_TYPE,androidx.media3.exoplayer.RendererCapabilities.getFormatSupport(
                    renderer.capabilities.supportsFormat(Format.Builder().setSampleMimeType(if(renderer.trackType==C.TRACK_TYPE_AUDIO)"audio/aac" else "video/avc").build())))
            }
        }
    }
    @Test fun encryptedIamfIsRejectedBeforeAnyNativeLibraryIsLoaded() {
        val context=RuntimeEnvironment.getApplication()
        val renderer=IamfAudioRenderer.Builder(DefaultAudioSink.Builder(context).build()).build()
        val format=Format.Builder().setSampleMimeType("audio/iamf")
            .setDrmInitData(DrmInitData(DrmInitData.SchemeData(C.WIDEVINE_UUID,"video/mp4",byteArrayOf(1)))).build()
        assertEquals(C.FORMAT_UNSUPPORTED_DRM,androidx.media3.exoplayer.RendererCapabilities.getFormatSupport(renderer.supportsFormat(format)))
        assertEquals(C.FORMAT_UNSUPPORTED_TYPE,androidx.media3.exoplayer.RendererCapabilities.getFormatSupport(renderer.supportsFormat(Format.Builder().setSampleMimeType("audio/aac").build())))
    }
}
