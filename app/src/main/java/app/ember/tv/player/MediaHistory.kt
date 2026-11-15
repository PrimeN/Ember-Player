package app.ember.tv.player

import android.content.Context
import app.ember.tv.tracker.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class RecentMedia(val uri:String,val title:String)

/** Stream addresses can contain credentials. History never has a plaintext storage fallback. */
internal class MediaHistory(private val readValue:()->String?,private val writeValue:(String)->Boolean) {
    constructor(context:Context):this(
        {TokenStore(context.applicationContext).get(KEY)},
        {TokenStore(context.applicationContext).putEncrypted(KEY,it)},
    )
    fun read():List<RecentMedia> = readValue()?.takeIf {it.length<=512*1024}?.let {raw->
        runCatching {json.decodeFromString<List<RecentMedia>>(raw)}.getOrNull()?.filter(::valid)?.distinctBy {it.uri}?.take(20)
    }.orEmpty()
    fun add(items:List<QueueMedia>):Boolean=synchronized(lock) {
        val next=(items.map {RecentMedia(it.uri,it.title.take(180))}.filter(::valid)+read()).distinctBy {it.uri}.take(20)
        writeValue(json.encodeToString(next))
    }
    fun clear():Boolean=writeValue("[]")
    fun moveUri(from:String,to:String,title:String):Boolean=synchronized(lock) {
        val previous=read()
        if(previous.none {it.uri==from})return@synchronized true
        val updated=previous.map {if(it.uri==from)RecentMedia(to,title.take(180)) else it}.filter(::valid).distinctBy {it.uri}
        writeValue(json.encodeToString(updated))
    }
    companion object {
        private const val KEY="player.recent"
        private val lock=Any()
        private val json=Json {ignoreUnknownKeys=true}
        private fun valid(value:RecentMedia)=value.title.isNotBlank() && value.title.length<=180 && app.ember.player.ExternalMedia.allowed(value.uri)
    }
}
