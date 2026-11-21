package app.ember.tv

import app.ember.tv.player.*
import android.net.Uri
import androidx.media3.common.PlaybackException as E
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlaybackFailureClassifierTest {
    @Test fun externalFailuresNeverChangeDecoderOrCore() {
        for(code in listOf(E.ERROR_CODE_IO_BAD_HTTP_STATUS,E.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            E.ERROR_CODE_DRM_LICENSE_EXPIRED,E.ERROR_CODE_IO_FILE_NOT_FOUND,E.ERROR_CODE_AUTHENTICATION_EXPIRED)) {
            val failure=PlaybackFailureClassifier.classify(code)
            assertFalse(failure.changesDecoder);assertFalse(failure.changesCore)
            assertFalse(PlaybackEnginePolicy.compatibilityError(code))
        }
    }
    @Test fun typedHttpStatusSeparatesRetryableAndPermanentFailures() {
        val cases = mapOf(
            401 to PlaybackFailure.AUTHENTICATION, 403 to PlaybackFailure.SOURCE_ACCESS,
            407 to PlaybackFailure.AUTHENTICATION, 404 to PlaybackFailure.SOURCE_MISSING,
            410 to PlaybackFailure.SOURCE_MISSING, 416 to PlaybackFailure.HTTP_REJECTED,
            408 to PlaybackFailure.NETWORK, 429 to PlaybackFailure.NETWORK,
            500 to PlaybackFailure.NETWORK, 502 to PlaybackFailure.NETWORK,
            503 to PlaybackFailure.NETWORK, 504 to PlaybackFailure.NETWORK,
            501 to PlaybackFailure.HTTP_REJECTED
        )
        for ((status, expected) in cases) {
            val cause = InvalidResponseCodeException(status, null, null, emptyMap(),
                DataSpec(Uri.EMPTY), byteArrayOf())
            val failure = PlaybackFailureClassifier.classify(E(null, IllegalStateException(cause), E.ERROR_CODE_IO_BAD_HTTP_STATUS))
            assertEquals("HTTP $status", expected, failure)
            assertFalse(failure.changesDecoder)
            assertFalse(failure.changesCore)
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
        assertTrue(PlaybackRoutePlanner.fallbackModes(true, true, manual = true).isEmpty())
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
