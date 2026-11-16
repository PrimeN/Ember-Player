package app.ember.player

import android.content.Context
import app.ember.tv.data.LocalActivity
import app.ember.tv.player.*

internal data class PlayerLibraryMedia(val media:QueueMedia,val position:Long=0,val duration:Long=0) {
    val progress get()=if(duration>0)(position.toDouble()/duration).coerceIn(0.0,1.0).toFloat() else 0f
    val continuing get()=position>=1000 && duration>0 && position<duration*.95
}
internal data class PlayerLibraryPage(val entries:List<PlayerLibraryMedia>,val next:Int?,val indexUnavailable:Boolean=false)
/** Reads saved identities and a page of the existing index; never scans storage or fetches a URL. */
internal fun loadPlayerLibrary(context:Context,query:String,offset:Int):PlayerLibraryPage {
    val recent=if(offset==0)MediaHistory(context).read().filter {it.title.contains(query.trim(),true)}.map {QueueMedia(it.uri,it.title)} else emptyList()
    var unavailable=false
    val indexed=try {
        if(DeviceFolders(context).read().none {!PrivateMedia.hidden(it.uri)})MediaLibrary.Result(emptyList(),null)
        else MediaLibrary(context).use {it.search(query,offset)}
    }catch(_:Exception){unavailable=true;MediaLibrary.Result(emptyList(),null)}
     catch(_:LinkageError){unavailable=true;MediaLibrary.Result(emptyList(),null)}
    val positions=PositionStore(context,LocalActivity.scope(context))
    val values=(recent+indexed.entries).filterNot {PrivateMedia.hidden(it.uri)}.distinctBy {it.key}.map {media->
        val saved=positions.get(media.key)
        PlayerLibraryMedia(media,saved?.first?.coerceAtLeast(0) ?: 0,saved?.second?.coerceAtLeast(0) ?: 0)
    }
    return PlayerLibraryPage(values,indexed.nextOffset,unavailable)
}
internal fun playerMediaTime(value:Long):String {
    val seconds=value.coerceAtLeast(0)/1000
    return if(seconds>=3600)String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
        else String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60)
}
