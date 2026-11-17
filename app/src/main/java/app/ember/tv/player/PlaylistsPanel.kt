package app.ember.tv.player

import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun PlaylistsPanel(activity:ComponentActivity,refresh:Int) {
    val scope=rememberCoroutineScope()
    val store=remember {SavedPlaylists(activity)}
    var values by remember {mutableStateOf<List<SavedPlaylist>>(emptyList())}
    var selected by remember {mutableStateOf<SavedPlaylist?>(null)}
    var rename by remember {mutableStateOf<SavedPlaylist?>(null)}
    var deleting by remember {mutableStateOf<SavedPlaylist?>(null)}
    var files by remember {mutableStateOf<List<QueueMedia>>(emptyList())}
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    LaunchedEffect(refresh,revision) {values=withContext(Dispatchers.IO) {store.read().filter {value->value.entries.none {app.ember.player.PrivateMedia.hidden(it.uri)}}}}
    fun play(value:SavedPlaylist,index:Int) {
        if(busy)return
        busy=true;notice=null
        scope.launch {
            var prepared:PreparedPlaylist?=null
            try {
                val ready=withContext(Dispatchers.IO) {preparePlaylist(activity,value).also {prepared=it}}
                ensureActive()
                MediaFiles.launch(activity,ready.items,ready.resources,index,value.id,value.shuffle,value.repeat)
                prepared=null
            } catch(e:CancellationException) {throw e}
            catch(_:Exception) {notice="This playlist is unavailable. Check folder permissions and saved network connections."}
            finally {prepared?.close();busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->
        if(uris.isNotEmpty()) {
            busy=true
            scope.launch {
                try {files=withContext(Dispatchers.IO) {uris.map {uri->ensureActive();MediaFiles.retain(activity,uri);QueueMedia(uri.toString(),MediaFiles.name(activity,uri))}}}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {notice="Could not read those files."}
                finally {busy=false}
            }
        }
    }
    app.ember.player.PlayerSection("Playlists") {
        Chip(if(busy)"Working…" else "New playlist from files") {if(!busy)runCatching {picker.launch(arrayOf("video/*","audio/*","application/octet-stream"))}.onFailure {notice="No document picker is installed on this device."}}
        values.filter {it.entries.none {entry->app.ember.player.PrivateMedia.hidden(entry.uri)}}.forEach {value->ButtonRow {
            Chip("${value.name} · ${value.entries.size}",maxLines=1) {if(!busy)play(value,value.index)}
            app.ember.player.PlayerIconAction("More options for ${value.name}",androidx.compose.material.icons.Icons.Outlined.MoreHoriz) {if(!busy)selected=value}
        }}
        notice?.let {app.ember.player.PlayerErrorText(it)}
        selected?.let {value->OptionMenu(value.name,listOf(
            MenuOption("Resume playlist") {play(value,value.index)},
            MenuOption("Play from beginning") {play(value,0)},
            MenuOption("Rename") {rename=value},
            MenuOption("Delete playlist") {deleting=value},
        ),{selected=null})}
        if(files.isNotEmpty() || rename!=null) {
            var name by remember(files,rename?.id) {mutableStateOf(rename?.name ?: "Playlist")}
            TvDialog({files=emptyList();rename=null},width=480.dp,resizeForKeyboard=true,scrollContent=true) {
                DialogHeading(if(rename==null)"Save playlist" else "Rename playlist")
                TvTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),placeholder="Playlist name")
                ButtonRow {
                    app.ember.player.PlayerAction("Save",primary=true) {if(name.isNotBlank()&&!busy) {
                        val changing=rename;val items=files;busy=true
                        scope.launch {
                            try {
                                val saved=withContext(Dispatchers.IO) {if(changing==null)store.save(name,items) else store.rename(changing.id,name)}
                                if(saved){files=emptyList();rename=null;revision++}else notice="Could not save playlist. Check encrypted storage and the 50-playlist limit."
                            } finally {busy=false}
                        }
                    }}
                    app.ember.player.PlayerAction("Cancel") {if(!busy){files=emptyList();rename=null}}
                }
            }
        }
        deleting?.let {value->app.ember.player.PlayerConfirmationDialog("Delete playlist?","${value.name} will be removed. Its media files will stay on your device.","Delete",{deleting=null}) {
            deleting=null;scope.launch {val saved=withContext(Dispatchers.IO){store.remove(value.id)};if(saved)revision++ else notice="Could not update encrypted playlist storage."}
        }}
    }
}
