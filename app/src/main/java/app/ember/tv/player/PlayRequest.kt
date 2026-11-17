package app.ember.tv.player

import android.content.Intent

/** Internal playback extras; exported requests are sanitized by OpenMediaActivity. */
internal object PlayRequest {
    const val EXTRA_URL = "url"
    const val EXTRA_HEADER_NAMES = "headerNames"
    const val EXTRA_HEADER_VALUES = "headerValues"
    const val EXTRA_START_OVER = "startOver"
    const val EXTRA_TITLE = "title"
    const val EXTRA_KIND = "kind"
    const val EXTRA_IMDB = "imdb"
    const val EXTRA_TMDB = "tmdb"
    const val EXTRA_SEASON = "season"
    const val EXTRA_EPISODE = "episode"
    const val EXTRA_RESUME_KEY = "resumeKey"
    const val EXTRA_RESUME_FRACTION = "resumeFraction"
    const val EXTRA_RELEASE = "release"
    const val EXTRA_SERIES_NAME = "seriesName"
    fun headers(intent:Intent):Map<String,String> {
        val names=intent.getStringArrayExtra(EXTRA_HEADER_NAMES) ?: return emptyMap()
        val values=intent.getStringArrayExtra(EXTRA_HEADER_VALUES) ?: return emptyMap()
        return app.ember.tv.data.StreamHeaders.clean(names.zip(values))
    }
}
