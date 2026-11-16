package app.ember.tv.player

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AdvancedPictureProfilesTest {
    private fun saved():PlaybackConfig=PictureProfiles.save(PlaybackConfig(),"Animation",PictureState(picture=PictureEnhancement(contrast=10),deband=true,hdr=HdrOutput(sdr=true)),id={"profile-1"})
    @Test fun profileRoundTripIncludesEveryPictureOptionAndBindings() {
        val config=PictureProfiles.bind(saved(),"content://private/media/1","profile-1")
        assertEquals(config,Json.decodeFromString<PlaybackConfig>(Json.encodeToString(config)))
        assertFalse(Json.encodeToString(config).contains("content://private"))
        val effective=PictureProfiles.effective(config,"content://private/media/1")
        assertEquals(10,effective.picture.contrast);assertTrue(effective.deband);assertTrue(effective.hdrOutput.sdr)
        assertEquals(PictureEnhancement(),PictureProfiles.effective(config,"another video").picture)
        assertEquals(config.picture,PlaybackConfig().picture)
    }
    @Test fun updatingAProfileKeepsItsIdentityAndExistingBindings() {
        val original=PictureProfiles.bind(saved(),"media","profile-1")
        val updated=PictureProfiles.save(original,"ANIMATION",PictureState(picture=PictureEnhancement(sharpness=20)),id={error("Should replace")})
        assertEquals(1,updated.pictureProfiles.size);assertEquals("profile-1",updated.pictureProfiles.single().id)
        assertEquals(20,PictureProfiles.effective(updated,"media").picture.sharpness)
        assertTrue(PictureProfiles.remove(updated,"profile-1").mediaPictureProfiles.isEmpty())
    }
    @Test fun mediaBoundEditsDoNotChangeGlobalDefaultsOrUnrelatedProfiles() {
        val original=PictureProfiles.bind(saved(),"media","profile-1")
        val edited=PictureProfiles.edit(original,"media"){it.copy(picture=it.picture.copy(brightness=15))}
        assertEquals(0,edited.picture.brightness)
        assertEquals(15,PictureProfiles.effective(edited,"media").picture.brightness)
        assertEquals(0,PictureProfiles.effective(edited,"other").picture.brightness)
        assertEquals(0,PictureProfiles.effective(PictureProfiles.bind(edited,"media",null),"media").picture.brightness)
    }
    @Test fun profilesHaveBoundedNamesCountsAndSafeNativeOptions() {
        var config=PlaybackConfig()
        repeat(16){i->config=PictureProfiles.save(config,"Name $i",PictureState(),id={"p-$i"})}
        try {PictureProfiles.save(config,"Too many",PictureState());fail("Expected limit")}catch(_:IllegalArgumentException){}
        try {PictureProfiles.save(config," \n ",PictureState());fail("Expected name")}catch(_:IllegalArgumentException){}
        repeat(300){i->config=PictureProfiles.bind(config,"media-$i","p-0")}
        assertEquals(256,config.mediaPictureProfiles.size);assertNull(PictureProfiles.selected(config,"media-0"))
        assertNotNull(PictureProfiles.selected(config,"media-299"))
        val hostile=PictureState(picture=PictureEnhancement(contrast=999),scaler="arbitrary-command",hdr=HdrOutput(tone="untrusted",gamut="untrusted",peak=99999)).normalized()
        assertEquals(50,hostile.picture.contrast);assertEquals("bilinear",hostile.scaler)
        assertEquals("bt.2390",hostile.hdr.tone);assertEquals(500,hostile.hdr.peak)
    }
    @Test fun mediaAssignmentsFollowFileIdentityChanges() {
        val original=PictureProfiles.bind(saved(),"file:old","profile-1")
        val renamed=PictureProfiles.moved(original,"file:old","file:new")
        assertNull(PictureProfiles.selected(renamed,"file:old"))
        assertEquals("profile-1",PictureProfiles.selected(renamed,"file:new")?.id)
        assertEquals(renamed,PictureProfiles.moved(renamed,"missing","other"))
    }
    @Test fun olderSettingsRetainSafeHdrAndDecoderDefaults() {
        val old=Json.decodeFromString<PlaybackConfig>("{}")
        assertFalse(old.hdrOutput.sdr);assertFalse(old.hdrOutput.analyzePeak)
        assertTrue(old.pictureProfiles.isEmpty());assertTrue(old.mediaPictureProfiles.isEmpty())
        assertEquals("auto",old.compatibilityDecoder);assertTrue(old.hi10pFallback)
    }
    @Test fun hdrConversionUsesSupportedOptionsAndRestoresAllAutomaticDefaults() {
        val config=HdrOutput(sdr=true,tone="reinhard",gamut="clip",peak=203,analyzePeak=true)
        val active=config.options(true)
        assertEquals("bt.709",active["target-prim"]);assertEquals("bt.1886",active["target-trc"])
        assertEquals("203",active["target-peak"]);assertEquals("reinhard",active["tone-mapping"])
        assertEquals("clip",active["gamut-mapping-mode"]);assertEquals("yes",active["hdr-compute-peak"])
        val normal=HdrOutput().options(true)
        assertEquals(normal,config.options(false));assertEquals(normal,config.options(true,bypassed=true))
        assertTrue(normal.values.all {it=="auto"})
        assertEquals("no",config.copy(analyzePeak=false).options(true)["hdr-compute-peak"])
        HdrOutput.tones.forEach {(tone,_)->assertEquals(tone,config.copy(tone=tone).options(true)["tone-mapping"])}
    }
}
