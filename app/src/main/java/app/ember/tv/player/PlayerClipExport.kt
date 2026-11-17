package app.ember.tv.player

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File

/** Video exports use device codecs; the source file is never modified. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlayerClipExport(private val activity:PlayerActivity):java.io.Closeable {
    var startMs:Long?=null;private set
    var endMs:Long?=null;private set
    private var visit=-1L
    private var source:String?=null
    private var output:Uri?=null
    private var temporary:File?=null
    private var transformer:Transformer?=null
    private var copy:Job?=null
    private var closed=false
    private var keepAwake=false
    val active get()=transformer!=null||copy?.isActive==true
    val progress:Int? get()=transformer?.let {engine->ProgressHolder().let {if(engine.getProgress(it)==Transformer.PROGRESS_STATE_AVAILABLE)it.progress else null}}
    private val picker=activity.registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) {uri->
        activity.pickingSidecar=false
        if(uri!=null&&validSource()) {output=uri;begin()}
        else {uri?.let(::remove);source=null}
    }
    fun markStart(){resetForSource();startMs=activity.player?.currentPosition;endMs=null}
    fun markEnd():Boolean {
        resetForSource();val end=activity.player?.currentPosition ?: return false
        if(!validRange(startMs,end))return false
        endMs=end;return true
    }
    private fun resetForSource(){if(visit!=activity.playbackVisit&&!active){startMs=null;endMs=null;visit=activity.playbackVisit}}
    fun chooseDestination() {
        resetForSource()
        if(active||!activity.exports.permitted()||!validRange(startMs,endMs)){message("Mark a clip start and end at least one second apart.");return}
        source=activity.currentUrl;visit=activity.playbackVisit
        activity.closeMenu();activity.pickingSidecar=true
        activity.player?.pause()
        try {picker.launch("Ember-clip-${System.currentTimeMillis()}.mp4")}
        catch(_:Exception){activity.pickingSidecar=false;source=null;message("No file picker is available.")}
    }
    private fun validSource()=!closed&&source!=null&&visit==activity.playbackVisit&&source==activity.currentUrl&&activity.exports.permitted()
    private fun begin() {
        if(!validSource()) {cancel();return}
        try {
            val file=File.createTempFile("clip-",".mp4",activity.cacheDir);temporary=file
            val media=MediaItem.Builder().setUri(source!!).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs!!).setEndPositionMs(endMs!!).build()).build()
            val listener=object:Transformer.Listener {
                override fun onCompleted(composition:Composition,result:ExportResult) {
                    transformer=null
                    if(!validSource()){cancel();return}
                    val uri=output ?: return
                    copy=activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                activity.contentResolver.openOutputStream(uri,"wt")?.use {out->file.inputStream().use {input->
                                    val buffer=ByteArray(65536)
                                    while(true){ensureActive();val count=input.read(buffer);if(count<0)break;out.write(buffer,0,count)}
                                };out.flush()} ?: error("Cannot write clip")
                            }
                            if(!validSource()){remove(uri);message("Export canceled because playback changed.")}else message("Clip saved")
                            output=null
                        } catch(e:CancellationException){remove(uri);throw e}
                          catch(_:Exception){remove(uri);message("Could not save the clip.")}
                        finally {cleanup()}
                    }
                }
                override fun onError(composition:Composition,result:ExportResult,exception:ExportException) {cancel();message("This format cannot be exported with the device's encoders.")}
            }
            transformer=Transformer.Builder(activity).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(listener).build()
            keepAwake=activity.window.decorView.keepScreenOn
            activity.window.decorView.keepScreenOn=true
            transformer!!.start(EditedMediaItem.Builder(media).build(),file.absolutePath)
            message("Exporting clip. Keep this page open; cancel from Playback options.")
        } catch(_:Exception){cancel();message("Could not start clip export.")}
    }
    fun cancel(){transformer?.cancel();transformer=null;copy?.cancel();copy=null;output?.let(::remove);output=null;cleanup()}
    private fun cleanup(){temporary?.delete();temporary=null;source=null;activity.window.decorView.keepScreenOn=keepAwake}
    private fun remove(uri:Uri){runCatching {android.provider.DocumentsContract.deleteDocument(activity.contentResolver,uri)}}
    private fun message(text:String){if(!closed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show()}
    override fun close(){closed=true;cancel()}
    companion object {fun validRange(start:Long?,end:Long?)=start!=null&&end!=null&&start>=0&&end>start&&end-start>=1000}
}
