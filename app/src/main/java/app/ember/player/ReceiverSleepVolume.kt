package app.ember.player

/** Receiver status can lag a volume command. Never mistake that echo for a new unfaded level. */
internal class ReceiverSleepVolume {
    private var original:Double?=null
    fun level(current:Double,gain:Float):Double {
        val factor=gain.coerceIn(0f,1f).toDouble()
        if(factor<1.0&&original==null)original=current.coerceIn(0.0,1.0)
        val base=original ?: current.coerceIn(0.0,1.0)
        if(factor>=1.0&&kotlin.math.abs(current-base)<.0001)original=null
        return base*factor
    }
    fun stopAndRestore(allowed:()->Boolean,stop:((Boolean)->Unit)->Unit,restore:(Double,(Boolean)->Unit)->Unit,close:()->Unit) {
        val base=original
        if(base==null){if(allowed())close();return}
        stop {stopped->
            if(allowed()) {
                if(stopped)restore(base){restored->if(allowed()){if(restored)original=null;close()}}
                else close()
            }
        }
    }
}
