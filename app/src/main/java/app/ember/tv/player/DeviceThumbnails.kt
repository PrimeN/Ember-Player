package app.ember.tv.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

internal data class BrowserPreview(val bitmap:Bitmap?=null,val duration:Long=0)

/** Browser artwork stays in bounded RAM and cannot be restored by a read finishing after lock. */
internal object DeviceThumbnails {
    private val memory=object:LruCache<String,BrowserPreview>(4*1024*1024) {
        override fun sizeOf(key:String,value:BrowserPreview)=128+(value.bitmap?.allocationByteCount ?: 0)
    }
    private val lock=Any()
    private val gate=Semaphore(2)
    private val generation=AtomicLong()
    fun clear()=synchronized(lock){generation.incrementAndGet();memory.evictAll()}
    fun audio(name:String)=name.substringAfterLast('.').lowercase(java.util.Locale.ROOT) in setOf("mp3","flac","aac","m4a","ogg","opus","wav","wma","aiff","aif","ac3","eac3","dts","mka","ape","alac","amr")
    private fun read(retriever:MediaMetadataRetriever,audio:Boolean,width:Int):BrowserPreview {
        val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        val frame=if(audio)null else runCatching {retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,width,width*9/16)}.getOrNull()
        return BrowserPreview(frame,duration)
    }
    private suspend fun cached(key:String,load:()->BrowserPreview):BrowserPreview {
        val visit=generation.get()
        memory.get(key)?.let {return it}
        return gate.withPermit {withContext(Dispatchers.IO) {
            ensureActive()
            val result=memory.get(key) ?: runCatching(load).getOrDefault(BrowserPreview())
            ensureActive()
            synchronized(lock){if(visit==generation.get()){memory.put(key,result);result}else BrowserPreview()}
        }}
    }
    suspend fun preview(context:Context,file:DeviceFile,requestedWidth:Int=160):BrowserPreview {
        if(file.directory)return BrowserPreview()
        val width=requestedWidth.coerceIn(160,320)
        val key="${file.uri}|${file.modified}|${file.size}|$width"
        return cached(key) {
            MediaMetadataRetriever().use {retriever->
                retriever.setDataSource(context,file.uri)
                read(retriever,file.mime.startsWith("audio/")||audio(file.name),width)
            }
        }
    }
    suspend fun load(context:Context,file:DeviceFile,requestedWidth:Int=160)=preview(context,file,requestedWidth).bitmap
    suspend fun remote(key:String,name:String,source:RemoteMedia):BrowserPreview= cached("remote:$key") {
        val budget=AtomicLong(8L*1024*1024)
        val deadline=android.os.SystemClock.elapsedRealtime()+8000
        val visit=generation.get()
        fun check(){check(visit==generation.get()&&budget.get()>0&&android.os.SystemClock.elapsedRealtime()<deadline){"Preview read limit reached"}}
        val bounded=object:RemoteMedia {
            override val length=source.length
            override fun open(offset:Long):InputStream {
                check()
                return object:FilterInputStream(source.open(offset)) {
                    override fun read():Int {val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255}
                    override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                        if(length==0)return 0
                        check()
                        val read=`in`.read(bytes,offset,minOf(length.toLong(),budget.get()).toInt())
                        if(read>0)budget.addAndGet(-read.toLong())
                        return read
                    }
                }
            }
        }
        MediaRelay().use {relay->MediaMetadataRetriever().use {retriever->
            retriever.setDataSource(relay.register(bounded,name),emptyMap())
            read(retriever,audio(name),160)
        }}
    }
}
