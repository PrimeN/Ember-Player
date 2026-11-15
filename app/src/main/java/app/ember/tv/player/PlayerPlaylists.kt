package app.ember.tv.player

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.saveFilePlaylist() {
    if(privatePlayback)return
    val queue=fileQueue ?: return
    bookmarkName("Save playlist","Playlist") {name->
        val items=queue.items.toList();val index=queue.index;val repeat=queue.repeat;val shuffle=queue.shuffle
        lifecycleScope.launch {
            val saved=withContext(Dispatchers.IO) {SavedPlaylists(applicationContext,watchProfileId).save(name,items,index,repeat,shuffle)}
            Toast.makeText(this@saveFilePlaylist,if(saved)"Playlist saved" else "Could not save playlist. Check encrypted storage and the 50-playlist limit.",Toast.LENGTH_LONG).show()
        }
    }
}

internal object PlaylistProgress {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO.limitedParallelism(1))
    fun save(context:Context,profile:String,id:String,key:String) {if(!app.ember.player.PrivateMedia.state.value.history)return;scope.launch {runCatching {SavedPlaylists(context,profile).progress(id,key)}}}
}
