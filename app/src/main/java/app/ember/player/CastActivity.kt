package app.ember.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.ember.tv.data.Prefs
import app.ember.tv.player.DlnaReceiver
import app.ember.tv.player.DlnaReceivers
import app.ember.tv.ui.*
import com.google.android.gms.cast.framework.*
import kotlinx.coroutines.*

/** Receiver controls reuse the app's settings controls. Cast SDK setup is explicitly user-started. */
class CastActivity:FragmentActivity() {
    private var pending:CastRequest?=null
    private var sdk by mutableStateOf<CastContext?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var status by mutableStateOf<String?>(null)
    private var initializing by mutableStateOf(false)
    private var registered=false
    private val observer=object:CastSessionObserver() {
        override fun onSessionStarted(session:CastSession,id:String){if(!isFinishing)sendToReceiver()}
        override fun onSessionStartFailed(session:CastSession,error:Int){notice="Could not connect to the Cast receiver."}
        override fun onSessionResumed(session:CastSession,suspended:Boolean){if(pending!=null&&!isFinishing)sendToReceiver()}
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        pending=CastRequests.take(intent.getStringExtra("request"))
        if(pending?.let {PrivateMedia.hidden(it.source)}==true){pending?.close();pending=null;finish();return}
        sdk=CastRuntime.context
        sdk?.let(::observe)
        setContent {PlayerTheme(Prefs(this)) {
            CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Fg) {
                val casting by CastPlaybackService.state.collectAsStateWithLifecycle()
                val privacy by PrivateMedia.state.collectAsStateWithLifecycle()
                SideEffect {if(PrivateMedia.enabled)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
                val scope=rememberCoroutineScope()
                var receivers by remember {mutableStateOf<List<DlnaReceiver>>(emptyList())}
                var discovery by remember {mutableStateOf<Job?>(null)}
                LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal=32.dp),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item {ButtonRow {Text("Receiver playback",style=androidx.tv.material3.MaterialTheme.typography.headlineSmall);Chip("Back"){finish()}}}
                    item {Text(pending?.title ?: casting?.title ?: "Select media in the player to send it to a receiver.",style=androidx.tv.material3.MaterialTheme.typography.bodyLarge,maxLines=2)}
                    item {Text("Use a receiver on the same network. Playback depends on its supported formats; the app does not transcode video for receivers. Private or protected playback cannot be sent.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)}
                    item {
                        Text("Google Cast",style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
                        if(sdk==null)Chip(if(initializing)"Setting up Cast…" else "Enable Google Cast for this session"){initializeCast()}
                        else {
                            Text("Choose a receiver",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
                            AndroidView(factory={context->MediaRouteButton(context).apply {CastButtonFactory.setUpMediaRouteButton(this@CastActivity,this)}},modifier=Modifier.size(52.dp))
                            if(pending!=null&&sdk?.sessionManager?.currentCastSession?.isConnected==true)Chip("Play selected media on this receiver"){sendToReceiver()}
                        }
                    }
                    item {
                        Text("DLNA receivers",style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
                        Chip(if(discovery?.isActive==true)"Cancel discovery" else "Find receivers") {
                            if(discovery?.isActive==true)discovery?.cancel()
                            else discovery=scope.launch {
                                notice=null;status=null
                                try {receivers=withContext(Dispatchers.IO){DlnaReceivers.discover(this@CastActivity){ensureActive()}};if(receivers.isEmpty())status="No DLNA media receiver found."}
                                catch(e:CancellationException){throw e}
                                catch(_:Exception){notice="Could not discover receivers. Check the local network."}
                            }
                        }
                        receivers.forEach {receiver->Chip(receiver.name){if(pending!=null)sendToReceiver(receiver) else status="Open receiver controls from the media you want to play."}}
                    }
                    casting?.let {view->item {
                        Text(view.receiver,style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
                        view.error?.let {PlayerErrorText(it)}
                        if(view.ready&&view.error==null) {
                            val status=view.status
                            Text("${DlnaReceivers.clock(status.position)} / ${DlnaReceivers.clock(status.duration)}",style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
                            ButtonRow {
                                Chip(if(status.playing)"Pause" else "Play"){CastPlaybackService.command(if(status.playing)"pause" else "play")}
                                if(status.duration>0) {
                                    Chip("−30 s"){CastPlaybackService.command("seek",(status.position-30000).coerceAtLeast(0).toDouble())}
                                    Chip("+30 s"){CastPlaybackService.command("seek",(status.position+30000).toDouble())}
                                }
                            }
                            ButtonRow {
                                Chip("Volume −"){CastPlaybackService.command("volume",status.volume-.05)}
                                Text("${(status.volume*100).toInt()}%",style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
                                Chip("Volume +"){CastPlaybackService.command("volume",status.volume+.05)}
                                Chip("Disconnect"){CastPlaybackService.disconnect()}
                            }
                        } else if(view.error==null)Text("Preparing media…",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
                    }}
                    notice?.let {item {PlayerErrorText(it)}}
                    status?.let {item {PlayerStatusText(it)}}
                }
            }
        }}
    }
    private fun initializeCast() {
        if(initializing||sdk!=null)return
        initializing=true
        try {CastContext.getSharedInstance(applicationContext,CastRuntime.executor)
            .addOnSuccessListener {context->CastRuntime.context=context;if(!isDestroyed){initializing=false;sdk=context;observe(context)}}
            .addOnFailureListener {if(!isDestroyed){initializing=false;notice="Google Cast is unavailable on this device. DLNA discovery can still be used."}}
        } catch(_:Exception){initializing=false;notice="Google Cast is unavailable on this device. DLNA discovery can still be used."}
    }
    private fun observe(context:CastContext) {if(!registered){context.sessionManager.addSessionManagerListener(observer,CastSession::class.java);registered=true}}
    private fun sendToReceiver(dlna:DlnaReceiver?=null) {
        val request=pending ?: return
        if(PrivateMedia.hidden(request.source)){notice="Protected media cannot be sent to a receiver.";return}
        val id=CastRequests.stage(request.copy(dlna=dlna))
        try {CastPlaybackService.start(this,id);pending=null}
        catch(_:Exception){CastRequests.take(id);notice="Android could not start receiver playback."}
    }
    override fun onStop(){super.onStop();if(pending!=null&&!isChangingConfigurations){pending?.close();pending=null;finish()}}
    override fun onDestroy(){if(registered)sdk?.sessionManager?.removeSessionManagerListener(observer,CastSession::class.java);pending?.close();pending=null;super.onDestroy()}
}
