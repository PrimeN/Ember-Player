package app.ember.tv.player

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Saves touch only one record. Migration and bounded pruning run at startup and backup boundaries. */
internal class PlaybackStateRetention(context:Context,private val file:String) {
    private val app=context.applicationContext
    private val values=app.getSharedPreferences(file,Context.MODE_PRIVATE)
    fun entry(key:String):Pair<String,Long>?=values.getString(key,null)?.let {record(file,it).let {entry->entry.value to entry.touched}}
    fun entries():Map<String,Pair<String,Long>> = values.all.mapNotNull {(key,value)->
        (value as? String)?.let {raw->record(file,raw).let {key to (it.value to it.touched)}}
    }.toMap()
    fun get(key:String):String?=entry(key)?.first
    fun put(key:String,value:String)=synchronized(lock) {
        values.edit().putString(key,encode(file,value,System.currentTimeMillis().coerceAtLeast(0))).apply()
    }
    fun remove(key:String)=synchronized(lock){values.edit().remove(key).apply()}
    fun move(from:String,to:String)=synchronized(lock) {
        values.getString(from,null)?.let {values.edit().remove(from).putString(to,it).apply()}
        Unit
    }
    fun clear(accept:(String)->Boolean={true})=synchronized(lock) {
        val edit=values.edit();values.all.keys.filter(accept).forEach {edit.remove(it)};edit.apply()
    }
    fun prune():Boolean=synchronized(lock) {
        val previous=values.all
        val strings=previous.mapNotNull {(key,value)->(value as? String)?.let {key to it}}.toMap()
        val legacy=app.getSharedPreferences(LEGACY_RECENCY,Context.MODE_PRIVATE).getString(file,null)
        val order=legacy?.takeIf {it.length<=2*1024*1024}?.let {runCatching {Json.decodeFromString<List<String>>(it)}.getOrNull()}.orEmpty()
        val kept=bounded(file,strings,order)
        if(previous!=kept) {
            val edit=values.edit()
            previous.keys.filterNot {it in kept}.forEach {edit.remove(it)}
            kept.forEach {(key,value)->if(previous[key]!=value)edit.putString(key,value)}
            if(!edit.commit())return@synchronized false
        }
        discardLegacyRecency(app,file)
    }
    companion object {
        private val lock=Any()
        private const val LEGACY_RECENCY="playbackStateRecency"
        private const val TRACK_RECORD="@1:"
        const val MAX_POSITIONS=2000
        const val MAX_TRACK_ENTRIES=4000
        private const val MAX_BYTES=512*1024
        private data class Record(val value:String,val touched:Long=0,val stamped:Boolean=false)
        private fun record(file:String,raw:String):Record {
            if(file=="positions") {
                val parts=raw.split(',')
                if(parts.size==3&&validPosition(raw))return Record(parts.take(2).joinToString(","),parts[2].toLong(),true)
            } else if(raw.startsWith(TRACK_RECORD)) {
                val end=raw.indexOf(':',TRACK_RECORD.length)
                if(end>0)raw.substring(TRACK_RECORD.length,end).toLongOrNull()?.takeIf {it>=0}?.let {
                    return Record(raw.substring(end+1),it,true)
                }
            }
            return Record(raw)
        }
        private fun encode(file:String,value:String,touched:Long)=if(file=="positions")"$value,$touched" else "$TRACK_RECORD$touched:$value"
        fun validPosition(value:String):Boolean {
            val parts=value.split(',')
            return parts.size in 2..3&&parts.all {it.toLongOrNull()?.let {n->n>=0}==true}
        }
        fun bounded(file:String,values:Map<String,String>,legacyOrder:List<String> = emptyList()):Map<String,String> {
            val knownOrder=legacyOrder.withIndex().associate {it.value to it.index+1L}
            val ordered=values.mapNotNull {(key,raw)->
                if(file=="positions"&&!validPosition(raw))return@mapNotNull null
                val entry=record(file,raw)
                val touched=if(entry.stamped)entry.touched else knownOrder[key] ?: 0L
                Triple(key,encode(file,entry.value,touched),touched)
            }.sortedWith(compareBy<Triple<String,String,Long>> {it.third}.thenBy {it.first})
            val limit=if(file=="positions")MAX_POSITIONS else MAX_TRACK_ENTRIES
            val result=linkedMapOf<String,String>();var bytes=0
            for((key,value) in ordered) {
                val size=entryBytes(key,value)
                if(size>MAX_BYTES)continue
                result[key]=value;bytes+=size
                while(result.size>limit||bytes>MAX_BYTES) {
                    val oldest=result.entries.first();bytes-=entryBytes(oldest.key,oldest.value);result.remove(oldest.key)
                }
            }
            return result
        }
        fun clearTrackChoices(context:Context):Boolean=synchronized(lock) {
            listOf("trackMemory",LEGACY_RECENCY).all {file->context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit()}
        }
        private fun entryBytes(key:String,value:String)=Json.encodeToString(key).toByteArray().size+Json.encodeToString(value).toByteArray().size+16
        private fun discardLegacyRecency(context:Context,file:String):Boolean {
            val legacy=context.getSharedPreferences(LEGACY_RECENCY,Context.MODE_PRIVATE)
            if(legacy.contains(file)&&!legacy.edit().remove(file).commit())return false
            if(legacy.all.isEmpty())context.deleteSharedPreferences(LEGACY_RECENCY)
            return true
        }
        fun clearPositions(context:Context):Boolean=synchronized(lock) {
            context.getSharedPreferences("positions",Context.MODE_PRIVATE).edit().clear().commit()&&discardLegacyRecency(context,"positions")
        }
        fun prune(context:Context):Boolean {
            val positions=PlaybackStateRetention(context,"positions").prune()
            val tracks=PlaybackStateRetention(context,"trackMemory").prune()
            return positions&&tracks
        }
    }
}
