package app.ember.tv.player

import android.app.Application
import android.view.Surface
import androidx.media3.common.*
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class AdvancedPlaybackRuntimeTest {
    private fun params(buffer:Long,rebuffer:Boolean=false)=LoadControl.Parameters(PlayerId.UNSET,Timeline.EMPTY,MediaPeriodId("media"),0,buffer,1f,true,rebuffer,C.TIME_UNSET,C.TIME_UNSET)
    @Test fun allocatorBudgetStopsLoadingButDoesNotPreventStartingWithASmallBuffer() {
        val config=PlaybackConfig(stream=StreamConfig(custom=true,forwardMiB=8,rewindMiB=0,readAheadSeconds=20))
        val control=ReloadableLoadControl(config,{config}){StreamConfig.plan(it,128L*1024*1024,false)}
        control.onPrepared(PlayerId.UNSET)
        assertTrue(control.shouldContinueLoading(params(0)))
        val allocator=control.getAllocator(PlayerId.UNSET)
        val held=List(8*1024*1024/C.DEFAULT_BUFFER_SEGMENT_SIZE){allocator.allocate()}
        assertFalse(control.shouldContinueLoading(params(0)))
        assertFalse(control.shouldStartPlayback(params(0)))
        assertTrue(control.shouldStartPlayback(params(100000)))
        held.forEach(allocator::release);control.onReleased(PlayerId.UNSET)
    }
    @Test fun preparingAgainReadsNewSettingsWithoutReplacingAllocatorOrKeepingBackBuffer() {
        var config=PlaybackConfig(stream=StreamConfig(custom=true,refillSeconds=5))
        val control=ReloadableLoadControl(config,{config}){StreamConfig.plan(it,512L*1024*1024,false)}
        val allocator=control.getAllocator(PlayerId.UNSET)
        control.onPrepared(PlayerId.UNSET)
        assertFalse(control.shouldStartPlayback(params(2_000_000,true)))
        config=config.copy(stream=config.stream.copy(refillSeconds=1,pauseForBuffer=false))
        control.onPrepared(PlayerId.UNSET)
        assertSame(allocator,control.getAllocator(PlayerId.UNSET));assertEquals(0L,control.getBackBufferDurationUs(PlayerId.UNSET))
        assertTrue(control.shouldStartPlayback(params(100000,true)))
        assertFalse(control.shouldContinueLoading(params(600_000_000)))
        control.onReleased(PlayerId.UNSET)
    }
    @Test fun surfaceMetadataRestoresOriginalOnSurfaceChangeAndOnExit() {
        val first=Surface(android.graphics.SurfaceTexture(0));val second=Surface(android.graphics.SurfaceTexture(0));val spaces=mutableMapOf(first to 0,second to 123)
        val binding=HdrSurfaceBinding({spaces[it]?:-1},{surface,space->spaces[surface]=space;true},{true})
        try {
            assertTrue(binding.signal(first));assertEquals(HdrSurface.BT2020_PQ,spaces[first])
            assertTrue(binding.signal(second));assertEquals(0,spaces[first]);assertEquals(HdrSurface.BT2020_PQ,spaces[second])
            binding.restore();assertEquals(123,spaces[second]);binding.restore();assertEquals(123,spaces[second])
        }finally{first.release();second.release()}
    }
    @Test fun unsupportedSurfaceNeverEnablesHdrOutput() {
        val surface=Surface(android.graphics.SurfaceTexture(0))
        try {
            val binding=HdrSurfaceBinding({-1},{_,_->fail("Should not write without original metadata");false},{true})
            assertFalse(binding.signal(surface));assertFalse(binding.signal(null))
            assertEquals("no",HdrOutput(expand=true).options(false,advanced=true,signaledHdr=false)["inverse-tone-mapping"])
        }finally{surface.release()}
    }
    @Test fun comparisonBypassesOptionalProcessingWithoutWritingSavedPreferences() {
        val activity=Robolectric.buildActivity(PlayerActivity::class.java).get()
        val config=PlaybackConfig(picture=PictureEnhancement(contrast=20),hdrOutput=HdrOutput(expand=true),deband=true,interpolation=true,renderer="gpu-next")
        activity.config=config;activity.compareOriginal=true
        val original=activity.renderConfig()
        assertFalse(original.picture.enabled);assertFalse(original.hdrOutput.expand);assertFalse(original.deband);assertFalse(original.interpolation)
        assertEquals(config,activity.config)
        activity.endPictureComparison();assertFalse(activity.compareOriginal);assertEquals(config,activity.renderConfig())
    }
    @Test fun hdrDiagnosticsUseActualCodecProfileAndKeepUnreportedValuesHonest() {
        val dv=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).setCodecs("dvhe.07.06").build()
        assertTrue(HdrDiagnostics.source(org.robolectric.RuntimeEnvironment.getApplication().resources,dv).contains("profile 7"))
        assertTrue(HdrDiagnostics.source(org.robolectric.RuntimeEnvironment.getApplication().resources,dv.buildUpon().setCodecs(null).build()).contains("not reported"))
        assertEquals("Not reported",HdrDiagnostics.source(org.robolectric.RuntimeEnvironment.getApplication().resources,null))
        assertFalse(HdrDiagnostics.isSdrTransfer(null));assertFalse(HdrDiagnostics.isSdrTransfer("pq"));assertFalse(HdrDiagnostics.isSdrTransfer("hlg"));assertTrue(HdrDiagnostics.isSdrTransfer("bt.1886"))
        assertEquals("SDR to HDR · experimental",HdrDiagnostics.requested(org.robolectric.RuntimeEnvironment.getApplication().resources,HdrOutput(expand=true)))
    }
}
