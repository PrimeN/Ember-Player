package app.ember.tv.player

import app.ember.tv.R
import android.widget.Toast
import app.ember.player.localized
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

/** Read chapters from an MKV source when the playback engine has not supplied them. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.loadFileChapters(p: ExoPlayer) {
    playbackEngine?.compatibility?.chapters()?.takeIf {it.isNotEmpty()}?.let {chapters=it;return}
    if (!advanced || chapters.isNotEmpty()) return
    val url = currentUrl ?: return
    val mkv = p.videoFormat?.containerMimeType == MimeTypes.VIDEO_MATROSKA ||
        p.audioFormat?.containerMimeType == MimeTypes.VIDEO_MATROSKA ||
        url.substringBefore('?').endsWith(".mkv", ignoreCase = true)
    // Document IDs need not contain a filename, and an extractor may omit its container MIME.
    val local=android.net.Uri.parse(url).scheme in setOf(null,"content","file")
    if (!mkv && !local) return
    val forUrl = url
    val headers=sourceHeaders.toMap()
    metadataLoads.load({ MkvChapters.read(applicationContext,forUrl,headers) }) { found ->
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
internal fun PlayerActivity.boostLabel(i: Int) = if (i == 0) getString(R.string.ui_off) else resources.getString(R.string.ui_db_024cae ,(boosts[i] / 100).toString())

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
                    "effects" -> OptionRow(resources.getString(R.string.ui_audio_effects),icon=MenuIcons.audio,action={openMenu("options","audio")})
                    "chapters" -> optionRows("playback").firstOrNull {it.title==getString(R.string.pl_chapters)}
                    "queue" -> fileQueue?.let {OptionRow(resources.getString(R.string.ui_queue),icon=MenuIcons.more,action={showFileQueue()})}
                    else -> null
                }
            }.forEach(::add)
            add(OptionRow(resources.getString(R.string.ui_all_player_options),icon=MenuIcons.more,action={openMenu("options","playback")}))
            add(OptionRow(resources.getString(R.string.ui_customize_shortcuts),icon=MenuIcons.more,action={editPlayerShortcuts()}))
            add(OptionRow(resources.getString(R.string.ui_lock_playback),action={closeMenu();touchControls?.setLocked(true)}))
        }
        "video" -> buildList {
            val engines=listOf("auto","standard","compatibility")
            add(OptionRow(resources.getString(R.string.ui_playback_engine),if(playbackEngine?.compatibility!=null)resources.getString(R.string.ui_compatibility) else resources.getString(R.string.ui_standard),listOf(resources.getString(R.string.ui_automatic),resources.getString(R.string.ui_standard),resources.getString(R.string.ui_compatibility)),engines.indexOf(config.engine),onPick={i -> config=config.copy(engine=engines[i]);prefs.playbackConfig=config;playbackEngine?.mode(engines[i])}))
            addAll(pictureOptionRows())
            playbackEngine?.compatibility?.let { native ->
                val pictureConfig=PictureProfiles.effective(config,resumeKey)
                add(OptionRow(resources.getString(R.string.ui_compatibility_decoder_profile),native.decoderProfileLabel,NativeDecoderProfile.choices.localized(resources).map {it.second},NativeDecoderProfile.choices.localized(resources).indexOfFirst {it.first==NativeDecoderProfile.normalized(config.compatibilityDecoder)},
                    note=resources.getString(R.string.ui_lighter_software_decoding_skips_some_deblocking_to_reduce_cpu),onPick={index->config=config.copy(compatibilityDecoder=NativeDecoderProfile.choices.localized(resources)[index].first);prefs.playbackConfig=config;native.decoderProfile(config.compatibilityDecoder,config.hi10pFallback);changed()}))
                add(OptionRow(resources.getString(R.string.ui_automatic_hi10p_fallback),toggle=config.hi10pFallback,note=resources.getString(R.string.ui_uses_full_quality_software_decoding_for_detected_10_bit),action={config=config.copy(hi10pFallback=!config.hi10pFallback);prefs.playbackConfig=config;native.decoderProfile(config.compatibilityDecoder,config.hi10pFallback);changed()}))
                val deinterlaceModes=listOf("off","auto","always")
                add(OptionRow(resources.getString(R.string.ui_deinterlacing),listOf(resources.getString(R.string.ui_off),resources.getString(R.string.ui_automatic),resources.getString(R.string.ui_always))[deinterlaceModes.indexOf(config.deinterlacing).coerceAtLeast(0)],listOf(resources.getString(R.string.ui_off),resources.getString(R.string.ui_automatic),resources.getString(R.string.ui_always)),deinterlaceModes.indexOf(config.deinterlacing).coerceAtLeast(0),
                    note=resources.getString(R.string.ui_automatic_handles_interlaced_video_always_is_for_streams_detected),
                    onPick={config=config.copy(deinterlacing=deinterlaceModes[it]);prefs.playbackConfig=config;applyEffectivePicture()}))
                // On/off settings are switches that flip on OK (#105).
                add(OptionRow(resources.getString(R.string.ui_debanding),toggle=pictureConfig.deband,action={changePictureState {it.copy(deband=!it.deband)}}))
                add(OptionRow(resources.getString(R.string.ui_frame_smoothing),toggle=pictureConfig.interpolation,note=resources.getString(R.string.ui_uses_more_graphics_processing_turn_off_if_playback_slows),action={changePictureState {it.copy(interpolation=!it.interpolation)}}))
                val scales=listOf("bilinear","bicubic","lanczos")
                if(!pictureConfig.picture.enabled || !pictureConfig.picture.upscaling)add(OptionRow(resources.getString(R.string.ui_upscaling),pictureConfig.scaler,scales,scales.indexOf(pictureConfig.scaler).coerceAtLeast(0),onPick={index->changePictureState {it.copy(scaler=scales[index])}}))
            }
            val z = zooms.indexOfFirst { it.first == currentZoom() }.coerceAtLeast(0)
            add(OptionRow(getString(R.string.pl_zoom), zooms[z].second, zooms.map { it.second }, z, onPick = { i -> changeZoom(zooms[i].first) }))
            add(OptionRow(getString(R.string.pl_aspect_ratio), aspects[aspect].first, aspects.map { it.first }, aspect, onPick = { i -> aspect = i;persistGeometry();applyAspect() }))
            if(!privatePlayback)add(OptionRow(resources.getString(R.string.ui_remember_geometry_for_this_media),toggle=GeometryMemory.selected(config,resumeKey)!=null,note=resources.getString(R.string.ui_save_aspect_ratio_picture_fit_and_magnification_for_this),action={toggleGeometryMemory()}))
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
            val timerOptions=listOf(resources.getString(R.string.ui_off),resources.getString(R.string.ui_15_minutes),resources.getString(R.string.ui_30_minutes),resources.getString(R.string.ui_60_minutes),resources.getString(R.string.ui_90_minutes),resources.getString(R.string.ui_end_of_item))
            add(OptionRow(resources.getString(R.string.ui_sleep_timer),if(timer.endOfItem)resources.getString(R.string.ui_end_of_item) else if(remaining>0)resources.getString(R.string.ui_min_remaining ,((remaining+59999)/60000).toString()) else resources.getString(R.string.ui_off),timerOptions,if(timer.endOfItem)5 else 0,onPick={i->
                when(i){0->app.ember.tv.data.SleepTimer.cancel();5->app.ember.tv.data.SleepTimer.atEnd();else->app.ember.tv.data.SleepTimer.after(listOf(0,15,30,60,90)[i])};changed()
            }))

            if(exports.permitted()) {
                if(audioUrl==null)add(OptionRow(resources.getString(R.string.ui_cast_to_a_receiver),action={openCasting()}))
                add(OptionRow(resources.getString(R.string.ui_save_screenshot),action={exports.screenshot()}))

                    add(OptionRow(resources.getString(R.string.ui_mark_clip_start),clips.startMs?.let(::clock).orEmpty(),action={clips.markStart();changed()}))
                    add(OptionRow(resources.getString(R.string.ui_mark_clip_end),clips.endMs?.let(::clock).orEmpty(),action={if(!clips.markEnd())Toast.makeText(this@optionRows,resources.getString(R.string.ui_mark_the_start_then_seek_at_least_one_second),Toast.LENGTH_SHORT).show();changed()}))
                    if(clips.active)add(OptionRow(resources.getString(R.string.ui_cancel_clip_export),clips.progress?.let {"$it%"}.orEmpty(),action={clips.cancel();changed()}))
                    else add(OptionRow(resources.getString(R.string.ui_export_clip),note=resources.getString(R.string.ui_mp4_using_device_encoders_original_kept),action={clips.chooseDestination()}))

            }
            add(OptionRow(resources.getString(R.string.ui_lock_playback),note=resources.getString(R.string.ui_use_the_unlock_button_or_back_to_restore_controls),action={closeMenu();touchControls?.setLocked(true)}))
            if (chapters.isNotEmpty()) {
                val pos = p.currentPosition
                val cur = chapters.indexOfLast { it.startMs <= pos }.coerceAtLeast(0)
                add(OptionRow(getString(R.string.pl_chapters), clock(chapters[cur].startMs), chapters.map { clock(it.startMs) + "   " + it.name }, cur, onPick = { i -> p.seekTo(chapters[i].startMs) }))
            }
            add(speedRow)
            add(OptionRow(resources.getString(R.string.ui_custom_playback_speed),speedLabel(speed),action={closeMenu();customPlaybackSpeed()}))
            if(p.duration>0 && p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
                add(OptionRow(resources.getString(R.string.ui_go_to_time),clock(p.currentPosition),action={closeMenu();goToPlaybackTime()}))
            if(p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                p.currentTracks.groups.any {it.type==C.TRACK_TYPE_VIDEO})
                add(OptionRow(resources.getString(R.string.ui_frame_inspection),resources.getString(R.string.ui_paused_precise_frames),note=resources.getString(R.string.ui_uses_compatibility_playback_and_leaves_the_video_paused),action={closeMenu();inspectFrames()}))
            add(OptionRow(resources.getString(R.string.ui_seeking),if(SeekPreference.fast(config.seekMode))resources.getString(R.string.ui_fast_nearby_keyframe) else resources.getString(R.string.ui_precise),listOf(resources.getString(R.string.ui_precise),resources.getString(R.string.ui_fast_nearby_keyframe)),if(SeekPreference.fast(config.seekMode))1 else 0,
                note=resources.getString(R.string.ui_resume_positions_and_chapter_intro_jumps_stay_precise),onPick={config=config.copy(seekMode=if(it==1)"fast" else "precise");prefs.playbackConfig=config}))

                add(OptionRow(resources.getString(R.string.ui_bookmarks),action={showBookmarks()}))
                val modes=listOf("off","one","all");val mode=fileQueue?.repeat ?: if(p.repeatMode==Player.REPEAT_MODE_ONE)"one" else "off"
                val labels=if(fileQueue!=null)listOf(resources.getString(R.string.ui_off),resources.getString(R.string.ui_repeat_item),resources.getString(R.string.ui_repeat_queue)) else listOf(resources.getString(R.string.ui_off),resources.getString(R.string.ui_repeat_item))
                add(OptionRow(resources.getString(R.string.ui_repeat),labels.getOrElse(modes.indexOf(mode)){resources.getString(R.string.ui_off)},labels,modes.indexOf(mode).coerceAtLeast(0),onPick={i->if(fileQueue!=null)fileQueue!!.repeat=modes[i] else p.repeatMode=if(i==1)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF}))
                add(OptionRow(resources.getString(R.string.ui_set_repeat_start),repeatRange.start?.let { clock(it) }.orEmpty(),action={repeatRange.markStart(p.currentPosition);changed()}))
                if(repeatRange.start!=null)add(OptionRow(resources.getString(R.string.ui_set_repeat_end),repeatRange.end?.let { clock(it) }.orEmpty(),action={if(!repeatRange.markEnd(p.currentPosition))Toast.makeText(this@optionRows,resources.getString(R.string.ui_choose_an_end_at_least_one_second_after_the),Toast.LENGTH_SHORT).show();subtitleTicker.refresh();changed()}))
                if(repeatRange.start!=null)add(OptionRow(resources.getString(R.string.ui_clear_repeat_range),action={repeatRange.clear();subtitleTicker.refresh();changed()}))
                fileQueue?.let {q->
                    add(OptionRow(resources.getString(R.string.ui_shuffle),toggle=q.shuffle,action={q.shuffle=!q.shuffle;refreshControlsTitle();changed()}))
                    if(q.items.none {it.identity?.startsWith(DirectUsbIdentity.PREFIX)==true})add(OptionRow(resources.getString(R.string.ui_save_queue_as_playlist),action={saveFilePlaylist()}))
                    add(OptionRow(resources.getString(R.string.ui_queue),resources.getString(R.string.ui_of ,(q.index+1).toString(),q.items.size.toString()),action={showFileQueue()}))
                    add(OptionRow(resources.getString(R.string.ui_add_files_to_queue),action={pickFile("queue")}))
                }

        }
        "audio" -> buildList {
            add(OptionRow(resources.getString(R.string.ui_night_listening),NightListening.choices.localized(resources).firstOrNull {it.first==config.audioEffects.compression}?.second ?: resources.getString(R.string.ui_off),NightListening.choices.localized(resources).map {it.second},NightListening.choices.localized(resources).indexOfFirst {it.first==config.audioEffects.compression}.coerceAtLeast(0),note=resources.getString(R.string.ui_decoded_audio_only_reduces_loud_effects_relative_to_dialogue),onPick={index->changeAudioEffects(config.audioEffects.copy(compression=NightListening.choices.localized(resources)[index].first))}))
            add(audioRow)
            add(OptionRow(resources.getString(R.string.ui_load_audio_file), resources.getString(R.string.ui_whole_title_soundtrack_compatibility_playback), action={pickFile("audio")}))
            add(boostRow)
            val effectNote=if(pass || playbackEngine?.compatibility?.audioEffectsAvailable==false) resources.getString(R.string.ui_passthrough_is_unchanged_use_decoded_output_to_apply_effects) else resources.getString(R.string.ui_decoded_audio_only_boosts_reserve_headroom_to_reduce_clipping)
            add(OptionRow(resources.getString(R.string.ui_equalizer),AudioEffects.presets.localized(resources).firstOrNull {it.first==config.audioEffects.preset}?.second ?: resources.getString(R.string.ui_off_flat),AudioEffects.presets.localized(resources).map {it.second},AudioEffects.presets.localized(resources).indexOfFirst {it.first==config.audioEffects.preset},note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(preset=AudioEffects.presets.localized(resources)[it].first))}))
            add(OptionRow(resources.getString(R.string.ui_equalizer_bands), resources.getString(R.string.ui_10_bands_12_to_12_db), note=effectNote, action={editEqualizer()}))
            val bassLevels=listOf(0,2,4,6)
            add(OptionRow(resources.getString(R.string.ui_bass),if(config.audioEffects.bass==0)resources.getString(R.string.ui_off) else resources.getString(R.string.ui_db_024cae ,config.audioEffects.bass.toString()),bassLevels.map {if(it==0)resources.getString(R.string.ui_off) else resources.getString(R.string.ui_db_024cae ,it.toString())},bassLevels.indexOf(config.audioEffects.bass),note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(bass=bassLevels[it]))}))
            add(OptionRow(resources.getString(R.string.ui_treble), resources.getString(R.string.ui_db_024cae ,config.audioEffects.treble.toString()), bassLevels.map {resources.getString(R.string.ui_db_024cae ,it.toString())}, bassLevels.indexOf(config.audioEffects.treble).coerceAtLeast(0), note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(treble=bassLevels[it]))}))
            val widths=listOf(0,50,100,125,150,175,200)
            add(OptionRow(resources.getString(R.string.ui_stereo_width), "${config.audioEffects.stereoWidth}%", widths.map {"$it%"}, widths.indexOf(config.audioEffects.stereoWidth).let {if(it>=0)it else 2}, note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(stereoWidth=widths[it]))}))
            add(OptionRow(resources.getString(R.string.ui_audio_channels),AudioEffects.channelChoices.localized(resources).firstOrNull {it.first==config.audioEffects.channels}?.second.orEmpty(),AudioEffects.channelChoices.localized(resources).map {it.second},AudioEffects.channelChoices.localized(resources).indexOfFirst {it.first==config.audioEffects.channels},note=effectNote,
                onPick={changeAudioEffects(config.audioEffects.copy(channels=AudioEffects.channelChoices.localized(resources)[it].first))}))
            val ms = (audioDelay.us / 1000).toInt()
            val di = delays.indices.minByOrNull { kotlin.math.abs(delays[it] - ms) } ?: 10
            add(
                OptionRow(getString(R.string.pl_delay), (if(bluetoothOutput)resources.getString(R.string.ui_bluetooth) else "") + delayLabel(ms), delays.map { delayLabel(it) }, di,
                    note = getString(R.string.pl_lip_sync_later_sound_fixes_a),
                    onPick = { i -> audioDelay.us = delays[i] * 1000L; playbackEngine?.compatibility?.audioDelay(delays[i].toLong()); updateSoundLabel() }),
            )
            if (ms != config.outputDelay(bluetoothOutput)) add(OptionRow(getString(R.string.pl_keep_delay), action = { keepSound(); changed() }))
        }
        "subtitles" -> buildList {
            add(OptionRow(resources.getString(R.string.ui_subtitle_style_presets),action={closeMenu();manageSubtitlePresets()}))
            add(subRow)
            add(OptionRow(resources.getString(R.string.ui_load_subtitle_file), resources.getString(R.string.ui_srt_vtt_ass_ttml_sup_or_idx_sub),action={pickFile("sub")}))
            add(OptionRow(resources.getString(R.string.ui_nearby_subtitle_files),action={findNearbySubtitles()}))
            add(OptionRow(resources.getString(R.string.ui_choose_subtitle_folder),action={pickSubtitleFolder()}))
            add(OptionRow(resources.getString(R.string.ui_reload_saved_subtitle),action={restoreManualSubtitle(notify=true)}))
            playbackEngine?.compatibility?.let { native ->
                val options=p.currentTracks.groups.filter {it.type==C.TRACK_TYPE_TEXT&&!it.isSelected}
                val ids=listOf<Int?>(null)+options.map {it.mediaTrackGroup.id.substringAfterLast(':').toInt()}
                val labels=listOf(resources.getString(R.string.ui_off))+options.map { val f=it.getTrackFormat(0);listOfNotNull(f.language?.let(app.ember.tv.data.Subtitles::languageName),f.label).joinToString(" · ") }
                add(OptionRow(resources.getString(R.string.ui_secondary_subtitles),labels.getOrElse(ids.indexOf(native.secondaryId)){resources.getString(R.string.ui_off)},labels,ids.indexOf(native.secondaryId).coerceAtLeast(0),onPick={native.secondary(ids[it])}))
                add(OptionRow(resources.getString(R.string.ui_secondary_timing),resources.getString(R.string.ui_adjust_separately),subShifts.map {String.format(Locale.US,resources.getString(R.string.ui_2f_s),it/1000.0)},20,onPick={native.secondaryDelay(subShifts[it])}))
            } ?: add(OptionRow(resources.getString(R.string.ui_dual_subtitles),resources.getString(R.string.ui_use_compatibility_playback),action={playbackEngine?.switch();changed()}))
            val subtitleSpeeds=listOf(0.95904f,0.96f,1f,1.001f,1.041667f,1.042709f)
            val subSpeed=if(subsInitialized)subs.engine.speed else 1f
            add(OptionRow(resources.getString(R.string.ui_subtitle_speed),String.format(Locale.US,"%.3f×",subSpeed),subtitleSpeeds.map {String.format(Locale.US,"%.3f×",it)},subtitleSpeeds.indexOf(subSpeed).coerceAtLeast(0),onPick={val value=subtitleSpeeds[it];subs.engine.speed=value;p.subtitleSpeed=value;playbackEngine?.compatibility?.subtitleSpeed(value)}))
            val off = if (subsInitialized) subs.engine.offsetMs else 0L
            val si = subShifts.indices.minByOrNull { kotlin.math.abs(subShifts[it] - off) } ?: 20
            add(
                OptionRow(getString(R.string.pl_timing), String.format(Locale.US, resources.getString(R.string.ui_2f_s), off / 1000.0), subShifts.map { String.format(Locale.US, resources.getString(R.string.ui_2f_s), it / 1000.0) }, si,
                    note = getString(R.string.pl_for_downloaded_subtitles_positive_the_text),
                    onPick = { i -> if (subsInitialized) { subs.engine.offsetMs = subShifts[i]; p.subtitleDelayMilliseconds = subShifts[i]; playbackEngine?.compatibility?.subtitleDelay(subShifts[i]); refreshSubtitlePanel() } }),
            )
            val c = prefs.subtitleConfig
            add(OptionRow(resources.getString(R.string.ui_read_subtitles_aloud),toggle=c.assistance.readAloud,note=resources.getString(R.string.ui_uses_an_installed_offline_voice_only),action={applySubLook(c.copy(assistance=c.assistance.copy(readAloud=!c.assistance.readAloud)))}))
            add(OptionRow(resources.getString(R.string.ui_translate_subtitles),toggle=c.assistance.translate,note=resources.getString(R.string.ui_choose_installed_languages_in_settings_subtitles_offline_subtitle_assistance),action={
                if(c.assistance.translate || c.assistance.source.isNotBlank()&&c.assistance.target.isNotBlank())applySubLook(c.copy(assistance=c.assistance.copy(translate=!c.assistance.translate)))
                else Toast.makeText(this@optionRows,resources.getString(R.string.ui_choose_installed_translation_languages_in_subtitle_settings_first),Toast.LENGTH_LONG).show()
            }))
            fun look(title: String, opts: List<Pair<String, String>>, cur: String, set: (String) -> app.ember.tv.data.SubtitleConfig) {
                val i = opts.indexOfFirst { it.first == cur }.coerceAtLeast(0)
                add(OptionRow(title, opts[i].second, opts.map { it.second }, i, onPick = { j -> applySubLook(set(opts[j].first)) }))
            }
            look(getString(R.string.pl_size), subSizes, c.size) { c.copy(size = it) }
            look(getString(R.string.pl_position), subPositions, c.position) { c.copy(position = it) }
            look(getString(R.string.pl_style), subLooks, c.background) { c.copy(background = it) }

            look(resources.getString(R.string.ui_font),app.ember.tv.data.SubtitleFonts.options.localized(resources).filter {it.first!="custom" || app.ember.tv.data.SubtitleFonts.hasCustom(this@optionRows)},c.font){c.copy(font=it)}
            look(resources.getString(R.string.ui_text_color),listOf("white" to resources.getString(R.string.ui_white),"yellow" to resources.getString(R.string.ui_yellow),"green" to resources.getString(R.string.ui_green),"cyan" to resources.getString(R.string.ui_cyan)),c.color){c.copy(color=it,layout=c.layout.copy(foreground=""))}
            val encodings=app.ember.tv.data.SubtitleEncodings.options.localized(resources)
            val encodingIndex=encodings.indexOfFirst {it.first==c.encoding}.coerceAtLeast(0)
            add(OptionRow(resources.getString(R.string.ui_text_encoding),encodings[encodingIndex].second,encodings.map {it.second},encodingIndex,
                note=resources.getString(R.string.ui_changes_apply_to_files_loaded_next_reload_the_saved),onPick={applySubLook(c.copy(encoding=encodings[it].first))}))
            val scales=listOf(50,75,100,125,150,175,200)
            add(OptionRow(resources.getString(R.string.ui_text_scale),"${c.layout.scalePercent}%",scales.map {"$it%"},scales.indexOf(c.layout.scalePercent).coerceAtLeast(0),onPick={applySubLook(c.copy(layout=c.layout.copy(scalePercent=scales[it])))}))
            val alignments=listOf("auto","left","center","right")
            val alignmentNames=listOf(resources.getString(R.string.ui_automatic),resources.getString(R.string.ui_left),resources.getString(R.string.ui_center),resources.getString(R.string.ui_right))
            add(OptionRow(resources.getString(R.string.ui_alignment),alignmentNames[alignments.indexOf(c.layout.alignment).coerceAtLeast(0)],alignmentNames,alignments.indexOf(c.layout.alignment).coerceAtLeast(0),note=resources.getString(R.string.ui_custom_alignment_and_spacing_use_compatibility_playback_authored_and),onPick={applySubLook(c.copy(layout=c.layout.copy(alignment=alignments[it])))}))
            val spacing=listOf(-2f,0f,1f,2f,4f,6f,10f)
            add(OptionRow(resources.getString(R.string.ui_letter_spacing),c.layout.letterSpacing.toString(),spacing.map {it.toString()},spacing.indexOf(c.layout.letterSpacing).coerceAtLeast(0),onPick={applySubLook(c.copy(layout=c.layout.copy(letterSpacing=spacing[it])))}))
            val lines=listOf(-5f,0f,2f,5f,10f,15f,20f)
            add(OptionRow(resources.getString(R.string.ui_line_spacing),c.layout.lineSpacing.toString(),lines.map {it.toString()},lines.indexOf(c.layout.lineSpacing).coerceAtLeast(0),onPick={applySubLook(c.copy(layout=c.layout.copy(lineSpacing=lines[it])))}))
            add(OptionRow(resources.getString(R.string.ui_more_appearance_options),note=resources.getString(R.string.ui_custom_colors_font_import_outline_and_default_timing_are)))
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
    playbackEngine?.compatibility?.geometry(currentZoom(),aspects[aspect].second)
    view.resizeMode = when (currentZoom()) {
        "crop" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        "stretch" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    applyTouchMagnification()
}

/** Forces a picture shape; Auto goes back to the video's own. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerActivity.applyAspect() {
    playbackEngine?.compatibility?.geometry(currentZoom(),aspects[aspect].second)
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
