package app.ember.tv.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.ember.player.PrivateMedia
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** Explicit user exports only. Protected playback cannot be captured or exported. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlayerExports(private val activity:PlayerActivity):java.io.Closeable {
    private var image:ByteArray?=null
    private var source:String?=null
    private var visit=0L
    private var job:Job?=null
    private var closed=false
    private val destination=activity.registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {uri->
        activity.pickingSidecar=false
        val bytes=image;image=null
        val allowed=!closed&&visit==activity.playbackVisit&&permitted()&&source==activity.currentUrl
        source=null
        if(uri!=null&&bytes!=null&&allowed)job=activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri,"wt")?.use {out->out.write(bytes);out.flush()} ?: error("Cannot write screenshot")
                }
                if(permitted()&&visit==activity.playbackVisit)message("Screenshot saved") else {remove(uri);message("Screenshot cancelled because playback changed.")}
            } catch(e:CancellationException){remove(uri);throw e}
              catch(_:Exception){remove(uri);message("Could not save the screenshot.")}
        } else if(uri!=null)remove(uri)
    }
    fun permitted():Boolean = !activity.privatePlayback && !PrivateMedia.hidden(activity.currentUrl.orEmpty()) &&
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0 &&
        activity.player?.currentMediaItem?.localConfiguration?.drmConfiguration==null
    fun screenshot() {
        if(closed||job?.isActive==true||image!=null)return
        if(!permitted()){message("Screenshots are unavailable for protected playback.");return}
        val currentVisit=activity.playbackVisit
        activity.closeMenu()
        job=activity.lifecycleScope.launch {
            var frame:Bitmap?=null
            try {
                frame=capture(activity.view.videoSurfaceView) ?: error("No readable video frame")
                val bytes=withContext(Dispatchers.Default){ByteArrayOutputStream().use {out->check(frame.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}}
                if(closed||currentVisit!=activity.playbackVisit||!permitted())return@launch
                image=bytes;source=activity.currentUrl;visit=currentVisit
                activity.pickingSidecar=true
                destination.launch("Ember-${System.currentTimeMillis()}.png")
            } catch(e:CancellationException){throw e}
              catch(_:Exception){image=null;source=null;activity.pickingSidecar=false;message("This video surface cannot be captured.")}
            finally {frame?.recycle()}
        }
    }
    private suspend fun capture(surface:android.view.View?):Bitmap? {
        surface ?: return null
        val size=boundedSize(surface.width,surface.height)
        if(size.first==0)return null
        if(surface is TextureView)return surface.getBitmap(size.first,size.second)
        if(surface !is SurfaceView||!surface.holder.surface.isValid)return null
        val bitmap=Bitmap.createBitmap(size.first,size.second,Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine {continuation->
            try {PixelCopy.request(surface,bitmap,{status->
                if(status==PixelCopy.SUCCESS&&continuation.isActive)continuation.resume(bitmap) {_,value,_->value.recycle()}
                else {bitmap.recycle();if(continuation.isActive)continuation.resume(null)}
            },Handler(Looper.getMainLooper()))}
            catch(_:Exception){bitmap.recycle();if(continuation.isActive)continuation.resume(null)}
        }
    }
    private fun message(text:String){if(!closed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show()}
    private fun remove(uri:Uri){runCatching {android.provider.DocumentsContract.deleteDocument(activity.contentResolver,uri)}}
    override fun close(){closed=true;job?.cancel();image=null;source=null}
    companion object {
        fun boundedSize(width:Int,height:Int):Pair<Int,Int> {
            if(width<=0||height<=0)return 0 to 0
            val scale=minOf(1.0,1920.0/width,1080.0/height)
            return maxOf(1,(width*scale).toInt()) to maxOf(1,(height*scale).toInt())
        }
    }
}
