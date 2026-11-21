package app.ember.tv.player

/** Subtitle speed correction is independent of the video's playback rate. */
internal object SubtitleClock {
    fun timeUs(positionMs:Long,partOffsetMs:Long=0,speed:Float=1f,delayMs:Long=0):Long =
        (((positionMs.toDouble()-partOffsetMs).coerceAtLeast(0.0)*SubtitleTiming.rate(speed)-delayMs)*1000.0).toLong()
    // A negative result is intentional: a positive delay must not reveal a cue at time zero.
}
