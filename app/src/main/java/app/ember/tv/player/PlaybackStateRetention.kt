package app.ember.tv.player

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Bounds automatic playback memory across profiles. Explicit bookmarks are kept separately. */
internal class PlaybackStateRetention(context:Context,private val file:String) {
    private val values=context.getSharedPreferences(file,Context.MODE_PRIVATE)
    private val recency=context.getSharedPreferences("playbackStateRecency",Context.MODE_PRIVATE)
    fun get(key:String):String?=values.getString(key,null)
    fun put(key:String,value:String)=update(listOf(key)) {it[key]=value}
    fun remove(key:String)=update {it.remove(key)}
    fun move(from:String,to:String)=update {data->data.remove(from)?.let {data[to]=it};Unit}
    fun clear(accept:(String)->Boolean={true})=update {data->data.keys.filter(accept).forEach {data.remove(it)}}
    fun prune()=update {}
    private fun update(touch:List<String> = emptyList(),change:(MutableMap<String,String>)->Unit)=synchronized(lock) {
        val previous=values.all.mapNotNull {(key,value)->(value as? String)?.let {key to it}}.toMap()
        val data=previous.toMutableMap();change(data)
        val recorded=recency.getString(file,null)?.let {runCatching {Json.decodeFromString<List<String>>(it)}.getOrNull()}.orEmpty()
        val known=recorded.toHashSet()
        val order=linkedSetOf<String>()
        previous.keys.filterNot {it in known}.sorted().forEach {order+=it}
        recorded.filter {it in data}.forEach {order+=it}
        data.forEach {(key,value)->if(previous[key]!=value){order-=key;order+=key}}
        data.keys.forEach {order+=it}
        touch.filter {it in data}.forEach {order-=it;order+=it}
        val retained=bounded(file,data,order.toList())
        val edit=values.edit()
        previous.keys.filterNot {it in retained}.forEach {edit.remove(it)}
        retained.forEach {(key,value)->if(previous[key]!=value)edit.putString(key,value)}
        edit.apply()
        val next=order.filter {it in retained}
        if(next!=recorded)recency.edit().putString(file,Json.encodeToString(next)).apply()
    }
    companion object {
        private val lock=Any()
        const val MAX_POSITIONS=2000
        const val MAX_TRACK_ENTRIES=4000
        private const val MAX_BYTES=512*1024
        fun bounded(file:String,values:Map<String,String>,order:List<String> = values.keys.toList()):Map<String,String> {
            val limit=if(file=="positions")MAX_POSITIONS else MAX_TRACK_ENTRIES
            val result=linkedMapOf<String,String>();var bytes=0
            for(key in order)values[key]?.let {value->
                val size=entryBytes(key,value)
                if(size<=MAX_BYTES) {
                    result.put(key,value)?.let {bytes-=entryBytes(key,it)};bytes+=size
                    while(result.size>limit||bytes>MAX_BYTES) {
                        val oldest=result.entries.first();bytes-=entryBytes(oldest.key,oldest.value);result.remove(oldest.key)
                    }
                }
            }
            return result
        }
        private fun entryBytes(key:String,value:String)=Json.encodeToString(key).toByteArray().size+Json.encodeToString(value).toByteArray().size+16
        fun prune(context:Context){PlaybackStateRetention(context,"positions").prune();PlaybackStateRetention(context,"trackMemory").prune()}
    }
}
