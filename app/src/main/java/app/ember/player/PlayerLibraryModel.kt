package app.ember.player

import android.content.Context
import app.ember.tv.data.LocalActivity
import app.ember.tv.player.*

internal data class PlayerLibraryMedia(val media:QueueMedia,val position:Long=0,val duration:Long=0,val modified:Long=0,val lastPlayed:Long=0) {
    val progress get()=if(duration>0)(position.toDouble()/duration).coerceIn(0.0,1.0).toFloat() else 0f
    val continuing get()=PositionStore.canResume(position,duration)
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
/** One saved-index snapshot per refresh; sorting, filtering and paging share the same identities. */
internal class PlayerLibrarySnapshot(entries:List<PlayerLibraryMedia>,val indexUnavailable:Boolean=false) {
    val entries=entries.distinctBy {it.media.key}
    val continuing=this.entries.filter {it.continuing}.sortedWith(compareByDescending<PlayerLibraryMedia> {it.lastPlayed}.thenBy {it.media.key})
    // Prepared on the loading dispatcher, so changing a filter or page does no index work on the UI thread.
    private val pages=PlayerLibrarySort.entries.associateWith {sort->
        val sorted=when(sort) {
            PlayerLibrarySort.NAME->this.entries.sortedWith(compareBy<PlayerLibraryMedia> {it.media.title.lowercase(java.util.Locale.ROOT)}.thenBy {it.media.key})
            PlayerLibrarySort.MODIFIED->this.entries.sortedWith(compareByDescending<PlayerLibraryMedia> {it.modified}.thenBy {it.media.title.lowercase(java.util.Locale.ROOT)}.thenBy {it.media.key})
        }
        PlayerLibraryFilter.entries.associateWith {filter->when(filter) {
            PlayerLibraryFilter.ALL->sorted
            PlayerLibraryFilter.CONTINUING->continuing
            else->sorted.filter(filter::accepts)
        }}
    }
    fun page(offset:Int,sort:PlayerLibrarySort,filter:PlayerLibraryFilter):PlayerLibraryPage {
        require(offset>=0)
        val values=pages.getValue(sort).getValue(filter)
        val end=(offset.toLong()+100).coerceAtMost(values.size.toLong()).toInt()
        return PlayerLibraryPage(values.subList(offset.coerceAtMost(end),end),end.takeIf {it<values.size},indexUnavailable)
    }
}
/** Reads only saved history and the existing index, without scanning storage or fetching media. */
internal fun loadPlayerLibrary(context:Context,query:String,check:()->Unit={}):PlayerLibrarySnapshot {
    val recent=MediaHistory(context).read().filter {it.title.contains(query.trim(),true)}.map {QueueMedia(it.uri,it.title)}
    val indexed=linkedMapOf<String,QueueMedia>();val modified=mutableMapOf<String,Long>()
    var unavailable=false
    try {
        if(DeviceFolders(context).read().any {!PrivateMedia.hidden(it.uri)})MediaLibrary(context).use {index->
            var offset=0
            do {
                check()
                val page=index.search(query,offset)
                page.entries.forEach {indexed[it.key]=it};modified.putAll(page.modified)
                offset=page.nextOffset ?: -1
            }while(offset>=0)
        }
    }catch(e:kotlinx.coroutines.CancellationException){throw e}
     catch(_:Exception){unavailable=true}
     catch(_:LinkageError){unavailable=true}
    val positions=PositionStore(context,LocalActivity.scope(context))
    val values=(indexed.values+recent).distinctBy {it.key}.filterNot {PrivateMedia.hidden(it.uri)}.map {media->
        check()
        val saved=positions.saved(media.key)
        PlayerLibraryMedia(media,saved?.position?.coerceAtLeast(0) ?: 0,saved?.duration?.coerceAtLeast(0) ?: 0,modified[media.key] ?: 0,saved?.lastPlayed ?: 0)
    }
    return PlayerLibrarySnapshot(values,unavailable)
}
internal fun playerMediaTime(value:Long):String {
    val seconds=value.coerceAtLeast(0)/1000
    return if(seconds>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
        else String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60)
}
