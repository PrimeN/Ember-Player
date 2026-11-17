package app.ember.tv.player

import android.content.Context
import app.ember.player.ExternalMedia
import app.ember.tv.data.Prefs
import app.ember.player.storage.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class NetworkQueueEntry(val accountId:String,val path:String,val size:Long)
@Serializable
internal data class PlaylistEntry(val uri:String,val title:String,val identity:String?=null,val network:NetworkQueueEntry?=null,val directory:String?=null)
@Serializable
internal data class SavedPlaylist(val id:String,val name:String,val entries:List<PlaylistEntry>,val index:Int=0,val repeat:String="off",val shuffle:Boolean=false)

/** Saved addresses and connection references stay in the Keystore-backed store. */
internal class SavedPlaylists(private val readValue:()->String?,private val writeValue:(String)->Boolean) {
    constructor(context:Context,profile:String=Prefs(context).currentProfile):this(
        {TokenStore(context.applicationContext).get("player.playlists."+profile)},
        {TokenStore(context.applicationContext).putEncrypted("player.playlists."+profile,it)},
    )
    fun read():List<SavedPlaylist> = readValue()?.takeIf {it.length<=16*1024*1024}?.let {raw->
        runCatching {json.decodeFromString<List<SavedPlaylist>>(raw)}.getOrNull()?.filter(::valid)?.distinctBy {it.id}?.take(50)
    }.orEmpty()
    fun save(name:String,items:List<QueueMedia>,index:Int=0,repeat:String="off",shuffle:Boolean=false,id:String?=null):Boolean=synchronized(lock) {
        if(items.any {app.ember.player.PrivateMedia.hidden(it.uri)||it.identity?.startsWith(DirectUsbIdentity.PREFIX)==true})return@synchronized false
        val values=read()
        if(values.none {it.id==id} && values.size>=50)return@synchronized false
        val entries=items.map {media->PlaylistEntry(if(media.network==null)media.uri else "",media.title.take(180),media.identity,media.network,media.directory)}
        val value=SavedPlaylist(id ?: java.util.UUID.randomUUID().toString(),name.filterNot {it.isISOControl()}.trim().take(80),entries,index,repeat,shuffle)
        if(!valid(value))return@synchronized false
        val updated=values.filterNot {it.id==value.id}+value
        val encoded=json.encodeToString(updated)
        encoded.length<=16*1024*1024 && writeValue(encoded)
    }
    fun rename(id:String,name:String):Boolean=synchronized(lock) {
        val value=read().firstOrNull {it.id==id} ?: return@synchronized false
        val clean=name.filterNot {it.isISOControl()}.trim().take(80)
        if(clean.isBlank())return@synchronized false
        writeValue(json.encodeToString(read().map {if(it.id==id)value.copy(name=clean) else it}))
    }
    fun remove(id:String):Boolean=synchronized(lock) {writeValue(json.encodeToString(read().filterNot {it.id==id}))}
    fun progress(id:String,key:String):Boolean=synchronized(lock) {
        val values=read();val value=values.firstOrNull {it.id==id} ?: return@synchronized false
        val index=value.entries.indexOfFirst {entry->(entry.identity ?: QueueMedia(entry.uri,entry.title).key)==key}
        if(index !in value.entries.indices)return@synchronized false
        writeValue(json.encodeToString(values.map {if(it.id==id)it.copy(index=index) else it}))
    }
    companion object {
        private val lock=Any()
        private val json=Json {ignoreUnknownKeys=true}
        fun valid(value:SavedPlaylist):Boolean = value.id.isNotBlank() && value.id.length<=80 && value.name.isNotBlank() && value.name.length<=80 &&
            value.entries.size in 1..MediaQueue.MAX_ITEMS && value.index in value.entries.indices && value.repeat in setOf("off","one","all") && value.entries.all {entry->
                entry.title.isNotBlank() && entry.title.length<=180 && (entry.identity==null || entry.identity.length<=256) && (entry.directory==null || entry.directory.length<=16384 && entry.directory.startsWith("content://")) &&
                    if(entry.network!=null) {
                        val network=entry.network
                        entry.uri.isEmpty() && network.accountId.isNotBlank() && network.accountId.length<=80 && network.size>=0 && network.path.length in 1..4096 &&
                            network.path.split('/').all {it.isNotEmpty() && it !in setOf(".","..") && it.none {c->c=='\\' || c.isISOControl()}}
                    } else ExternalMedia.allowed(entry.uri)
            }
    }
}

internal class PreparedPlaylist(val items:List<QueueMedia>,val resources:List<java.io.Closeable>):java.io.Closeable {
    override fun close(){resources.forEach {runCatching {it.close()}}}
}

internal fun preparePlaylist(context:Context,playlist:SavedPlaylist):PreparedPlaylist {
    require(SavedPlaylists.valid(playlist)) {"Invalid playlist."}
    var relay:MediaRelay?=null
    try {
        val accounts=if(playlist.entries.any {it.network!=null}) {
            val raw=TokenStore(context).get("media.network."+Prefs(context).currentProfile) ?: "[]"
            require(raw.length<=1024*1024)
            Json {ignoreUnknownKeys=true}.decodeFromString<List<NetworkAccount>>(raw).associateBy {it.id}
        } else emptyMap()
        val items=playlist.entries.map {entry->
            val network=entry.network
            val uri=if(network==null)entry.uri else {
                val account=accounts[network.accountId] ?: error("A network folder used by this playlist is unavailable. Add its connection again.")
                val server=relay ?: MediaRelay().also {relay=it}
                server.register(NetworkFiles.resource(account,network.path,network.size),entry.title)
            }
            QueueMedia(uri,entry.title,entry.identity,network,entry.directory)
        }
        return PreparedPlaylist(items,listOfNotNull(relay))
    } catch(e:Throwable) {relay?.close();throw e}
}
