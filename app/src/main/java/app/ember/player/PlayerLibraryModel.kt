package app.ember.player

import android.content.Context
import app.ember.tv.data.LocalActivity
import app.ember.tv.player.*

internal data class PlayerLibraryMedia(val media:QueueMedia,val position:Long=0,val duration:Long=0,val modified:Long=0) {
    val progress get()=if(duration>0)(position.toDouble()/duration).coerceIn(0.0,1.0).toFloat() else 0f
    val continuing get()=position>=1000 && duration>0 && position<duration*.95
    val watched get()=duration>0 && position>=duration*.95
}
internal enum class PlayerLibraryFilter(val id:String,val label:String) {
    ALL("all","All media"),CONTINUING("continuing","Continue watching"),UNWATCHED("unwatched","Unwatched"),WATCHED("watched","Watched");
    fun accepts(value:PlayerLibraryMedia)=when(this){ALL->true;CONTINUING->value.continuing;UNWATCHED->!value.watched;WATCHED->value.watched}
    companion object {fun from(id:String)=entries.firstOrNull {it.id==id} ?: ALL}
}
internal enum class PlayerLibrarySort(val id:String,val label:String) {
    MODIFIED("modified","Date modified"),NAME("name","Name A–Z");
    companion object {fun from(id:String)=entries.firstOrNull {it.id==id} ?: MODIFIED}
}
internal data class PlayerLibraryPage(val entries:List<PlayerLibraryMedia>,val next:Int?,val indexUnavailable:Boolean=false)
/** Reads saved identities and a page of the existing index; never scans storage or fetches a URL. */
internal fun loadPlayerLibrary(context:Context,query:String,offset:Int,sort:PlayerLibrarySort=PlayerLibrarySort.MODIFIED):PlayerLibraryPage {
    val recent=if(offset==0)MediaHistory(context).read().filter {it.title.contains(query.trim(),true)}.map {QueueMedia(it.uri,it.title)} else emptyList()
    var unavailable=false
    val indexed=try {
        if(DeviceFolders(context).read().none {!PrivateMedia.hidden(it.uri)})MediaLibrary.Result(emptyList(),null)
        else MediaLibrary(context).use {it.search(query,offset,sort.id)}
    }catch(_:Exception){unavailable=true;MediaLibrary.Result(emptyList(),null)}
     catch(_:LinkageError){unavailable=true;MediaLibrary.Result(emptyList(),null)}
    val positions=PositionStore(context,LocalActivity.scope(context))
    val values=(recent+indexed.entries).filterNot {PrivateMedia.hidden(it.uri)}.distinctBy {it.key}.map {media->
        val saved=positions.get(media.key)
        PlayerLibraryMedia(media,saved?.first?.coerceAtLeast(0) ?: 0,saved?.second?.coerceAtLeast(0) ?: 0,indexed.modified[media.key] ?: 0)
    }
    val sorted=when(sort){PlayerLibrarySort.NAME->values.sortedBy {it.media.title.lowercase(java.util.Locale.ROOT)}
        PlayerLibrarySort.MODIFIED->values.sortedByDescending {it.modified}}
    return PlayerLibraryPage(sorted,indexed.nextOffset,unavailable)
}
internal fun playerMediaTime(value:Long):String {
    val seconds=value.coerceAtLeast(0)/1000
    return if(seconds>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
        else String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60)
}
