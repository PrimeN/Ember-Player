package app.ember.tv.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.annotation.Keep

internal data class HdrDisplayInfo(val pq:Boolean,val summary:String) {
    companion object {
        fun read(context:Context):HdrDisplayInfo=runCatching {
            val display=if(context is android.app.Activity && android.os.Build.VERSION.SDK_INT>=30)context.display
                else (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val types=display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            HdrDisplayInfo(types.any {it==Display.HdrCapabilities.HDR_TYPE_HDR10 || it==Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS},
                types.joinToString {when(it){1->"Dolby Vision";2->"HDR10";3->"HLG";4->"HDR10+";else->"HDR type $it"}}.ifBlank {"SDR / HDR not reported"})
        }.getOrDefault(HdrDisplayInfo(false,"Display capabilities unavailable"))
    }
}
@Keep
internal object HdrSurface {
    const val BT2020_PQ=0x09C60000
    private val available by lazy {runCatching {System.loadLibrary("ember_surface");true}.getOrDefault(false)}
    private external fun readSpace(surface:Surface):Int
    private external fun writeSpace(surface:Surface,space:Int):Boolean
    fun read(surface:Surface)=if(available&&surface.isValid)runCatching {readSpace(surface)}.getOrDefault(-1) else -1
    fun write(surface:Surface,space:Int)=available&&surface.isValid&&runCatching {writeSpace(surface,space)}.getOrDefault(false)
}
/** A surface's original data space is restored before it is detached or comparison ends. */
internal class HdrSurfaceBinding(private val read:(Surface)->Int=HdrSurface::read,private val write:(Surface,Int)->Boolean=HdrSurface::write,private val valid:(Surface)->Boolean={it.isValid}) {
    private var surface:Surface?=null
    private var original:Int?=null
    fun signal(value:Surface?):Boolean {
        if(value==null || !valid(value))return false
        if(surface!==value){restore();val current=read(value);if(current<0)return false;surface=value;original=current}
        return write(value,HdrSurface.BT2020_PQ)
    }
    fun restore() {val target=surface;val space=original;if(target!=null&&space!=null)write(target,space);surface=null;original=null}
}
