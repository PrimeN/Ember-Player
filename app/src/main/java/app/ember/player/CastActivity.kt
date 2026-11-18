package app.ember.player

import app.ember.tv.R

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
        override fun onSessionStartFailed(session:CastSession,error:Int){notice=resources.getString(R.string.ui_could_not_connect_to_the_cast_receiver)}
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
                    item {PlayerHeading(resources.getString(R.string.ui_receiver_playback),back={finish()})}
                    item {Text(pending?.title ?: casting?.title ?: resources.getString(R.string.ui_select_media_in_the_player_to_send_it_to),style=androidx.tv.material3.MaterialTheme.typography.bodyLarge,maxLines=2)}
                    item {Text(resources.getString(R.string.ui_use_a_receiver_on_the_same_network_playback_depends),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)}
                    item {
                        Text(resources.getString(R.string.ui_google_cast),style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
                        if(sdk==null)Chip(if(initializing)resources.getString(R.string.ui_setting_up_cast) else resources.getString(R.string.ui_enable_google_cast_for_this_session)){initializeCast()}
                        else {
                            Text(resources.getString(R.string.ui_choose_a_receiver),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
                            AndroidView(factory={context->MediaRouteButton(context).apply {CastButtonFactory.setUpMediaRouteButton(this@CastActivity,this)}},modifier=Modifier.size(52.dp))
                            if(pending!=null&&sdk?.sessionManager?.currentCastSession?.isConnected==true)Chip(resources.getString(R.string.ui_play_selected_media_on_this_receiver)){sendToReceiver()}
                        }
                    }
                    item {
                        Text(resources.getString(R.string.ui_dlna_receivers),style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
                        Chip(if(discovery?.isActive==true)resources.getString(R.string.ui_cancel_discovery) else resources.getString(R.string.ui_find_receivers)) {
                            if(discovery?.isActive==true)discovery?.cancel()
                            else discovery=scope.launch {
                                notice=null;status=null
                                try {receivers=withContext(Dispatchers.IO){DlnaReceivers.discover(this@CastActivity){ensureActive()}};if(receivers.isEmpty())status=resources.getString(R.string.ui_no_dlna_media_receiver_found)}
                                catch(e:CancellationException){throw e}
                                catch(_:Exception){notice=resources.getString(R.string.ui_could_not_discover_receivers_check_the_local_network)}
                            }
                        }
                        receivers.forEach {receiver->Chip(receiver.name){if(pending!=null)sendToReceiver(receiver) else status=resources.getString(R.string.ui_open_receiver_controls_from_the_media_you_want_to)}}
                    }
                    casting?.let {view->item {
                        Text(view.receiver,style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
                        view.error?.let {PlayerErrorText(it)}
                        if(view.ready&&view.error==null) {
                            val status=view.status
                            Text("${DlnaReceivers.clock(status.position)} / ${DlnaReceivers.clock(status.duration)}",style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
                            ButtonRow {
                                Chip(if(status.playing)resources.getString(R.string.ui_pause) else resources.getString(R.string.ui_play)){CastPlaybackService.command(if(status.playing)"pause" else "play")}
                                if(status.duration>0) {
                                    Chip(resources.getString(R.string.ui_30_s)){CastPlaybackService.command("seek",(status.position-30000).coerceAtLeast(0).toDouble())}
                                    Chip(resources.getString(R.string.ui_30_s_224790)){CastPlaybackService.command("seek",(status.position+30000).toDouble())}
                                }
                            }
                            ButtonRow {
                                Chip(resources.getString(R.string.ui_volume)){CastPlaybackService.command("volume",status.volume-.05)}
                                Text("${(status.volume*100).toInt()}%",style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
                                Chip(resources.getString(R.string.ui_volume_066105)){CastPlaybackService.command("volume",status.volume+.05)}
                                Chip(resources.getString(R.string.ui_disconnect)){CastPlaybackService.disconnect()}
                            }
                        } else if(view.error==null)Text(resources.getString(R.string.ui_preparing_media),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
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
            .addOnFailureListener {if(!isDestroyed){initializing=false;notice=resources.getString(R.string.ui_google_cast_is_unavailable_on_this_device_dlna_discovery)}}
        } catch(_:Exception){initializing=false;notice=resources.getString(R.string.ui_google_cast_is_unavailable_on_this_device_dlna_discovery)}
    }
    private fun observe(context:CastContext) {if(!registered){context.sessionManager.addSessionManagerListener(observer,CastSession::class.java);registered=true}}
    private fun sendToReceiver(dlna:DlnaReceiver?=null) {
        val request=pending ?: return
        if(PrivateMedia.hidden(request.source)){notice=resources.getString(R.string.ui_protected_media_cannot_be_sent_to_a_receiver);return}
        val id=CastRequests.stage(request.copy(dlna=dlna))
        try {CastPlaybackService.start(this,id);pending=null}
        catch(_:Exception){CastRequests.take(id);notice=resources.getString(R.string.ui_android_could_not_start_receiver_playback)}
    }
    override fun onStop(){super.onStop();if(pending!=null&&!isChangingConfigurations){pending?.close();pending=null;finish()}}
    override fun onDestroy(){if(registered)sdk?.sessionManager?.removeSessionManagerListener(observer,CastSession::class.java);pending?.close();pending=null;super.onDestroy()}
}
