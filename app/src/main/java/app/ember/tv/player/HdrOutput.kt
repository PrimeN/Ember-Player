package app.ember.tv.player

import kotlinx.serialization.Serializable

/** A fixed, supported vocabulary for the bundled gpu renderer; no arbitrary native options. */
@Serializable
data class HdrOutput(
    val sdr:Boolean=false,
    val tone:String="bt.2390",
    val gamut:String="desaturate",
    val peak:Int=100,
    val analyzePeak:Boolean=false,
) {
    fun normalized()=copy(tone=tone.takeIf {v->tones.any {it.first==v}} ?: "bt.2390",
        gamut=gamut.takeIf {v->gamuts.any {it.first==v}} ?: "desaturate",peak=peak.coerceIn(50,500))
    fun options(hdr:Boolean,bypassed:Boolean=false):Map<String,String> {
        val p=normalized();val active=p.sdr&&hdr&&!bypassed
        return linkedMapOf("target-prim" to if(active)"bt.709" else "auto",
            "target-trc" to if(active)"bt.1886" else "auto","target-peak" to if(active)p.peak.toString() else "auto",
            "tone-mapping" to if(active)p.tone else "auto","gamut-mapping-mode" to if(active)p.gamut else "auto",
            "hdr-compute-peak" to if(active){if(p.analyzePeak)"yes" else "no"} else "auto")
    }
    companion object {
        val tones=listOf("bt.2390" to "BT.2390","reinhard" to "Reinhard","hable" to "Hable","mobius" to "Mobius","gamma" to "Gamma","clip" to "Clip highlights")
        // The gpu renderer implements desaturation and hard clipping. Other modes require gpu-next.
        val gamuts=listOf("desaturate" to "Preserve hue / reduce saturation","clip" to "Clip out-of-range colours")
    }
}
