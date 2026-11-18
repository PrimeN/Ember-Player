package app.ember.tv.player

import app.ember.tv.data.HttpIdentity

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderManager

internal interface BackgroundAudioEngine {
    val player:Player
    fun load(source:EngineSource,position:Long)
    fun recover(error:PlaybackException):Boolean
    fun close()
}

/** The service owns its application context, decoder and network factories, never a screen. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AndroidBackgroundAudioEngine(context:Context,private val request:BackgroundAudioRequest):BackgroundAudioEngine {
    private val app=context.applicationContext
    private val config=request.config
    private val manager=DecoderManager()
    private val choice=Playback.CodecChoice().apply {pcm=!config.passthrough}
    private val delay=Playback.AudioDelay()
    private val timing=AudioTiming(delay,::engineDelay)
    private val effects=AudioEffectState().apply {value=config.audioEffects}
    private val http=DefaultHttpDataSource.Factory().setConnectTimeoutMs(config.stream.normalized().connectSeconds*1000).setReadTimeoutMs(config.stream.normalized().readSeconds*1000)
        .setAllowCrossProtocolRedirects(true).setUserAgent(HttpIdentity.userAgent).setDefaultRequestProperties(request.source.headers)
    private val factory=DefaultMediaSourceFactory(DefaultDataSource.Factory(app,http))
    private val standard=ExoPlayer.Builder(app,Playback.Renderers(app,config,delay,choice,manager,effects))
        .setTrackSelector(DefaultTrackSelector(app))
        .setLoadControl(Playback.loadControl(config,app))
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
        .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).setMediaSourceFactory(factory).build()
    private var boost:android.media.audiofx.LoudnessEnhancer?=null
    private var boostSession=0
    private val engine=PlaybackEngine(app,standard,{config.copy(passthrough=config.passthrough&&!choice.pcm)}) {native->
        if(native){boost?.release();boost=null;boostSession=0;timing.backendChanged()}
    }
    private fun engineDelay(milliseconds:Long){engine.compatibility?.audioDelay(milliseconds)}
    override val player:Player get()=engine.player
    private val listener=object:Player.Listener {override fun onEvents(player:Player,events:Player.Events) {
        if(request.boostMillibels<=0)return
        engine.compatibility?.let {it.volumeBoost(request.boostMillibels.coerceIn(0,1200)/100.0);return}
        val session=engine.player.audioSessionId
        if(session>0 && session!=boostSession) {
            boost?.release();boost=null;boostSession=session
            boost=runCatching {android.media.audiofx.LoudnessEnhancer(session).apply {setTargetGain(request.boostMillibels.coerceIn(0,1200));enabled=true}}.getOrNull()
        }
    }}
    private val route=OutputRouteMonitor(app) {bluetooth->
        timing.set(config.outputDelay(bluetooth).toLong())
    }
    init {
        try {
            manager.attach(standard)
            engine.player.addListener(listener)
            engine.player.trackSelectionParameters=engine.player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()
            engine.player.setPlaybackSpeed(request.speed.coerceIn(.25f,4f));engine.player.volume=request.volume.coerceIn(0f,1f)
        } catch(e:Throwable){close();throw e}
    }
    override fun load(source:EngineSource,position:Long) {
        choice.sourceUrl=source.urls.first();choice.sourceFilename=source.filename
        engine.configure(source)
        val media=MediaItem.Builder().setUri(source.urls.first()).setMediaId(source.key)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build()).apply {PlaybackErrors.mimeHint(source.urls.first())?.let(::setMimeType)}.build()
        val content=factory.createMediaSource(media)
        if(source.audio!=null)engine.player.setMediaSource(MergingMediaSource(content,factory.createMediaSource(MediaItem.fromUri(source.audio))))
        else engine.player.setMediaSource(content)
        engine.player.seekTo(position.coerceAtLeast(0));engine.player.prepare();engine.player.playWhenReady=true
    }
    override fun recover(error:PlaybackException)=engine.recover(error.errorCode)
    override fun close(){route.close();engine.player.removeListener(listener);boost?.release();boost=null;manager.detach();engine.player.release()}
}
