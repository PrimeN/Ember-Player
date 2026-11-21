package app.ember.tv.player

/** Automatic choices belong to one item; explicit session choices are kept separately. */
internal class DecoderRecovery {
    private val triedRoutes = linkedSetOf<PlaybackRoute>()

    fun reset() = triedRoutes.clear()

    fun attempt(route: PlaybackRoute, manual: Boolean = false): Boolean {
        val firstAttempt = triedRoutes.size < 12 && triedRoutes.add(route)
        return manual || firstAttempt
    }
}
object DolbyVisionPolicy {
    /** DV5 has no HDR10 base. DV8 requires explicit HDR10 static metadata. */
    fun hdr10Base(codecs: String?, hdr10Static: Boolean): Boolean {
        val profile=Regex("(?i)(?:dvhe|dvh1)\\.(\\d{2})\\.").find(codecs.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return profile==7 || profile==8 && hdr10Static
    }
}
