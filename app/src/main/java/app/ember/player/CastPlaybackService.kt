package app.ember.player

import app.ember.tv.R

import android.app.*
import android.content.*
import android.media.MediaMetadata as PlatformMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import app.ember.tv.data.SleepTimer
import app.ember.tv.player.*
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference
import java.net.InetAddress

internal data class CastingState(val title:String,val receiver:String,val key:String,val status:ReceiverStatus=ReceiverStatus(),val ready:Boolean=false,val busy:Boolean=false,val error:String?=null)

/** Owns only an explicit remote playback session; discovery and source setup never run at app startup. */
class CastPlaybackService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var request:CastRequest?=null
    private var source:CastLanSource?=null
    private var loading:Job?=null
    private var polling:Job?=null
    private var privacy:Job?=null
    private var sleep:java.io.Closeable?=null
    private var session:MediaSession?=null
    private var generation=0L
    private var stopping=false
    private var commandBusy=false
    private var wake:PowerManager.WakeLock?=null
    private var lastNotification=""
    private val profileListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key=="currentProfile"&&request?.let(::protected)==true)stopRemote()
    }
    private val observer=object:CastSessionObserver() {
        override fun onSessionEnded(session:CastSession,error:Int){if(request?.dlna==null)stopRemote(false)}
        override fun onSessionSuspended(session:CastSession,error:Int){if(request?.dlna==null)fail(resources.getString(R.string.ui_receiver_disconnected))}
    }
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate();current=WeakReference(this)
        getSharedPreferences("ember",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(profileListener)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("casting",resources.getString(R.string.ui_receiver_playback),NotificationManager.IMPORTANCE_LOW))
        session=MediaSession(this,"Ember remote playback").apply {
            setCallback(object:MediaSession.Callback() {
                override fun onPlay(){control("play")};override fun onPause(){control("pause")}
                override fun onPlayFromSearch(query:String?,extras:Bundle?) {if(MediaSearch.matches(request?.title,query))onPlay()}
                override fun onStop(){stopRemote()};override fun onSeekTo(pos:Long){control("seek",pos.toDouble())}
            })
            isActive=true
        }
        sleep=SleepTimer.listen {stopRemote()}
        privacy=scope.launch {PrivateMedia.state.collect {if(request?.let(::protected)==true)fail(resources.getString(R.string.ui_protected_media_cannot_be_sent_to_a_receiver))}}
        CastRuntime.context?.sessionManager?.addSessionManagerListener(observer,CastSession::class.java)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action!=null){when(intent.action){"stop"->stopRemote();"play"->control("play");"pause"->control("pause")};return START_NOT_STICKY}
        startForeground(740,notification(resources.getString(R.string.ui_preparing_receiver),false))
        val next=CastRequests.take(intent?.getStringExtra("request")) ?: run {stopSelf();return START_NOT_STICKY}
        val before=loading;before?.cancel();polling?.cancel();releaseSource()
        request=next;stopping=false;session?.isActive=true
        val visit=++generation
        state.value=CastingState(next.title,next.dlna?.name ?: CastRuntime.context?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: resources.getString(R.string.ui_cast_receiver),next.key)
        loading=scope.launch {
            before?.join()
            var prepared:CastLanSource?=null
            try {
                check(!protected(next))
                val peer=next.dlna?.let {InetAddress.getByName(it.peer)} ?: CastRuntime.context?.sessionManager?.currentCastSession?.castDevice?.inetAddress ?: error("Receiver disconnected")
                val media=withContext(Dispatchers.IO) {
                    CastLanSource(applicationContext,peer).also {prepared=it}.prepare(next.source,next.title,next.headers)
                }
                ensureActive();check(!protected(next))
                source=prepared;prepared=null
                if(next.dlna!=null) {
                    withContext(Dispatchers.IO) {
                        DlnaReceivers.load(next.dlna,media,next.title)
                        if(next.position>0&&!next.live)runCatching {DlnaReceivers.control(next.dlna,"seek",next.position.toDouble())}
                    }
                    if(visit==generation)ready(visit)
                } else {
                    val remote=CastRuntime.context?.sessionManager?.currentCastSession?.remoteMediaClient ?: error("Receiver disconnected")
                    val metadata=MediaMetadata(if(media.mime.startsWith("audio/"))MediaMetadata.MEDIA_TYPE_MUSIC_TRACK else MediaMetadata.MEDIA_TYPE_MOVIE).apply {putString(MediaMetadata.KEY_TITLE,next.title)}
                    val info=MediaInfo.Builder(media.url).setContentType(media.mime).setStreamType(if(next.live)MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).apply {if(next.duration>0)setStreamDuration(next.duration)}.build()
                    remote.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(if(next.live)0 else next.position).build()).setResultCallback {
                        if(visit==generation&&!stopping){if(it.status.isSuccess)ready(visit) else fail(resources.getString(R.string.ui_the_receiver_could_not_play_this_format))}
                    }
                }
            } catch(e:CancellationException){throw e}
              catch(_:Exception){if(visit==generation)fail(resources.getString(R.string.ui_could_not_start_receiver_playback_check_the_connection_and))}
            finally {prepared?.close()}
        }
        return START_NOT_STICKY
    }
    private fun protected(value:CastRequest)=value.profile!=app.ember.tv.data.Prefs(this).currentProfile||PrivateMedia.hidden(value.source)||PrivateMedia.hidden(value.key)||value.key.startsWith(DirectUsbIdentity.PREFIX)&&PrivateMedia.enabled
    @android.annotation.SuppressLint("NotificationPermission")
    private fun ready(visit:Long) {
        if(stopping||visit!=generation)return
        state.value=state.value?.copy(ready=true)
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Ember:receiver").apply {acquire(12*60*60*1000L)}
        session?.setMetadata(PlatformMetadata.Builder().putString(PlatformMetadata.METADATA_KEY_TITLE,request?.title).build())
        session?.setPlaybackToRemote(object:VolumeProvider(VOLUME_CONTROL_ABSOLUTE,100,50){override fun onSetVolumeTo(volume:Int){control("volume",volume/100.0)};override fun onAdjustVolume(direction:Int){control("volume",(state.value?.status?.volume ?: .5)+direction*.05)}})
        polling?.cancel()
        val started=SystemClock.elapsedRealtime()
        polling=scope.launch {
            var failures=0
            while(isActive&&visit==generation) {
                val active=request ?: break
                try {
                    if(protected(active)){fail(resources.getString(R.string.ui_protected_media_cannot_be_sent_to_a_receiver));break}
                    val status=if(active.dlna!=null)withContext(Dispatchers.IO){DlnaReceivers.status(active.dlna,state.value?.status?.volume ?: .5)} else {
                        val cast=CastRuntime.context?.sessionManager?.currentCastSession ?: error("Disconnected")
                        val remote=cast.remoteMediaClient ?: error("Disconnected")
                        ReceiverStatus(remote.approximateStreamPosition,remote.mediaInfo?.streamDuration?.coerceAtLeast(0) ?: active.duration,remote.isPlaying,
                            remote.mediaStatus?.idleReason==MediaStatus.IDLE_REASON_FINISHED,cast.volume.coerceIn(0.0,1.0))
                    }
                    failures=0;state.value=state.value?.copy(status=status)
                    val platform=if(status.playing)PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                    session?.setPlaybackState(PlaybackState.Builder().setState(platform,status.position,if(status.playing)1f else 0f,SystemClock.elapsedRealtime()).setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO).build())
                    val text=if(status.playing)resources.getString(R.string.ui_playing_on ,state.value?.receiver) else resources.getString(R.string.ui_paused_on ,state.value?.receiver)
                    if(text!=lastNotification){lastNotification=text;getSystemService(NotificationManager::class.java).notify(740,notification(text,status.playing))}
                    if(status.finished&&SystemClock.elapsedRealtime()-started>10000){SleepTimer.itemEnded();stopRemote();break}
                }catch(e:CancellationException){throw e}catch(_:Exception){if(++failures>=3){fail(resources.getString(R.string.ui_receiver_disconnected));break}}
                delay(2000)
            }
        }
    }
    internal fun control(action:String,value:Double=0.0) {
        val active=request ?: return
        if(!value.isFinite())return
        if(stopping||state.value?.ready!=true||commandBusy)return
        if(protected(active)){stopRemote();return}
        val argument=if(action=="volume")value.coerceIn(0.0,1.0) else value.coerceIn(0.0,(state.value?.status?.duration?.takeIf {it>0} ?: Long.MAX_VALUE).toDouble())
        if(active.dlna!=null) {
            commandBusy=true;state.value=state.value?.copy(busy=true)
            scope.launch {
                try {withContext(Dispatchers.IO) {DlnaReceivers.control(active.dlna,action,argument)}
                    if(action=="volume")state.value=state.value?.let {it.copy(status=it.status.copy(volume=argument))}
                }catch(e:CancellationException){throw e}catch(_:Exception){android.widget.Toast.makeText(this@CastPlaybackService,resources.getString(R.string.ui_this_receiver_did_not_accept_the_control),android.widget.Toast.LENGTH_SHORT).show()}
                finally {commandBusy=false;state.value=state.value?.copy(busy=false)}
            }
        } else runCatching {
            val cast=CastRuntime.context?.sessionManager?.currentCastSession ?: return
            when(action) {"play"->cast.remoteMediaClient?.play();"pause"->cast.remoteMediaClient?.pause();"seek"->cast.remoteMediaClient?.seek(MediaSeekOptions.Builder().setPosition(argument.toLong()).build());"volume"->{cast.volume=argument;cast.isMute=false}}
        }
    }
    private fun notification(text:String,playing:Boolean):Notification {
        val open=PendingIntent.getActivity(this,740,Intent(this,CastActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String)=PendingIntent.getService(this,name.hashCode(),Intent(this,CastPlaybackService::class.java).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"casting").setSmallIcon(R.drawable.ic_player_notification).setContentTitle(resources.getString(R.string.ui_ember_player)).setContentText(text).setContentIntent(open).setOngoing(true)
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0,1))
            .addAction(Notification.Action.Builder(null,if(playing)resources.getString(R.string.ui_pause) else resources.getString(R.string.ui_play),action(if(playing)"pause" else "play")).build())
            .addAction(Notification.Action.Builder(null,resources.getString(R.string.ui_disconnect),action("stop")).build()).build()
    }
    private fun fail(message:String){state.value=CastingState(resources.getString(R.string.ui_receiver_playback),resources.getString(R.string.ui_receiver),"",error=message);stopRemote()}
    private fun releaseSource(){source?.close();source=null;request?.close();request=null;if(wake?.isHeld==true)wake?.release();wake=null}
    internal fun stopRemote(endReceiver:Boolean=true) {
        if(stopping)return
        stopping=true;generation++;loading?.cancel();polling?.cancel()
        session?.setMetadata(null);session?.isActive=false;stopForeground(STOP_FOREGROUND_REMOVE)
        val dlna=request?.dlna
        val wasGoogle=request!=null&&dlna==null
        releaseSource()
        if(endReceiver) {
            if(dlna!=null)Thread({runCatching {DlnaReceivers.soap(dlna,"Stop")}},"Ember receiver stop").apply {isDaemon=true;start()}
            else if(wasGoogle)runCatching {CastRuntime.context?.sessionManager?.endCurrentSession(true)}
        }
        if(state.value?.error==null)state.value=null
        stopSelf()
    }
    override fun onDestroy() {
        stopRemote();sleep?.close();privacy?.cancel();scope.cancel();releaseSource()
        getSharedPreferences("ember",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(profileListener)
        CastRuntime.context?.sessionManager?.removeSessionManagerListener(observer,CastSession::class.java)
        session?.isActive=false;session?.release();session=null
        if(current?.get()===this)current=null
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    companion object {
        internal val state=MutableStateFlow<CastingState?>(null)
        private var current:WeakReference<CastPlaybackService>?=null
        fun start(context:Context,id:String){context.startForegroundService(Intent(context,CastPlaybackService::class.java).putExtra("request",id))}
        fun command(action:String,value:Double=0.0){current?.get()?.control(action,value)}
        fun disconnect(){current?.get()?.stopRemote()}
        fun localPlayback(key:String){if(state.value?.key==key)disconnect()}
    }
}
