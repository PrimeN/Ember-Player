package app.ember.tv.player

internal enum class PlaybackCore { MEDIA3, MPV }
/** Identities are decoder names or explicit selector modes, never media URLs or titles. */
internal data class PlaybackRoute(val core: PlaybackCore, val videoDecoder: String?, val audioDecoder: String?, val output: String)

/** Eligibility precedes preference; Media3's codec selector still checks actual device capabilities. */
internal object PlaybackRoutePlanner {
    fun fallbackModes(softwareAllowed: Boolean, ffmpegAllowed: Boolean, manual: Boolean): List<String> =
        if (manual || !softwareAllowed) emptyList() else listOfNotNull("software", "ffmpeg".takeIf { ffmpegAllowed })

    fun canSwitchCore(failure: PlaybackFailure, automatic: Boolean, audioEligible: Boolean) =
        automatic && audioEligible && failure.changesCore
}

internal data class PlaybackQuality(val startupMs: Long? = null, val renderedFrames: Int? = null, val droppedFrames: Int? = null) {
    /** Unknown video delivery is not evidence of a successful video route. */
    fun learnable(video: Boolean): Boolean {
        if (!video) return true
        val rendered = renderedFrames ?: return false
        val dropped = droppedFrames ?: return false
        return rendered >= 120 && dropped >= 0 && dropped.toLong() * 100 <= (rendered.toLong() + dropped) * 2
    }
}

internal data class FrameCount(val rendered: Int, val dropped: Int) {
    fun since(start: FrameCount): PlaybackQuality? {
        if (start.rendered < 0 || start.dropped < 0 || rendered < start.rendered || dropped < start.dropped) return null
        return PlaybackQuality(renderedFrames = rendered - start.rendered, droppedFrames = dropped - start.dropped)
    }
}

internal class PlaybackQualityWindow<T : Any>(
    private val decoder: T,
    private val startedAt: Long,
    private val baseline: FrameCount,
) {
    fun measure(currentDecoder: T, now: Long, current: FrameCount): PlaybackQuality? =
        if (decoder === currentDecoder && now - startedAt >= STABLE_PLAYBACK_MS) current.since(baseline) else null
}

internal const val STABLE_PLAYBACK_MS = 15_000L
