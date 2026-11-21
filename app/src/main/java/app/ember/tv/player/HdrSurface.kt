package app.ember.tv.player

import app.ember.tv.R

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.annotation.Keep

internal data class HdrDisplayInfo(val pq:Boolean,val summary:String,val types:Set<Int> = emptySet()) {
    companion object {
        fun read(context:Context):HdrDisplayInfo=runCatching {
            val display=if(context is android.app.Activity && android.os.Build.VERSION.SDK_INT>=30)context.display
                else (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val types=display?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            HdrDisplayInfo(types.any {it==Display.HdrCapabilities.HDR_TYPE_HDR10 || it==Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS},
                types.joinToString {when(it){1->"Dolby Vision";2->"HDR10";3->"HLG";4->"HDR10+";else->context.getString(R.string.ui_hdr_type,it.toString())}}.ifBlank {context.getString(R.string.ui_sdr_hdr_not_reported)},types.toSet())
        }.getOrDefault(HdrDisplayInfo(false,context.getString(R.string.ui_display_capabilities_unavailable)))
    }
}
@Keep
internal object HdrSurface {
    const val BT2020_PQ=0x09C60000
    val available by lazy {runCatching {System.loadLibrary("ember_surface");true}.getOrDefault(false)}
    private external fun readSpace(surface:Surface):Int
    private external fun writeSpace(surface:Surface,space:Int):Boolean
    fun read(surface:Surface)=if(available&&surface.isValid)runCatching {readSpace(surface)}.getOrDefault(-1) else -1
    fun write(surface:Surface,space:Int)=available&&surface.isValid&&runCatching {writeSpace(surface,space)}.getOrDefault(false)
}
/** A surface's original data space is restored before it is detached or comparison ends. */
internal enum class HdrSurfaceStatus { ORIGINAL, BRIDGE_UNAVAILABLE, INVALID_SURFACE, ORIGINAL_UNREADABLE, WRITE_FAILED, READBACK_MISMATCH, CONFIGURED, RESTORED, RESTORE_FAILED, RELEASED }
internal data class HdrSurfaceResult(
    val status: HdrSurfaceStatus,
    val dataspace: Int? = null,
    val expectedDataspace: Int? = null,
) {
    val configured get()=status==HdrSurfaceStatus.CONFIGURED
}
internal class HdrSurfaceBinding(private val read:(Surface)->Int=HdrSurface::read,private val write:(Surface,Int)->Boolean=HdrSurface::write,private val valid:(Surface)->Boolean={it.isValid},private val available:()->Boolean={true}) {
    private var surface:Surface?=null
    private var original:Int?=null
    private var closed=false
    var result=HdrSurfaceResult(HdrSurfaceStatus.ORIGINAL);private set
    var restoreFailure: HdrSurfaceResult? = null
        private set
    fun signal(value:Surface?):Boolean=signalResult(value).configured
    fun signalResult(value:Surface?):HdrSurfaceResult {
        if(closed)return HdrSurfaceResult(HdrSurfaceStatus.RELEASED)
        if(!available())return HdrSurfaceResult(HdrSurfaceStatus.BRIDGE_UNAVAILABLE).also {result=it}
        if(value==null || !valid(value))return HdrSurfaceResult(HdrSurfaceStatus.INVALID_SURFACE).also {result=it}
        if(surface!==value){
            if(surface != null && restore().status==HdrSurfaceStatus.RESTORE_FAILED && surface != null)return result
            val current=read(value)
            if(current<0)return HdrSurfaceResult(HdrSurfaceStatus.ORIGINAL_UNREADABLE).also {result=it}
            surface=value;original=current
        }
        val written=write(value,HdrSurface.BT2020_PQ)
        val actual=if(written)read(value).takeIf {it>=0} else null
        if(written && actual==HdrSurface.BT2020_PQ)return HdrSurfaceResult(HdrSurfaceStatus.CONFIGURED,actual).also {result=it}
        val failure=HdrSurfaceResult(if(written)HdrSurfaceStatus.READBACK_MISMATCH else HdrSurfaceStatus.WRITE_FAILED,actual)
        // Some bridges change the native window even when their call reports failure.
        if(restore().status!=HdrSurfaceStatus.RESTORE_FAILED)result=failure
        return result
    }
    fun restore(): HdrSurfaceResult {
        if (closed) return HdrSurfaceResult(HdrSurfaceStatus.RELEASED)
        val target = surface ?: return result
        val space = original ?: return result
        val usable = valid(target)
        val written = usable && write(target, space)
        val actual = if (usable) read(target).takeIf { it >= 0 } else null
        if (!written || actual != space) {
            if (!usable) {
                surface = null
                original = null
            }
            return HdrSurfaceResult(HdrSurfaceStatus.RESTORE_FAILED, actual, space).also {
                restoreFailure = it
                result = it
            }
        }
        surface = null
        original = null
        return HdrSurfaceResult(HdrSurfaceStatus.RESTORED, space).also { result = it }
    }

    fun detach() {
        if (closed) return
        restore()
        surface = null
        original = null
        result = HdrSurfaceResult(HdrSurfaceStatus.ORIGINAL)
    }

    fun close() {
        if (closed) return
        detach()
        closed = true
        result = HdrSurfaceResult(HdrSurfaceStatus.RELEASED)
    }
}
