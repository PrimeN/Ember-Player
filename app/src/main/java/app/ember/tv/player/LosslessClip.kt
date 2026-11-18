package app.ember.tv.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.Format
import java.nio.ByteBuffer

/** Copy encoded samples directly to a seekable MP4 destination; no intermediate movie is made. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object LosslessClip {
    class AudioUnavailable:IllegalStateException("Selected audio cannot be copied to MP4")
    data class Track(val index:Int,val id:Int?,val mime:String,val language:String?,val channels:Int,val rate:Int)
    fun chooseAudio(tracks:List<Track>,selected:Format?,disabled:Boolean):Int? {
        if(disabled)return null
        if(tracks.isEmpty()){if(selected!=null)throw AudioUnavailable();return null}
        selected?.id?.toIntOrNull()?.let {id->tracks.singleOrNull {it.id==id}?.let {return it.index}}
        fun matches(track:Track)=selected==null ||
            (selected.sampleMimeType==null || track.mime==selected.sampleMimeType) &&
            (selected.language.isNullOrBlank() || language(track.language)==language(selected.language)) &&
            (selected.channelCount<=0 || track.channels==selected.channelCount) && (selected.sampleRate<=0 || track.rate==selected.sampleRate)
        selected?.id?.takeIf {it.startsWith("mpv:audio:")}?.substringAfterLast(':')?.toIntOrNull()?.let {ordinal->
            tracks.getOrNull(ordinal-1)?.takeIf(::matches)?.let {return it.index}
            throw AudioUnavailable()
        }
        return tracks.filter(::matches).singleOrNull()?.index ?: throw AudioUnavailable()
    }
    internal fun openDestination(open:(String)->android.os.ParcelFileDescriptor?):android.os.ParcelFileDescriptor {
        val file=try {open("rwt")}catch(_:java.io.IOException){null}catch(_:IllegalArgumentException){null}catch(_:UnsupportedOperationException){null}
            ?: open("rw") ?: error("Cannot open destination")
        try {
            android.system.Os.ftruncate(file.fileDescriptor,0)
            android.system.Os.lseek(file.fileDescriptor,0,android.system.OsConstants.SEEK_SET)
            return file
        }catch(e:Exception){file.close();throw e}
    }
    private fun language(value:String?):String {
        val code=runCatching {java.util.Locale.forLanguageTag(value.orEmpty()).isO3Language}.getOrDefault(value.orEmpty()).lowercase(java.util.Locale.ROOT)
        return mapOf("fre" to "fra","ger" to "deu","chi" to "zho","dut" to "nld","cze" to "ces","gre" to "ell","rum" to "ron","slo" to "slk","may" to "msa")[code] ?: code
    }
    fun copy(context:Context,source:String,headers:Map<String,String>,destination:Uri,startMs:Long,endMs:Long,selected:Format?,audioDisabled:Boolean,check:()->Unit,progress:(Int)->Unit) {
        require(startMs>=0 && endMs>startMs && endMs<=Long.MAX_VALUE/1000)
        val extractor=MediaExtractor()
        try {
            check();extractor.setDataSource(context,Uri.parse(source),headers)
            val formats=(0 until extractor.trackCount).associateWith(extractor::getTrackFormat)
            fun MediaFormat.integer(key:String)=if(containsKey(key))getInteger(key) else -1
            val video=formats.entries.filter {it.value.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")}.singleOrNull()?.key ?: error("A single video track is required")
            val tracks=formats.mapNotNull {(index,f)->val mime=f.getString(MediaFormat.KEY_MIME).orEmpty();if(!mime.startsWith("audio/"))null else
                Track(index,f.integer(MediaFormat.KEY_TRACK_ID).takeIf {it>=0},mime,f.getString(MediaFormat.KEY_LANGUAGE),f.integer(MediaFormat.KEY_CHANNEL_COUNT),f.integer(MediaFormat.KEY_SAMPLE_RATE))}
            val audio=chooseAudio(tracks,selected,audioDisabled)
            extractor.selectTrack(video)
            extractor.seekTo(startMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val anchor=extractor.sampleTime.takeIf {it>=0} ?: error("No video samples")
            extractor.seekTo(endMs*1000,MediaExtractor.SEEK_TO_NEXT_SYNC)
            val format=formats.getValue(video)
            val sourceEnd=if(format.containsKey(MediaFormat.KEY_DURATION))format.getLong(MediaFormat.KEY_DURATION) else Long.MAX_VALUE
            val end=extractor.sampleTime.takeIf {it>=endMs*1000 && it>anchor} ?: sourceEnd
            require(end>anchor)
            audio?.let(extractor::selectTrack)
            extractor.seekTo(anchor,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            openDestination {mode->context.contentResolver.openFileDescriptor(destination,mode)}.use {file->
                val muxer=MediaMuxer(file.fileDescriptor,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var started=false
                try {
                    val map=mutableMapOf(video to muxer.addTrack(formats.getValue(video)))
                    audio?.let {index->
                        map[index]=try {muxer.addTrack(formats.getValue(index))}catch(_:IllegalArgumentException){throw AudioUnavailable()}catch(_:IllegalStateException){throw AudioUnavailable()}
                    }
                    if(format.containsKey(MediaFormat.KEY_ROTATION))muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                    muxer.start();started=true
                    var buffer=ByteBuffer.allocateDirect(256*1024);val info=MediaCodec.BufferInfo();var written=false
                    while(extractor.sampleTrackIndex>=0) {
                        check()
                        val time=extractor.sampleTime
                        if(time>=end)break
                        if(time>=anchor) {
                            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED==0)
                            val size=extractor.sampleSize
                            require(size in 1..64L*1024*1024)
                            if(size>buffer.capacity())buffer=ByteBuffer.allocateDirect(size.toInt())
                            buffer.clear();val n=extractor.readSampleData(buffer,0);if(n<0)break
                            info.set(0,n,time-anchor,if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0)MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            map[extractor.sampleTrackIndex]?.let {muxer.writeSampleData(it,buffer,info);written=true}
                            if(end!=Long.MAX_VALUE)progress(((time-anchor).toDouble()/(end-anchor)*100).toInt().coerceIn(0,99))
                        }
                        if(!extractor.advance())break
                    }
                    check(written);check();muxer.stop();started=false;progress(100)
                } finally {if(started)runCatching {muxer.stop()};muxer.release()}
            }
        } finally {extractor.release()}
    }
}
