package app.ember.tv

import app.ember.tv.player.MediaFileFormats
import app.ember.tv.player.PlaybackEnginePolicy
import app.ember.player.ExternalMedia
import org.junit.Assert.*
import org.junit.Test

class MediaFileFormatsTest {
    @Test fun rawVideoProfessionalAndAudioFilesAreVisibleWithoutProviderMime() {
        for(name in listOf("picture.VVC","picture.h266","picture.hevc","picture.h264","picture.mxf","picture.apv",
            "book.m4b","sound.aif","sound.mp2","sound.dsf","sound.iamf")) assertTrue(name,MediaFileFormats.media(name,""))
        assertTrue(MediaFileFormats.media("1234","Video/MP4; charset=binary"))
        assertFalse(MediaFileFormats.media("picture.jpg","image/jpeg"))
        assertFalse(MediaFileFormats.media("notes.txt","text/plain"))
    }
    @Test fun opaqueUrisUseKnownFilenameAndIgnoreMisleadingQuerySuffixes() {
        assertTrue(PlaybackEnginePolicy.nativeFirst("content://documents/video/1234","Film.vvc"))
        assertTrue(PlaybackEnginePolicy.nativeFirst("https://example.invalid/stream?token=1","Film.mxf"))
        assertFalse(PlaybackEnginePolicy.nativeFirst("https://example.invalid/film.mp4?token=film.vvc"))
        assertTrue(PlaybackEnginePolicy.nativeFirst("https://example.invalid/film.vvc?token=film.mp4"))
    }
    @Test fun secureSupportedTransportsAreAllowedAndUseCompatibilityImmediately() {
        for(uri in listOf("rtmps://example.invalid/live","srtp://239.1.1.1:5004")) {
            assertTrue(ExternalMedia.allowed(uri));assertTrue(PlaybackEnginePolicy.nativeFirst(uri))
        }
        for(uri in listOf("pipe://0","concat:file","unix:///tmp/socket","file:///etc/passwd","rtmps://host:99999/live"))assertFalse(ExternalMedia.allowed(uri))
    }
    @Test fun fallbackPreservesStandardOnlyAudioAndProtectedTracks() {
        for(mime in listOf("audio/ac4","audio/mhm1","audio/mha1","audio/iamf"))assertFalse(PlaybackEnginePolicy.preservesAudio(mime))
        assertTrue(PlaybackEnginePolicy.preservesAudio("audio/eac3"))
        assertFalse(PlaybackEnginePolicy.preservesAudio("audio/aac",protected=true))
    }
}
