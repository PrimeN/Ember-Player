package app.ember.tv.player

import app.ember.tv.iptv.*
import app.ember.tv.R
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import java.util.Locale
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleSpeed

// PlayerActivity's quick and options menus: rows, labels and the picture/sound settings they change.
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.openMenu(which: String, tab: String?) {
    updateSubtitleSheet()
    controls.audioTracks = audioTracks()
    controls.menuTab = tab ?: "playback"
    controls.menuRow = null
    controls.menuFocus = ""
    controls.menu = which
    controls.visible = false
    controls.optionsTick++
}

/** Addon sources: chapters from the MKV file itself, when the media server gave none. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.loadFileChapters(p: ExoPlayer) {
    playbackEngine?.compatibility?.chapters()?.takeIf {it.isNotEmpty()}?.let {chapters=it;return}
    if (!advanced || trailer || chapters.isNotEmpty()) return
    val url = currentUrl ?: return
    if (!url.startsWith("http")) return
    val mkv = p.videoFormat?.containerMimeType == MimeTypes.VIDEO_MATROSKA ||
        p.audioFormat?.containerMimeType == MimeTypes.VIDEO_MATROSKA ||
        url.substringBefore('?').endsWith(".mkv", ignoreCase = true)
    if (!mkv) return
    val forUrl = url
    metadataLoads.load({ MkvChapters.read(forUrl) }) { found ->
        if (found.size > 1 && currentUrl == forUrl && chapters.isEmpty()) {
            chapters = found
            changed()
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.closeMenu() {
    controls.closeMenuSession()
    showControls()
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.changed() {
    if(itemInitialized)refreshControlsTitle()
    controls.optionsTick++
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.passthrough(): Boolean {
    playbackEngine?.compatibility?.let {return it.isPassthrough()}
    if(!config.passthrough||codecs.pcm)return false
    val mime = player?.audioFormat?.sampleMimeType ?: return false
    return audioDecoder == null && mime in setOf(
        MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3,
        MimeTypes.AUDIO_DTS_X, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS,
    )
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.speedLabel(x: Float) = if (x == 1f) getString(R.string.pl_normal) else String.format(Locale.US, "%.3f", x).trimEnd('0').trimEnd('.') + "×"

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.boostLabel(i: Int) = if (i == 0) getString(R.string.ui_off) else "+${boosts[i] / 100} dB"

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.delayLabel(ms: Int) = when {
    ms == 0 -> getString(R.string.pl_delay_zero)
    ms > 0 -> getString(R.string.pl_delay_later, ms)
    else -> getString(R.string.pl_delay_earlier, ms)
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.clock(ms: Long) = PlaybackErrors.clock(ms)

/** The rows of a menu: "quick", "video", "audio" or "subtitles". */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.optionRows(which: String): List<OptionRow> {
    val p = player ?: return emptyList()
    val tracks = controls.audioTracks.ifEmpty { audioTracks() }
    val audioIdx = tracks.indexOfFirst { it.second }
    val audioRow = OptionRow(
        getString(R.string.pl_sound_track), tracks.getOrNull(audioIdx)?.first ?: getString(R.string.ui_none), tracks.map { it.first }, audioIdx, MenuIcons.audio,
        onPick = { i ->
            pickAudioTrack(i)
            handler.postDelayed({ controls.audioTracks = audioTracks(); changed() }, 400)
        },
    )
    val subOpts = controls.subtitleOptions
    val subIdx = subOpts.indexOfFirst { it.second }
    val subRow = OptionRow(
        getString(R.string.settings_subtitles), subOpts.getOrNull(subIdx)?.first ?: getString(R.string.ui_off), subOpts.map { it.first }, subIdx, MenuIcons.subtitles,
        onPick = { i ->
            subtitleActions.getOrNull(i)?.let { subsTouched = true; it() }
            updateSubtitleSheet()
        },
    )
    val pass = passthrough()
    val boostRow = OptionRow(
        getString(R.string.pl_volume_boost), boostLabel(boostLevel), boosts.indices.map { boostLabel(it) }, boostLevel, MenuIcons.boost,
        note = if (pass) getString(R.string.pl_no_effect_while_the_sound_goes) else null,
        onPick = { setBoost(it) },
    )
    val speedChoices=(speeds+speed).filter {it.isFinite() && it in .25f..4f}.distinct().sorted()
    val speedRow = OptionRow(
        getString(R.string.pl_playback_speed), speedLabel(speed), speedChoices.map { speedLabel(it) }, speedChoices.indexOf(speed), MenuIcons.speed,
        note = if (pass) getString(R.string.pl_with_passthrough_the_sound_may_not) else null,
        onPick = { i -> speed = speedChoices[i]; p.setPlaybackSpeed(speed) },
    )
    return when (which) {
        "quick" -> buildList {
            PlayerShortcuts.normalized(config.quickShortcuts).mapNotNull { id ->
                when(id) {
                    "audio" -> audioRow
                    "subtitles" -> subRow
                    "speed" -> speedRow
                    "zoom" -> optionRows("video").firstOrNull {it.title==getString(R.string.pl_zoom)}
                    "decoder" -> optionRows("video").firstOrNull {it.title==getString(R.string.pl_video_decoder)}
                    "delay" -> optionRows("audio").firstOrNull {it.title==getString(R.string.pl_delay)}
                    "effects" -> OptionRow("Audio effects",icon=MenuIcons.audio,action={openMenu("options","audio")})
                    "chapters" -> optionRows("playback").firstOrNull {it.title==getString(R.string.pl_chapters)}
                    "queue" -> fileQueue?.let {OptionRow("Queue",icon=MenuIcons.more,action={showFileQueue()})}
                    else -> null
                }
            }.forEach(::add)
            add(OptionRow("All player options",icon=MenuIcons.more,action={openMenu("options","playback")}))
            add(OptionRow("Customize shortcuts",icon=MenuIcons.more,action={editPlayerShortcuts()}))
            add(OptionRow("Lock playback",action={closeMenu();touchControls?.setLocked(true)}))
        }
        "video" -> buildList {
            val engines=listOf("auto","standard","compatibility")
            add(OptionRow("Playback engine",if(playbackEngine?.compatibility!=null)"Compatibility" else "Standard",listOf("Automatic","Standard","Compatibility"),engines.indexOf(config.engine),onPick={i -> config=config.copy(engine=engines[i]);prefs.playbackConfig=config;playbackEngine?.mode(engines[i])}))
            addAll(pictureOptionRows())
            playbackEngine?.compatibility?.let { native ->
                val pictureConfig=PictureProfiles.effective(config,resumeKey)
                add(OptionRow("Compatibility decoder profile",native.decoderProfileLabel,NativeDecoderProfile.choices.map {it.second},NativeDecoderProfile.choices.indexOfFirst {it.first==NativeDecoderProfile.normalized(config.compatibilityDecoder)},
                    note="Lighter software decoding skips some deblocking to reduce CPU work. Explicit track rules and manual decoder choices take priority.",onPick={index->config=config.copy(compatibilityDecoder=NativeDecoderProfile.choices[index].first);prefs.playbackConfig=config;native.decoderProfile(config.compatibilityDecoder,config.hi10pFallback);changed()}))
                add(OptionRow("Automatic Hi10P fallback",toggle=config.hi10pFallback,note="Uses full-quality software decoding for detected 10-bit H.264 on NVIDIA Shield in Automatic mode.",action={config=config.copy(hi10pFallback=!config.hi10pFallback);prefs.playbackConfig=config;native.decoderProfile(config.compatibilityDecoder,config.hi10pFallback);changed()}))
                val deinterlaceModes=listOf("off","auto","always")
                add(OptionRow("Deinterlacing",listOf("Off","Automatic","Always")[deinterlaceModes.indexOf(config.deinterlacing).coerceAtLeast(0)],listOf("Off","Automatic","Always"),deinterlaceModes.indexOf(config.deinterlacing).coerceAtLeast(0),
                    note="Automatic handles interlaced video. Always is for streams detected incorrectly. Uses more processing; leave Off for progressive video.",
                    onPick={config=config.copy(deinterlacing=deinterlaceModes[it]);prefs.playbackConfig=config;applyEffectivePicture()}))
                // On/off settings are switches that flip on OK (#105).
                add(OptionRow("Debanding",toggle=pictureConfig.deband,action={changePictureState {it.copy(deband=!it.deband)}}))
                add(OptionRow("Frame smoothing",toggle=pictureConfig.interpolation,note="Uses more graphics processing. Turn off if playback slows.",action={changePictureState {it.copy(interpolation=!it.interpolation)}}))
                val scales=listOf("bilinear","bicubic","lanczos")
                if(!pictureConfig.picture.enabled || !pictureConfig.picture.upscaling)add(OptionRow("Upscaling",pictureConfig.scaler,scales,scales.indexOf(pictureConfig.scaler).coerceAtLeast(0),onPick={index->changePictureState {it.copy(scaler=scales[index])}}))
            }
            val z = zooms.indexOfFirst { it.first == config.zoom }.coerceAtLeast(0)
            add(OptionRow(getString(R.string.pl_zoom), zooms[z].second, zooms.map { it.second }, z, onPick = { i -> config = config.copy(zoom = zooms[i].first); prefs.playbackConfig = config; applyZoom() }))
            add(OptionRow(getString(R.string.pl_aspect_ratio), aspects[aspect].first, aspects.map { it.first }, aspect, onPick = { i -> aspect = i; applyAspect() }))
            val ff = playbackEngine?.compatibility?.softwareVideo ?: codecs.ffmpegVideo
            add(
                OptionRow(
                    getString(R.string.pl_video_decoder), if (ff) getString(R.string.pl_ffmpeg_software) else getString(R.string.pl_tv_s_decoder), listOf(getString(R.string.pl_tv_s_decoder), getString(R.string.pl_ffmpeg_software)), if (ff) 1 else 0,
                    note = getString(R.string.pl_ffmpeg_plays_formats_the_tv_s),
                    onPick = { i -> if ((i == 1) != (playbackEngine?.compatibility?.softwareVideo ?: codecs.ffmpegVideo)) switchVideoDecoder(i == 1) },
                ),
            )
            val h = huds.indexOfFirst { it.first == config.hud }.coerceAtLeast(0)
            add(
                OptionRow(getString(R.string.pl_playback_stats_hud), huds[h].second, huds.map { it.second }, h, onPick = { i ->
                    config = config.copy(hud = huds[i].first)
                    prefs.playbackConfig = config
                    handler.removeCallbacks(statsTicker)
                    handler.post(statsTicker)
                }),
            )
        }
        "playback" -> buildList {
            val timer=app.ember.tv.data.SleepTimer.state.value
            val remaining=timer.remaining(android.os.SystemClock.elapsedRealtime())
            val timerOptions=listOf("Off","15 minutes","30 minutes","60 minutes","90 minutes","End of item")
            add(OptionRow("Sleep timer",if(timer.endOfItem)"End of item" else if(remaining>0)"${(remaining+59999)/60000} min remaining" else "Off",timerOptions,if(timer.endOfItem)5 else 0,onPick={i->
                when(i){0->app.ember.tv.data.SleepTimer.cancel();5->app.ember.tv.data.SleepTimer.atEnd();else->app.ember.tv.data.SleepTimer.after(listOf(0,15,30,60,90)[i])};changed()
            }))

            if(exports.permitted()) {
                if(audioUrl==null)add(OptionRow("Cast to a receiver",action={openCasting()}))
                add(OptionRow("Save screenshot",action={exports.screenshot()}))
                if(!live) {
                    add(OptionRow("Mark clip start",clips.startMs?.let(::clock).orEmpty(),action={clips.markStart();changed()}))
                    add(OptionRow("Mark clip end",clips.endMs?.let(::clock).orEmpty(),action={if(!clips.markEnd())Toast.makeText(this@optionRows,"Mark the start, then seek at least one second ahead.",Toast.LENGTH_SHORT).show();changed()}))
                    if(clips.active)add(OptionRow("Cancel clip export",clips.progress?.let {"$it%"}.orEmpty(),action={clips.cancel();changed()}))
                    else add(OptionRow("Export clip",note="MP4 using device encoders · original kept",action={clips.chooseDestination()}))
                }
            }
            add(OptionRow("Lock playback",note="Use the unlock button or Back to restore controls.",action={closeMenu();touchControls?.setLocked(true)}))
            if (chapters.isNotEmpty()) {
                val pos = p.currentPosition
                val cur = chapters.indexOfLast { it.startMs <= pos }.coerceAtLeast(0)
                add(OptionRow(getString(R.string.pl_chapters), clock(chapters[cur].startMs), chapters.map { clock(it.startMs) + "   " + it.name }, cur, onPick = { i -> p.seekTo(chapters[i].startMs) }))
            }
            add(speedRow)
            add(OptionRow("Custom playback speed",speedLabel(speed),action={closeMenu();customPlaybackSpeed()}))
            if(!live && p.duration>0 && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
                add(OptionRow("Go to time",clock(p.currentPosition),action={closeMenu();goToPlaybackTime()}))
            if(!live && !trailer && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                p.currentTracks.groups.any {it.type==C.TRACK_TYPE_VIDEO})
                add(OptionRow("Frame inspection","Paused · precise frames",note="Uses Compatibility playback and leaves the video paused.",action={closeMenu();inspectFrames()}))
            add(OptionRow("Seeking",if(SeekPreference.fast(config.seekMode))"Fast / nearby keyframe" else "Precise",listOf("Precise","Fast / nearby keyframe"),if(SeekPreference.fast(config.seekMode))1 else 0,
                note="Resume positions and chapter/intro jumps stay precise.",onPick={config=config.copy(seekMode=if(it==1)"fast" else "precise");prefs.playbackConfig=config}))
            if(!live) {
                if(!trailer)add(OptionRow("Bookmarks",action={showBookmarks()}))
                val modes=listOf("off","one","all");val mode=fileQueue?.repeat ?: if(p.repeatMode==Player.REPEAT_MODE_ONE)"one" else "off"
                val labels=if(fileQueue!=null)listOf("Off","Repeat item","Repeat queue") else listOf("Off","Repeat item")
                add(OptionRow("Repeat",labels.getOrElse(modes.indexOf(mode)){"Off"},labels,modes.indexOf(mode).coerceAtLeast(0),onPick={i->if(fileQueue!=null)fileQueue!!.repeat=modes[i] else p.repeatMode=if(i==1)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF}))
                add(OptionRow("Set repeat start",repeatRange.start?.let { clock(it) }.orEmpty(),action={repeatRange.markStart(p.currentPosition);changed()}))
                if(repeatRange.start!=null)add(OptionRow("Set repeat end",repeatRange.end?.let { clock(it) }.orEmpty(),action={if(!repeatRange.markEnd(p.currentPosition))Toast.makeText(this@optionRows,"Choose an end at least one second after the start.",Toast.LENGTH_SHORT).show();changed()}))
                if(repeatRange.start!=null)add(OptionRow("Clear repeat range",action={repeatRange.clear();changed()}))
                fileQueue?.let {q->
                    add(OptionRow("Shuffle",toggle=q.shuffle,action={q.shuffle=!q.shuffle;refreshControlsTitle();changed()}))
                    if(q.items.none {it.identity?.startsWith(DirectUsbIdentity.PREFIX)==true})add(OptionRow("Save queue as playlist",action={saveFilePlaylist()}))
                    add(OptionRow("Queue","${q.index+1} of ${q.items.size}",action={showFileQueue()}))
                    add(OptionRow("Add files to queue",action={pickFile("queue")}))
                }
            }
        }
        "audio" -> buildList {
            add(audioRow)
            add(OptionRow("Load audio file", "Whole-title soundtrack · compatibility playback", action={pickFile("audio")}))
            add(boostRow)
            val effectNote=if(pass || playbackEngine?.compatibility?.audioEffectsAvailable==false) "Passthrough is unchanged. Use decoded output to apply effects to this track." else "Decoded audio only; boosts reserve headroom to reduce clipping."
            add(OptionRow("Equalizer",AudioEffects.presets.firstOrNull {it.first==config.audioEffects.preset}?.second ?: "Off / flat",AudioEffects.presets.map {it.second},AudioEffects.presets.indexOfFirst {it.first==config.audioEffects.preset},note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(preset=AudioEffects.presets[it].first))}))
            add(OptionRow("Equalizer bands", "10 bands · −12 to +12 dB", note=effectNote, action={editEqualizer()}))
            val bassLevels=listOf(0,2,4,6)
            add(OptionRow("Bass",if(config.audioEffects.bass==0)"Off" else "+${config.audioEffects.bass} dB",bassLevels.map {if(it==0)"Off" else "+$it dB"},bassLevels.indexOf(config.audioEffects.bass),note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(bass=bassLevels[it]))}))
            add(OptionRow("Treble", "+${config.audioEffects.treble} dB", bassLevels.map {"+$it dB"}, bassLevels.indexOf(config.audioEffects.treble).coerceAtLeast(0), note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(treble=bassLevels[it]))}))
            val widths=listOf(0,50,100,125,150,175,200)
            add(OptionRow("Stereo width", "${config.audioEffects.stereoWidth}%", widths.map {"$it%"}, widths.indexOf(config.audioEffects.stereoWidth).let {if(it>=0)it else 2}, note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(stereoWidth=widths[it]))}))
            add(OptionRow("Audio channels",AudioEffects.channelChoices.firstOrNull {it.first==config.audioEffects.channels}?.second.orEmpty(),AudioEffects.channelChoices.map {it.second},AudioEffects.channelChoices.indexOfFirst {it.first==config.audioEffects.channels},note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(channels=AudioEffects.channelChoices[it].first))}))
            val ms = (audioDelay.us / 1000).toInt()
            val di = delays.indices.minByOrNull { kotlin.math.abs(delays[it] - ms) } ?: 10
            add(
                OptionRow(getString(R.string.pl_delay), (if(bluetoothOutput)"Bluetooth · " else "") + delayLabel(ms), delays.map { delayLabel(it) }, di,
                    note = getString(R.string.pl_lip_sync_later_sound_fixes_a),
                    onPick = { i -> audioDelay.us = delays[i] * 1000L; playbackEngine?.compatibility?.audioDelay(delays[i].toLong()); updateSoundLabel() }),
            )
            if (ms != config.outputDelay(bluetoothOutput)) add(OptionRow(getString(R.string.pl_keep_delay), action = { keepSound(); changed() }))
        }
        "subtitles" -> buildList {
            add(subRow)
            add(OptionRow("Load subtitle file", "SRT, VTT, ASS, TTML, SUP or IDX + SUB",action={pickFile("sub")}))
            add(OptionRow("Nearby subtitle files",action={findNearbySubtitles()}))
            add(OptionRow("Choose subtitle folder",action={pickSubtitleFolder()}))
            add(OptionRow("Reload saved subtitle",action={restoreManualSubtitle(notify=true)}))
            playbackEngine?.compatibility?.let { native ->
                val options=p.currentTracks.groups.filter {it.type==C.TRACK_TYPE_TEXT&&!it.isSelected}
                val ids=listOf<Int?>(null)+options.map {it.mediaTrackGroup.id.substringAfterLast(':').toInt()}
                val labels=listOf("Off")+options.map { val f=it.getTrackFormat(0);listOfNotNull(f.language?.let(app.ember.tv.data.Subtitles::languageName),f.label).joinToString(" · ") }
                add(OptionRow("Secondary subtitles",labels.getOrElse(ids.indexOf(native.secondaryId)){"Off"},labels,ids.indexOf(native.secondaryId).coerceAtLeast(0),onPick={native.secondary(ids[it])}))
                add(OptionRow("Secondary timing","Adjust separately",subShifts.map {String.format(Locale.US,"%+.2f s",it/1000.0)},20,onPick={native.secondaryDelay(subShifts[it])}))
            } ?: add(OptionRow("Dual subtitles","Use compatibility playback",action={playbackEngine?.switch();changed()}))
            val subtitleSpeeds=listOf(0.95904f,0.96f,1f,1.001f,1.041667f,1.042709f)
            val subSpeed=if(subsInitialized)subs.engine.speed else 1f
            add(OptionRow("Subtitle speed",String.format(Locale.US,"%.3f×",subSpeed),subtitleSpeeds.map {String.format(Locale.US,"%.3f×",it)},subtitleSpeeds.indexOf(subSpeed).coerceAtLeast(0),onPick={val value=subtitleSpeeds[it];subs.engine.speed=value;p.subtitleSpeed=value;playbackEngine?.compatibility?.subtitleSpeed(value)}))
            val off = if (subsInitialized) subs.engine.offsetMs else 0L
            val si = subShifts.indices.minByOrNull { kotlin.math.abs(subShifts[it] - off) } ?: 20
            add(
                OptionRow(getString(R.string.pl_timing), String.format(Locale.US, "%+.2f s", off / 1000.0), subShifts.map { String.format(Locale.US, "%+.2f s", it / 1000.0) }, si,
                    note = getString(R.string.pl_for_downloaded_subtitles_positive_the_text),
                    onPick = { i -> if (subsInitialized) { subs.engine.offsetMs = subShifts[i]; p.subtitleDelayMilliseconds = subShifts[i]; playbackEngine?.compatibility?.subtitleDelay(subShifts[i]); refreshSubtitlePanel() } }),
            )
            val c = prefs.subtitleConfig
            add(OptionRow("Read subtitles aloud",toggle=c.assistance.readAloud,note="Uses an installed offline voice only.",action={applySubLook(c.copy(assistance=c.assistance.copy(readAloud=!c.assistance.readAloud)))}))
            add(OptionRow("Translate subtitles",toggle=c.assistance.translate,note="Choose installed languages in Settings > Subtitles > Offline subtitle assistance. No cloud service or model downloads.",action={
                if(c.assistance.translate || c.assistance.source.isNotBlank()&&c.assistance.target.isNotBlank())applySubLook(c.copy(assistance=c.assistance.copy(translate=!c.assistance.translate)))
                else Toast.makeText(this@optionRows,"Choose installed translation languages in subtitle settings first.",Toast.LENGTH_LONG).show()
            }))
            fun look(title: String, opts: List<Pair<String, String>>, cur: String, set: (String) -> app.ember.tv.data.SubtitleConfig) {
                val i = opts.indexOfFirst { it.first == cur }.coerceAtLeast(0)
                add(OptionRow(title, opts[i].second, opts.map { it.second }, i, onPick = { j -> applySubLook(set(opts[j].first)) }))
            }
            look(getString(R.string.pl_size), subSizes, c.size) { c.copy(size = it) }
            look(getString(R.string.pl_position), subPositions, c.position) { c.copy(position = it) }
            look(getString(R.string.pl_style), subLooks, c.background) { c.copy(background = it) }

            look("Font",app.ember.tv.data.SubtitleFonts.options.filter {it.first!="custom" || app.ember.tv.data.SubtitleFonts.hasCustom(this@optionRows)},c.font){c.copy(font=it)}
            look("Text color",listOf("white" to "White","yellow" to "Yellow","green" to "Green","cyan" to "Cyan"),c.color){c.copy(color=it,layout=c.layout.copy(foreground=""))}
            val encodings=app.ember.tv.data.SubtitleEncodings.options
            val encodingIndex=encodings.indexOfFirst {it.first==c.encoding}.coerceAtLeast(0)
            add(OptionRow("Text encoding",encodings[encodingIndex].second,encodings.map {it.second},encodingIndex,
                note="Changes apply to files loaded next. Reload the saved subtitle to apply now.",onPick={applySubLook(c.copy(encoding=encodings[it].first))}))
            val scales=listOf(50,75,100,125,150,175,200)
            add(OptionRow("Text scale","${c.layout.scalePercent}%",scales.map {"$it%"},scales.indexOf(c.layout.scalePercent).coerceAtLeast(0),onPick={applySubLook(c.copy(layout=c.layout.copy(scalePercent=scales[it])))}))
            val alignments=listOf("auto","left","center","right")
            add(OptionRow("Alignment",c.layout.alignment,alignments,alignments.indexOf(c.layout.alignment).coerceAtLeast(0),note="Custom alignment and spacing use compatibility playback. Authored and system caption styles take priority.",onPick={applySubLook(c.copy(layout=c.layout.copy(alignment=alignments[it])))}))
            val spacing=listOf(-2f,0f,1f,2f,4f,6f,10f)
            add(OptionRow("Letter spacing",c.layout.letterSpacing.toString(),spacing.map {it.toString()},spacing.indexOf(c.layout.letterSpacing).coerceAtLeast(0),onPick={applySubLook(c.copy(layout=c.layout.copy(letterSpacing=spacing[it])))}))
            val lines=listOf(-5f,0f,2f,5f,10f,15f,20f)
            add(OptionRow("Line spacing",c.layout.lineSpacing.toString(),lines.map {it.toString()},lines.indexOf(c.layout.lineSpacing).coerceAtLeast(0),onPick={applySubLook(c.copy(layout=c.layout.copy(lineSpacing=lines[it])))}))
            add(OptionRow("More appearance options",note="Custom colors, font import, outline and default timing are in Settings > Subtitles."))
            add(OptionRow(getString(R.string.pl_get_more), action = { closeMenu(); showInfo() }))
        }
        else -> emptyList()
    }
}

