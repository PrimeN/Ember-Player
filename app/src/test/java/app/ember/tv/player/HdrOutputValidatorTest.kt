package app.ember.tv.player

import android.app.Application
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class HdrOutputValidatorTest {
    @Test fun configurationNeverClaimsThatThePanelOrDynamicMetadataWasVerified() {
        val display=HdrDisplayInfo(true,"HDR10",setOf(2))
        val pq=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build()).build()
        assertEquals(HdrSignal.HDR10,HdrOutputValidator.source(pq))
        assertEquals(HdrSignal.HDR10_PLUS,HdrOutputValidator.source(pq,true))
        val state=HdrOutputValidator.state(HdrSignal.HDR10,HDR10,display,"gpu-next",HdrSurfaceResult(HdrSurfaceStatus.CONFIGURED,HdrSurface.BT2020_PQ),false)
        assertEquals(HdrConfidence.OUTPUT_CONFIGURED,state.confidence)
        assertTrue(state.displaySupportsOutput==true)
        assertFalse(HdrOutputValidator.state(HdrSignal.DOLBY_VISION,HdrSignal.DOLBY_VISION,display,"Media3",null,false).displaySupportsOutput==true)
        assertEquals(HdrConfidence.SOURCE_IDENTIFIED,HdrOutputValidator.state(HdrSignal.HLG,HdrSignal.SDR,display,"gpu",null,true).confidence)
        assertNull(HdrOutputValidator.state(HdrSignal.UNKNOWN,HdrSignal.UNKNOWN,display,"Media3",null,false).displaySupportsOutput)
        assertEquals(HdrSignal.UNKNOWN,HdrOutputValidator.source(pq.buildUpon().setColorInfo(null).build()))
    }
    private val HDR10=HdrSignal.HDR10
    @Test fun aFailedOrUnconfirmedWriteRestoresTheOriginalAndDoesNotEnableExpansion() {
        val surface=Surface(SurfaceTexture(0))
        try {
            var space=42;var writes=0
            val binding=HdrSurfaceBinding({space},{_,value->writes++;space=if(value==HdrSurface.BT2020_PQ)999 else value;true},{true})
            assertEquals(HdrSurfaceStatus.READBACK_MISMATCH,binding.signalResult(surface).status)
            assertEquals(42,space);assertEquals(2,writes)
            val failed=HdrSurfaceBinding({space},{_,value->space=value;value!=HdrSurface.BT2020_PQ},{true})
            assertEquals(HdrSurfaceStatus.WRITE_FAILED,failed.signalResult(surface).status)
            assertEquals(42,space)
        }finally{surface.release()}
    }
    @Test fun failedRestorationCanBeRetriedAndReleaseStopsNativeCallbacks() {
        val surface=Surface(SurfaceTexture(0));val second=Surface(SurfaceTexture(0))
        try {
            var space=0;var rejectRestore=false;var calls=0
            val binding=HdrSurfaceBinding({calls++;space},{_,value->calls++;if(value==0&&rejectRestore)false else {space=value;true}},{true})
            assertTrue(binding.signal(surface));rejectRestore=true
            assertEquals(HdrSurfaceStatus.RESTORE_FAILED,binding.restore().status)
            assertFalse(binding.signal(second))
            rejectRestore=false;assertEquals(HdrSurfaceStatus.RESTORED,binding.restore().status)
            assertEquals(0,space);binding.close();val atClose=calls
            assertFalse(binding.signal(surface));binding.restore();binding.close()
            assertEquals(atClose,calls)
        }finally{surface.release();second.release()}
    }
}
