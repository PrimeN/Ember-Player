package app.ember.tv.player

internal data class NativeEdition(val index:Int,val title:String,val selected:Boolean)

/** mpv maps MPEG-TS programs and HLS variants to zero-based editions. */
internal object NativeEditions {
    fun read(count:Int,selected:Int?,title:(Int)->String?,fallback:(Int)->String):List<NativeEdition> =
        (0 until count.coerceIn(0,256)).map {index->
            NativeEdition(index,title(index)?.filterNot(Char::isISOControl)?.trim()?.take(180)?.takeIf {it.isNotEmpty()} ?: fallback(index+1),index==selected)
        }
    fun resumePosition(position:Long,seekable:Boolean,live:Boolean)=if(seekable&&!live)position.coerceAtLeast(0) else 0L
}

/** Some editions reload the file; HLS/TS program changes can complete in place. */
internal class NativeEditionRestore {
    private var pending:Long?=null
    fun request(position:Long,seekable:Boolean,live:Boolean){pending=NativeEditions.resumePosition(position,seekable,live)}
    fun takeOnRestart():Long?=pending.also {pending=null}
    fun selectedInPlace(){pending=null}
}
