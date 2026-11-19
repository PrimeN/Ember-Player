package app.ember.tv.player

import android.view.Display
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

internal enum class HdrSignal { SDR, HDR10, HDR10_PLUS, HLG, DOLBY_VISION, UNKNOWN }
internal enum class HdrConfidence { SOURCE_UNKNOWN, SOURCE_IDENTIFIED, OUTPUT_CONFIGURED }
internal data class HdrPlaybackState(val input:HdrSignal,val requestedOutput:HdrSignal,val displaySupportsOutput:Boolean?,
    val configuredDataspace:Int?,val renderer:String,val toneMappingActive:Boolean,val confidence:HdrConfidence)

/** Dataspace read-back confirms configuration, never a panel mode or dynamic-metadata path. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object HdrOutputValidator {
    fun source(format:Format?,dynamicHdr10:Boolean=false):HdrSignal = when {
        format==null->HdrSignal.UNKNOWN
        format.sampleMimeType==MimeTypes.VIDEO_DOLBY_VISION || format.codecs?.contains(Regex("(?i)(dvhe|dvh1|dvav|dva1)\\."))==true->HdrSignal.DOLBY_VISION
        format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_ST2084->if(dynamicHdr10)HdrSignal.HDR10_PLUS else HdrSignal.HDR10
        format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_HLG->HdrSignal.HLG
        format.colorInfo?.colorTransfer==C.COLOR_TRANSFER_SDR->HdrSignal.SDR
        else->HdrSignal.UNKNOWN
    }
    fun state(input:HdrSignal,requested:HdrSignal,display:HdrDisplayInfo,renderer:String,surface:HdrSurfaceResult?,toneMapping:Boolean):HdrPlaybackState {
        val supported=if(display.types.isEmpty() && requested!=HdrSignal.SDR)null else when(requested) {
            HdrSignal.SDR->true
            HdrSignal.HDR10->display.pq
            HdrSignal.HDR10_PLUS->Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in display.types
            HdrSignal.HLG->Display.HdrCapabilities.HDR_TYPE_HLG in display.types
            HdrSignal.DOLBY_VISION->Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in display.types
            HdrSignal.UNKNOWN->null
        }
        return HdrPlaybackState(input,requested,supported,surface?.dataspace,renderer,toneMapping,
            if(surface?.configured==true)HdrConfidence.OUTPUT_CONFIGURED else if(input!=HdrSignal.UNKNOWN)HdrConfidence.SOURCE_IDENTIFIED else HdrConfidence.SOURCE_UNKNOWN)
    }
}
