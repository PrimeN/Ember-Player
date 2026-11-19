package app.ember.tv

import app.ember.tv.player.*
import androidx.media3.common.PlaybackException as E
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackFailureClassifierTest {
    @Test fun externalFailuresNeverChangeDecoderOrCore() {
        for(code in listOf(E.ERROR_CODE_IO_BAD_HTTP_STATUS,E.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            E.ERROR_CODE_DRM_LICENSE_EXPIRED,E.ERROR_CODE_IO_FILE_NOT_FOUND,E.ERROR_CODE_AUTHENTICATION_EXPIRED)) {
            val failure=PlaybackFailureClassifier.classify(code)
            assertFalse(failure.changesDecoder);assertFalse(failure.changesCore)
            assertFalse(PlaybackEnginePolicy.compatibilityError(code))
        }
    }
    @Test fun outputAndGpuFailuresHaveTheirOwnRecoveryWithoutChangingCores() {
        for(code in listOf(E.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,E.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
            E.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,E.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED)) {
            assertEquals(PlaybackFailure.AUDIO_OUTPUT,PlaybackFailureClassifier.classify(code))
            assertFalse(PlaybackEnginePolicy.compatibilityError(code))
        }
        assertEquals(PlaybackFailure.GPU_RENDERER,PlaybackFailureClassifier.classify(E.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED))
        assertEquals(PlaybackFailure.AUDIO_DECODER,PlaybackFailureClassifier.classify(E.ERROR_CODE_DECODER_INIT_FAILED,"audio/aac"))
        assertEquals(PlaybackFailure.VIDEO_DECODER_INIT,PlaybackFailureClassifier.classify(E.ERROR_CODE_DECODER_INIT_FAILED,"video/avc"))
        assertTrue(PlaybackEnginePolicy.compatibilityError(E.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
    }
    @Test fun routesAreBoundedPerItemAndExplicitModesAreNeverReplaced() {
        val recovery=DecoderRecovery()
        val first=PlaybackRoute(PlaybackCore.MEDIA3,"hardware.avc","platform.aac","pcm")
        assertTrue(recovery.attempt(first));assertFalse(recovery.attempt(first))
        assertTrue(recovery.attempt(first.copy(audioDecoder="ffmpeg.aac")))
        repeat(10){assertTrue(recovery.attempt(first.copy(videoDecoder="codec$it")))}
        assertFalse(recovery.attempt(first.copy(output="new")))
        recovery.reset();assertTrue(recovery.attempt(first))
        assertNull(recovery.next(false,true,manual=true))
        assertTrue(PlaybackRoutePlanner.fallbackModes(false,true,false).isEmpty())
        assertFalse(PlaybackRoutePlanner.canSwitchCore(PlaybackFailure.EXTRACTOR,false,true))
        assertFalse(PlaybackRoutePlanner.canSwitchCore(PlaybackFailure.AUDIO_DECODER,true,false))
    }
    @Test fun droppedFramesAndMissingEvidenceDoNotBecomeSuccessfulHints() {
        assertFalse(PlaybackQuality().learnable(true))
        assertFalse(PlaybackQuality(renderedFrames=119,droppedFrames=0).learnable(true))
        assertFalse(PlaybackQuality(renderedFrames=900,droppedFrames=100).learnable(true))
        assertTrue(PlaybackQuality(renderedFrames=980,droppedFrames=20).learnable(true))
        assertTrue(PlaybackQuality().learnable(false))
    }
    @Test fun oldDecoderRecordsStillDecodeWithOptionalQualityFields() {
        val old=Json.decodeFromString<DecoderHealthDocument>("""{"environment":"same","entries":{"avc":{"name":"platform.avc","savedAt":10}}}""")
        assertNull(old.entries.getValue("avc").renderedFrames)
        val memory=DecoderHealthMemory("same",old){11}
        assertEquals("platform.avc",memory.get("avc")?.name)
    }
}
