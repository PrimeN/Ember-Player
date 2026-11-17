package app.ember.tv.player

import app.ember.tv.R

/** Names describe the input signal; a source label or display capability is not proof of HDR output. */
internal object PlaybackFormats {
    fun video(mime: String?, codecs: String?): String = when (mime) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/vvc", "video/h266" -> "VVC/H.266"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/avs3" -> "AVS3"
        "video/mpeg2" -> "MPEG-2"
        "video/dolby-vision" -> when {
            codecs.orEmpty().startsWith("dav1", true) -> "AV1"
            codecs.orEmpty().startsWith("dva", true) -> "H.264"
            else -> "HEVC"
        }
        else -> mime?.substringAfter('/') ?: "?"
    }

    fun dolbyVision(mime: String?, codecs: String?): Boolean = mime == "video/dolby-vision" ||
        listOf("dvhe", "dvh1", "dav1", "dvav", "dva1").any { codecs.orEmpty().startsWith(it, true) }

    fun hdr(mime: String?, codecs: String?, transfer: Int?, hdr10Plus: Boolean = false, unknown: Boolean = false): Int = when {
        dolbyVision(mime, codecs) -> R.string.ui_dolby_vision
        hdr10Plus -> R.string.ui_hdr10_metadata_observed
        transfer == 6 -> R.string.ui_hdr10_pq
        transfer == 7 -> R.string.ui_hlg
        unknown && transfer == null -> R.string.ui_hdr_not_reported
        else -> R.string.ui_sdr
    }

    fun audio(mime: String?): String? = when (mime) {
        "audio/ac4" -> "Dolby AC-4"
        "audio/mhm1", "audio/mha1", "audio/mpeg-h", "audio/mpegh_3d_audio" -> "MPEG-H 3D Audio"
        "audio/iamf" -> "IAMF"
        else -> null
    }

    fun nativeFirst(mime: String?, standardSupported: Boolean = false) = !standardSupported && mime in setOf("video/vvc", "video/h266")
}
