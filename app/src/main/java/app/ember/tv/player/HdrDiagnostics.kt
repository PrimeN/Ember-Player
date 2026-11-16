package app.ember.tv.player

import androidx.media3.common.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object HdrDiagnostics {
    private val dolbyCodec=Regex("(?:dvhe|dvh1|dvav|dva1)\\.(\\d{2})\\.(\\d{2})",RegexOption.IGNORE_CASE)
    fun isSdrTransfer(value:String?)=value in setOf("bt.1886","srgb","linear","gamma1.8","gamma2.0","gamma2.2","gamma2.4","gamma2.6","gamma2.8","prophoto")
    fun source(format:Format?):String {
        format?:return "Not reported"
        val dv=dolbyCodec.find(format.codecs.orEmpty())
        if(dv!=null)return "Dolby Vision profile ${dv.groupValues[1].toInt()} · level ${dv.groupValues[2].toInt()}"
        return when {format.sampleMimeType==MimeTypes.VIDEO_DOLBY_VISION->"Dolby Vision · profile not reported"
            format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_ST2084->"PQ / HDR10-family input"
            format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_HLG->"HLG input"
            format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_SDR->"SDR input"
            else->"Transfer not reported"}
    }
    fun requested(value:HdrOutput)=when {value.sdr->"HDR to SDR";value.expand->"SDR to HDR · experimental";else->"Automatic / source-driven"}
}