/** Subtitle size, position and style, changed while watching; kept for every video. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.applySubLook(c: app.ember.tv.data.SubtitleConfig) {
    prefs.subtitleConfig = c
    captionConfig=c;closeCaptionAssistance()
    baseSubCfg = c
    if(subsInitialized)subs.config=subConfigFor(c)
    if(c.layout.needsCompatibility && !c.systemStyle && playbackEngine?.compatibility==null && config.engine=="auto")playbackEngine?.switch()
    view.subtitleView?.let { SubtitleEngine.style(it, c) }
    playbackEngine?.compatibility?.captionStyle(c)
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.setBoost(i: Int) {
    boostLevel = i
    val p = player ?: return
    playbackEngine?.compatibility?.let {it.volumeBoost(boosts[i]/100.0);return}
    // Session 0 is the whole TV's output; never touch it.
    if (i > 0 && p.audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
        boostLevel = 0
        Toast.makeText(this, getString(R.string.pl_volume_boost_is_not_available_until), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        if (i == 0) {
            loudness?.enabled = false
            return
        }
        val le = loudness ?: android.media.audiofx.LoudnessEnhancer(p.audioSessionId).also { loudness = it }
        le.setTargetGain(boosts[i])
        le.enabled = true
    }.onFailure {
        boostLevel = 0
        Toast.makeText(this, getString(R.string.pl_volume_boost_is_not_available_on), Toast.LENGTH_SHORT).show()
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.applyZoom() {
    playbackEngine?.compatibility?.geometry(config.zoom,aspects[aspect].second)
    view.resizeMode = when (config.zoom) {
        "crop" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        "stretch" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    applyTouchMagnification()
}

/** Forces a picture shape; Auto goes back to the video's own. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.applyAspect() {
    playbackEngine?.compatibility?.geometry(config.zoom,aspects[aspect].second)
    applyTouchMagnification()
    val frame = view.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return
    val r = aspects[aspect].second
    if (r > 0f) {
        frame.setAspectRatio(r)
    } else {
        val v = player?.videoSize ?: return
        if (v.width > 0 && v.height > 0) frame.setAspectRatio(v.width * v.pixelWidthHeightRatio / v.height)
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.switchVideoDecoder(ffmpeg: Boolean) {
    val mode=if(ffmpeg)DecoderMode.FFMPEG else DecoderMode.AUTO
    userVideoMode=mode
    codecs.manualVideo=true
    codecs.ffmpegVideo=ffmpeg
    if(playbackEngine?.compatibility!=null)playbackEngine?.compatibility?.softwareVideo(ffmpeg) else decoderManager.selectVideoDecoder(mode)
}
