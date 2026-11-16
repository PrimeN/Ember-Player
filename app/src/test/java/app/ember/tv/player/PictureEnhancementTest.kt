package app.ember.tv.player

import android.app.Application
import androidx.media3.common.*
import androidx.media3.effect.LanczosResample
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PictureEnhancementTest {
    private fun video(transfer:Int=C.COLOR_TRANSFER_SDR)=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(1280).setHeight(720).setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build()).build()

    @Test fun olderPreferencesEnableUpscalingWithoutChangingPictureColors() {
        val config=Json.decodeFromString<PlaybackConfig>("{}")
        assertTrue(config.picture.enabled);assertTrue(config.picture.upscaling);assertTrue(config.picture.shader)
        assertEquals(PictureEnhancement(),config.picture)
        val saved=config.copy(picture=PictureEnhancement(enabled=false,brightness=15,contrast=8,sharpness=20))
        assertEquals(saved,Json.decodeFromString<PlaybackConfig>(Json.encodeToString(saved)))
    }
    @Test fun enhancementOnlyAllocatesAnUpscalerWhenItEnlargesTheVideo() {
        val p=PictureEnhancement()
        assertTrue(StandardPictureEffects.effects(p,1920,1080,video()).single() is LanczosResample)
        assertTrue(StandardPictureEffects.effects(p,1280,720,video()).isEmpty())
        assertTrue(StandardPictureEffects.effects(p,640,360,video()).isEmpty())
        assertTrue(StandardPictureEffects.effects(p,0,0,video()).isEmpty())
        assertTrue(StandardPictureEffects.effects(p.copy(enabled=false,sharpness=30),1920,1080,video()).isEmpty())
        assertTrue(StandardPictureEffects.effects(p.copy(upscaling=false),1920,1080,video()).isEmpty())
    }
    @Test fun portraitWindowsAndRotatedVideoDoNotAllocateAnOversizedUpscaler() {
        val p=PictureEnhancement()
        assertTrue(StandardPictureEffects.effects(p,1080,2400,video()).isEmpty())
        val portrait=video().buildUpon().setWidth(720).setHeight(1280).build()
        assertTrue(StandardPictureEffects.effects(p,1920,1080,portrait).isEmpty())
        val rotated=video().buildUpon().setRotationDegrees(90).build()
        assertTrue(StandardPictureEffects.effects(p,1920,1080,rotated).isEmpty())
        assertTrue(StandardPictureEffects.effects(p,480,270,video()).isEmpty())
        assertTrue(StandardPictureEffects.effects(p,1080,1920,portrait).single() is LanczosResample)
    }
    @Test fun hdrDolbyVisionProtectedAndUnknownVideoKeepTheOriginalPath() {
        val p=PictureEnhancement(contrast=10,gamma=10,sharpness=20)
        val protected=video().buildUpon().setDrmInitData(DrmInitData("cenc",DrmInitData.SchemeData(C.WIDEVINE_UUID,"video/mp4",byteArrayOf(1)))).build()
        for(format in listOf(null,video(C.COLOR_TRANSFER_ST2084),video(C.COLOR_TRANSFER_HLG),protected,
            video().buildUpon().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).build())) {
            assertFalse(StandardPictureEffects.supported(format))
            assertTrue(StandardPictureEffects.effects(p,1920,1080,format).isEmpty())
        }
    }
    @Test fun pictureBoundsAndPresetsCannotExceedRendererRanges() {
        val hostile=PictureEnhancement(brightness=Int.MIN_VALUE,contrast=Int.MAX_VALUE,saturation=Int.MIN_VALUE,gamma=Int.MAX_VALUE,hue=Int.MAX_VALUE,sharpness=-1).normalized()
        assertEquals(-50,hostile.brightness);assertEquals(50,hostile.contrast);assertEquals(-100,hostile.saturation)
        assertEquals(50,hostile.gamma);assertEquals(100,hostile.hue);assertEquals(0,hostile.sharpness)
        PictureEnhancement.presets.indices.forEach {assertEquals(PictureEnhancement.preset(it),PictureEnhancement.preset(it).normalized())}
        assertEquals(PictureEnhancement(),PictureEnhancement.preset(-1))
        assertEquals(4,StandardPictureEffects.effects(PictureEnhancement(upscaling=false,brightness=5,contrast=5,saturation=5,gamma=5,sharpness=10),1920,1080,video()).size)
    }
    @Test fun choosingAPresetPreservesEnhancementSwitchesAndShowsItsActualName() {
        val disabled=PictureEnhancement(enabled=false,upscaling=false,shader=false)
        val vivid=PictureEnhancement.preset(3,disabled)
        assertFalse(vivid.enabled);assertFalse(vivid.upscaling);assertFalse(vivid.shader)
        assertEquals(3,PictureEnhancement.presetIndex(vivid))
        assertEquals(-1,PictureEnhancement.presetIndex(vivid.copy(brightness=5)))
        assertEquals(0,PictureEnhancement.presetIndex(disabled))
    }
    @Test fun bundledShaderIsVersionedAndRepairsAStalePrivateCopy() {
        val context=RuntimeEnvironment.getApplication()
        val original=context.assets.open("shaders/fast-upscale.glsl").use {it.readBytes()}
        assertEquals("4c53ec2e287908f7ee7bcb266b0170421626d663576468b7d7dafc62962649a4",java.security.MessageDigest.getInstance("SHA-256").digest(original).joinToString(""){"%02x".format(it)})
        assertTrue(original.decodeToString().contains("Permission is hereby granted"))
        val file=NativePlayerFiles.upscaleShader(context)
        file.writeText("old shader")
        assertArrayEquals(original,NativePlayerFiles.upscaleShader(context).readBytes())
        assertTrue(context.assets.open("licenses/Anime4K-MIT.txt").bufferedReader().use {it.readText()}.contains("bloc97"))
    }
}
