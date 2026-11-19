package app.ember.tv.player

/** Automatic choices belong to one item; explicit session choices are kept separately. */
internal class DecoderRecovery {
    private val triedVideo = linkedSetOf<String>()
    private val triedAudio = linkedSetOf<String>()
    private val triedRoutes = linkedSetOf<PlaybackRoute>()
    var pcm = false; private set
    fun reset() { triedVideo.clear(); triedAudio.clear(); triedRoutes.clear(); pcm = false }
    fun attempt(route: PlaybackRoute): Boolean = triedRoutes.size < 12 && triedRoutes.add(route)
    fun pcmFallback(): Boolean { if(pcm)return false;pcm=true;return true }
    fun next(audio: Boolean, ffmpeg: Boolean, softwareAllowed: Boolean = true, manual: Boolean = false): String? {
        val tried=if(audio)triedAudio else triedVideo
        return PlaybackRoutePlanner.fallbackModes(softwareAllowed,ffmpeg,manual).firstOrNull { tried.add(it) }
    }
}
object DolbyVisionPolicy {
    /** DV5 has no HDR10 base. DV8 requires explicit HDR10 static metadata. */
    fun hdr10Base(codecs: String?, hdr10Static: Boolean): Boolean {
        val profile=Regex("(?i)(?:dvhe|dvh1)\\.(\\d{2})\\.").find(codecs.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return profile==7 || profile==8 && hdr10Static
    }
}
