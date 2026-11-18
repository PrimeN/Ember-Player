package app.ember.player

import app.ember.tv.R

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
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
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    TvDialog(close,width=520.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading(uiResources.getString(R.string.ui_add_to_playlist))
        PlaylistPickerContent(context,items,close=close)
    }
}

@Composable
internal fun PlaylistPickerContent(context:Context,items:List<QueueMedia>,index:Int=0,repeat:String="off",shuffle:Boolean=false,close:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val store=remember(context){SavedPlaylists(context)}
    val scope=rememberCoroutineScope()
    var playlists by remember {mutableStateOf<List<SavedPlaylist>>(emptyList())}
    var name by remember {mutableStateOf("")}
    var readError by remember {mutableStateOf(false)}
    var revision by remember {mutableIntStateOf(0)}
    var loading by remember {mutableStateOf(true)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(store,revision){
        loading=true
        val loaded=withContext(Dispatchers.IO){runCatching {store.readForDisplay()}.getOrNull()}
        playlists=loaded.orEmpty().filter {p->p.entries.none {PrivateMedia.hidden(it.uri)}}
        readError=loaded==null;loading=false
    }
    fun save(id:String?) {
        if(busy||loading||readError)return
        busy=true;error=null
        scope.launch {
            try {
                val saved=withContext(Dispatchers.IO) {
                    if(id==null)store.save(name,items,index,repeat,shuffle) else store.append(id,items)
                }
                if(saved)close() else error=uiResources.getString(R.string.ui_could_not_save_this_playlist_check_the_name_storage)
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage)}
            finally {busy=false}
        }
    }
    if(loading)PlayerStatusText(uiResources.getString(R.string.ui_loading_playlists))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=240.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items(playlists,key={it.id}) {playlist->PlayerAction(playlist.name,enabled=!busy){save(playlist.id)}}
    }
    TvTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_new_playlist_name))
    if(readError)PlayerErrorText(uiResources.getString(R.string.ui_playlists_unreadable))
    error?.let {PlayerErrorText(it)}
    ButtonRow {
        if(readError)PlayerAction(uiResources.getString(R.string.ui_refresh),enabled=!busy&&!loading){revision++}
        PlayerAction(if(busy)uiResources.getString(R.string.ui_saving) else uiResources.getString(R.string.ui_create_playlist),primary=true,enabled=!busy&&!loading&&!readError&&name.isNotBlank()){save(null)}
        PlayerAction(uiResources.getString(R.string.ui_cancel),enabled=!busy,onClick=close)
    }
}

@Composable
internal fun LibraryPlaylistAction(media:QueueMedia,modifier:Modifier=Modifier) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val context=androidx.compose.ui.platform.LocalContext.current
    var shown by remember {mutableStateOf(false)}
    PlayerIconAction(uiResources.getString(R.string.ui_add_to_playlist_d2410e ,media.title),androidx.compose.material.icons.Icons.AutoMirrored.Outlined.PlaylistAdd,modifier){shown=true}
    if(shown)PlaylistPicker(context,listOf(media)){shown=false}
}
