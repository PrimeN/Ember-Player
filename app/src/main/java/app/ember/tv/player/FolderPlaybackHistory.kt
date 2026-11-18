package app.ember.tv.player

import android.content.Context
import android.net.Uri
import app.ember.tv.data.CachePolicy
import app.ember.tv.data.LocalActivity
import app.ember.player.storage.TokenStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Last-played file per chosen directory, encrypted like the main recent-media list. */
internal class FolderPlaybackHistory(private val readValue:()->String?,private val writeValue:(String)->Boolean,private val visible:(String)->Boolean={true}) {
    constructor(context:Context):this(
        {TokenStore(context.applicationContext).getForUpdate("player.folderRecents")},
        {TokenStore(context.applicationContext).putEncrypted("player.folderRecents",it)},
        {app.ember.player.PrivateMedia.historyAllowed(it)},
    )
    private fun load():Map<String,RecentMedia> {
        val raw=readValue() ?: return emptyMap()
        require(raw.length<=256*1024)
        val values=Json.decodeFromString<Map<String,RecentMedia>>(raw)
        require(values.size<=100 && values.all {(key,value)->
            key.matches(Regex("[0-9a-f]{64}")) && value.title.isNotBlank() && value.title.length<=180 && value.uri.length<=4096 &&
                Uri.parse(value.uri).scheme=="content"
        })
        return values
    }
    fun readForDisplay()=load().filterValues {visible(it.uri)}
    fun resetUnreadable():Boolean=synchronized(lock) {
        app.ember.player.storage.resetUnreadableList(::load){writeValue("{}")}
    }
    private fun update(change:(Map<String,RecentMedia>)->Map<String,RecentMedia>):Boolean=synchronized(lock) {
        try {writeValue(Json.encodeToString(change(load())))}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){false}
    }
    fun purgeHidden():Boolean=update {it.filterValues {value->visible(value.uri)}}
    fun get(directory:Uri):RecentMedia?=runCatching {readForDisplay()[CachePolicy.digest(directory.toString())]}.getOrNull()
    fun remember(directory:Uri,media:RecentMedia):Boolean {
        if(!visible(media.uri))return true
        return update {previous->
            require(media.title.isNotBlank()&&media.title.length<=180&&media.uri.length<=4096&&Uri.parse(media.uri).scheme=="content")
            val key=CachePolicy.digest(directory.toString())
            (previous.filterKeys {it!=key}+(key to media)).entries.toList().takeLast(100).associate {it.toPair()}
        }
    }
    fun moved(file:DeviceFile,newUri:Uri,newParent:Uri,name:String):Boolean=update {previous->
        if(previous.values.none {it.uri==file.uri.toString()})previous
        else previous.filterValues {it.uri!=file.uri.toString()}+(CachePolicy.digest(newParent.toString()) to RecentMedia(newUri.toString(),name.take(180)))
    }
    companion object {private val lock=Any()}
}

/** Keep a file's playback state when its provider changes its address after rename or move. */
internal fun transferFilePlaybackState(context:Context,file:DeviceFile,to:Uri,parent:Uri,name:String) {
    val fromKey=QueueMedia(file.uri.toString(),file.name).key
    val toKey=QueueMedia(to.toString(),name).key
    val prefs=app.ember.tv.data.Prefs(context)
    val config=prefs.playbackConfig
    val migrated=GeometryMemory.moved(PictureProfiles.moved(config,fromKey,toKey),fromKey,toKey)
    if(migrated!=config)prefs.playbackConfig=migrated
    val scope=LocalActivity.scope(context)
    PositionStore(context,scope).moveKey(fromKey,toKey)
    TrackMemory(context,scope).moveKey(fromKey,toKey)
    check(PlaybackBookmarks(context,scope).moveKey(fromKey,toKey)) {"File updated, but bookmarks could not be moved."}
    check(MediaHistory(context).moveUri(file.uri.toString(),to.toString(),name)) {"File updated, but recent history could not be updated."}
    check(FolderPlaybackHistory(context).moved(file,to,parent,name)) {"File updated, but folder history could not be updated."}
}
