package app.ember.tv.player

import app.ember.tv.R
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import kotlinx.coroutines.*

internal data class ClipCopyRequest(val source:String,val headers:Map<String,String>,val destination:Uri,val start:Long,val end:Long,val audio:Format?,val audioDisabled:Boolean)

/** Lossless exports snapshot player state on its owner thread; workers only see immutable inputs. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlayerClipExport(private val activity:PlayerActivity,
    private val copy:suspend (ClipCopyRequest,()->Unit,(Int)->Unit)->Unit={request,check,progress->
        LosslessClip.copy(activity.applicationContext,request.source,request.headers,request.destination,request.start,request.end,request.audio,request.audioDisabled,check,progress)
    }):java.io.Closeable {
    var startMs:Long?=null;private set
    var endMs:Long?=null;private set
    private var visit=-1L
    private var source:String?=null
    private var job:Job?=null
    private var audio:Format?=null
    private var audioDisabled=false
    private var closed=false
    @Volatile private var generation=0L
    private var keepAwake:Boolean?=null
    val active get()=job?.isActive==true
    @Volatile var progress:Int?=null;private set
    private val picker=activity.registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) {uri->
        activity.pickingSidecar=false
        if(uri!=null&&validSource())begin(uri) else {uri?.let {value->activity.lifecycleScope.launch {remove(value)}};source=null}
    }
    fun markStart(){if(active)return;resetForSource();startMs=activity.player?.currentPosition;endMs=null}
    fun markEnd():Boolean {if(active)return false;resetForSource();val end=activity.player?.currentPosition ?: return false;if(!validRange(startMs,end))return false;endMs=end;return true}
    private fun resetForSource(){if(visit!=activity.playbackVisit&&!active){startMs=null;endMs=null;visit=activity.playbackVisit}}
    fun chooseDestination(withoutAudio:Boolean=false) {
        resetForSource()
        if(closed||activity.pickingSidecar)return
        if(active||!activity.exports.permitted()||!validRange(startMs,endMs)){message(activity.getString(R.string.ui_mark_a_clip_start_and_end_at_least_one));return}
        if(activity.audioUrl!=null&&!withoutAudio){offerVideoOnly();return}
        source=activity.currentUrl;visit=activity.playbackVisit
        audio=activity.player?.audioFormat
        audioDisabled=withoutAudio||activity.player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_AUDIO)==true
        activity.closeMenu();activity.pickingSidecar=true;activity.player?.pause()
        message(activity.getString(if(audioDisabled)R.string.ui_video_only_clip_boundaries else R.string.ui_lossless_clip_boundaries))
        try {picker.launch("Ember-clip${if(audioDisabled)"-video-only" else ""}-${System.currentTimeMillis()}.mp4")}
        catch(_:Exception){activity.pickingSidecar=false;source=null;message(activity.getString(R.string.ui_no_file_picker_is_available))}
    }
    private fun validSource()=!closed&&source!=null&&visit==activity.playbackVisit&&source==activity.currentUrl&&activity.exports.permitted()
    internal fun begin(uri:Uri) {
        if(!validSource()||!validRange(startMs,endMs)||active){activity.lifecycleScope.launch {remove(uri)};return}
        val request=ClipCopyRequest(source!!,activity.sourceHeaders.toMap(),uri,startMs!!,endMs!!,audio,audioDisabled)
        val run=++generation
        keepAwake=activity.window.decorView.keepScreenOn;activity.window.decorView.keepScreenOn=true;progress=0
        job=activity.lifecycleScope.launch {
            var completed=false;var offerVideoOnly=false
            try {
                withContext(Dispatchers.IO) {
                    val owner=currentCoroutineContext()
                    var reported=-1
                    copy(request,{owner.ensureActive();check(!app.ember.player.PrivateMedia.hidden(request.source))},{value->
                        if(value!=reported){reported=value;activity.handler.post {if(run==generation)progress=value}}
                    })
                }
                if(run==generation&&validSource()){completed=true;message(activity.getString(R.string.ui_clip_saved))}
                else if(run==generation)message(activity.getString(R.string.ui_export_canceled_because_playback_changed))
            }catch(e:CancellationException){throw e}
             catch(_:LosslessClip.AudioUnavailable){offerVideoOnly=!request.audioDisabled}
             catch(_:Exception){if(run==generation)message(activity.getString(R.string.ui_lossless_clip_unsupported))}
            finally {
                if(!completed)remove(request.destination)
                if(run==generation){
                    source=null;progress=null;restoreAwake()
                    if(offerVideoOnly)activity.handler.post {
                        if(run==generation&&!closed&&!activity.isFinishing)offerVideoOnly()
                    }
                }
            }
        }
    }
    private fun offerVideoOnly() {
        activity.showPlayerActions(activity.getString(R.string.ui_export_clip),listOf(
            PlayerDialogAction(activity.getString(R.string.ui_export_without_audio)) {chooseDestination(withoutAudio=true)}),message=activity.getString(R.string.ui_clip_audio_unavailable))
    }
    fun cancel(){generation++;job?.cancel();job=null;source=null;progress=null;restoreAwake()}
    private fun restoreAwake(){keepAwake?.let {activity.window.decorView.keepScreenOn=it};keepAwake=null}
    private suspend fun remove(uri:Uri)=withContext(NonCancellable+Dispatchers.IO){runCatching {android.provider.DocumentsContract.deleteDocument(activity.contentResolver,uri)};Unit}
    private fun message(text:String){if(!closed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show()}
    override fun close(){closed=true;cancel()}
    companion object {fun validRange(start:Long?,end:Long?)=start!=null&&end!=null&&start>=0&&end>start&&end-start>=1000}
}
