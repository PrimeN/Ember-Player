package app.ember.player

import android.os.Handler
import android.os.Looper
import app.ember.tv.player.MediaQueues
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

internal data class CastRequest(val source:String,val title:String,val key:String,val headers:Map<String,String>,val position:Long,val duration:Long,val live:Boolean,val queueLease:Closeable?,val dlna:app.ember.tv.player.DlnaReceiver?=null,val profile:String="main"):Closeable {
    override fun close(){queueLease?.close()}
}
internal object CastRequests {
    private val values=ConcurrentHashMap<String,CastRequest>()
    private val handler=Handler(Looper.getMainLooper())
    fun stage(value:CastRequest):String=java.util.UUID.randomUUID().toString().also {id->values[id]=value;handler.postDelayed({values.remove(id)?.close()},120000)}
    fun take(id:String?)=id?.let(values::remove)
    fun discard(id:String?){take(id)?.close()}
}
