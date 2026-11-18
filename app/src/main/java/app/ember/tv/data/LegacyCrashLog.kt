package app.ember.tv.data

import android.content.Context

/** Earlier versions retained diagnostics with no way to review or share them. */
internal object LegacyCrashLog {
    fun clear(context:Context):Boolean=runCatching {
        val files=context.filesDir.listFiles {_,name->name=="crashes.txt" || name.startsWith(".crashes.txt-")&&name.endsWith(".tmp")}
            ?: return@runCatching false
        files.map {it.delete() || !it.exists()}.all {it}
    }.getOrDefault(false)
}
