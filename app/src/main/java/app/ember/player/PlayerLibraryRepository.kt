package app.ember.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import app.ember.tv.data.LocalActivity
import app.ember.tv.player.*

internal class PlayerLibraryViewModel(app:Application):AndroidViewModel(app) {
    val library=PlayerLibraryRepository(app)
}

/** At most two catalogs: the library and the most recent search. Owned by the activity ViewModel. */
internal class PlayerLibraryRepository(
    private val stamp:()->Any,
    private val catalog:(String,()->Unit)->PlayerLibraryCatalog,
    private val recent:(String)->List<QueueMedia>,
    private val positions:()->Map<String,PositionStore.SavedPosition>,
    private val folders:()->List<DeviceFolder>,
) {
    constructor(context:Context):this(
        {sourceStamp(context)},
        {query,check->loadPlayerLibraryCatalog(context,query,check)},
        {query->MediaHistory(context).read().filter {MediaSearch.matches(it.title,query)}.map {QueueMedia(it.uri,it.title)}},
        {PositionStore(context,LocalActivity.scope(context)).savedAll()},
        {DeviceFolders(context).read()},
    )
    private data class Cached(val stamp:Any,val revision:Int,val snapshot:PlayerLibrarySnapshot)
    @Volatile private var cache:Map<String,Cached> = emptyMap()
    fun peek(query:String):PlayerLibrarySnapshot?=cache[query.trim()]?.snapshot
    @Synchronized fun load(query:String,revision:Int,check:()->Unit={}):Pair<PlayerLibrarySnapshot,List<DeviceFolder>> {
        val term=query.trim()
        val current=stamp();val previous=cache[term]
        val indexed=if(previous!=null && previous.stamp==current && previous.revision==revision && !previous.snapshot.indexUnavailable)previous.snapshot.catalog
            else catalog(term,check)
        check()
        val snapshot=PlayerLibrarySnapshot(indexed,recent(term),positions())
        val savedFolders=folders()
        check()
        val after=stamp()
        // Opening an older index may write missing sort keys. A concurrent refresh must still invalidate it.
        val savedStamp=if(current is SourceStamp && after is SourceStamp && current.copy(files=after.files)==after)after else current
        val updated=cache.toMutableMap().apply {remove(term);put(term,Cached(savedStamp,revision,snapshot))}
        // Retain the main catalog while search queries change.
        while(updated.size>2)updated.remove(updated.keys.first {it.isNotEmpty() && it!=term})
        cache=updated
        return snapshot to savedFolders
    }
    companion object {
        private data class SourceStamp(val scope:String,val roots:List<DeviceFolder>,val privacy:Any,val revision:Long,val files:List<Pair<Long,Long>>)
        private fun sourceStamp(context:Context):Any {
            val database=context.getDatabasePath("media-library.db")
            return SourceStamp(LocalActivity.scope(context),DeviceFolders(context).read(),PrivateMedia.libraryVisibility,MediaLibrary.revision.get(),
                listOf(database,java.io.File(database.path+"-wal"),java.io.File(database.path+"-journal")).map {it.lastModified() to it.length()})
        }
    }
}
