package app.ember.tv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** ListenerSet compares adapters during removal, so Object methods must use proxy identity. */
internal fun playbackListener(listener: Player.Listener, player: () -> Player): Player.Listener =
    Proxy.newProxyInstance(Player.Listener::class.java.classLoader, arrayOf(Player.Listener::class.java)) { proxy, callback, values ->
        try {
            when (callback.name) {
                "equals" -> proxy === values?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "EmberPlaybackListener"
                "onEvents" -> { listener.onEvents(player(), values!![1] as Player.Events); null }
                else -> callback.invoke(listener, *(values ?: emptyArray()))
            }
        } catch (e: InvocationTargetException) { throw e.targetException }
    } as Player.Listener

/** One public player identity. Backend changes retain controls, position and reporting owners. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackEngine(private val context:Context,val standard:ExoPlayer,private val config:()->PlaybackConfig,
    private val compatibilityFactory:((EngineSource,Long,TrackSelectionParameters)->Player)?=null,
    private val recoveryAllowed:()->Boolean={true},
    private val recovery: DecoderRecovery = DecoderRecovery(),
    private val changed:(Boolean)->Unit) {
    internal var selectionReason=if(config().engine=="auto")app.ember.tv.R.string.ui_core_default else app.ember.tv.R.string.ui_core_manual;private set
    private var active:Player=standard
    private val listeners=linkedMapOf<Player.Listener,Player.Listener>()
    private var surfaceMethod:Method?=null
    private var surface:Any?=null
    private var source:EngineSource?=null
    private var previousHdrRestoreFailure: HdrSurfaceResult? = null
    val hdrRestoreFailure get() = compatibility?.surfaceRestoreFailure ?: previousHdrRestoreFailure
    private var mode=config().engine
    private var released=false
    private var health:DecoderHealth?=if(config().rememberDecoderChoices)DecoderHealth.current() else null
    private val handler=Handler(Looper.getMainLooper())
    private val seekFlush:Runnable=Runnable {navigation.flush()}
    private val navigation:NavigationSeekQueue=NavigationSeekQueue({handler.postDelayed(seekFlush,60)},{handler.removeCallbacks(seekFlush)}) {at,fast->
        if(!released)seekNow(at,fast)
    }
    private var stable:Runnable?=null
    private fun memoryKey()=source?.let {app.ember.tv.data.CachePolicy.digest("${it.key}|${it.filename.orEmpty()}|${it.audio!=null}")}
    private var videoWindow: PlaybackQualityWindow<DecoderCounters>? = null

    private fun DecoderCounters.frames(): FrameCount {
        ensureUpdated()
        return FrameCount(renderedOutputBufferCount, droppedBufferCount)
    }

    fun standardQuality(): PlaybackQuality? {
        if (active !== standard || !standard.isPlaying) return null
        val counters = standard.videoDecoderCounters ?: return null
        return videoWindow?.measure(counters, SystemClock.elapsedRealtime(), counters.frames())
    }

    private fun clearQualityWindow() {
        stable?.let(handler::removeCallbacks)
        stable = null
        videoWindow = null
    }

    fun restartQualityWindow() {
        clearQualityWindow()
        if (released || !active.isPlaying || !config().rememberDecoderChoices) return
        val owner = active
        if (owner === standard) {
            standard.videoDecoderCounters?.let {
                videoWindow = PlaybackQualityWindow(it, SystemClock.elapsedRealtime(), it.frames())
            }
        }
        val key = memoryKey() ?: return
        if (mode != "auto") return
        stable = Runnable {
            stable = null
            if (released || active !== owner || !owner.isPlaying || memoryKey() != key ||
                mode != "auto" || !config().rememberDecoderChoices) return@Runnable
            val video = owner.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected } ||
                owner === standard && standard.videoFormat != null
            val quality = standardQuality() ?: PlaybackQuality()
            if (quality.learnable(video)) health?.rememberEngine(key, if (owner === standard) "standard" else "compatibility")
        }.also { handler.postDelayed(it, STABLE_PLAYBACK_MS) }
    }

    private val stabilityListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = restartQualityWindow()

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            restartQualityWindow()
        }
    }
    init {
        standard.addListener(stabilityListener)
        if(config().rememberDecoderChoices && health==null)DecoderHealth.loadAsync(context) {if(!released){health=it;if(active.isPlaying)restartQualityWindow()}}
    }
    val compatibility get()=if(active===standard)null else active as? MpvPlayer
    internal fun snapshot():EngineSource? {
        val native=compatibility
        val current=native?.source ?: source ?: return null
        return current.copy(edition=if(native!=null)native.currentEdition ?: current.edition else null)
    }
    private fun key(method:Method)=method.name+method.parameterTypes.joinToString {it.name}
    private val common=Player::class.java.methods.associateBy(::key)
    val player:ExoPlayer=Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)) { proxy,method,args ->
        val arguments=args?:emptyArray()
        try {
            when(method.name) {
                "equals" -> proxy===arguments.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "EmberPlaybackEngine"
                "addListener" -> {val listener=arguments[0] as Player.Listener
                    val adapter=listeners.getOrPut(listener) {playbackListener(listener) {player}};active.addListener(adapter);null}
                "removeListener" -> {listeners.remove(arguments[0])?.let(active::removeListener);null}
                // Drop the released libmpv instance: native calls on a destroyed handle crash the process.
                "release" -> {released=true;navigation.cancel();cancelTrackRestore();clearQualityWindow();active.removeListener(stabilityListener);listeners.values.forEach(active::removeListener);if(active!==standard)releaseCompatibility(active);active=standard;standard.release();listeners.clear();null}
                "getCurrentPosition", "getContentPosition" -> navigation.target ?: if(method.name=="getContentPosition")active.contentPosition else active.currentPosition
                "seekBack", "seekForward" -> {
                    val delta=if(method.name=="seekBack")-active.seekBackIncrement else active.seekForwardIncrement
                    navigate(PlaybackSeeking.target(navigation.target?:active.currentPosition,delta,active.duration),SeekPreference.fast(config().seekMode));null
                }
                "prepare" -> {val current=source
                    val learned=if(config().rememberDecoderChoices)memoryKey()?.let {health?.engine(it)} else null
                    val restoredEdition=current?.edition!=null
                    val automatic=current?.urls?.firstOrNull()?.let {PlaybackEnginePolicy.nativeFirst(it,current.filename)}==true
                    if(active===standard && (mode=="compatibility" || mode=="auto" && (restoredEdition || learned=="compatibility" || learned==null&&automatic))) {
                        if(!switch(if(mode=="compatibility")app.ember.tv.R.string.ui_core_manual else if(restoredEdition)app.ember.tv.R.string.ui_core_feature else if(learned=="compatibility")app.ember.tv.R.string.ui_core_remembered else app.ember.tv.R.string.ui_core_format)) {
                            selectionReason=app.ember.tv.R.string.ui_core_unavailable;standard.prepare()
                        }
                    } else {if(active===standard)selectionReason=if(mode!="auto")app.ember.tv.R.string.ui_core_manual else if(learned=="standard")app.ember.tv.R.string.ui_core_remembered else app.ember.tv.R.string.ui_core_default;active.prepare()};null}
                "getVideoFormat" -> compatibility?.currentTracks?.groups?.firstOrNull {it.type==androidx.media3.common.C.TRACK_TYPE_VIDEO&&it.isSelected}?.getTrackFormat(0) ?: if(active===standard)standard.videoFormat else null
                "getAudioFormat" -> compatibility?.currentTracks?.groups?.firstOrNull {it.type==androidx.media3.common.C.TRACK_TYPE_AUDIO&&it.isSelected}?.getTrackFormat(0) ?: if(active===standard)standard.audioFormat else null
                else -> {
                    if(method.name in setOf("seekTo","seekToDefaultPosition","seekToNextMediaItem","seekToPreviousMediaItem","setMediaItem","setMediaItems","setMediaSource","setMediaSources","replaceMediaItem","replaceMediaItems","removeMediaItem","removeMediaItems","seekToNext","seekToPrevious","clearMediaItems","stop"))navigation.cancel()
                    if(method.name in setOf("setVideoSurface","setVideoSurfaceView","setVideoSurfaceHolder","setVideoTextureView")) {surfaceMethod=common[key(method)];surface=arguments.firstOrNull()}
                    if (method.name in setOf("clearVideoSurface", "clearVideoSurfaceView", "clearVideoSurfaceHolder", "clearVideoTextureView") &&
                        (arguments.isEmpty() || arguments[0] === surface)) {
                        surfaceMethod = null
                        surface = null
                    }
                    val target=if(active!==standard)common[key(method)] else null
                    if(target!=null)target.invoke(active,*arguments) else method.invoke(standard,*arguments)
                }
            }
        } catch(e:InvocationTargetException){throw e.targetException}
    } as ExoPlayer
    fun navigate(target: Long, fast: Boolean, coalesce:Boolean=true) {
        val bounded=target.coerceIn(0,active.duration.takeIf {it>0}?:Long.MAX_VALUE)
        if(coalesce)navigation.request(bounded,fast) else {navigation.cancel();seekNow(bounded,fast)}
    }
    fun flushNavigation(){navigation.flush()}
    private fun seekNow(target:Long,fast:Boolean) {
        compatibility?.let { it.seekNavigation(target,fast);return }
        PlaybackSeeking.seek(standard,target,fast)
    }
    fun configure(value:EngineSource) {
        navigation.cancel()
        clearQualityWindow()
        cancelTrackRestore()
        if (active !== standard) {
            returnToStandard()
            changed(false)
        }
        source=value;recovery.reset();selectionReason=if(mode=="auto")app.ember.tv.R.string.ui_core_default else app.ember.tv.R.string.ui_core_manual
    }
    fun mode(value:String) {
        mode=value
        if(value=="auto")selectionReason=app.ember.tv.R.string.ui_core_retained
        else if(value=="standard"&&active===standard || value=="compatibility"&&active!==standard)selectionReason=app.ember.tv.R.string.ui_core_manual
        if (value == "compatibility" && active === standard) {
            if (!switch(app.ember.tv.R.string.ui_core_manual, manual = true)) selectionReason = app.ember.tv.R.string.ui_core_unavailable
        } else if (value == "standard" && active !== standard) {
            val outgoing = active
            val position = navigation.target ?: outgoing.currentPosition
            val wanted = outgoing.playWhenReady
            val parameters = outgoing.playbackParameters
            val volume = outgoing.volume
            val repeat = outgoing.repeatMode
            val tracks = outgoing.trackSelectionParameters
            navigation.cancel()
            clearQualityWindow()
            returnToStandard()
            selectionReason = app.ember.tv.R.string.ui_core_manual
            standard.trackSelectionParameters = tracks.buildUpon().also { builder ->
                // Backend track groups have different IDs. Retain the remembered Standard overrides
                // until its groups can be used to map the new selection.
                for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
                    builder.clearOverridesOfType(type)
                    standard.trackSelectionParameters.overrides.values.firstOrNull { it.type == type }?.let(builder::addOverride)
                }
            }.build()
            restoreTrackSelection(tracks)
            standard.seekTo(position)
            standard.playbackParameters = parameters
            standard.volume = volume
            standard.repeatMode = repeat
            standard.playWhenReady = wanted
            standard.prepare()
            changed(false)
        }
    }

    private fun returnToStandard() {
        val outgoing = active
        outgoing.removeListener(stabilityListener)
        listeners.values.forEach(outgoing::removeListener)
        releaseCompatibility(outgoing)
        active = standard
        standard.addListener(stabilityListener)
        listeners.values.forEach(standard::addListener)
        attachSurface(standard)
    }

    private fun releaseCompatibility(owner: Player) {
        try {
            owner.release()
        } finally {
            (owner as? MpvPlayer)?.surfaceRestoreFailure?.let { previousHdrRestoreFailure = it }
        }
    }

    private var trackRestore: Player.Listener? = null

    private fun cancelTrackRestore() {
        trackRestore?.let(standard::removeListener)
        trackRestore = null
    }

    private fun restoreTrackSelection(parameters: TrackSelectionParameters) {
        cancelTrackRestore()
        val overrides = parameters.overrides.values.filter { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT }
        if (overrides.isEmpty()) return
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (tracks.groups.isEmpty()) return
                cancelTrackRestore()
                val builder = standard.trackSelectionParameters.buildUpon()
                for (override in overrides) {
                    val target = override.trackIndices.firstOrNull()?.let(override.mediaTrackGroup::getFormat) ?: continue
                    matchingPlaybackTrack(tracks, override.type, target)?.let(builder::setOverrideForType)
                }
                standard.trackSelectionParameters = builder.build()
            }
        }
        trackRestore = listener
        standard.addListener(listener)
        listener.onTracksChanged(standard.currentTracks)
    }

    private fun attachSurface(target: Player) {
        surfaceMethod?.let { method -> surface?.let { method.invoke(target, it) } }
    }

    internal fun selectedAudio(): Format? {
        if (active !== standard) return null
        val parameters = standard.trackSelectionParameters
        if (C.TRACK_TYPE_AUDIO in parameters.disabledTrackTypes) return null
        val groups = standard.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        groups.firstNotNullOfOrNull { group ->
            parameters.overrides[group.mediaTrackGroup]?.trackIndices?.firstOrNull()?.let(group::getTrackFormat)
        }?.let { return it }
        return groups.firstNotNullOfOrNull { group ->
            (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
        } ?: standard.audioFormat
    }

    private fun protectedAudio(audio: Format?) = audio?.drmInitData != null ||
        standard.videoFormat?.drmInitData != null ||
        standard.currentMediaItem?.localConfiguration?.drmConfiguration != null

    private fun preservesStandardAudio(audio: Format?) =
        PlaybackEnginePolicy.preservesAudio(audio?.sampleMimeType, protectedAudio(audio))

    fun recover(error: PlaybackException): Boolean {
        if (active !== standard || !PlaybackRoutePlanner.canSwitchCore(
                PlaybackFailureClassifier.classify(error), mode == "auto", preservesStandardAudio(selectedAudio()),
            )) return false
        return switch(app.ember.tv.R.string.ui_core_recovery)
    }

    fun switch(reason: Int = app.ember.tv.R.string.ui_core_feature, manual: Boolean = false): Boolean {
        if (mode == "standard" || active !== standard) return false
        val current = source ?: return false
        if (mode == "auto" && reason in setOf(app.ember.tv.R.string.ui_core_recovery, app.ember.tv.R.string.ui_core_remembered,
                app.ember.tv.R.string.ui_core_format, app.ember.tv.R.string.ui_core_styled_subtitles)) {
            val c = config()
            if (!recoveryAllowed() || c.tunneling || c.ffmpegVideo in setOf("always", "off") ||
                c.ffmpeg in setOf("prefer", "off") ||
                c.decoderRules.preference(current.urls.firstOrNull().orEmpty(), standard.videoFormat?.sampleMimeType,
                    current.filename) !in setOf("inherit", "auto")) return false
        }
        val audio = selectedAudio()
        val canPreparePcm = (manual || mode == "compatibility") && !protectedAudio(audio) &&
            CompatibilityPcmAudio.supports(audio?.sampleMimeType) &&
            current.urls.singleOrNull()?.let { url ->
                !app.ember.player.PrivateMedia.hidden(url) && !standard.isCurrentMediaItemLive &&
                    CompatibilityPcmAudio.eligible(url, current.filename, current, config())
            } == true
        if (!preservesStandardAudio(audio) && !canPreparePcm) return false
        val route = PlaybackRoute(PlaybackCore.MPV, "mpv-native", audio?.sampleMimeType, "automatic")
        if (!recovery.attempt(route, manual)) return false
        if (reason == app.ember.tv.R.string.ui_core_recovery) {
            memoryKey()?.let { key -> if (health?.engine(key) == "standard") health?.forgetEngine(key) }
        }
        val position = (navigation.target ?: standard.currentPosition).coerceAtLeast(0)
        val desired = standard.playWhenReady
        val state = standard.playbackState
        val speed = standard.playbackParameters
        val volume = standard.volume
        val repeat = standard.repeatMode
        val parameters = standard.trackSelectionParameters.buildUpon().also { builder ->
            val existing = standard.trackSelectionParameters
            for (type in listOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
                if (type in existing.disabledTrackTypes || existing.overrides.values.any { it.type == type }) continue
                val group = standard.currentTracks.groups.firstOrNull { it.type == type && it.isSelected } ?: continue
                val track = (0 until group.length).firstOrNull(group::isTrackSelected) ?: continue
                builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track))
            }
        }.build()
        val native=try {
            val input=current.copy(live=current.live||standard.isCurrentMediaItemLive,durationMs=current.durationMs ?: standard.duration.takeIf {it>0&&current.urls.size==1})
            compatibilityFactory?.invoke(input, position, parameters) ?: MpvPlayer(context, input, config(), position, parameters)
        } catch(_:Exception){return false} catch(_:LinkageError){return false}
        try {
            navigation.cancel()
            clearQualityWindow()
            cancelTrackRestore()
            standard.removeListener(stabilityListener);listeners.values.forEach(standard::removeListener)
            standard.clearVideoSurface();standard.stop()
            active=native;native.addListener(stabilityListener);listeners.values.forEach(native::addListener)
            attachSurface(native)
            native.playbackParameters = speed
            native.volume = volume
            native.repeatMode = repeat
            native.playWhenReady = desired
            native.prepare();selectionReason=reason;changed(true);return true
        } catch(e:Throwable) {
            if(e !is Exception && e !is LinkageError)throw e
            clearQualityWindow()
            runCatching {native.removeListener(stabilityListener)};listeners.values.forEach {runCatching {native.removeListener(it)}}
            runCatching {releaseCompatibility(native)};active=standard
            standard.addListener(stabilityListener);listeners.values.forEach(standard::addListener)
            runCatching {attachSurface(standard)}
            standard.seekTo(position);standard.playWhenReady=desired
            if(state==Player.STATE_READY || state==Player.STATE_BUFFERING)runCatching {standard.prepare()}
            runCatching {changed(false)}
            return false
        }
    }
}

/** Match a selection across backends even when their track-group IDs differ. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun matchingPlaybackTrack(tracks: androidx.media3.common.Tracks, type: Int, target: Format): TrackSelectionOverride? {
    val language = app.ember.tv.data.Subtitles.normalise(target.language)
    val forced = target.selectionFlags and C.SELECTION_FLAG_FORCED
    val candidates = tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it, true) }.map { group to it }
    }.filter { (group, index) ->
        val format = group.getTrackFormat(index)
        app.ember.tv.data.Subtitles.normalise(format.language) == language &&
            (format.selectionFlags and C.SELECTION_FLAG_FORCED) == forced
    }
    val match = candidates.firstOrNull { (group, index) -> group.getTrackFormat(index).id == target.id && target.id != null }
        ?: candidates.maxByOrNull { (group, index) ->
            val format = group.getTrackFormat(index)
            (if (target.label != null && target.label == format.label) 4 else 0) +
                (if (target.sampleMimeType == format.sampleMimeType) 2 else 0) +
                (if (target.channelCount > 0 && target.channelCount == format.channelCount) 1 else 0)
        }
    return match?.let { (group, index) -> TrackSelectionOverride(group.mediaTrackGroup, index) }
}
