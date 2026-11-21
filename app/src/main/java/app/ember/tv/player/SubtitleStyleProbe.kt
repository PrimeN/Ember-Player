package app.ember.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format

internal object SubtitleStyleProbe {
    private val tags=Regex("\\\\(?:pos|move|k[fo]?|t|i?clip|fade?|fr[xyz]?|fn|an|p)(?=[(0-9+\\-])",RegexOption.IGNORE_CASE)
    fun styled(text:String):Boolean {
        // Input files are bounded by SubtitleFiles; cap probing separately on the UI path.
        val sample=text.take(512*1024)
        if(tags.containsMatchIn(sample) || Regex("""\\fn[^\\{}\r\n]+""",RegexOption.IGNORE_CASE).containsMatchIn(sample))return true
        return sample.lineSequence().filter {it.trimStart().startsWith("Style:",true)}.any {line->
            val fields=line.substringAfter(':').split(',',limit=4)
            fields.size>=2 && (fields[0].trim().lowercase()!="default" || fields[1].trim().lowercase() !in setOf("arial","sans-serif","roboto"))
        }
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun routeToMpv(automatic:Boolean,authoredStyles:Boolean,systemStyle:Boolean,styled:Boolean,video:Format?,audio:Format?):Boolean =
        automatic && authoredStyles && !systemStyle && styled && video!=null &&
            video.colorInfo?.colorTransfer==C.COLOR_TRANSFER_SDR && video.drmInitData==null &&
            HdrOutputValidator.source(video)==HdrSignal.SDR && PlaybackEnginePolicy.preservesAudio(audio?.sampleMimeType,audio?.drmInitData!=null)
}
