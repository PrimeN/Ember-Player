package app.ember.tv.player

import android.os.Handler

internal class SubtitleTicker(private val handler:Handler,private val enabled:()->Boolean,
    private val periodic:()->Boolean,private val intervalMs:()->Long={100L},private val render:()->Unit):Runnable {
    override fun run() {
        if(!enabled())return
        render()
        if(periodic())handler.postDelayed(this,intervalMs())
    }
    fun refresh(){handler.removeCallbacks(this);run()}
}
