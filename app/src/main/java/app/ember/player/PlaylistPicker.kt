package app.ember.player

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun PlaylistPicker(context:Context,items:List<QueueMedia>,close:()->Unit) {
    TvDialog(close,width=520.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading("Add to playlist")
        PlaylistPickerContent(context,items,close=close)
    }
}

@Composable
internal fun PlaylistPickerContent(context:Context,items:List<QueueMedia>,index:Int=0,repeat:String="off",shuffle:Boolean=false,close:()->Unit) {
    val store=remember(context){SavedPlaylists(context)}
    val scope=rememberCoroutineScope()
    var playlists by remember {mutableStateOf<List<SavedPlaylist>>(emptyList())}
    var name by remember {mutableStateOf("")}
    var loading by remember {mutableStateOf(true)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(store){playlists=withContext(Dispatchers.IO){store.read().filter {p->p.entries.none {PrivateMedia.hidden(it.uri)}}};loading=false}
    fun save(id:String?) {
        if(busy)return
        busy=true;error=null
        scope.launch {
            try {
                val saved=withContext(Dispatchers.IO) {
                    if(id==null)store.save(name,items,index,repeat,shuffle) else store.append(id,items)
                }
                if(saved)close() else error="Could not save this playlist. Check the name, storage and playlist limits. Private media and temporary USB addresses cannot be saved."
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error="Could not update encrypted playlist storage."}
            finally {busy=false}
        }
    }
    if(loading)PlayerStatusText("Loading playlists…")
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=240.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items(playlists,key={it.id}) {playlist->PlayerAction(playlist.name,enabled=!busy){save(playlist.id)}}
    }
    TvTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),placeholder="New playlist name")
    error?.let {PlayerErrorText(it)}
    ButtonRow {
        PlayerAction(if(busy)"Saving…" else "Create playlist",primary=true,enabled=!busy&&name.isNotBlank()){save(null)}
        PlayerAction("Cancel",enabled=!busy,onClick=close)
    }
}

@Composable
internal fun LibraryPlaylistAction(media:QueueMedia,modifier:Modifier=Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var shown by remember {mutableStateOf(false)}
    PlayerIconAction("Add ${media.title} to playlist",androidx.compose.material.icons.Icons.Outlined.PlaylistAdd,modifier){shown=true}
    if(shown)PlaylistPicker(context,listOf(media)){shown=false}
}
