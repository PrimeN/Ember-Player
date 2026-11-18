package app.ember.tv.player

import android.view.Window

/** Brightness follows the playback session without changing the system setting or saving to disk. */
internal object SessionBrightness {
    @Volatile private var value:Float?=null
    fun restore(window:Window){value?.let {level->window.attributes=window.attributes.apply {screenBrightness=level}}}
    fun set(window:Window,level:Float) {
        if(!level.isFinite())return
        value=level.coerceIn(.01f,1f)
        restore(window)
    }
}
