package app.ember.tv.player

import app.ember.tv.R

import androidx.media3.common.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object HdrDiagnostics {
    private val dolbyCodec=Regex("(?:dvhe|dvh1|dvav|dva1)\\.(\\d{2})\\.(\\d{2})",RegexOption.IGNORE_CASE)
    fun isSdrTransfer(value:String?)=value in setOf("bt.1886","srgb","linear","gamma1.8","gamma2.0","gamma2.2","gamma2.4","gamma2.6","gamma2.8","prophoto")
    fun source(resources:android.content.res.Resources,format:Format?):String {
        format?:return resources.getString(R.string.ui_not_reported)
        val dv=dolbyCodec.find(format.codecs.orEmpty())
        if(dv!=null)return resources.getString(R.string.ui_dolby_vision_profile_level ,(dv.groupValues[1].toInt()).toString(),(dv.groupValues[2].toInt()).toString())
        return when {format.sampleMimeType==MimeTypes.VIDEO_DOLBY_VISION->resources.getString(R.string.ui_dolby_vision_profile_not_reported)
            format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_ST2084->resources.getString(R.string.ui_pq_hdr10_family_input)
            format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_HLG->resources.getString(R.string.ui_hlg_input)
            format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_SDR->resources.getString(R.string.ui_sdr_input)
            else->resources.getString(R.string.ui_transfer_not_reported)}
    }
    fun requested(resources:android.content.res.Resources,value:HdrOutput)=when {value.sdr->resources.getString(R.string.ui_hdr_to_sdr);value.expand->resources.getString(R.string.ui_sdr_to_hdr_experimental);else->resources.getString(R.string.ui_automatic_source_driven)}
}
