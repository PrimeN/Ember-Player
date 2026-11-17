package app.ember.tv.player

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.common.util.UnstableApi
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.*

internal data class BackgroundNowPlaying(val id:String,val title:String,val playing:Boolean)

internal data class BackgroundAudioRequest(
    val source:EngineSource,val config:PlaybackConfig,val resumeIntent:Intent,val queueId:String?,
    val profile:String,val scope:String,val position:Long,val speed:Float,val volume:Float,val boostMillibels:Int=0,val audioTrack:BackgroundAudioTrack?=null,val artwork:ByteArray?=null,
)

/** A single foreground media session, explicitly opted into by the user. */
@androidx.annotation.OptIn(UnstableApi::class)
class BackgroundAudioService:MediaLibraryService(),Player.Listener {
    private val handler=Handler(Looper.getMainLooper())
    private var id:String?=null
    private var handoff:BackgroundHandoff<BackgroundAudioRequest>?=null
    private var audio:BackgroundAudioEngine?=null
    private var session:MediaLibrarySession?=null
    private var sessionPlayer:BackgroundSessionPlayer?=null
    private val artworkScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var artworkJob:Job?=null
    private var artwork:ByteArray?=null
    private var source:EngineSource?=null
    private var sleepWatch:java.io.Closeable?=null
    private var usbWatch:java.io.Closeable?=null
    private var pickedAudio=false
    private var restoreTrack=true
    private var closing=false
    private var wanted=true
    private var wake:android.os.PowerManager.WakeLock?=null
    private var wakeUntil=0L
    private val timeout=Runnable {if(audio==null)stopPlayback()}
    private val preferences=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key==null || key=="playbackConfig" || key=="currentProfile")handler.post {
            val prefs=app.ember.tv.data.Prefs(applicationContext)
            if(!prefs.playbackConfig.backgroundAudio || handoff?.value?.profile?.let {it!=prefs.currentProfile}==true)stopPlayback()
        }
    }
    private val ticker=object:Runnable {override fun run(){if(closing)return;savePosition();updatePower();if(audio?.player?.playWhenReady==true)handler.postDelayed(this,5000)}}
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaLibrarySession? {if(closing)return null;return session ?: createSession()}
    override fun onCreate() {
        super.onCreate();current=WeakReference(this)
        artworkScope.launch {
            combine(app.ember.player.PrivateMedia.state,app.ember.player.PrivateMedia.unlocked){_,_->Unit}.collect {
                if(!closing) {
                    if(source?.urls?.any {app.ember.player.PrivateMedia.hidden(it)}==true)stopPlayback()
                    else sessionPlayer?.refresh()
                }
            }
        }
        sleepWatch=app.ember.tv.data.SleepTimer.listen {stopPlayback()}
        getSharedPreferences("ember",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferences)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Background playback",NotificationManager.IMPORTANCE_LOW))
        configureNotifications()
        wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"EmberPlayer:background").apply {setReferenceCounted(false)}

    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent!=null&&!intent.hasExtra(EXTRA_ID))return super.onStartCommand(intent,flags,startId)
        val incoming=intent?.getStringExtra(EXTRA_ID)
        if(intent?.action!=null && (incoming!=id || closing)) {
            if(id==null || closing)stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action==ACTION_TOGGLE){setPlaying(audio?.player?.playWhenReady!=true);return START_NOT_STICKY}
        if(intent?.action==ACTION_STOP){if(incoming==id)stopPlayback();return START_NOT_STICKY}
        if(intent?.action==ACTION_PLAY){if(incoming==id)setPlaying(true);return START_NOT_STICKY}
        if(intent?.action==ACTION_PAUSE){if(incoming==id)setPlaying(false);return START_NOT_STICKY}
        if(intent?.action==ACTION_NEXT){if(incoming==id)step(1);return START_NOT_STICKY}
        if(intent?.action==ACTION_PREVIOUS){if(incoming==id)step(-1);return START_NOT_STICKY}
        val entry=incoming?.let(pending::get)
        if(entry==null || entry.state==BackgroundHandoff.State.CLOSED){stopPlayback();return START_NOT_STICKY}
        if(id==incoming&&audio!=null)return START_NOT_STICKY
        if(id!=incoming||handoff==null&&session!=null)releaseSession(true)
        id=incoming;handoff=entry;closing=false;wanted=true
        createSession()
        startForeground(NOTIFICATION,preparingNotification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        handler.removeCallbacks(timeout);handler.postDelayed(timeout,10000)
        beginIfCommitted()
        return START_NOT_STICKY
    }
    internal fun beginIfCommitted() {
        val state=handoff ?: return
        if(state.state!=BackgroundHandoff.State.COMMITTED || audio!=null || closing)return
        handler.removeCallbacks(timeout)
        val request=state.value
        try {
            usbWatch=UsbMedia.watch(this,request.source.urls.firstOrNull().orEmpty()){stopPlayback()}
            check(!app.ember.player.PrivateMedia.hidden(request.source.urls.firstOrNull().orEmpty()))
            source=request.source
            audio=factory(applicationContext,request)
            audio!!.player.addListener(this)
            audio!!.load(request.source,request.position)
            audio!!.player.playWhenReady=wanted
            loadArtwork()
            update()
            handler.removeCallbacks(ticker);handler.postDelayed(ticker,5000)
        } catch(_:Exception){stopPlayback()} catch(_:LinkageError){stopPlayback()}
    }
    private fun setPlaying(value:Boolean){if(closing)return;wanted=value;audio?.player?.playWhenReady=value;update()}
    override fun onEvents(player:Player,events:Player.Events){if(!closing)update()}
    override fun onTracksChanged(tracks:Tracks) {
        if(pickedAudio)return
        val request=handoff?.value ?: return
        if(tracks.groups.none {it.type==C.TRACK_TYPE_AUDIO})return
        pickedAudio=true
        val remembered=if(restoreTrack)request.audioTrack?.selection(tracks) else null
        (remembered ?: Playback.bestAudio(tracks,request.config,source?.audioLanguages?.firstOrNull()))?.let {selection->
            audio?.player?.let {it.trackSelectionParameters=it.trackSelectionParameters.buildUpon().setOverrideForType(selection).build()}
        }
    }
    override fun onPlaybackStateChanged(playbackState:Int) {
        if(playbackState==Player.STATE_ENDED){
            source?.takeIf {app.ember.player.PrivateMedia.historyAllowed(it.urls.firstOrNull().orEmpty())}?.let {key->
                PositionStore(this,handoff?.value?.scope ?: app.ember.tv.data.LocalActivity.SHARED).finished(key.key,audio?.player?.duration ?: 0)
            }
            if(app.ember.tv.data.SleepTimer.itemEnded())return
            val queue=MediaQueues.get(handoff?.value?.queueId)
            queue?.next(ended=true,accept={ !app.ember.player.PrivateMedia.hidden(it.uri) })?.let {loadQueueItem(it)} ?: stopPlayback()
        }
    }
    override fun onPlayerError(error:PlaybackException) {
        if(audio?.recover(error)!=true)stopPlayback()
    }
    private fun step(direction:Int) {
        val player=audio?.player ?: return
        if(direction<0 && player.currentPosition>3000){player.seekTo(0);return}
        val queue=MediaQueues.get(handoff?.value?.queueId) ?: return
        savePosition()
        (if(direction>0)queue.next(accept={!app.ember.player.PrivateMedia.hidden(it.uri)}) else queue.previous {!app.ember.player.PrivateMedia.hidden(it.uri)})?.let(::loadQueueItem)
    }
    private fun loadQueueItem(media:QueueMedia) {
        if(app.ember.player.PrivateMedia.hidden(media.uri)){stopPlayback();return}
        val request=handoff?.value ?: return
        pickedAudio=false
        restoreTrack=false
        usbWatch?.close();usbWatch=UsbMedia.watch(this,media.uri){stopPlayback()}
        source=EngineSource(listOf(media.uri),media.title,media.key,headers=request.source.headers,
            audioLanguages=request.source.audioLanguages,filename=media.title)
        val at=PositionStore(this,request.scope).resume(media.key)?.first ?: 0
        audio?.load(source!!,at)
        loadArtwork()
        MediaQueues.get(request.queueId)?.let {queue->queue.playlistId?.let {PlaylistProgress.save(applicationContext,request.profile,it,media.key)}}
        update()
    }
    private fun savePosition() {
        if(!app.ember.player.PrivateMedia.historyAllowed(source?.urls?.firstOrNull().orEmpty()))return
        val player=audio?.player ?: return
        val request=handoff?.value ?: return
        val key=source?.key ?: return
        val duration=player.duration
        if(duration>0 && player.playbackState!=Player.STATE_ENDED) {
            val store=PositionStore(this,request.scope)
            store.put(key,player.currentPosition.coerceAtLeast(0),duration)
        }
    }
    private fun updatePower() {
        val player=audio?.player
        val awake=player?.playWhenReady==true&&player.playbackState in setOf(Player.STATE_BUFFERING,Player.STATE_READY)
        val now=SystemClock.elapsedRealtime()
        wake?.let {
            if(awake&&(!it.isHeld||now>=wakeUntil-60_000)) {
                if(it.isHeld)it.release()
                it.acquire(30*60_000L);wakeUntil=now+30*60_000L
            }else if(!awake&&it.isHeld)it.release()
        }
    }
    private fun update() {
        if(closing)return
        updatePower()
        val player=audio?.player
        id?.let {playingState.value=BackgroundNowPlaying(it,source?.title ?: "Ember Player",player?.isPlaying==true)}
        sessionPlayer?.refresh()
        handler.removeCallbacks(ticker)
        if(player?.playWhenReady==true)handler.postDelayed(ticker,5000)
    }
    private fun select(index:Int) {
        val queue=MediaQueues.get(handoff?.value?.queueId) ?: return
        val item=queue.items.getOrNull(index)?.takeUnless {app.ember.player.PrivateMedia.hidden(it.uri)} ?: return
        savePosition();queue.select(index);loadQueueItem(item)
    }
    private fun createSession():MediaLibrarySession {
        val adapter=BackgroundSessionPlayer({audio?.player},{source},{id ?: "idle"},{MediaQueues.get(handoff?.value?.queueId)},::select,::step,::setPlaying,{handler.post {stopPlayback()}},{artwork})
        sessionPlayer=adapter
        val resume=PendingIntent.getActivity(this,id?.hashCode() ?: 0,Intent(this,ResumeBackgroundAudioActivity::class.java)
            .setData(Uri.parse("emberplayer:background:$id")).putExtra(EXTRA_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return MediaLibrarySession.Builder(this,adapter,BackgroundLibrary(adapter::libraryItems)).setId(id ?: java.util.UUID.randomUUID().toString())
            .setSessionActivity(resume).build().also {session=it;addSession(it)}
    }
    private fun loadArtwork() {
        artworkJob?.cancel();artwork=null
        val current=source ?: return
        val request=handoff?.value
        artwork=if(current.key==request?.source?.key)request.artwork else null
        if(artwork!=null)return
        artworkJob=artworkScope.launch {
            val bytes=withContext(Dispatchers.IO){BackgroundArtwork.read(applicationContext,current.urls.first())}
            if(!closing&&source===current){artwork=bytes;sessionPlayer?.refresh()}
        }
    }
    private fun configureNotifications() {
        val delegate=DefaultMediaNotificationProvider.Builder(this).setNotificationId(NOTIFICATION).setChannelId(CHANNEL).build()
        delegate.setSmallIcon(app.ember.tv.R.drawable.ic_player_notification)
        fun privateNotification(value:MediaNotification):MediaNotification {value.notification.visibility=Notification.VISIBILITY_PRIVATE;return value}
        setMediaNotificationProvider(object:MediaNotification.Provider {
            override fun getNotificationChannelInfo()=delegate.notificationChannelInfo
            override fun createNotification(mediaSession:MediaSession,mediaButtonPreferences:com.google.common.collect.ImmutableList<CommandButton>,actionFactory:MediaNotification.ActionFactory,onNotificationChangedCallback:MediaNotification.Provider.Callback):MediaNotification {
                val actions=object:MediaNotification.ActionFactory by actionFactory {
                    override fun createMediaActionPendingIntent(mediaSession:MediaSession,command:Int):PendingIntent=control(when(command){
                        Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM->ACTION_NEXT
                        Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM->ACTION_PREVIOUS
                        Player.COMMAND_STOP->ACTION_STOP
                        else->ACTION_TOGGLE
                    })
                    override fun createMediaAction(mediaSession:MediaSession,icon:androidx.core.graphics.drawable.IconCompat,title:CharSequence,command:Int)=androidx.core.app.NotificationCompat.Action(icon,title,createMediaActionPendingIntent(mediaSession,command))
                    override fun createNotificationDismissalIntent(mediaSession:MediaSession)=control(ACTION_STOP)
                }
                return privateNotification(delegate.createNotification(mediaSession,mediaButtonPreferences,actions) {onNotificationChangedCallback.onNotificationChanged(privateNotification(it))})
            }
            override fun handleCustomCommand(session:MediaSession,action:String,extras:Bundle)=delegate.handleCustomCommand(session,action,extras)
        })
    }
    private fun control(action:String):PendingIntent = PendingIntent.getService(this,action.hashCode(),Intent(this,BackgroundAudioService::class.java)
        .setAction(action).setData(Uri.Builder().scheme("emberplayer").authority("background").appendPath(id).appendPath(action).build())
        .putExtra(EXTRA_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun preparingNotification():Notification=Notification.Builder(this,CHANNEL).setSmallIcon(app.ember.tv.R.drawable.ic_player_notification)
        .setContentTitle("Ember Player").setContentText("Preparing background playback").setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).build()
    private fun releaseSession(releaseQueue:Boolean) {
        usbWatch?.close();usbWatch=null
        closing=true
        artworkJob?.cancel();artworkJob=null;artwork=null
        handler.removeCallbacksAndMessages(null)
        savePosition()
        session?.let {removeSession(it);it.release()};session=null
        sessionPlayer?.release();sessionPlayer=null
        audio?.let {it.player.removeListener(this);it.close()};audio=null
        val state=handoff
        if(state?.close()==true && releaseQueue)MediaQueues.release(state.value.queueId)
        id?.let(pending::remove);handoff=null;source=null
        if(playingState.value?.id==id)playingState.value=null
        wake?.let {if(it.isHeld)it.release()}
    }
    internal fun stopPlayback(){releaseSession(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    internal fun takeResume(expected:String):BackgroundAudioRequest? {
        if(id!=expected || handoff?.state!=BackgroundHandoff.State.COMMITTED)return null
        val request=handoff!!.value
        val now=source ?: request.source
        val updated=request.copy(source=now,position=audio?.player?.currentPosition ?: request.position,
            speed=audio?.player?.playbackParameters?.speed ?: request.speed,volume=audio?.player?.volume ?: request.volume,
            audioTrack=audio?.player?.currentTracks?.let(BackgroundAudioTrack::selected) ?: request.audioTrack)
        releaseSession(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
        return updated
    }
    override fun onDestroy(){
        sleepWatch?.close();sleepWatch=null
        getSharedPreferences("ember",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferences)
        releaseSession(true);artworkScope.cancel();if(current?.get()===this)current=null;super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent:Intent?){super.onTaskRemoved(rootIntent)}

    companion object {
        private const val CHANNEL="player-background"
        private const val NOTIFICATION=4104
        internal const val EXTRA_ID="player.backgroundSession"
        internal const val FORCE_POSITION="player.backgroundPosition"
        internal const val FORCE_SPEED="player.backgroundSpeed"
        internal const val FORCE_SOUND="player.backgroundSound"
        internal const val FORCE_TRACK="player.backgroundTrack"
        private const val ACTION_TOGGLE="app.ember.player.TOGGLE_AUDIO"
        private const val ACTION_STOP="app.ember.player.STOP_AUDIO"
        private const val ACTION_PLAY="app.ember.player.PLAY_AUDIO"
        private const val ACTION_PAUSE="app.ember.player.PAUSE_AUDIO"
        private const val ACTION_NEXT="app.ember.player.NEXT_AUDIO"
        private const val ACTION_PREVIOUS="app.ember.player.PREVIOUS_AUDIO"
        private val pending=ConcurrentHashMap<String,BackgroundHandoff<BackgroundAudioRequest>>()
        private var current:WeakReference<BackgroundAudioService>?=null
        internal fun currentIsPrivate()=current?.get()?.source?.urls?.any {app.ember.player.PrivateMedia.hidden(it)}==true
        internal fun stopPrivatePlayback(){current?.get()?.let {service->if(service.source?.urls?.any {app.ember.player.PrivateMedia.hidden(it)}==true)service.stopPlayback()}}
        private val playingState=MutableStateFlow<BackgroundNowPlaying?>(null)
        internal val nowPlaying=playingState.asStateFlow()
        internal var factory:(Context,BackgroundAudioRequest)->BackgroundAudioEngine=::AndroidBackgroundAudioEngine
        internal fun stage(context:Context,request:BackgroundAudioRequest):String {
            val id=java.util.UUID.randomUUID().toString()
            pending[id]=BackgroundHandoff(request)
            val intent=Intent(context,BackgroundAudioService::class.java).putExtra(EXTRA_ID,id)
            intent.clipData=request.resumeIntent.clipData ?: request.source.urls.firstOrNull {it.startsWith("content://")}?.let {ClipData.newRawUri("Media",Uri.parse(it))}
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {checkNotNull(ContextCompat.startForegroundService(context,intent)) {"Background service unavailable."}} catch(e:Exception){pending.remove(id);throw e}
            return id
        }
        internal fun commit(id:String,request:BackgroundAudioRequest):Boolean {
            if(pending[id]?.commit(request)!=true)return false
            current?.get()?.takeIf {it.id==id}?.beginIfCommitted()
            return true
        }
        internal fun cancelStage(id:String) {
            val state=pending[id] ?: return
            if(state.state==BackgroundHandoff.State.STAGED){state.close();pending.remove(id);current?.get()?.takeIf {it.id==id}?.stopPlayback()}
        }
        internal fun stopCurrent(){current?.get()?.stopPlayback()}
        internal fun resume(id:String)=current?.get()?.takeResume(id)
    }
}

/** Notification links contain only an opaque session ID, never a saved source or credentials. */
class ResumeBackgroundAudioActivity:androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val request=intent.getStringExtra(BackgroundAudioService.EXTRA_ID)?.let(BackgroundAudioService::resume)
        if(request==null)startActivity(Intent(this,MediaFilesActivity::class.java))
        else {
            val next=Intent(request.resumeIntent).setClass(this,PlayerActivity::class.java)
                .putExtra(PlayRequest.EXTRA_URL,request.source.urls.first()).putExtra(PlayRequest.EXTRA_TITLE,request.source.title)
                .putExtra(PlayRequest.EXTRA_RESUME_KEY,request.source.key).putExtra(BackgroundAudioService.FORCE_POSITION,request.position)
                .putExtra(BackgroundAudioService.FORCE_SPEED,request.speed)
                .putExtra(PlayerActivity.EXTRA_AUDIO_URL,request.source.audio)
                .putExtra(PlayRequest.EXTRA_RELEASE,request.source.filename)
                .putExtra(PlayRequest.EXTRA_HEADER_NAMES,request.source.headers.keys.toTypedArray())
                .putExtra(PlayRequest.EXTRA_HEADER_VALUES,request.source.headers.values.toTypedArray())
                .putExtra(BackgroundAudioService.FORCE_TRACK,request.audioTrack?.bundle())
                .putExtra(BackgroundAudioService.FORCE_SOUND,Bundle().apply {
                    putFloat("volume",request.volume);putInt("boost",request.boostMillibels)
                    putInt("delay",request.config.audioDelayMs)
                    request.config.bluetoothDelayMs?.let {putInt("bluetooth",it)}
                })
            try {startActivity(next)} catch(_:Exception){MediaQueues.release(request.queueId)}
        }
        finish()
    }
}
