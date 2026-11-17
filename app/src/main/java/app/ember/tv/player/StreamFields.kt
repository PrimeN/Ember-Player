package app.ember.tv.player

import app.ember.tv.R

internal data class StreamField(@androidx.annotation.StringRes val label:Int,val unit:String,val suggested:List<Int>,val read:(StreamConfig)->Int,val write:(StreamConfig,Int)->StreamConfig) {
    fun choices(p:StreamConfig)=(suggested+read(p.normalized())).distinct().sorted()
}
internal val timeoutFields=listOf(
    StreamField(R.string.ui_connect_timeout,"s",listOf(5,10,15,20,30,60,120),{it.connectSeconds},{p,v->p.copy(connectSeconds=v)}),
    StreamField(R.string.ui_read_timeout,"s",listOf(5,10,20,30,60,90,120,180,300),{it.readSeconds},{p,v->p.copy(readSeconds=v)}))
internal val bufferFields=listOf(
    StreamField(R.string.ui_forward_buffer,"MiB",listOf(8,16,24,32,48,64,96,128,192,256,384,512),{it.forwardMiB},{p,v->p.copy(forwardMiB=v)}),
    StreamField(R.string.ui_rewind_buffer_compatibility,"MiB",listOf(0,4,8,16,24,32,48,64,96,128),{it.rewindMiB},{p,v->p.copy(rewindMiB=v)}),
    StreamField(R.string.ui_buffer_ahead,"s",listOf(10,20,30,60,120,180,240,300,600),{it.aheadSeconds},{p,v->p.copy(aheadSeconds=v)}),
    StreamField(R.string.ui_refill_before_resuming,"s",listOf(1,2,3,5,10,15,20,30),{it.refillSeconds},{p,v->p.copy(refillSeconds=v)}),
    StreamField(R.string.ui_read_ahead_target,"s",listOf(0,1,5,10,20,30,60,120,180,300),{it.readAheadSeconds},{p,v->p.copy(readAheadSeconds=v)}))
