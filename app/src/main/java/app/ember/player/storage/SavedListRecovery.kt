package app.ember.player.storage

import kotlinx.coroutines.CancellationException

/** Called only after explicit reset confirmation; readable collections are never discarded. */
internal fun resetUnreadableList(read:()->Any?,reset:()->Boolean):Boolean {
    try {read();return false}
    catch(e:CancellationException){throw e}
    catch(_:Exception) { }
    return try {reset()}
    catch(e:CancellationException){throw e}
    catch(_:Exception){false}
}
