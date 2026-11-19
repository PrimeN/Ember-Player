package app.ember.tv.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException

internal enum class PlaybackFailure(val changesDecoder: Boolean = false, val changesCore: Boolean = false) {
    VIDEO_DECODER_INIT(true, true), VIDEO_DECODER_RUNTIME(true, true), AUDIO_DECODER(true, true),
    AUDIO_OUTPUT, UNSUPPORTED_FORMAT(true, true), EXTRACTOR(changesCore = true),
    NETWORK, AUTHENTICATION, DRM, GPU_RENDERER, UNKNOWN,
}

/** Classify codes and typed causes only. Error messages can contain private stream addresses. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object PlaybackFailureClassifier {
    fun classify(error: PlaybackException): PlaybackFailure = classify(error.errorCode,
        (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType)

    fun classify(code: Int, mime: String? = null): PlaybackFailure = when (code) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
            if (MimeTypes.isAudio(mime)) PlaybackFailure.AUDIO_DECODER else PlaybackFailure.VIDEO_DECODER_INIT
        PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED ->
            if (MimeTypes.isAudio(mime)) PlaybackFailure.AUDIO_DECODER else PlaybackFailure.VIDEO_DECODER_RUNTIME
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackFailure.UNSUPPORTED_FORMAT
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackFailure.EXTRACTOR
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED, PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> PlaybackFailure.AUDIO_OUTPUT
        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED, PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED -> PlaybackFailure.GPU_RENDERER
        in PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> PlaybackFailure.DRM
        PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED, PlaybackException.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackFailure.AUTHENTICATION
        in 2000..2999, PlaybackException.ERROR_CODE_TIMEOUT, PlaybackException.ERROR_CODE_DISCONNECTED -> PlaybackFailure.NETWORK
        else -> PlaybackFailure.UNKNOWN
    }
}
