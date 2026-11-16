package app.ember.tv.player

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AdvancedPlaybackOptionsTest {
    @Test fun bufferingHonoursOneDeviceBudgetAndKeepsUsefulForwardSpace() {
        val config=PlaybackConfig(stream=StreamConfig(custom=true,forwardMiB=512,rewindMiB=128))
        val low=StreamConfig.plan(config,64L*1024*1024,true)
        assertTrue(low.forwardMiB>=8);assertTrue(low.totalBytes<=16*1024*1024)
        val high=StreamConfig.plan(config,4L*1024*1024*1024,false)
        assertTrue(high.totalBytes<=192*1024*1024);assertTrue(high.rewindMiB<=high.forwardMiB)
        assertTrue(StreamConfig.plan(config.copy(stream=config.stream.copy(forwardMiB=8)),128L*1024*1024,true).forwardMiB>=8)
    }
    @Test fun presetsAndCustomTimesHaveBoundedNativeOptions() {
        val normal=StreamConfig.plan(PlaybackConfig(buffer="normal"),1024L*1024*1024,false)
        val huge=StreamConfig.plan(PlaybackConfig(buffer="huge"),1024L*1024*1024,false)
        assertTrue(huge.forwardMiB>normal.forwardMiB);assertTrue(huge.aheadSeconds>normal.aheadSeconds)
        val hostile=StreamConfig(true,Int.MIN_VALUE,Int.MAX_VALUE,Int.MIN_VALUE,Int.MAX_VALUE,Int.MAX_VALUE,Int.MAX_VALUE,false,Int.MIN_VALUE).normalized()
        assertEquals(5,hostile.connectSeconds);assertEquals(300,hostile.readSeconds);assertEquals(8,hostile.forwardMiB)
        assertEquals(600,hostile.aheadSeconds);assertEquals(30,hostile.refillSeconds)
        val p=StreamConfig.plan(PlaybackConfig(stream=hostile),512L*1024*1024,false)
        assertEquals("no",p.nativeOptions()["cache-pause"]);assertEquals("300",p.nativeOptions()["network-timeout"])
    }
    @Test fun rendererRecoveryRetriesOnceAndRecreatesBeforeStableInitialization() {
        val state=RendererSession("gpu-next");val calls=mutableListOf<String>()
        state.initialize({mode->calls+=mode;if(mode=="gpu-next")throw IllegalStateException("Unavailable")},{calls+="recreate"})
        assertEquals(listOf("gpu-next","recreate","gpu"),calls);assertTrue(state.recovered);assertFalse(state.fallback())
        state.select("gpu-next");assertFalse(state.recovered);assertTrue(state.fallback())
        try {RendererSession("gpu").initialize({throw IllegalStateException("Unavailable")},{fail("No retry for stable")});fail("Expected failure")}catch(_:IllegalStateException){}
    }
    @Test fun expansionRequiresAdvancedOutputAndConfirmedSurfaceMetadata() {
        val hdr=HdrOutput(expand=true,hdrPeak=800)
        for(options in listOf(hdr.options(false),hdr.options(false,advanced=true),hdr.options(true,advanced=true,signaledHdr=true),hdr.options(false,bypassed=true,advanced=true,signaledHdr=true)))
            assertEquals("no",options["inverse-tone-mapping"])
        val active=hdr.options(false,advanced=true,signaledHdr=true)
        assertEquals("pq",active["target-trc"]);assertEquals("bt.2020",active["target-prim"]);assertEquals("800",active["target-peak"])
        assertEquals("yes",active["inverse-tone-mapping"]);assertEquals("no",active["target-colorspace-hint-strict"])
        assertFalse(HdrOutput(sdr=true,expand=true).normalized().expand)
        assertEquals(1500,HdrOutput(hdrPeak=99999).normalized().hdrPeak)
        assertEquals("gpu",RendererPolicy.initial("auto",true,false));assertEquals("gpu-next",RendererPolicy.initial("auto",true,true))
        assertEquals("gpu",RendererPolicy.initial("gpu",true,true))
    }
    @Test fun subtitlePresetsChangeAppearanceWithoutEnablingServicesOrChangingLanguage() {
        val saved=app.ember.tv.data.SubtitleConfig(font="mono",color="yellow",stremioOpenSubs=true,autoSelect="always",languages=listOf("fr"))
        val preset=SubtitlePresets.save(emptyList(),"Cinema",saved).single()
        val current=app.ember.tv.data.SubtitleConfig(stremioOpenSubs=false,autoSelect="off",languages=listOf("en"))
        val applied=preset.style.applyTo(current)
        assertEquals("mono",applied.font);assertEquals("yellow",applied.color)
        assertFalse(applied.stremioOpenSubs);assertEquals("off",applied.autoSelect);assertEquals(listOf("en"),applied.languages)
        assertEquals(1,SubtitlePresets.save(listOf(preset),"CINEMA",saved).size)
    }
    @Test fun geometryStaysWithItsMediaAndMigratesWithFileIdentity() {
        val original=GeometryMemory.set(PlaybackConfig(),"first",VideoGeometry("crop",4,2f))
        assertEquals(VideoGeometry("crop",4,2f),GeometryMemory.selected(original,"first"));assertNull(GeometryMemory.selected(original,"second"))
        val moved=GeometryMemory.moved(original,"first","renamed")
        assertNull(GeometryMemory.selected(moved,"first"));assertEquals(4,GeometryMemory.selected(moved,"renamed")?.aspect)
        assertTrue(GeometryMemory.set(moved,"renamed",null).mediaGeometry.isEmpty())
        assertEquals(VideoGeometry("fit",5,1f),VideoGeometry("invalid",999,Float.NaN).normalized())
    }
    @Test fun newPlaybackPreferencesRoundTripWithoutChangingOlderDefaults() {
        val value=PlaybackConfig(stream=StreamConfig(custom=true,connectSeconds=50),renderer="gpu-next",audioEffects=AudioEffects(compression="night"),hdrOutput=HdrOutput(expand=true),mediaGeometry=mapOf("key" to VideoGeometry("crop",2)))
        assertEquals(value,Json.decodeFromString<PlaybackConfig>(Json.encodeToString(value)))
        val old=Json.decodeFromString<PlaybackConfig>("{}")
        assertFalse(old.hdrOutput.expand);assertEquals("auto",old.renderer);assertEquals("off",old.audioEffects.compression);assertTrue(old.mediaGeometry.isEmpty())
    }
    @Test fun nightCompressionReducesDynamicRangeWithoutClippingOrChannelDrift() {
        fun rms(effects:AudioEffects,amplitude:Float):Double {
            val dsp=AudioDsp(48000,2,effects);val f=FloatArray(2);var sum=0.0
            repeat(48000){i->val sample=(amplitude*sin(2*PI*440*i/48000)).toFloat();f.fill(sample);dsp.frame(f)
                assertTrue(f.all {it.isFinite()&&abs(it)<=1f});assertEquals(f[0],f[1],0f)
                if(i>=24000)sum+=f[0]*f[0]}
            return sqrt(sum/24000)
        }
        val flat=rms(AudioEffects(),.8f)/rms(AudioEffects(),.03f)
        val night=rms(AudioEffects(compression="night"),.8f)/rms(AudioEffects(compression="night"),.03f)
        assertTrue(night<flat*.65)
        assertTrue(AudioEffects(compression="night").nativeFilters().contains("acompressor="))
        assertTrue(AudioEffects(compression="night").headroomDb(2)>=6)
        val frame=floatArrayOf(Float.NaN,Float.POSITIVE_INFINITY);val dsp=AudioDsp(48000,2,AudioEffects(compression="night"));dsp.frame(frame)
        assertTrue(frame.all {it.isFinite()})
    }
}
