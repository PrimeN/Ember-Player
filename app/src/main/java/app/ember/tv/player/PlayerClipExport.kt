package app.ember.tv.player

import app.ember.tv.R
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import kotlinx.coroutines.*

/** Lossless, keyframe-aligned exports preserve encoded video and the selected audio track. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlayerClipExport(private val activity:PlayerActivity):java.io.Closeable {
    var startMs:Long?=null;private set
    var endMs:Long?=null;private set
    private var visit=-1L
    private var source:String?=null
    private var output:Uri?=null
    private var job:Job?=null
    private var audio:Format?=null
    private var audioDisabled=false
    private var closed=false
    private var keepAwake=false
    val active get()=job?.isActive==true
    var progress:Int?=null;private set
    private val picker=activity.registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) {uri->
        activity.pickingSidecar=false
        if(uri!=null&&validSource()){output=uri;begin()}else {uri?.let(::remove);source=null}
    }
    fun markStart(){resetForSource();startMs=activity.player?.currentPosition;endMs=null}
    fun markEnd():Boolean {resetForSource();val end=activity.player?.currentPosition ?: return false;if(!validRange(startMs,end))return false;endMs=end;return true}
    private fun resetForSource(){if(visit!=activity.playbackVisit&&!active){startMs=null;endMs=null;visit=activity.playbackVisit}}
    fun chooseDestination() {
        resetForSource()
        if(active||!activity.exports.permitted()||!validRange(startMs,endMs)){message(activity.getString(R.string.ui_mark_a_clip_start_and_end_at_least_one));return}
        if(activity.audioUrl!=null){message(activity.getString(R.string.ui_lossless_clip_unsupported));return}
        source=activity.currentUrl;visit=activity.playbackVisit
        audio=activity.player?.audioFormat
        audioDisabled=activity.player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_AUDIO)==true
        activity.closeMenu();activity.pickingSidecar=true;activity.player?.pause()
        message(activity.getString(R.string.ui_lossless_clip_boundaries))
        try {picker.launch("Ember-clip-${System.currentTimeMillis()}.mp4")}
        catch(_:Exception){activity.pickingSidecar=false;source=null;message(activity.getString(R.string.ui_no_file_picker_is_available))}
    }
    private fun validSource()=!closed&&source!=null&&visit==activity.playbackVisit&&source==activity.currentUrl&&activity.exports.permitted()
    private fun begin() {
        if(!validSource()){cancel();return}
        val uri=output ?: return
        keepAwake=activity.window.decorView.keepScreenOn;activity.window.decorView.keepScreenOn=true;progress=0
        job=activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO){LosslessClip.copy(activity.applicationContext,source!!,activity.sourceHeaders.toMap(),uri,startMs!!,endMs!!,audio,audioDisabled,{ensureActive();check(validSource())},{progress=it})}
                if(validSource())message(activity.getString(R.string.ui_clip_saved))else {remove(uri);message(activity.getString(R.string.ui_export_canceled_because_playback_changed))}
                output=null
            }catch(e:CancellationException){remove(uri);throw e}
             catch(_:Exception){remove(uri);message(activity.getString(R.string.ui_lossless_clip_unsupported))}
            finally {output=null;source=null;progress=null;activity.window.decorView.keepScreenOn=keepAwake}
        }
    }
    fun cancel(){job?.cancel();output?.let(::remove);output=null;source=null;progress=null;activity.window.decorView.keepScreenOn=keepAwake}
    private fun remove(uri:Uri){runCatching {android.provider.DocumentsContract.deleteDocument(activity.contentResolver,uri)}}
    private fun message(text:String){if(!closed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show()}
    override fun close(){closed=true;cancel()}
    companion object {fun validRange(start:Long?,end:Long?)=start!=null&&end!=null&&start>=0&&end>start&&end-start>=1000}
}
