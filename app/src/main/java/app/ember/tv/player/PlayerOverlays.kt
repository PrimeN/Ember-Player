package app.ember.tv.player

import app.ember.tv.R
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds

// PlayerActivity's skip-intro markers, sound timing, info panel and subtitle panel.
/** Reset per-title chapter markers and pending metadata. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.loadMarkers() {
    metadataLoads.reset();markers=emptyList();markerChapterState=null
    autoSkipped.clear();showSkip(null);chapters=emptyList()
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.checkMarkers() {
    val p = player ?: return
    if (subsInitialized && p.playbackState==Player.STATE_READY) subs.onReady()
    val state=chapters to p.duration
    if(markerChapterState!=state){markers=ChapterMarkers.from(chapters,p.duration);markerChapterState=state}
    if (markers.isEmpty() || ended || config.skipIntro == "off") return showSkip(null)
    val pos = p.currentPosition
    val eligible = markers
    val m = eligible.firstOrNull { pos >= it.startMs && pos < it.endMs - 1_000 }
    if (m != null && m.kind != "credits" && config.skipIntro == "auto" && m !in autoSkipped) {
        autoSkipped += m
        p.seekTo(m.endMs)
        Toast.makeText(this, if (m.kind == "recap") getString(R.string.pl_skipped_the_recap) else getString(R.string.pl_skipped_the_intro), Toast.LENGTH_SHORT).show()
        return showSkip(null)
    }
    showSkip(m)
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.showSkip(m: app.ember.tv.player.Marker?) {
    if (!skipButtonInitialized) return
    skipTarget = m
    if (advanced) {
        // The advanced controls draw their own pill.
        skipButton.visibility = View.GONE
        controls.skipLabel = m?.let {
            when (it.kind) {
                "recap" -> getString(R.string.pl_skip_recap)
                "credits" -> getString(R.string.pl_skip_credits)
                else -> getString(R.string.ui_skip_intro)
            }
        }
        return
    }
    if (m == null) {
        skipButton.visibility = View.GONE
        return
    }
    skipButton.text = when (m.kind) {
        "recap" -> getString(R.string.pl_action_arrow, getString(R.string.pl_skip_recap))
        "credits" -> getString(R.string.pl_action_arrow, getString(R.string.pl_skip_credits))
        else -> getString(R.string.pl_action_arrow, getString(R.string.ui_skip_intro))
    }
    skipButton.visibility = View.VISIBLE
}

/** Jump past the selected intro, recap or credits marker. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.doSkip() {
    val p = player ?: return
    val m = skipTarget ?: return
    p.seekTo(m.endMs)
    showSkip(null)
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.shiftSound(deltaMs: Int?) {
    val now = (audioDelay.us / 1000).toInt()
    val next = if (deltaMs == null) 0 else (now + deltaMs).coerceIn(-1000, 1000)
    audioTiming.set(next.toLong())
    updateSoundLabel()
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.keepSound() {
    val ms = (audioDelay.us / 1000).toInt()
    config = if (bluetoothOutput) config.copy(bluetoothDelayMs = ms) else config.copy(audioDelayMs = ms)
    prefs.playbackConfig = config
    Toast.makeText(this, if(bluetoothOutput) resources.getString(R.string.ui_bluetooth_timing_saved) else getString(R.string.pl_sound_timing_saved_for_all_videos), Toast.LENGTH_SHORT).show()
    updateSoundLabel()
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.updateSoundLabel() {
    if (!soundLabelInitialized) return
    val ms = (audioDelay.us / 1000).toInt()
    val state = when {
        ms == 0 -> getString(R.string.pl_sound_in_step)
        ms > 0 -> getString(R.string.pl_sound_later, ms)
        else -> getString(R.string.pl_sound_earlier, -ms)
    }
    val timing = getString(R.string.pl_sound_timing, state)
    soundLabel.text = if (ms != config.outputDelay(bluetoothOutput)) getString(R.string.pl_label_pair, timing, getString(R.string.pl_this_video_only)) else timing
}

/** Switches the display to a refresh rate (and optionally resolution) that fits the video, once per title. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.matchDisplayMode(p: ExoPlayer) {
    val seamless=config.frameRate=="seamless" && playbackEngine?.compatibility!=null
    if ((config.frameRate != "always" && !seamless) || modeApplied) return
    val f = p.videoFormat ?: return
    modeApplied = true
    val display = if (android.os.Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
    display ?: return
    Playback.applyMode(this, Playback.modeFor(display, f.frameRate, f.width, f.height, config.matchResolution && !seamless,seamless))
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.infoText(): String {
    val p = player ?: return ""
    val display = if (android.os.Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
    val mode = display?.mode?.let { m -> getString(R.string.pl_info_display, resources.getString(R.string.ui_hz ,m.physicalWidth.toString(),m.physicalHeight.toString(),String.format(Locale.US, "%.3f", m.refreshRate).trimEnd('0').trimEnd('.'))) }
    val buffer = getString(R.string.pl_info_buffered, ((p.bufferedPosition - p.currentPosition) / 1000).coerceAtLeast(0))
    return listOfNotNull(
        Playback.describeVideo(resources,p.videoFormat, videoDecoder, hdr10PlusMetadata && playbackEngine?.compatibility == null, playbackEngine?.compatibility != null),
        Playback.describeAudio(resources,p.audioFormat, audioDecoder),
        resources.getString(R.string.ui_source_hdr ,HdrDiagnostics.source(resources,p.videoFormat)),
        resources.getString(R.string.ui_requested_output ,HdrDiagnostics.requested(resources,PictureProfiles.effective(config,resumeKey).hdrOutput)),
        playbackEngine?.compatibility?.diagnostics()?.filterKeys {it in setOf(R.string.ui_renderer,R.string.ui_hdr_output,R.string.ui_android_hdr_surface,R.string.ui_expansion_status) }?.entries?.joinToString("\n"){"${resources.getString(it.key)}: ${it.value}"},
        resources.getString(R.string.ui_dolby_vision_rpu_fel_reconstruction_not_independently_verified),
        mode,
        buffer,
        getString(R.string.pl_info_hide),
    ).joinToString("\n")
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.showInfo(hideAfterMs: Long = 0) {
    panel.visibility = View.VISIBLE
    updateBackHandler()
    refreshSubtitlePanel()
    handler.removeCallbacks(infoUpdater)
    handler.post(infoUpdater)
    handler.removeCallbacks(hideInfo)
    if (hideAfterMs > 0) {
        handler.postDelayed(hideInfo, hideAfterMs)
    } else {
        // Opened by hand: give the remote to the subtitle buttons.
        subList.getChildAt(0)?.requestFocus()
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.hidePanel() {
    if (!panelInitialized) return
    panel.visibility = View.GONE
    updateBackHandler()
    panel.scrollTo(0,0)
    view.requestFocus()
}

/** Rebuilds the subtitle choices: off, the video's own tracks, then downloads (best first). */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.refreshSubtitlePanel() {
    if (!subListInitialized || !subsInitialized) return
    val hadFocus = subList.findFocus() != null
    subStatus.text = subs.status ?: ""
    subStatus.visibility = if (subs.status == null) View.GONE else View.VISIBLE
    subList.removeAllViews()
    val sel = subs.selection
    fun add(label: String, chosen: Boolean, action: () -> Unit) {
        subList.addView(styled(Button(this)).apply {
            text = if (chosen) getString(R.string.pl_chosen_option, label) else label
            isAllCaps = false
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setOnClickListener {
                subsTouched = true
                action()
            }
        })
    }
    add(getString(R.string.ui_off), sel is SubtitleController.Selection.Off) { subs.selectOff() }
    subs.embedded().forEach { (g, t, label) ->
        add(getString(R.string.pl_in_the_video, label), sel == SubtitleController.Selection.Embedded(g, t)) { subs.selectEmbedded(g, t) }
    }
    subs.candidates.take(24).forEach { c ->
        val chosen = sel is SubtitleController.Selection.External && sel.candidate == c
        val label = listOfNotNull(
            if (c.hashMatch) "★" else null,
            c.languageName,
            if (c.forced) resources.getString(R.string.ui_forced) else null, if (c.hearingImpaired) getString(R.string.pl_hi) else null,
            c.provider,
            c.release?.take(60),
        ).joinToString(" · ")
        add(label, chosen) { subs.selectExternal(c) }
    }
    val subtitleTiming = getString(R.string.pl_subtitle_timing, String.format(Locale.US, resources.getString(R.string.ui_1f_s), subs.engine.offsetMs / 1000.0))
    timingLabel.text = if (sel is SubtitleController.Selection.External) subtitleTiming
        else getString(R.string.pl_label_pair, subtitleTiming, getString(R.string.pl_for_downloaded))
    updateSoundLabel()
    if (hadFocus && panelVisible) subList.getChildAt(0)?.requestFocus()
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.shiftSubtitles(deltaMs: Long?) {
    if (!subsInitialized) return
    subs.engine.offsetMs = if (deltaMs == null) 0 else subs.engine.offsetMs + deltaMs
    player?.subtitleDelayMilliseconds = subs.engine.offsetMs
    playbackEngine?.compatibility?.subtitleDelay(subs.engine.offsetMs)
    refreshSubtitlePanel()
    updateSubtitleSheet()
}

/** Fills the advanced Subtitles sheet: off, the video's own tracks, then the best downloads. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.updateSubtitleSheet() {
    if (!advanced || !subsInitialized) return
    val (rows, acts) = subtitleChoices()
    subtitleActions = acts
    controls.subtitleOptions = rows
    controls.subtitleShift = subs.engine.offsetMs
    changed()
}

/** #249: the subtitle choices and what each does: for the TV's sheet and the phone remote alike. */
internal fun PlayerActivity.subtitleChoices(): Pair<List<Pair<String, Boolean>>, List<() -> Unit>> {
    if (!subsInitialized) return emptyList<Pair<String, Boolean>>() to emptyList()
    val sel = subs.selection
    val rows = ArrayList<Pair<String, Boolean>>()
    val acts = ArrayList<() -> Unit>()
    rows += getString(R.string.ui_off) to (sel is SubtitleController.Selection.Off); acts += { subs.selectOff() }
    subs.embedded().forEach { (g, t, label) ->
        rows += getString(R.string.pl_in_the_video, label) to (sel == SubtitleController.Selection.Embedded(g, t)); acts += { subs.selectEmbedded(g, t) }
    }
    subs.candidates.take(12).forEach { c ->
        val label = listOfNotNull(if (c.hashMatch) "★" else null, c.languageName, if (c.forced) resources.getString(R.string.ui_forced) else null, if (c.hearingImpaired) getString(R.string.pl_hi) else null, c.provider).joinToString(" · ")
        rows += label to (sel is SubtitleController.Selection.External && sel.candidate == c); acts += { subs.selectExternal(c) }
    }
    if(sel is SubtitleController.Selection.Manual){rows+=sel.label to true;acts+={}}
    return rows to acts
}
