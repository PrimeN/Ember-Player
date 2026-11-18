package app.ember.tv

import app.ember.tv.player.*
import app.ember.tv.player.Marker
import app.ember.tv.player.Kind
import app.ember.tv.player.PlaybackItem
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class IntroDbAndFormatsTest {
    @Test fun introDbPreferenceSurvivesConfigSerializationWithoutChangingTheSkipMode() {
        val config = PlaybackConfig(skipIntro = "auto", introDb = true)
        val restored = Json.decodeFromString<PlaybackConfig>(Json.encodeToString(config))
        assertTrue(restored.introDb); assertEquals("auto", restored.skipIntro)
        // Legacy backups retain their field without changing the chapter skip preference.
        assertFalse(Json.decodeFromString<PlaybackConfig>("{}").introDb)
    }

    @Test fun vvcAndNewerAudioFormatsHaveNamesAndRoutesWithoutClaimingUnbundledDecoders() {
        assertTrue(PlaybackEnginePolicy.nativeFirst("https://example.com/clip.h266?token=ignored"))
        assertTrue(PlaybackFormats.nativeFirst("video/vvc")); assertFalse(PlaybackFormats.nativeFirst("video/hevc"))
        assertFalse(PlaybackFormats.nativeFirst("video/vvc", standardSupported = true))
        assertEquals("VVC/H.266", PlaybackFormats.video("video/vvc", null))
        assertEquals("Dolby AC-4", PlaybackFormats.audio("audio/ac4"))
        assertEquals("MPEG-H 3D Audio", PlaybackFormats.audio("audio/mhm1")); assertEquals("IAMF", PlaybackFormats.audio("audio/iamf"))
    }

    @Test fun hdrReportingDoesNotInferDynamicMetadataOrOutputSupportFromTheFilename() {
        assertEquals(app.ember.tv.R.string.ui_hdr10_pq, PlaybackFormats.hdr("video/hevc", null, 6))
        assertEquals(app.ember.tv.R.string.ui_hdr10_metadata_observed, PlaybackFormats.hdr("video/hevc", null, 6, true))
        assertEquals(app.ember.tv.R.string.ui_hdr_not_reported, PlaybackFormats.hdr("video/hevc", null, null, unknown = true))
        assertEquals(app.ember.tv.R.string.ui_dolby_vision, PlaybackFormats.hdr("video/av01", "dav1.10.08", 6))
        assertEquals("AV1", PlaybackFormats.video("video/dolby-vision", "dav1.10.08"))
        assertEquals("H.264", PlaybackFormats.video("video/dolby-vision", "dvav.09.06"))
    }
}
