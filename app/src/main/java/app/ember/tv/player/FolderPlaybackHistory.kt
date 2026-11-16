package app.ember.tv.player

import android.content.Context
import android.net.Uri
import app.ember.tv.data.CachePolicy
import app.ember.tv.data.LocalActivity
import app.ember.tv.tracker.TokenStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Last-played file per chosen directory, encrypted like the main recent-media list. */
internal class FolderPlaybackHistory(private val readValue:()->String?,private val writeValue:(String)->Boolean,private val visible:(String)->Boolean={true}) {
    constructor(context:Context):this(
        {TokenStore(context.applicationContext).get("player.folderRecents")},
        {TokenStore(context.applicationContext).putEncrypted("player.folderRecents",it)},
        {app.ember.player.PrivateMedia.historyAllowed(it)},
    )
    private fun read():Map<String,RecentMedia> = readValue()?.takeIf {it.length<=256*1024}?.let {raw->
        runCatching {Json.decodeFromString<Map<String,RecentMedia>>(raw)}.getOrNull()?.filter {(key,value)->
            visible(value.uri) && key.matches(Regex("[0-9a-f]{64}")) && value.title.isNotBlank() && value.title.length<=180 && value.uri.length<=4096 &&
                runCatching {Uri.parse(value.uri).scheme=="content"}.getOrDefault(false)
        }?.entries?.toList()?.takeLast(100)?.associate {it.toPair()}
    }.orEmpty()
    fun purgeHidden():Boolean=writeValue(Json.encodeToString(read()))
    fun get(directory:Uri):RecentMedia?=read()[CachePolicy.digest(directory.toString())]
    fun remember(directory:Uri,media:RecentMedia):Boolean=synchronized(lock) {
        if(!visible(media.uri))return@synchronized true
        val key=CachePolicy.digest(directory.toString())
        val updated=(read().filterKeys {it!=key}+ (key to media)).entries.toList().takeLast(100).associate {it.toPair()}
        writeValue(Json.encodeToString(updated))
    }
    fun moved(file:DeviceFile,newUri:Uri,newParent:Uri,name:String):Boolean=synchronized(lock) {
        val previous=read()
        if(previous.values.none {it.uri==file.uri.toString()})return@synchronized true
        val updated=previous.filterValues {it.uri!=file.uri.toString()} + (CachePolicy.digest(newParent.toString()) to RecentMedia(newUri.toString(),name.take(180)))
        writeValue(Json.encodeToString(updated))
    }
    companion object {private val lock=Any()}
}

/** Keep a file's playback state when its provider changes its address after rename or move. */
internal fun transferFilePlaybackState(context:Context,file:DeviceFile,to:Uri,parent:Uri,name:String) {
    val fromKey=QueueMedia(file.uri.toString(),file.name).key
    val toKey=QueueMedia(to.toString(),name).key
    val prefs=app.ember.tv.data.Prefs(context)
    val config=prefs.playbackConfig
    val migrated=PictureProfiles.moved(config,fromKey,toKey)
    if(migrated!=config)prefs.playbackConfig=migrated
    val scope=LocalActivity.scope(context)
    PositionStore(context,scope).moveKey(fromKey,toKey)
    TrackMemory(context,scope).moveKey(fromKey,toKey)
    check(PlaybackBookmarks(context,scope).moveKey(fromKey,toKey)) {"File updated, but bookmarks could not be moved."}
    check(MediaHistory(context).moveUri(file.uri.toString(),to.toString(),name)) {"File updated, but recent history could not be updated."}
    check(FolderPlaybackHistory(context).moved(file,to,parent,name)) {"File updated, but folder history could not be updated."}
}
