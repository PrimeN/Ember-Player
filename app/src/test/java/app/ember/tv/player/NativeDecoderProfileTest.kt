package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class NativeDecoderProfileTest {
    private fun choice(mode:String="auto",manual:Boolean?=null,rule:String="inherit",legacy:Boolean=false,deinterlacing:String="off",shield:Boolean=false,hi10p:Boolean=false,allow:Boolean=true)=
        NativeDecoderProfile.choose(mode,manual,rule,legacy,deinterlacing,shield,hi10p,allow)
    @Test fun profilesChooseRealBundledDecoderPaths() {
        assertEquals("mediacodec,mediacodec-copy",choice().hardware)
        assertEquals("mediacodec",choice("direct").hardware)
        assertEquals("mediacodec-copy",choice("copy").hardware)
        assertEquals("no",choice("software").hardware)
        assertEquals("no",choice("light").hardware)
        assertEquals("nonref",choice("light").options["vd-lavc-skiploopfilter"])
        assertEquals("default",choice("software").options["vd-lavc-skiploopfilter"])
    }
    @Test fun manualChoicesAndSourceRulesKeepPriority() {
        assertEquals("no",choice("direct",manual=true).hardware)
        assertNotEquals("no",choice("software",manual=false).hardware)
        assertEquals("no",choice("direct",rule="software").hardware)
        assertNotEquals("no",choice("software",rule="hardware").hardware)
        assertNotEquals("no",choice(rule="auto",legacy=true).hardware)
        assertEquals("no",choice(legacy=true).hardware)
        assertNotEquals("no",choice("direct",legacy=true).hardware)
        assertEquals("mediacodec-copy",choice("direct",deinterlacing="auto").hardware)
    }
    @Test fun hi10pFallbackIsNarrowlyTargetedAndUsesFullQuality() {
        val safe=choice(shield=true,hi10p=true)
        assertEquals("no",safe.hardware);assertTrue(safe.hi10p);assertFalse(safe.light)
        assertEquals("default",safe.options["vd-lavc-skiploopfilter"])
        assertNotEquals("no",choice(shield=false,hi10p=true).hardware)
        assertNotEquals("no",choice(shield=true,hi10p=false).hardware)
        assertNotEquals("no",choice(shield=true,hi10p=true,allow=false).hardware)
        assertNotEquals("no",choice(shield=true,hi10p=true,manual=false).hardware)
        assertNotEquals("no",choice(shield=true,hi10p=true,rule="hardware").hardware)
        assertEquals("mediacodec",choice("direct",shield=true,hi10p=true).hardware)
    }
    @Test fun normalTuningIsRestoredAfterLighterDecodeOrHi10pVideo() {
        val light=choice("light").options;val next=choice().options
        assertEquals(light.keys,next.keys)
        assertEquals("default",next["vd-lavc-skiploopfilter"])
        assertEquals("mediacodec,mediacodec-copy",next["hwdec"])
        assertEquals("0",next["vd-lavc-threads"])
    }
    @Test fun profileAndPixelDetectionAvoidsOtherCodecsAndDevices() {
        assertTrue(NativeDecoderProfile.hi10p("h264","High 10",null))
        assertTrue(NativeDecoderProfile.hi10p("video/avc",null,"yuv420p10le"))
        assertTrue(NativeDecoderProfile.hi10p("video/avc",null,"p010le"))
        assertFalse(NativeDecoderProfile.hi10p("video/hevc","Main 10","yuv420p10le"))
        assertFalse(NativeDecoderProfile.hi10p("h264","High","yuv420p"))
        assertTrue(NativeDecoderProfile.shield("NVIDIA","SHIELD Android TV"))
        assertFalse(NativeDecoderProfile.shield("Other","SHIELD"))
        assertEquals("auto",NativeDecoderProfile.normalized("arbitrary-native-text"))
    }
}
