package app.ember.tv.player

/** The current route delay follows a backend switch as well as user timing adjustments. */
internal class AudioTiming(private val delay:Playback.AudioDelay,private val native:(Long)->Unit) {
    fun set(milliseconds:Long){delay.us=milliseconds*1000;backendChanged()}
    fun backendChanged(){native(delay.us/1000)}
}
