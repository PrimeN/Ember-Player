package app.ember.tv.player

import app.ember.tv.R

internal data class NativeDecoderChoice(val hardware:String,val light:Boolean,val hi10p:Boolean) {
    val options get()=linkedMapOf("hwdec" to hardware,"vd-lavc-skiploopfilter" to if(light)"nonref" else "default",
        "framedrop" to "vo","vd-lavc-threads" to "0")
}
internal object NativeDecoderProfile {
    val choices=listOf("auto" to R.string.ui_automatic,"direct" to R.string.ui_direct_hardware,"copy" to R.string.ui_hardware_with_copied_frames,
        "software" to R.string.ui_software_full_quality,"light" to R.string.ui_software_lighter_decoding)
    private val highBitDepth=Regex("(?:p|p0)(?:10|12|16)(?:le|be)?$")
    fun normalized(value:String)=value.takeIf {v->choices.any {it.first==v}} ?: "auto"
    fun hi10p(codec:String?,profile:String?,pixel:String?):Boolean {
        if(codec?.lowercase() !in setOf("h264","video/avc"))return false
        val p=profile.orEmpty().lowercase();val format=pixel.orEmpty().lowercase()
        return p.contains("high 10") || p.contains("high10") || p.contains("hi10") ||
            p.trim() in setOf("110","high 10 intra") ||
            highBitDepth.containsMatchIn(format)
    }
    fun shield(manufacturer:String?,model:String?)=manufacturer.orEmpty().equals("nvidia",true) && model.orEmpty().contains("shield",true)
    fun choose(mode:String,manual:Boolean?,rule:String,legacySoftware:Boolean,deinterlacing:String,
        shield:Boolean,hi10p:Boolean,allowHi10p:Boolean):NativeDecoderChoice {
        val selected=normalized(mode)
        val automaticHi10p=selected=="auto" && allowHi10p && shield && hi10p && manual!=false && rule!="hardware"
        val software=manual ?: when(rule){"software"->true;"hardware"->false;"auto"->automaticHi10p;else->
            selected in setOf("software","light") || selected=="auto"&&(legacySoftware || automaticHi10p)}
        val defaultDecoder=NativeDeinterlacing.decoder(software,deinterlacing)
        val hardware=when {
            software || NativeDeinterlacing.mode(deinterlacing)!="no"->defaultDecoder
            selected=="direct"->"mediacodec"
            selected=="copy"->"mediacodec-copy"
            else->defaultDecoder
        }
        return NativeDecoderChoice(hardware,software&&selected=="light",software&&automaticHi10p)
    }
}
