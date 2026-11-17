package app.ember.player

import app.ember.tv.R

import android.content.Context
import app.ember.tv.player.*

internal data class PlayerLibraryMedia(val media:QueueMedia,val position:Long=0,val duration:Long=0,val modified:Long=0,val lastPlayed:Long=0) {
    val key=media.key
    val progress get()=if(duration>0)(position.toDouble()/duration).coerceIn(0.0,1.0).toFloat() else 0f
    val continuing get()=PositionStore.canResume(position,duration)
    val watched get()=duration>0 && position>=duration*.95
}
internal enum class PlayerLibraryFilter(val id:String,@androidx.annotation.StringRes val label:Int) {
    ALL("all",R.string.ui_all_media),CONTINUING("continuing",R.string.ui_continue_watching),UNWATCHED("unwatched",R.string.ui_unwatched),WATCHED("watched",R.string.ui_watched);
    fun accepts(value:PlayerLibraryMedia)=when(this){ALL->true;CONTINUING->value.continuing;UNWATCHED->!value.watched;WATCHED->value.watched}
    companion object {fun from(id:String)=entries.firstOrNull {it.id==id} ?: ALL}
}
internal enum class PlayerLibrarySort(val id:String,@androidx.annotation.StringRes val label:Int) {
    MODIFIED("modified",R.string.ui_date_modified),NAME("name",R.string.ui_name_a_z);
    companion object {fun from(id:String)=entries.firstOrNull {it.id==id} ?: MODIFIED}
}
internal data class PlayerLibraryPage(val entries:List<PlayerLibraryMedia>,val next:Int?,val indexUnavailable:Boolean=false)
/** Immutable catalog orders are shared by progress snapshots until the saved index changes. */
internal class PlayerLibraryCatalog(entries:List<PlayerLibraryMedia>,val indexUnavailable:Boolean=false) {
    val entries=entries.distinctBy {it.key}
    val byKey=this.entries.associateBy {it.key}
    val ordered=PlayerLibrarySort.entries.associateWith {this.entries.sortedWith(comparator(it))}
    companion object {
        fun comparator(sort:PlayerLibrarySort):Comparator<PlayerLibraryMedia> = when(sort) {
            PlayerLibrarySort.NAME->compareBy<PlayerLibraryMedia,String>(NaturalOrder) {it.media.title}.thenBy {it.key}
            PlayerLibrarySort.MODIFIED->compareByDescending<PlayerLibraryMedia> {it.modified}.thenBy(NaturalOrder) {it.media.title}.thenBy {it.key}
        }
    }
}
internal class PlayerLibrarySnapshot(
    internal val catalog:PlayerLibraryCatalog,
    recent:List<QueueMedia>,
    private val positions:Map<String,PositionStore.SavedPosition>,
) {
    constructor(entries:List<PlayerLibraryMedia>,indexUnavailable:Boolean=false):this(PlayerLibraryCatalog(entries,indexUnavailable),emptyList(),
        entries.distinctBy {it.key}.associate {it.key to PositionStore.SavedPosition(it.position,it.duration,it.lastPlayed)})
    private val extra=PlayerLibraryCatalog(recent.filterNot {it.key in catalog.byKey}.map {PlayerLibraryMedia(it)})
    val indexUnavailable get()=catalog.indexUnavailable
    private fun progress(value:PlayerLibraryMedia):PlayerLibraryMedia {
        val saved=positions[value.key]
        return value.copy(position=saved?.position?.coerceAtLeast(0) ?: 0,duration=saved?.duration?.coerceAtLeast(0) ?: 0,lastPlayed=saved?.lastPlayed ?: 0)
    }
    // A progress refresh doesn't allocate one new entry for every indexed file.
    val entries:List<PlayerLibraryMedia> = object:AbstractList<PlayerLibraryMedia>() {
        override val size get()=catalog.entries.size+extra.entries.size
        override fun get(index:Int)=progress(if(index<catalog.entries.size)catalog.entries[index] else extra.entries[index-catalog.entries.size])
    }
    val continuing=positions.keys.mapNotNull {key->(catalog.byKey[key] ?: extra.byKey[key])?.let(::progress)}.filter {it.continuing}
        .sortedWith(compareByDescending<PlayerLibraryMedia> {it.lastPlayed}.thenBy {it.key})
    private fun ordered(sort:PlayerLibrarySort):Sequence<PlayerLibraryMedia> = sequence {
        val indexed=catalog.ordered.getValue(sort);val recent=extra.ordered.getValue(sort)
        val comparator=PlayerLibraryCatalog.comparator(sort)
        var i=0;var r=0
        while(i<indexed.size || r<recent.size) {
            yield(if(r>=recent.size || i<indexed.size && comparator.compare(indexed[i],recent[r])<=0)indexed[i++] else recent[r++])
        }
    }
    fun page(offset:Int,sort:PlayerLibrarySort,filter:PlayerLibraryFilter,hidden:(String)->Boolean={false}):PlayerLibraryPage {
        require(offset>=0)
        val source=if(filter==PlayerLibraryFilter.CONTINUING)continuing.asSequence() else ordered(sort)
        val values=source.filter {value->
            val saved=positions[value.key]
            val watched=saved!=null && saved.duration>0 && saved.position>=saved.duration*.95
            !hidden(value.media.uri) && when(filter) {
                PlayerLibraryFilter.ALL,PlayerLibraryFilter.CONTINUING->true
                PlayerLibraryFilter.WATCHED->watched
                PlayerLibraryFilter.UNWATCHED->!watched
            }
        }.drop(offset).take(101).map(::progress).toList()
        return PlayerLibraryPage(values.take(100),(offset+100).takeIf {values.size>100},indexUnavailable)
    }
}
/** Reads only the saved index. Playback history and positions are refreshed independently. */
internal fun loadPlayerLibraryCatalog(context:Context,query:String,check:()->Unit={}):PlayerLibraryCatalog {
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
    check()
    return PlayerLibraryCatalog(indexed.values.filterNot {PrivateMedia.hidden(it.uri)}.map {PlayerLibraryMedia(it,modified=modified[it.key] ?: 0)},unavailable)
}
internal fun playerMediaTime(value:Long):String {
    val seconds=value.coerceAtLeast(0)/1000
    return if(seconds>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
        else String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60)
}
