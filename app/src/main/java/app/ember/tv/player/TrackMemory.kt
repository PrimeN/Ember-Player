package app.ember.tv.player

import android.content.Context

/**
 * Remembers the audio language and subtitle choice per show (Settings > Player), so the next
 * episode starts the same way: a dubbed track, subtitles in a language, or subtitles off.
 */
class TrackMemory(context: Context,scope:String=app.ember.tv.data.LocalActivity.SHARED) {
    private val prefix=app.ember.tv.data.LocalActivity.prefix(scope)
    private val sp = PlaybackStateRetention(context,"trackMemory")

    /** [audio]: a language code; [subtitles]: "off" or a language code; null when not set. */
    data class Choice(val audio: String?, val subtitles: String?)

    fun get(show: String): Choice? {
        val a = sp.get(prefix+"a:$show")
        val s = sp.get(prefix+"s:$show")
        return if (a == null && s == null) null else Choice(a, s)
    }

    fun putAudio(show: String, language: String) = sp.put(prefix+"a:$show", language)
    fun putSubtitles(show: String, choice: String) = sp.put(prefix+"s:$show", choice)
    fun moveKey(from:String,to:String) {
        if(from==to)return
        sp.move(prefix+"a:$from",prefix+"a:$to")
        sp.move(prefix+"s:$from",prefix+"s:$to")
    }
    fun clear()=sp.clear {key->if(prefix.isEmpty())key.startsWith("a:") || key.startsWith("s:") else key.startsWith(prefix)}
}
