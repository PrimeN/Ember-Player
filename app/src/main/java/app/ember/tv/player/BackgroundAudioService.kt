package app.ember.tv.player

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.*
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BackgroundNowPlaying(val id:String,val title:String,val playing:Boolean)

internal data class BackgroundAudioRequest(
    val source:EngineSource,val config:PlaybackConfig,val resumeIntent:Intent,val queueId:String?,
    val profile:String,val scope:String,val position:Long,val speed:Float,val volume:Float,val boostMillibels:Int=0,val audioTrack:BackgroundAudioTrack?=null,
)

/** A single foreground media session, explicitly opted into by the user. */
@androidx.annotation.OptIn(UnstableApi::class)
class BackgroundAudioService:Service(),Player.Listener {
    private val handler=Handler(Looper.getMainLooper())
    private var id:String?=null
    private var handoff:BackgroundHandoff<BackgroundAudioRequest>?=null
    private var audio:BackgroundAudioEngine?=null
    private var session:MediaSession?=null
    private var source:EngineSource?=null
    private var sleepWatch:java.io.Closeable?=null
    private var usbWatch:java.io.Closeable?=null
    private var pickedAudio=false
    private var restoreTrack=true
    private var closing=false
    private var wanted=true
    private var wake:android.os.PowerManager.WakeLock?=null
    private var wifi:android.net.wifi.WifiManager.WifiLock?=null
    private val timeout=Runnable {if(audio==null)stopPlayback()}
    private val preferences=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key==null || key=="playbackConfig" || key=="currentProfile")handler.post {
            val prefs=app.ember.tv.data.Prefs(applicationContext)
            if(!prefs.playbackConfig.backgroundAudio || handoff?.value?.profile?.let {it!=prefs.currentProfile}==true)stopPlayback()
        }
    }
    private val ticker=object:Runnable {override fun run(){savePosition();update();handler.postDelayed(this,5000)}}
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate();current=WeakReference(this)
        sleepWatch=app.ember.tv.data.SleepTimer.listen {stopPlayback()}
        getSharedPreferences("ember",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferences)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Background playback",NotificationManager.IMPORTANCE_LOW))
        session=MediaSession(this,"Ember Player").apply {
            setCallback(object:MediaSession.Callback() {
                override fun onPlay(){setPlaying(true)}
                override fun onPause(){setPlaying(false)}
                override fun onStop(){stopPlayback()}
                override fun onSeekTo(pos:Long){audio?.player?.let {if(it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))it.seekTo(pos.coerceIn(0,it.duration.takeIf {d->d>0} ?: Long.MAX_VALUE))}}
                override fun onSkipToNext(){step(1)}
                override fun onSkipToPrevious(){step(-1)}
            },handler)
            isActive=true
        }
        wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"EmberPlayer:background").apply {setReferenceCounted(false)}
        @Suppress("DEPRECATION")
        wifi=(applicationContext.getSystemService(WIFI_SERVICE) as? android.net.wifi.WifiManager)
            ?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,"EmberPlayer:audio-network")?.apply {setReferenceCounted(false)}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val incoming=intent?.getStringExtra(EXTRA_ID)
        if(intent?.action!=null && (incoming!=id || closing)) {
            if(id==null || closing)stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action==ACTION_STOP){if(incoming==id)stopPlayback();return START_NOT_STICKY}
        if(intent?.action==ACTION_PLAY){if(incoming==id)setPlaying(true);return START_NOT_STICKY}
        if(intent?.action==ACTION_PAUSE){if(incoming==id)setPlaying(false);return START_NOT_STICKY}
        if(intent?.action==ACTION_NEXT){if(incoming==id)step(1);return START_NOT_STICKY}
        if(intent?.action==ACTION_PREVIOUS){if(incoming==id)step(-1);return START_NOT_STICKY}
        val entry=incoming?.let(pending::get)
        if(entry==null || entry.state==BackgroundHandoff.State.CLOSED){stopPlayback();return START_NOT_STICKY}
        if(id!=null && id!=incoming)releaseSession(true)
        id=incoming;handoff=entry;closing=false
        startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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
            update()
            handler.post(ticker)
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
        if(playbackState==Player.STATE_ENDED&&app.ember.tv.data.SleepTimer.itemEnded())return
        if(playbackState==Player.STATE_ENDED){
            source?.let {key->PositionStore(this,handoff?.value?.scope ?: app.ember.tv.data.LocalActivity.SHARED).clear(key.key)}
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
        val at=PositionStore(this,request.scope).get(media.key)?.first ?: 0
        audio?.load(source!!,at)
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
            if(player.currentPosition>=duration*.95)store.clear(key) else store.put(key,player.currentPosition.coerceAtLeast(0),duration)
        }
    }
    // MediaStyle notifications with an active MediaSession are exempt from Android 13's
    // notification runtime permission: developer.android.com/develop/ui/views/notifications/notification-permission#exemptions
    @android.annotation.SuppressLint("NotificationPermission")
    private fun update() {
        if(closing)return
        val player=audio?.player
        val playing=player?.isPlaying==true
        val awake=player?.playWhenReady==true && player.playbackState in setOf(Player.STATE_BUFFERING,Player.STATE_READY)
        wake?.let {if(awake&&!it.isHeld)it.acquire(30*60_000L) else if(!awake&&it.isHeld)it.release()}
        wifi?.let {runCatching {if(awake&&!it.isHeld)it.acquire() else if(!awake&&it.isHeld)it.release()}}
        val queue=MediaQueues.get(handoff?.value?.queueId)
        val actions=PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SEEK_TO or (if(queue?.hasNext()==true)PlaybackState.ACTION_SKIP_TO_NEXT else 0L) or
            (if(player!=null)PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0L)
        val state=when {player==null || player.playbackState==Player.STATE_BUFFERING->PlaybackState.STATE_BUFFERING;playing->PlaybackState.STATE_PLAYING;else->PlaybackState.STATE_PAUSED}
        session?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,source?.title?.take(180) ?: "Ember Player")
            .putLong(MediaMetadata.METADATA_KEY_DURATION,player?.duration?.coerceAtLeast(0) ?: 0).build())
        session?.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state,player?.currentPosition ?: 0,if(playing)player?.playbackParameters?.speed ?: 1f else 0f).build())
        if(audio!=null && !closing)id?.let {playingState.value=BackgroundNowPlaying(it,source?.title ?: "Ember Player",playing)}
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification())
    }
    private fun control(action:String):PendingIntent = PendingIntent.getService(this,action.hashCode(),Intent(this,BackgroundAudioService::class.java)
        .setAction(action).setData(Uri.Builder().scheme("emberplayer").authority("background").appendPath(id).appendPath(action).build())
        .putExtra(EXTRA_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification():Notification {
        val resume=PendingIntent.getActivity(this,0,Intent(this,ResumeBackgroundAudioActivity::class.java)
            .setData(Uri.parse("emberplayer:background:$id")).putExtra(EXTRA_ID,id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playing=audio?.player?.playWhenReady==true
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(source?.title?.filterNot {it.isISOControl()}?.take(180) ?: "Ember Player")
            .setContentText(if(audio==null)"Preparing background playback" else if(playing)"Background audio" else "Paused")
            .setContentIntent(resume).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setOngoing(playing)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",control(ACTION_PREVIOUS)).build())
            .addAction(Notification.Action.Builder(if(playing)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(playing)"Pause" else "Play",control(if(playing)ACTION_PAUSE else ACTION_PLAY)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",control(ACTION_NEXT)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",control(ACTION_STOP)).build())
            .setDeleteIntent(control(ACTION_STOP)).setStyle(Notification.MediaStyle().setMediaSession(checkNotNull(session).sessionToken).setShowActionsInCompactView(0,1,2)).build()
    }
    private fun releaseSession(releaseQueue:Boolean) {
        usbWatch?.close();usbWatch=null
        closing=true
        handler.removeCallbacksAndMessages(null)
        savePosition()
        audio?.let {it.player.removeListener(this);it.close()};audio=null
        val state=handoff
        if(state?.close()==true && releaseQueue)MediaQueues.release(state.value.queueId)
        id?.let(pending::remove);handoff=null;source=null
        if(playingState.value?.id==id)playingState.value=null
        wake?.let {if(it.isHeld)it.release()}
        wifi?.let {runCatching {if(it.isHeld)it.release()}}
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
        releaseSession(true);session?.isActive=false;session?.release();session=null;if(current?.get()===this)current=null;super.onDestroy()
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
