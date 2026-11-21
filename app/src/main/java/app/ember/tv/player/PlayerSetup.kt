package app.ember.tv.player

import app.ember.tv.R
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.Tracks
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import app.ember.tv.data.Prefs
import app.ember.tv.player.Kind
import app.ember.tv.player.PlaybackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleSpeed

@OptIn(UnstableApi::class)
internal fun PlayerActivity.createPlayer(savedInstanceState: Bundle?) {
    PlayerActivity.current?.get()?.takeIf { it !== this }?.endPlayback(finishActivity=true)
    BackgroundAudioService.stopCurrent()
    PlayerActivity.current = java.lang.ref.WeakReference(this)
    startOver = intent.getBooleanExtra(PlayRequest.EXTRA_START_OVER, false)
    queueId=intent.getStringExtra("ember.fileQueue");fileQueue=MediaQueues.get(queueId)
    if(queueId!=null&&fileQueue==null){Toast.makeText(this,resources.getString(R.string.ui_reopen_your_media_files_to_restore_this_queue),Toast.LENGTH_LONG).show();finish();return}

    applyStreamHeaders(PlayRequest.headers(intent))
    audioUrl = intent.getStringExtra(PlayerActivity.EXTRA_AUDIO_URL)
    val url = intent.getStringExtra(PlayRequest.EXTRA_URL)
    if (url.isNullOrBlank()) {
        finish()
        return
    }
    privatePlayback=app.ember.player.PrivateMedia.hidden(url.orEmpty())
    if(privatePlayback&&!app.ember.player.PrivateMedia.unlocked.value){finish();return}
    if(privatePlayback)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    val title = intent.getStringExtra(PlayRequest.EXTRA_TITLE).orEmpty()
    resumeKey = intent.getStringExtra(PlayRequest.EXTRA_RESUME_KEY) ?: url.orEmpty()
    resumeFraction = intent.getFloatExtra(PlayRequest.EXTRA_RESUME_FRACTION, -1f)
    item = PlaybackItem(
        key = resumeKey,
        title = title,
        kind = runCatching { Kind.valueOf(intent.getStringExtra(PlayRequest.EXTRA_KIND) ?: "") }.getOrDefault(Kind.MOVIE),
        imdbId = intent.getStringExtra(PlayRequest.EXTRA_IMDB),
        tmdbId = intent.getLongExtra(PlayRequest.EXTRA_TMDB, -1L).takeIf { it > 0 },
        season = intent.getIntExtra(PlayRequest.EXTRA_SEASON, -1).takeIf { it >= 0 },
        episode = intent.getIntExtra(PlayRequest.EXTRA_EPISODE, -1).takeIf { it >= 0 },
    )
    seriesName = intent.getStringExtra(PlayRequest.EXTRA_SERIES_NAME) ?: title
    prefs = Prefs(this)
    watchProfileId=prefs.currentProfile
    if(!contentPlaybackAllowed()){finish();return}
    getSharedPreferences("ember",android.content.Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(contentProfileListener)
    localActivityScope=app.ember.tv.data.LocalActivity.scope(this,watchProfileId)
    memory=TrackMemory(this,localActivityScope)
    positions = PositionStore(this,localActivityScope)

    view = PlayerView(this).apply {
        setShowSubtitleButton(true)
        setShowNextButton(false)
        setShowPreviousButton(false)
        controllerShowTimeoutMs = 4000
    }
    view.addOnLayoutChangeListener {_,_,_,_,_,_,_,_,_->updatePictureInPicture()}
    config = prefs.playbackConfig
    if(config.rememberDecoderChoices)lifecycleScope.launch {
        val health=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){DecoderHealth.load(this@createPlayer)}
        if(!isFinishing)codecs.decoderHealth=health
    }
    audioEffectState.value = config.audioEffects
    playerInterface = PlayerInterfaces.resolve(config, prefs.uiStyle)
    controls.design = playerInterface
    advanced = true
    // The accent colour (Settings > Appearance) for the progress bar and menus, in both player looks.
    val accent = app.ember.tv.ui.emberColorScheme(prefs.accent, day = false, oled = false).primary
    controls.accent = accent
    accentArgb = android.graphics.Color.argb(255, (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())
    view.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
        setPlayedColor(accentArgb)
        setScrubberColor(accentArgb)
    }
    styleNativeIcons(view)
    controls.localUri=url?.takeIf {fileQueue!=null&&it.startsWith("content://")}
    controls.details = null
    refreshControlsTitle()
    setContentView(buildLayout())
    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }
    installTouchControls()
    if (advanced) handler.post(controlsTicker)

    applyZoom()
    if (advanced) handler.post(statsTicker)
    val trackSelector = DefaultTrackSelector(this)
    trackSelector.setParameters(
        trackSelector.buildUponParameters().apply {
            setTunnelingEnabled(config.tunneling)
            // Allow the full channel count and the highest quality the hardware reports.
            setMaxAudioChannelCount(Int.MAX_VALUE)
        },
    )

    // Media links can redirect and high-bitrate sources may need longer timeouts.
    val http = httpFactory
    audioDelay.us = config.audioDelayMs * 1000L
    // Dolby Vision only where the screen shows it; projectors and most TVs without it get the HDR10 picture.
    codecs.dvAsHevc = when (config.dolbyVision) {
        "hdr10" -> true
        "always" -> false
        else -> !Playback.screenHasDolbyVision(this)
    }
    val standard = ExoPlayer.Builder(this, Playback.Renderers(this, config, audioDelay, codecs, decoderManager, audioEffectState, nativeSubtitles = true))
        .setTrackSelector(trackSelector)
        .setLoadControl(Playback.loadControl(config, this){config})
        .setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setMediaSourceFactory(mediaSourceFactory(DefaultDataSource.Factory(this, http)))
        .setVideoChangeFrameRateStrategy(
            if (config.frameRate == "seamless") C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
        )
        .build()

    playbackEngine=PlaybackEngine(this,standard,{renderConfig().copy(passthrough=config.passthrough&&!codecs.pcm)},
        recoveryAllowed={userVideoMode==null && userAudioMode==null}, recovery = decoderRecovery) { compatible ->
        pictureBypassed=false;pictureSignature=null;pictureEffectsActive=false
        videoDecoder=if(compatible)resources.getString(R.string.ui_compatibility_engine) else null
        standardAudioTrack = null
        loudness?.release();loudness=null
        if(boostLevel>0)setBoost(boostLevel)
        if (subsInitialized) {
            view.subtitleView?.setCues(emptyList())
            embeddedCaptionText = ""
            val visit = playbackVisit
            handler.post { if (playbackVisit == visit && player != null) subs.onBackendChanged() }
        }
        if(compatible) {
            playbackEngine?.compatibility?.onCaptionTextChanged={subtitleTicker.refresh()}
            playbackEngine?.compatibility?.primarySubtitleVisible(false)
            playbackEngine?.compatibility?.onSubtitleLoadFailed = { if (subsInitialized) subs.nativeLoadFailed() }
            playbackEngine?.compatibility?.onOptionsChanged={
                metadataLoads.reset();chapters=playbackEngine?.compatibility?.chapters().orEmpty()
                markerChapterState=null;autoSkipped.clear()
                if(subsInitialized)subs.reconcileEmbeddedSelection()
                changed()
            }
            audioDecoder=null
            applyZoom();applyAspect();applyTouchMagnification()
            playbackEngine?.compatibility?.captionStyle(prefs.subtitleConfig)
            playbackEngine?.compatibility?.audioDelay(audioDelay.us/1000)
            playbackEngine?.compatibility?.audioEffects(config.audioEffects)
            if(subsInitialized){playbackEngine?.compatibility?.subtitleSpeed(subs.engine.speed);playbackEngine?.compatibility?.subtitleDelay(subs.engine.offsetMs)}
            Toast.makeText(this,resources.getString(R.string.ui_trying_compatibility_playback),Toast.LENGTH_SHORT).show()
        }
    }
    val p=playbackEngine!!.player
    val restoredSpeed=intent.getFloatExtra(BackgroundAudioService.FORCE_SPEED,1f).takeIf {it.isFinite()}?.coerceIn(.25f,4f) ?: 1f
    speed=restoredSpeed;p.setPlaybackSpeed(restoredSpeed)
    intent.getBundleExtra(BackgroundAudioService.FORCE_SOUND)?.let {sound->
        p.volume=sound.getFloat("volume",1f).takeIf {it.isFinite()}?.coerceIn(0f,1f) ?: 1f
        boostLevel=boosts.indexOf(sound.getInt("boost",0)).coerceAtLeast(0)
    }
    outputRoute?.close()
    outputRoute = OutputRouteMonitor(this) { bluetooth ->
        bluetoothOutput = bluetooth
        val sound=intent.getBundleExtra(BackgroundAudioService.FORCE_SOUND)
        val timing=if(sound==null)config.outputDelay(bluetooth) else if(bluetooth && sound.containsKey("bluetooth"))sound.getInt("bluetooth") else sound.getInt("delay")
        audioDelay.us = timing.coerceIn(-10000,10000) * 1000L
        playbackEngine?.compatibility?.audioDelay(audioDelay.us / 1000)
        changed(); updateSoundLabel()
    }
    decoderManager.attach(standard)
    if(config.ffmpegVideo=="always")useFfmpegVideo(p)
    // Renderer priority already implements "prefer software". Forcing FFmpeg here
    // disables MPEG-H and device-only formats such as AC-4 in Nextlib's controller.

    p.setVideoFrameMetadataListener { _, _, _, output ->
        if (!hdr10PlusMetadata && output?.containsKey("hdr10-plus-info") == true)
            hdr10PlusMetadata = runCatching { output.getByteBuffer("hdr10-plus-info")?.hasRemaining() == true }.getOrDefault(false)
        if (animateAssOverlay) {
            handler.removeCallbacks(subtitleVideoFrame)
            handler.post(subtitleVideoFrame)
        }
    }
    p.addAnalyticsListener(object : AnalyticsListener {
        override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            hdr10PlusMetadata = false
            val visit=playbackVisit
            handler.post {if(player===p && playbackVisit==visit)applyPictureEnhancement(format)}
            playbackEngine?.restartQualityWindow()
            decoderLearned = false
        }
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            videoDecoder = decoderName
            playbackEngine?.restartQualityWindow()
            decoderLearned = false
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) {
            if (playbackEngine?.compatibility != null) return
            standardAudioTrack = audioTrackConfig
            updateSoundLabel()
        }

        override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, audioTrackConfig: androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig) {
            val active = standardAudioTrack ?: return
            if (active.encoding == audioTrackConfig.encoding && active.sampleRate == audioTrackConfig.sampleRate &&
                active.channelConfig == audioTrackConfig.channelConfig && active.offload == audioTrackConfig.offload) {
                standardAudioTrack = null
                updateSoundLabel()
            }
        }

        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            audioDecoder = decoderName
        }

        override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
            bandwidth = bitrateEstimate
            if (totalLoadTimeMs > 0) loadSpeed = totalBytesLoaded * 8_000 / totalLoadTimeMs
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
            dropped += droppedFrames
        }

        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            // Volume boost is tied to the sound session; follow it to the new one.
            loudness?.let { runCatching { it.release() } }
            loudness = null
            if (boostLevel > 0) setBoost(boostLevel)
        }

        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            // A new audio format may go straight to the receiver; the decoder callback says otherwise.
            audioDecoder = null
            val visit=playbackVisit
            handler.post {if(player===p && playbackVisit==visit)applyPictureEnhancement()}
        }
    })

    p.addListener(object : Player.Listener {
        override fun onEvents(player:Player,events:Player.Events){
            subtitleTicker.refresh()
            if(events.containsAny(Player.EVENT_PLAY_WHEN_READY_CHANGED,Player.EVENT_IS_PLAYING_CHANGED,Player.EVENT_PLAYBACK_STATE_CHANGED,Player.EVENT_AVAILABLE_COMMANDS_CHANGED,Player.EVENT_VIDEO_SIZE_CHANGED))updatePictureInPicture()
        }
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            updatePictureInPicture()
            // PlayerView sets the video's own shape; a forced one goes back on top.
            if (aspect != 0) handler.post { applyAspect() }
        }

        override fun onCues(cueGroup:androidx.media3.common.text.CueGroup){embeddedCaptionText=cueGroup.cues.mapNotNull {it.text?.toString()}.joinToString("\n")}

        override fun onTracksChanged(tracks: Tracks) {
            // Start on the best audio track once per title; after that the user's choice stands.
            if (!audioPicked && tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }) {
                audioPicked = true
                val restored=BackgroundAudioTrack.from(intent.getBundleExtra(BackgroundAudioService.FORCE_TRACK))?.selection(tracks)
                intent.removeExtra(BackgroundAudioService.FORCE_TRACK)
                (restored ?: Playback.bestAudio(tracks, config, showKey()?.let { memory.get(it)?.audio }))?.let { o ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(o).build()
                }
            }
            val captions=prefs.subtitleConfig
            if(SubtitleStyleProbe.routeToMpv(config.engine=="auto",true,captions.systemStyle,captions.layout.needsCompatibility,p.videoFormat,p.audioFormat) && playbackEngine?.compatibility==null &&
                tracks.groups.any {it.type==C.TRACK_TYPE_TEXT && it.isSelected} && playbackEngine?.switch()==true)return
            if (config.engine == "auto" && playbackEngine?.compatibility == null && tracks.groups.any { group ->
                group.type == C.TRACK_TYPE_VIDEO && (0 until group.length).any { group.isTrackSelected(it) && PlaybackFormats.nativeFirst(group.getTrackFormat(it).sampleMimeType, group.isTrackSupported(it, false)) }
            } && playbackEngine?.switch(R.string.ui_core_format) == true) return
            val styledAss=tracks.groups.any {group->group.type==C.TRACK_TYPE_TEXT && (0 until group.length).any {index->
                val format=group.getTrackFormat(index)
                group.isTrackSelected(index) && format.sampleMimeType==MimeTypes.TEXT_SSA && format.initializationData.any {SubtitleStyleProbe.styled(it.toString(Charsets.UTF_8))}
            }}
            val sourceVideo = p.videoFormat ?: tracks.groups.firstNotNullOfOrNull { group ->
                if (group.type != C.TRACK_TYPE_VIDEO) null
                else (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
            }
            if (playbackEngine?.compatibility == null &&
                SubtitleStyleProbe.routeToMpv(config.engine == "auto", captions.authoredStyles, captions.systemStyle,
                    styledAss, sourceVideo, playbackEngine?.selectedAudio())) {
                if(playbackEngine?.switch(R.string.ui_core_styled_subtitles)==true)return
            }
            if (subsInitialized) subs.reconcileEmbeddedSelection(allowMissing = true)
            if (advanced) {
                controls.audioTracks = audioTracks()
                changed()
            }
            matchDisplayMode(p)
            val visit=playbackVisit
            handler.post {if(player===p && playbackVisit==visit)applyPictureEnhancement()}
        }

        override fun onPlaybackStateChanged(state: Int) {
            // Playing again after a retry: a later drop (an hour into a film or a channel) gets its
            // own retries, but not if it keeps failing right after starting.
            if (state == Player.STATE_READY && android.os.SystemClock.elapsedRealtime() - lastRetryAt > 60_000L) {
                ioRetries = 0
            }
            if (state == Player.STATE_READY && !resumed) {
                resumed = resume(p)
                loadFileChapters(p)
                if (advanced) showControls()
                if (config.infoOnStart) showInfo(8_000)
                if (subsInitialized) subs.onReady()
            }
            if (state == Player.STATE_ENDED && !ended) {
                ended = true
                if(!privatePlayback&&app.ember.player.PrivateMedia.historyAllowed(currentUrl.orEmpty()))positions.finished(resumeKey,playbackDuration(p))
                else positions.clear(resumeKey)
                onEpisodeEnded()
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (p.playbackState == Player.STATE_READY && !resumed) resumed = resume(p)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if(isPlaying)app.ember.player.CastPlaybackService.localPlayback(resumeKey)
            controls.playing = isPlaying
            // Paused by the user: show the controls (with the synopsis and cast).
            if (advanced && !isPlaying && !p.playWhenReady && p.playbackState == Player.STATE_READY) showControls()
            // Keep the screen on only while video plays, so a paused video lets the screensaver start.
            view.keepScreenOn = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            if(recoverPicture(error))return

            // A network stream behind its live window jumps back to the current position.
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                p.seekToDefaultPosition()
                p.prepare()
                return
            }
            // A link with no extension that is not a file ExoPlayer knows: try it once as HLS.
            if (!triedAsHls && playbackEngine?.compatibility == null && audioUrl == null &&
                PlaybackErrors.mayBeHls(error.errorCode, currentUrl)) {
                triedAsHls = true
                p.currentMediaItem?.buildUpon()?.setMimeType(MimeTypes.APPLICATION_M3U8)?.build()?.let {
                    val position = p.currentPosition
                    p.setMediaItem(it, position)
                    p.prepare()
                    return
                }
            }
            playbackEngine?.restartQualityWindow()
            PlaybackFailureClassifier.failedVideoDecoder(error)?.let { (format, name) ->
                codecs.decoderHealth?.forget(format, name)
            }
            if (recover(error)) return
            if(playbackEngine?.recover(error)==true)return
            Toast.makeText(
                this@createPlayer,
                getString(
                    R.string.pl_source_cannot_play,
                    getString(PlaybackErrors.message(error.errorCode)),
                ),
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    })

    view.player = p
    player = p
    sleepFade?.refresh()
    view.addOnLayoutChangeListener {_,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom->
        if(player===p && right>left && bottom>top &&
            (right-left!=oldRight-oldLeft || bottom-top!=oldBottom-oldTop))applyPictureEnhancement()
    }
    platformSession = if(privatePlayback)null else PlatformPlaybackSession(this, p, { endPlayback() }, { if (fileQueue!=null) playNext() }).also { it.nextAvailable((fileQueue?.items?.size?:0)>1) }
    val subCfg = prefs.subtitleConfig
    view.subtitleView?.let { sv ->
        SubtitleEngine.style(sv, subCfg)
        baseSubCfg = subCfg
        val overlay = androidx.media3.ui.SubtitleView(this).apply {
            visibility = android.view.View.INVISIBLE
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val parent = sv.parent as android.view.ViewGroup
        parent.addView(overlay, parent.indexOfChild(sv) + 1, android.widget.FrameLayout.LayoutParams(sv.layoutParams))
        overlay.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) subtitleTicker.refresh()
        }
        SubtitleEngine.style(overlay, subCfg)
        sv.visibility = android.view.View.INVISIBLE
        val externalSubtitles = SubtitleEngine(overlay) { subtitleTicker.refresh() }.apply {
            onAnimationChanged = { animateAssOverlay = it }
        }
        subs = SubtitleController(this, { player }, externalSubtitles,
            subConfigFor(subCfg), { refreshSubtitlePanel(); updateSubtitleSheet(); subtitleTicker.refresh() },
            nativeBackend = { playbackEngine?.compatibility },
            showOwner = { owner ->
                val native = playbackEngine?.compatibility
                val drawing = subtitleDrawing(owner, native != null)
                sv.visibility = if (drawing.embedded) android.view.View.VISIBLE else android.view.View.INVISIBLE
                overlay.visibility = if (drawing.overlay) android.view.View.VISIBLE else android.view.View.INVISIBLE
                native?.primarySubtitleVisible(drawing.native)
            },
        ) { text,format,wholeFile ->
            val captions=prefs.subtitleConfig
            val styled = format == "ass" && captions.authoredStyles && SubtitleStyleProbe.styled(text)
            if(SubtitleStyleProbe.routeToMpv(config.engine=="auto",true,captions.systemStyle,styled||captions.layout.needsCompatibility,p.videoFormat,playbackEngine?.selectedAudio()))
                playbackEngine?.switch(if(styled)R.string.ui_core_styled_subtitles else R.string.ui_core_feature)
            playbackEngine?.compatibility?.external(text,format,wholeFile)==true
        }
        subs.onManualSelection={subtitleSelectionChanged()}
        subs.engine.offsetMs=subCfg.defaultDelayMs.coerceIn(-600000,600000)
        p.subtitleDelayMilliseconds=subs.engine.offsetMs
        playbackEngine?.compatibility?.subtitleDelay(subs.engine.offsetMs)
    }
    subtitleTicker.refresh()
    handler.post(markerTicker)
    start(url, title, intent.getStringExtra(PlayRequest.EXTRA_RELEASE))
    view.requestFocus()
    handler.postDelayed(saver, 5_000)
}
