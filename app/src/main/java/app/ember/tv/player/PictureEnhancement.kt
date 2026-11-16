package app.ember.tv.player

import kotlinx.serialization.Serializable

@Serializable
data class PictureEnhancement(
    val enabled:Boolean=true,
    val upscaling:Boolean=true,
    val shader:Boolean=true,
    val brightness:Int=0,
    val contrast:Int=0,
    val saturation:Int=0,
    val gamma:Int=0,
    val hue:Int=0,
    val sharpness:Int=0,
) {
    fun normalized()=copy(brightness=brightness.coerceIn(-50,50),contrast=contrast.coerceIn(-50,50),
        saturation=saturation.coerceIn(-100,100),gamma=gamma.coerceIn(-50,50),hue=hue.coerceIn(-100,100),sharpness=sharpness.coerceIn(0,100))
    companion object {
        val presets=listOf("Neutral","Gentle contrast","Soft picture","Vivid")
        fun preset(index:Int)=when(index){
            1->PictureEnhancement(contrast=8,saturation=4)
            2->PictureEnhancement(contrast=-8,saturation=-5,sharpness=0)
            3->PictureEnhancement(contrast=12,saturation=15,sharpness=10)
            else->PictureEnhancement()
        }
    }
}

/** Identical ranges in persistent settings and the in-player picture menu. */
internal data class PictureAdjustment(val label:String,val minimum:Int,val maximum:Int,
    val read:(PictureEnhancement)->Int,val write:(PictureEnhancement,Int)->PictureEnhancement) {
    fun values(p:PictureEnhancement)=((minimum..maximum step 5).toList()+read(p.normalized())).distinct().sorted()
}
internal val pictureAdjustments=listOf(
    PictureAdjustment("Brightness",-50,50,{it.brightness},{p,v->p.copy(brightness=v)}),
    PictureAdjustment("Contrast",-50,50,{it.contrast},{p,v->p.copy(contrast=v)}),
    PictureAdjustment("Saturation",-100,100,{it.saturation},{p,v->p.copy(saturation=v)}),
    PictureAdjustment("Gamma",-50,50,{it.gamma},{p,v->p.copy(gamma=v)}),
    PictureAdjustment("Hue",-100,100,{it.hue},{p,v->p.copy(hue=v)}),
    PictureAdjustment("Sharpness",0,100,{it.sharpness},{p,v->p.copy(sharpness=v)}),
)
