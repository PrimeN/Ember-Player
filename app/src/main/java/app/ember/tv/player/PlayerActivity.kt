package app.ember.tv.player

import androidx.compose.material.icons.outlined.*

import androidx.core.view.isVisible

import app.ember.tv.R
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.lifecycle.withStarted
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.ember.tv.data.Prefs
import app.ember.tv.player.PlaybackItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode

/**
 * Full-screen player built on Media3 ExoPlayer.
 * Remembers playback positions and advances the local file queue.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    internal var player: ExoPlayer? = null
        set(value){field=value;updateBackHandler()}
    internal var playbackEngine: PlaybackEngine? = null
    internal lateinit var view: PlayerView
    internal lateinit var positions: PositionStore
    internal lateinit var prefs: Prefs
    internal lateinit var item: PlaybackItem

    internal var resumeKey = ""
    internal var resumeFraction = -1f
    internal var resumed = false
    internal var ended = false
        set(value){field=value;updateBackHandler()}
    internal var seriesName = ""
    internal var playbackVisit = 0L
    internal var pendingRecovery: Runnable? = null

    internal val metadataLoads by lazy { PlaybackMetadataLoads(lifecycleScope) }
    @Volatile internal var hdr10PlusMetadata = false
    internal var platformSession: PlatformPlaybackSession? = null

    // Media links can redirect and high-bitrate sources may need longer timeouts.
    internal val httpFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(prefs.playbackConfig.stream.normalized().connectSeconds*1000)
            .setReadTimeoutMs(prefs.playbackConfig.stream.normalized().readSeconds*1000)
            .setUserAgent(app.ember.tv.data.HttpIdentity.userAgent)
    }

    internal var queueId:String?=null
    internal var fileQueue:MediaQueue?=null
    internal val repeatRange=RepeatRange()
    internal var pickingSidecar=false
        set(value){field=value;updatePictureInPicture()}
    internal var pickerWasPlaying=false
    internal var captionForeground=false
    internal var captionAssistant:CaptionAssistant?=null
    internal var captionConfig:app.ember.tv.data.SubtitleConfig?=null
    internal var captionOptions:app.ember.tv.data.SubtitleAssistance?=null
    internal var captionOverlay:androidx.media3.ui.SubtitleView?=null
    internal var embeddedCaptionText=""
    internal var privatePlayback=false
        set(value){field=value;updatePictureInPicture()}
    internal val clips=PlayerClipExport(this)
    internal val exports=PlayerExports(this)
    internal var sleepFade:app.ember.tv.data.SleepFade?=null
    internal var sleepWatch:java.io.Closeable?=null
    internal var usbWatch:java.io.Closeable?=null
    internal var manualSubtitleJob:Job?=null
    internal var nearbySubtitleJob:Job?=null
    internal val subtitleFilePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) {uris ->
        pickingSidecar=false
        app.ember.player.PrivateMedia.endPicker()
        if(uris.isNotEmpty()&&::subs.isInitialized)loadPickedSubtitles(uris)
        player?.playWhenReady=pickerWasPlaying
    }
    internal val subtitleFolderPicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) {uri ->
        pickingSidecar=false
        app.ember.player.PrivateMedia.endPicker()
        if(uri!=null&&::subs.isInitialized)saveSubtitleFolder(uri)
        player?.playWhenReady=pickerWasPlaying
    }
    internal val audioFilePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri ->
        pickingSidecar=false
        app.ember.player.PrivateMedia.endPicker()
        if(uri!=null){
            val native=playbackEngine?.compatibility ?: if(playbackEngine?.switch()==true)playbackEngine?.compatibility else null
            if(native!=null)native.additionalAudio(uri.toString()) else Toast.makeText(this,resources.getString(R.string.ui_could_not_start_compatibility_playback),Toast.LENGTH_LONG).show()
        }
        player?.playWhenReady=pickerWasPlaying;changed()
    }
    internal val queueFilePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) {uris ->
        pickingSidecar=false
        app.ember.player.PrivateMedia.endPicker()
        runCatching {fileQueue?.add(uris.map {MediaFiles.retain(this,it);QueueMedia(it.toString(),MediaFiles.name(this,it))})}
            .onFailure {Toast.makeText(this,resources.getString(R.string.ui_the_queue_is_full_create_a_separate_playlist),Toast.LENGTH_LONG).show()}
        player?.playWhenReady=pickerWasPlaying;changed()
    }
    /** Optional separate audio source restored during playback handoff. */
    internal var audioUrl: String? = null

    /** Some providers only answer a particular player name or referrer. */
    internal var sourceHeaders:Map<String,String> = mapOf("User-Agent" to app.ember.tv.data.HttpIdentity.userAgent)

    companion object {
        internal var current: java.lang.ref.WeakReference<PlayerActivity>? = null
        const val EXTRA_AUDIO_URL = "ember.audioUrl"
    }

    // Playback engine (Settings > Player)
    internal lateinit var config: PlaybackConfig
    internal var audioPicked = false
    internal var modeApplied = false
    internal var videoDecoder: String? = null
    internal var decoderLearned=false
    internal var audioDecoder: String? = null
    internal lateinit var info: TextView
    /** Info and subtitle panel (Info, Menu or Guide). */
    internal lateinit var panel: android.widget.ScrollView
    internal lateinit var subStatus: TextView
    internal lateinit var subList: LinearLayout
    internal lateinit var timingLabel: TextView
    internal lateinit var subs: SubtitleController
    internal val handler = Handler(Looper.getMainLooper())
    internal var subtitleUpdatesStarted=false
    internal val subtitleTicker = SubtitleTicker(handler,{subtitleUpdatesStarted&&player!=null&&!isFinishing},{
        player?.isPlaying==true&&(::subs.isInitialized&&subs.engine.active||repeatRange.end!=null)
    }) {
            val p = player
            if(p!=null && p.isPlaying)repeatRange.destination(p.currentPosition)?.let(p::seekTo)
            if (p != null && ::subs.isInitialized) {val prefix=if(subs.wholeFileTiming)0L else (playbackEngine?.compatibility?.partOffset)?:0L;subs.engine.render(p.currentPosition,prefix)}
            if(p!=null&&::prefs.isInitialized){
                val native=playbackEngine?.compatibility
                val text=if(native!=null)native.captionText else if(::subs.isInitialized&&(subs.selection is SubtitleController.Selection.Manual||subs.selection is SubtitleController.Selection.External))subs.engine.visibleText else embeddedCaptionText
                updateCaptionAssistance(text,p.isPlaying)
            }
    }

    // ---- resume, per-show tracks, picture-in-picture ----
    internal var startOver = false
    internal lateinit var memory: TrackMemory
    /** The user picked a subtitle in the panel, so it is remembered for the show. */
    internal var subsTouched = false
    internal var baseSubCfg: app.ember.tv.data.SubtitleConfig? = null

    /** The subtitle settings with this show's remembered choice applied. */
    internal fun subConfigFor(base: app.ember.tv.data.SubtitleConfig): app.ember.tv.data.SubtitleConfig {
        val effective=if(privatePlayback)base.copy(stremioOpenSubs=false,openSubsCom=false,subdl=false,hashMatch=false) else base
        val mem = showKey()?.let { memory.get(it) }?.subtitles ?: return effective
        return if (mem == "off") effective.copy(autoSelect = "off")
        else effective.copy(autoSelect = "always", languages = listOf(mem) + base.languages.filter { it != mem })
    }

    /** Picture-in-picture when Home is pressed during playback (Settings > Player). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if(pickingSidecar)return
        // Home (or another app) chosen by the user: the player closes when it stops (unless picture-in-picture).
        userLeft = true
        if(android.os.Build.VERSION.SDK_INT>=31)return
        if(!pictureInPictureAllowed())return
        runCatching { enterPictureInPictureMode(pictureInPictureParams()) }.onSuccess {if(it)userLeft=false}
    }

    // ---- paused when the screensaver starts: stay open, so the video is still there afterwards ----
    internal var userLeft = false
    internal var keptAlive = false
    /** A paused player kept in the background closes after half an hour (the TV may be off by then). */
    internal val closeKept = Runnable { endPlayback() }

    internal var lastPipState:Any?=null
    internal var pipActions:PlayerPipActions?=null
    internal var pictureInPictureEntered=false

    override fun onResume(){super.onResume();backgroundStage?.let(BackgroundAudioService::cancelStage);backgroundStage=null;pictureInPictureEntered=false;updatePictureInPicture();captionConfig=null;captionForeground=true;subtitleTicker.refresh()}

    override fun onStart() {
        super.onStart()
        subtitleUpdatesStarted=true
        backgroundStage?.let(BackgroundAudioService::cancelStage);backgroundStage=null
        if(::prefs.isInitialized && !contentPlaybackAllowed()){endPlayback();finish();return}
        userLeft = false
        if (!keptAlive) return
        keptAlive = false
        handler.removeCallbacks(closeKept)
        subtitleTicker.refresh()
        handler.post(markerTicker)
        handler.postDelayed(saver, 5_000)
        if (advanced) {
            handler.post(controlsTicker)
            handler.post(statsTicker)
            showControls()
        }
    }

    override fun onDestroy() {
        pipActions?.close();pipActions=null
        playbackBackCallback?.remove();playbackBackCallback=null
        sleepFade?.close();sleepFade=null;clips.close();exports.close();sleepWatch?.close();sleepWatch=null
        closeCaptionAssistance()
        backgroundStage?.let(BackgroundAudioService::cancelStage);backgroundStage=null
        touchControls?.close(); touchControls = null
        // A dialog still showing would leak its window when the player closes (#77).
        actionDialog?.dismiss(); actionDialog = null
        outputRoute?.close(); outputRoute=null
        leaveDialog?.dismiss(); leaveDialog = null
        getSharedPreferences("ember",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(contentProfileListener)
        handler.removeCallbacks(closeKept)
        // Closed by the system while kept in the background.
        if (player != null) endPlayback(finishActivity = false)
        if (!isChangingConfigurations) {if(!backgroundQueueTransferred)MediaQueues.release(queueId)}
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            pictureInPictureEntered=true;userLeft=false
            stageBackgroundAudio(allowPip=true)
            backgroundStage?.let(BackgroundAudioService::retainStage)
        } else if(pictureInPictureEntered && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            if(getSystemService(android.os.PowerManager::class.java).isInteractive)endPlayback()
            return
        }
        if (!::view.isInitialized) return
        view.useController = !isInPictureInPictureMode && !advanced
        if (isInPictureInPictureMode) {
            controls.visible = false
            closeMenu()
            view.hideController()
            if (::panel.isInitialized) hidePanel()
            showSkip(null)
        }
    }

    // ---- advanced controls (Settings > Player > Player look) ----
    internal val controls = ControlsState {updateBackHandler()}
    internal var advanced = false
    internal var playerInterface = PlayerInterfaces.GLASS
    internal val playerLayoutStyle: String
        get() = PlayerInterfaces.layoutStyle(playerInterface)
    internal val hideControls = Runnable {
        // Paused, or with a list open, the controls stay.
        if (player?.isPlaying == true && controls.sheet == null) controls.visible = false else scheduleHide()
    }
    internal val controlsTicker = object : Runnable {
        override fun run() {
            updateControls()
            handler.postDelayed(this, 500)
        }
    }

    internal fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 5_000)
    }

    internal fun showControls() {
        if (touchControls?.locked == true) return
        if (!advanced || controls.menu != null) return
        updateControls()
        if (!controls.visible) {
            // Sound and Subtitles lists (every design) show the current choices without opening a menu.
            controls.audioTracks = audioTracks(); updateSubtitleSheet()
            controls.visible = true
            controls.shownAt = android.os.SystemClock.elapsedRealtime()
        }
        scheduleHide()
    }

    internal fun updateControls() {
        val p = player ?: return
        controls.playing = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
        controls.buffering = p.playbackState == Player.STATE_BUFFERING
        controls.playbackSpeed = p.playbackParameters.speed
        controls.position = p.currentPosition
        controls.duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        controls.buffered = p.bufferedPosition
    }

    internal fun refreshControlsTitle() {
        controls.title = item.title
        controls.seasonEpisode = if (item.season != null && item.episode != null) "S${item.season}:E${item.episode}" else null
        controls.fileQueueLabel=fileQueue?.let {"${it.index+1} / ${it.items.size}"}
        controls.fileQueueNext=fileQueue?.hasNext()==true
    }

    internal val controlActions by lazy {
        ControlsActions(
            seekStep = {TouchPlaybackMath.step(config.touch.seekSeconds)},
            playPause = { player?.let { it.playWhenReady = !it.playWhenReady }; showControls() },
            seekBy = { d -> player?.let { p -> navigateSeek(PlaybackSeeking.target(p.currentPosition,d,p.duration)) }; showControls() },
            skip = { doSkip() },
            openMenu = { which, tab -> openMenu(which, tab) },
            options = { which -> optionRows(which) },
            next = {playNext()},
            touched = { scheduleHide() },
            queue = {showFileQueue()},
            close = {finish()},
            pickAudio = { i ->
                pickAudioTrack(i)
                controls.audioTracks = controls.audioTracks.mapIndexed { index, (label, _) -> label to (index == i) }
                handler.postDelayed({ controls.audioTracks = audioTracks(); changed() }, 400)
                showControls()
            },
            pickSubtitle = { i ->
                subtitleActions.getOrNull(i)?.let { subsTouched = true; it() }
                updateSubtitleSheet()
                showControls()
            },
        )
    }

    // ---- player menus (advanced look): the small menu, the Options page and playback stats ----
    internal var speed = 1f
    internal var compareOriginal=false
    internal var pictureBypassed=false
    internal var pictureSignature:String?=null
    internal var pictureEffectsActive=false
    internal var boostLevel = 0
    internal val audioEffectState = AudioEffectState()
    internal var outputRoute: OutputRouteMonitor? = null
    internal var bluetoothOutput = false
    internal var loudness: android.media.audiofx.LoudnessEnhancer? = null
    internal var aspect = 0
    internal var chapters: List<app.ember.tv.player.Chapter> = emptyList()
    /** Network estimate and speed of the latest loads, in bits per second. */
    internal var bandwidth = 0L
    internal var loadSpeed = 0L
    internal var dropped = 0

    internal val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
    /** Volume boost steps in millibels (+3, +6, +9, +12 dB). */
    internal val boosts = listOf(0, 300, 600, 900, 1200)
    internal val aspects by lazy { listOf(getString(R.string.pl_auto) to 0f, "16:9" to 16f / 9, "4:3" to 4f / 3, "1.85:1" to 1.85f, "2.00:1" to 2f, "2.39:1" to 2.39f) }
    internal val zooms by lazy { listOf("fit" to getString(R.string.pl_fit), "crop" to getString(R.string.pl_fill_crop), "stretch" to getString(R.string.pl_stretch)) }
    internal val huds by lazy { listOf("off" to getString(R.string.ui_off), "simple" to getString(R.string.pl_simple), "full" to getString(R.string.pl_full)) }
    internal val subSizes by lazy { listOf("small" to getString(R.string.pl_small), "normal" to getString(R.string.pl_normal), "large" to getString(R.string.pl_large), "xlarge" to getString(R.string.pl_extra_large)) }
    internal val subPositions by lazy { listOf("low" to getString(R.string.pl_low), "normal" to getString(R.string.pl_normal), "high" to getString(R.string.pl_high)) }
    internal val subLooks by lazy { listOf("outline" to getString(R.string.pl_outline), "shadow" to getString(R.string.pl_shadow), "box" to getString(R.string.pl_box)) }
    internal val delays = (-10..10).map { it * 50 }
    internal val subShifts = (-20..20).map { it * 250L }

    /** The address being played, for reading MKV chapters. */
    internal var currentUrl: String? = null
    /** The current link was retried as HLS after ExoPlayer could not read it as a file. */
    internal var triedAsHls = false

    internal val statsTicker = object : Runnable {
        override fun run() {
            updateStats()
            if (config.hud != "off") handler.postDelayed(this, 1000)
        }
    }

    /** Keys while the advanced controls are in use; null lets the key through. */
    @android.annotation.SuppressLint("RestrictedApi") // super.dispatchKeyEvent, as in dispatchKeyEvent below
    internal fun advancedKey(e: KeyEvent): Boolean? {
        val p = player ?: return null
        val down = e.action == KeyEvent.ACTION_DOWN
        val up = e.action == KeyEvent.ACTION_UP
        when (e.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down && e.repeatCount == 0) {
                    p.playWhenReady = when (e.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> true
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> false
                        else -> !p.playWhenReady
                    }
                }
                if (up) showControls()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down) controlActions.seekBy(if (e.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) 30_000 else -10_000)
                return true
            }
        }
        // An open menu keeps its own focus and navigation keys.
        if (controls.menu != null) {
            return super.dispatchKeyEvent(e)
        }
        if (!controls.visible) {
            return when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Hidden controls: Left and Right jump back 10 s or ahead 10 s and show where you are.
                    if (down) controlActions.seekBy(if (e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 10_000 else -10_000)
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    // OK while "Skip intro" shows skips; anything else brings up the controls.
                    val ok = e.keyCode != KeyEvent.KEYCODE_DPAD_UP && e.keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                    if (up && ok && skipTarget != null) doSkip() else if (up) showControls()
                    true
                }
                else -> null
            }
        }
        scheduleHide()
        return super.dispatchKeyEvent(e)
    }

    // ---- Live TV: bounded retries for transient provider failures ----
    internal val codecs = Playback.CodecChoice()
    internal val audioTiming by lazy {AudioTiming(audioDelay){playbackEngine?.compatibility?.audioDelay(it)}}
    internal val decoderManager = DecoderManager()
    internal val decoderRecovery = DecoderRecovery()
    internal var userVideoMode: DecoderMode? = null
    internal var userAudioMode: DecoderMode? = null
    internal var recoveries = 0
    /** Network hiccups (a dropped connection, a link that stalls) are retried a couple of times. */
    internal var ioRetries = 0
    internal var lastRetryAt=0L

    // ---- sound timing and skip intro ----
    internal val audioDelay = Playback.AudioDelay()
    internal lateinit var soundLabel: TextView
    internal lateinit var skipButton: TextView
    internal var markers: List<app.ember.tv.player.Marker> = emptyList()
    internal var markerChapterState:Pair<List<Chapter>,Long>?=null
    /** Markers already skipped automatically, so seeking back into one does not skip it again. */
    internal val autoSkipped = HashSet<app.ember.tv.player.Marker>()
    internal var skipTarget: app.ember.tv.player.Marker? = null
    internal val markerTicker = object : Runnable {
        override fun run() {
            checkMarkers()
            handler.postDelayed(this, 500)
        }
    }
    internal val infoUpdater = object : Runnable {
        override fun run() {
            if (!::panel.isInitialized || panel.visibility != View.VISIBLE) return
            info.text = infoText()
            handler.postDelayed(this, 1000)
        }
    }
    internal val hideInfo = Runnable { hidePanel() }
    internal val sleepAtStart = app.ember.tv.data.SleepTimer.fired
    internal val saver = object : Runnable {
        override fun run() {
            // Quick settings > Sleep timer ran out: stop (position and progress are saved on the way out).
            if (app.ember.tv.data.SleepTimer.fired != sleepAtStart) {
                finish()
                return
            }
            savePosition()
            rememberWorkingDecoder()
            handler.postDelayed(this, 10_000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sleepFade=app.ember.tv.data.SleepFade {player}
        sleepWatch=app.ember.tv.data.SleepTimer.listen {if(player!=null)endPlayback()}
        lifecycleScope.launch {kotlinx.coroutines.flow.combine(app.ember.player.PrivateMedia.unlocked,app.ember.player.PrivateMedia.state){allowed,_->allowed}.collect {allowed->
            if(!privatePlayback&&fileQueue?.items?.any {app.ember.player.PrivateMedia.hidden(it.uri)}==true){
                actionDialog?.dismiss();actionDialog=null;window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
            if(currentUrl!=null&&app.ember.player.PrivateMedia.hidden(currentUrl.orEmpty())) {
                privatePlayback=true;window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);platformSession?.release();platformSession=null
                if(!allowed){player?.pause();endPlayback()}
            }
        }}
        lifecycleScope.launch {
            try {NativePlayerFiles.await(applicationContext)}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){ /* Media3 remains available if native assets cannot be prepared. */ }
            lifecycle.withStarted {
                if(!isFinishing&&!isDestroyed){createPlayer(savedInstanceState);installBackHandler()}
            }
        }
    }

    private var playbackBackCallback:OnBackPressedCallback?=null
    internal fun installBackHandler() {
        playbackBackCallback?.remove()
        val callback=object:OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if(consumePlayerBack()){updateBackHandler();return}
                isEnabled=false
                try {onBackPressedDispatcher.onBackPressed()}finally {updateBackHandler()}
            }
        }
        playbackBackCallback=callback;onBackPressedDispatcher.addCallback(callback)
        if(viewInitialized)view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener {updateBackHandler()})
        updateBackHandler()
    }

    internal fun updateBackHandler() {
        val callback=playbackBackCallback ?: return
        callback.isEnabled=touchControls?.locked==true||actionDialog?.isShowing==true||panelVisible||
            (player!=null&&(advanced&&(controls.menu!=null||controls.visible)||
                configInitialized&&config.confirmExit&&!ended||!advanced&&viewInitialized&&view.isControllerFullyVisible))
    }

    /** System gestures and remote keys dismiss the same playback layer before leaving. */
    internal fun consumePlayerBack():Boolean {
        touchControls?.takeIf {it.locked}?.let {it.setLocked(false);return true}
        if(actionDialog?.dispatch(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK))==true)return true
        if(panelVisible){hidePanel();return true}
        if(advanced&&player!=null) {
            if(controls.menu!=null) {
                if(controls.menuRow!=null)controls.menuRow=null else closeMenu()
                return true
            }
            if(controls.visible) {
                if(controls.sheet!=null)controls.sheet=null else controls.visible=false
                return true
            }
        }
        if(configInitialized&&config.confirmExit&&player!=null&&!ended){askToLeave();return true}
        if(!advanced&&viewInitialized&&view.isControllerFullyVisible){view.hideController();return true}
        return false
    }

    internal val panelVisible: Boolean get() = ::panel.isInitialized && panel.isVisible

    /** What each row of the advanced Subtitles sheet does, in order. */
    internal var subtitleActions: List<() -> Unit> = emptyList()

    /** Route physical remote keys to the active player controls or dialog. */
    @android.annotation.SuppressLint("RestrictedApi") // super.dispatchKeyEvent is the normal Activity path
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (touchControls?.dispatchKey(event) == true) return true
        if (actionDialog?.dispatch(event) == true) return true
        if(playbackKey(event))return true
        if(fileQueue!=null && event.keyCode in setOf(KeyEvent.KEYCODE_MEDIA_NEXT,KeyEvent.KEYCODE_MEDIA_PREVIOUS)){
            if(event.action==KeyEvent.ACTION_UP)(if(event.keyCode==KeyEvent.KEYCODE_MEDIA_NEXT)fileQueue?.next(accept=::queueItemAllowed) else fileQueue?.previous(::queueItemAllowed))?.let { playFile(it) }
            return true
        }
        val menuIsOurs = event.keyCode == KeyEvent.KEYCODE_MENU &&
            ::prefs.isInitialized && prefs.remoteSettingsKey == "ember"
        if (event.keyCode == KeyEvent.KEYCODE_INFO || menuIsOurs || event.keyCode == KeyEvent.KEYCODE_GUIDE) {
            if (event.action == KeyEvent.ACTION_UP && ::panel.isInitialized) {
                when {
                    panelVisible -> hidePanel()
                    // The advanced look: the small menu (Options page and stats are in it).
                    advanced && controls.menu != null -> closeMenu()
                    advanced -> openMenu("quick", null)
                    else -> showInfo()
                }
            }
            return true
        }
        if (panelVisible) {
            handler.removeCallbacks(hideInfo)
            return super.dispatchKeyEvent(event)
        }

        if (advanced) advancedKey(event)?.let { return it }
        if (skipTarget != null && !(if (advanced) controls.visible else view.isControllerFullyVisible) &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            if (event.action == KeyEvent.ACTION_UP) doSkip()
            return true
        }
        return (::view.isInitialized && view.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        subtitleUpdatesStarted=false
        touchControls?.cancel()
        super.onStop()
        if(privatePlayback&&!isChangingConfigurations&&!pickingSidecar){player?.pause();endPlayback();return}
        cancelRecovery()
        handler.removeCallbacks(saver)
        handler.removeCallbacks(infoUpdater)
        handler.removeCallbacks(hideInfo)
        handler.removeCallbacks(subtitleTicker)
        handler.removeCallbacks(markerTicker)
        handler.removeCallbacks(controlsTicker)
        handler.removeCallbacks(hideControls)
        handler.removeCallbacks(statsTicker)
        if(pictureInPictureEntered && !isChangingConfigurations){
            if(!getSystemService(android.os.PowerManager::class.java).isInteractive && commitBackgroundAudio(true))return
            endPlayback();return
        }
        if(commitBackgroundAudio())return
        val p = player
        // Paused and still on screen when the screensaver (or another overlay) took over: keep the
        // player, so the video is there when you come back. Home, Back and a running video close it.
        if (p != null && !userLeft && !isFinishing && !isChangingConfigurations && !p.playWhenReady && !ended) {
            keptAlive = true
            savePosition()
            rememberTracks()
            handler.postDelayed(closeKept, 30 * 60_000L)
            return
        }
        endPlayback()
    }

    internal var actionDialog: PlayerActionDialog? = null
        set(value){field=value;value?.onDismissed={updateBackHandler()};updateBackHandler()}
    internal var backgroundStage:String?=null
    internal var backgroundQueueTransferred=false

    override fun onPause() {
        stageBackgroundAudio(allowPip=pictureInPictureEntered||isInPictureInPictureMode)
        super.onPause()
        captionForeground=false;closeCaptionAssistance()
    }
    internal var touchControls: PlayerTouchControls? = null
    internal var touchMagnification = 1f

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (isInPictureInPictureMode) return super.dispatchTouchEvent(event)
        return touchControls?.dispatch(event) == true || super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) touchControls?.cancel()
    }
    internal var leaveDialog: PlayerActionDialog? = null

    /** The accent colour (Settings > Appearance) as a colour int, for the panel's buttons and the simple look's progress bar. */
    internal var accentArgb = 0xFFFF7A2F.toInt()

    internal var localActivityScope=app.ember.tv.data.LocalActivity.SHARED
    internal var watchProfileId=app.ember.tv.data.Profile.MAIN
    internal val contentProfileListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key==null || key=="currentProfile")handler.post {
            if(::prefs.isInitialized && !contentPlaybackAllowed()){endPlayback();finish()}
        }
    }
    // Lateinit checks for members split into extension files (isInitialized is file-scoped).
    internal val itemInitialized: Boolean get() = ::item.isInitialized
    internal val panelInitialized: Boolean get() = ::panel.isInitialized
    internal val skipButtonInitialized: Boolean get() = ::skipButton.isInitialized
    internal val soundLabelInitialized: Boolean get() = ::soundLabel.isInitialized
    internal val subListInitialized: Boolean get() = ::subList.isInitialized
    internal val subsInitialized: Boolean get() = ::subs.isInitialized
    internal val viewInitialized: Boolean get() = ::view.isInitialized
    internal val positionsInitialized: Boolean get() = ::positions.isInitialized
    internal val prefsInitialized: Boolean get() = ::prefs.isInitialized
    internal val configInitialized: Boolean get() = ::config.isInitialized
    internal val infoInitialized: Boolean get() = ::info.isInitialized
    internal val subStatusInitialized: Boolean get() = ::subStatus.isInitialized
    internal val timingLabelInitialized: Boolean get() = ::timingLabel.isInitialized
    internal val memoryInitialized: Boolean get() = ::memory.isInitialized
}
