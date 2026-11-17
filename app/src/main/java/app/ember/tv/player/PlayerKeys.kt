package app.ember.tv.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.ember.tv.player.Kind

@OptIn(UnstableApi::class)
/** Show or local title identity for remembered audio/subtitle choices. */
internal fun PlayerActivity.showKey(): String? {
    if(fileQueue!=null&&config.rememberTracks)return resumeKey
    return if (!config.rememberTracks || !itemInitialized || item.kind == Kind.MOVIE) null
    else (item.imdbId ?: seriesName).takeIf { it.isNotBlank() }
}
