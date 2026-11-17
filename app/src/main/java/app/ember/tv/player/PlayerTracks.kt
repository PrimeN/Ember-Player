package app.ember.tv.player

import app.ember.tv.R
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@OptIn(UnstableApi::class)
/** Saves the audio language (always) and the subtitle choice (when picked by hand) for the show. */
internal fun PlayerActivity.rememberTracks() {
    if(privatePlayback || !app.ember.player.PrivateMedia.historyAllowed(currentUrl.orEmpty()))return
    val key = showKey() ?: return
    val p = player ?: return
    p.audioFormat?.language?.let { app.ember.tv.data.Subtitles.normalise(it) }
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { memory.putAudio(key, it) }
    if (!subsTouched || !subsInitialized) return
    when (val sel = subs.selection) {
        is SubtitleController.Selection.Manual -> Unit
        is SubtitleController.Selection.Off -> memory.putSubtitles(key, "off")
        is SubtitleController.Selection.External -> memory.putSubtitles(key, sel.candidate.lang)
        is SubtitleController.Selection.Embedded -> p.currentTracks.groups.getOrNull(sel.group)
            ?.getTrackFormat(sel.track)?.language?.let { app.ember.tv.data.Subtitles.normalise(it) }
            ?.takeIf { it.isNotBlank() && it != "und" }?.let { memory.putSubtitles(key, it) }
    }
}

@OptIn(UnstableApi::class)
/** Sound tracks for the Sound list: language, format and channels, with the one playing marked. */
internal fun PlayerActivity.audioTracks(): List<Pair<String, Boolean>> {
    val p = player ?: return emptyList()
    return p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it, true) }.map { i ->
            val f = g.getTrackFormat(i)
            val lang = f.language?.let { Locale.forLanguageTag(it).displayLanguage.takeIf { d -> d.isNotBlank() } ?: it }
            val label = listOfNotNull(lang, f.label, Playback.audioName(f), if (f.channelCount > 0) resources.getString(R.string.ui_ch ,f.channelCount.toString()) else null)
                .distinct().joinToString(" · ").ifBlank { getString(R.string.pl_track_n, i + 1) }
            label to g.isTrackSelected(i)
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerActivity.pickAudioTrack(index: Int) {
    val p = player ?: return
    var n = 0
    for (g in p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }) {
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i, true)) continue
            if (n == index) {
                audioPicked = true
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(g.mediaTrackGroup, i))
                    .build()
                return
            }
            n++
        }
    }
}
