package app.ember.tv.player

import android.os.Handler

internal class SubtitleTicker(private val handler:Handler,private val enabled:()->Boolean,
    private val periodic:()->Boolean,private val render:()->Unit):Runnable {
    override fun run() {
        if(!enabled())return
        render()
        if(periodic())handler.postDelayed(this,100)
    }
    fun refresh(){handler.removeCallbacks(this);run()}
}
