package app.ember.tv.player

import androidx.core.content.edit

import android.content.Context

/** Remembers where you stopped in the built-in player, per title or episode. */
class PositionStore(context: Context,scope:String=app.ember.tv.data.LocalActivity.SHARED) {
    private val prefix=app.ember.tv.data.LocalActivity.prefix(scope)
    private val sp = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    /** Position and duration in milliseconds, or null when nothing is saved. */
    fun get(key: String): Pair<Long, Long>? {
        if(!app.ember.player.PrivateMedia.state.value.history)return null
        val v = sp.getString(prefix+key, null) ?: return null
        val parts = v.split(',')
        val pos = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val dur = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return pos to dur
    }

    fun put(key: String, positionMs: Long, durationMs: Long) =
        if(app.ember.player.PrivateMedia.state.value.history)sp.edit { putString(prefix+key, "$positionMs,$durationMs") } else Unit

    /** Keep completion for the library, while playback always restarts a finished item. */
    fun resume(key:String):Pair<Long,Long>?=get(key)?.takeIf {it.second>0 && it.first<it.second*.95}
    fun finished(key:String,durationMs:Long) {
        if(durationMs>0)put(key,durationMs,durationMs) else clear(key)
    }

    fun clear(key: String) = sp.edit { remove(prefix+key) }

    fun moveKey(from:String,to:String) {
        if(from==to)return
        val value=sp.getString(prefix+from,null) ?: return
        sp.edit {putString(prefix+to,value);remove(prefix+from)}
    }
}
