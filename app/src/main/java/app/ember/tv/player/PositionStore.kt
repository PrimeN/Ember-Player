package app.ember.tv.player

import android.content.Context

/** Remembers where you stopped in the built-in player, per title or episode. */
class PositionStore(context: Context,scope:String=app.ember.tv.data.LocalActivity.SHARED) {
    private val prefix=app.ember.tv.data.LocalActivity.prefix(scope)
    private val sp = PlaybackStateRetention(context,"positions")

    /** Position and duration in milliseconds, or null when nothing is saved. */
    internal data class SavedPosition(val position:Long,val duration:Long,val lastPlayed:Long)
    internal fun saved(key:String):SavedPosition? {
        if(!app.ember.player.PrivateMedia.state.value.history)return null
        val entry = sp.entry(prefix+key) ?: return null
        val parts = entry.first.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return SavedPosition(pos,dur,entry.second)
    }
    internal fun savedAll():Map<String,SavedPosition> {
        if(!app.ember.player.PrivateMedia.state.value.history)return emptyMap()
        return sp.entries().mapNotNull {(key,entry)->
            if(if(prefix.isEmpty())key.startsWith("profile:") else !key.startsWith(prefix))return@mapNotNull null
            val parts=entry.first.split(',')
            val position=parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val duration=parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            key.removePrefix(prefix) to SavedPosition(position,duration,entry.second)
        }.toMap()
    }
    fun get(key: String): Pair<Long, Long>? = saved(key)?.let {it.position to it.duration}

    fun put(key: String, positionMs: Long, durationMs: Long) =
        if(app.ember.player.PrivateMedia.state.value.history)sp.put(prefix+key, "$positionMs,$durationMs") else Unit

    /** Keep completion for the library, while playback always restarts a finished item. */
    fun resume(key:String):Pair<Long,Long>?=get(key)?.takeIf {canResume(it.first,it.second)}
    fun finished(key:String,durationMs:Long) {
        if(durationMs>0)put(key,durationMs,durationMs) else clear(key)
    }

    fun clear(key: String) = sp.remove(prefix+key)

    fun moveKey(from:String,to:String) {
        if(from==to)return
        sp.move(prefix+from,prefix+to)
    }
    companion object {
        internal fun canResume(position:Long,duration:Long)=position>30_000&&duration>0&&position<duration*.95
    }
}
