package app.ember.tv.player

import android.content.Context
import android.provider.Settings
import app.ember.tv.data.SubtitleConfig

/** Android caption preferences override app fonts, colours and authored styling. */
internal object SystemCaptionStyle {
    fun family(context:Context):String=Settings.Secure.getString(context.contentResolver,"accessibility_captioning_typeface")
        ?.trim()?.takeIf {it.isNotBlank()} ?: "sans-serif"
    fun apply(context:Context,c:SubtitleConfig,string:(String,String)->Unit,number:(String,Double)->Unit,flag:(String,Boolean)->Unit) {
            val manager=context.getSystemService(Context.CAPTIONING_SERVICE) as? android.view.accessibility.CaptioningManager
            manager?.let {
                val style=it.userStyle
                string("sub-fonts-dir","")
                string("sub-font",SystemCaptionStyle.family(context))
                fun color(value:Int)="#%08X".format(java.util.Locale.ROOT,value)
                string("sub-color",color(style.foregroundColor))
                string("sub-back-color",color(style.backgroundColor))
                string("sub-border-style",if(android.graphics.Color.alpha(style.backgroundColor)>0)"background-box" else "outline-and-shadow")
                string("sub-border-color",color(style.edgeColor))
                number("sub-border-size",if(style.edgeType==android.view.accessibility.CaptioningManager.CaptionStyle.EDGE_TYPE_OUTLINE)2.5 else 0.0)
                number("sub-shadow-offset",if(style.edgeType==android.view.accessibility.CaptioningManager.CaptionStyle.EDGE_TYPE_DROP_SHADOW)2.5 else 0.0)
                number("sub-font-size",38.0*it.fontScale*c.layout.scale*when(c.size){"small"->0.8;"large"->1.25;"xlarge"->1.5;else->1.0})
                flag("sub-bold",style.typeface?.isBold==true);flag("sub-italic",style.typeface?.isItalic==true)
            }
    }
}
