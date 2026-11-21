package app.ember.tv.player

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException

/** One folder visit, never persisted. Oversized listings retain the bounded keyset fallback. */
internal class FolderBrowseVisit(private val maxEntries:Int=20_000,private val maxBytes:Long=16L*1024*1024):AutoCloseable {
    private sealed interface State
    private data object Empty:State
    private data object Oversized:State
    private data object Closed:State
    private class Snapshot(val entries:List<DeviceFile>):State {
        var options:Triple<String,String,Boolean>?=null
        var ordered=emptyList<DeviceFile>()
    }
    private val state=AtomicReference<State>(Empty)
    override fun close(){state.set(Closed)}
    @Synchronized fun page(load:(Int)->DeviceFiles.Page,query:String,sort:String,hidden:Boolean,after:DeviceFile?=null,
        hiddenMedia:(String)->Boolean={false},check:()->Unit={}):DeviceFiles.BrowsePage {
        fun active(){check();if(state.get()===Closed)throw CancellationException("Folder visit ended")}
        active()
        var current=state.get()
        if(current===Closed)throw CancellationException("Folder visit ended")
        if(current===Empty) {
            val retained=linkedMapOf<String,DeviceFile>()
            var bytes=0L
            var oversized=false
            val first=DeviceFiles.browsePages({offset->
                active()
                load(offset).also {page->if(!oversized)for(file in page.entries) {
                    active()
                    val key=file.uri.toString()
                    if(key in retained)continue
                    bytes+=256L+2L*(key.length+file.name.length+file.mime.length+file.parent.toString().length)
                    if(retained.size>=maxEntries||bytes>maxBytes){oversized=true;retained.clear();break}
                    retained[key]=file
                }}
            },query,sort,hidden,after,hiddenMedia,::active)
            active()
            current=if(oversized)Oversized else Snapshot(retained.values.toList())
            if(!state.compareAndSet(Empty,current))throw CancellationException("Folder visit ended")
            if(oversized)return first
        }
        if(current===Oversized)return DeviceFiles.browsePages(load,query,sort,hidden,after,hiddenMedia,::active)
        val snapshot=current as Snapshot
        val options=Triple(query,sort,hidden)
        if(snapshot.options!=options) {
            val ordered=DeviceFiles.sorted(snapshot.entries,query,sort,hidden)
            active();snapshot.ordered=ordered;snapshot.options=options
        }
        val comparator=DeviceFiles.order(sort)
        val start=if(after==null)0 else snapshot.ordered.binarySearch(after,comparator).let {if(it>=0)it+1 else -it-1}
        val entries=snapshot.ordered.asSequence().drop(start).filter {active();!hiddenMedia(it.uri.toString())}.take(DeviceFiles.LIMIT+1).toList()
        active()
        return DeviceFiles.BrowsePage(entries.take(DeviceFiles.LIMIT),entries.getOrNull(DeviceFiles.LIMIT)?.let {entries[DeviceFiles.LIMIT-1]})
    }
}
