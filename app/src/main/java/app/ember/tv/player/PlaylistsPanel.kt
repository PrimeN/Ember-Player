package app.ember.tv.player

import app.ember.tv.R

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
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scope=rememberCoroutineScope()
    val store=remember {SavedPlaylists(activity)}
    var values by remember {mutableStateOf<List<SavedPlaylist>>(emptyList())}
    var selected by remember {mutableStateOf<SavedPlaylist?>(null)}
    var editing by remember {mutableStateOf<SavedPlaylist?>(null)}
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
            catch(_:Exception) {notice=uiResources.getString(R.string.ui_this_playlist_is_unavailable_check_folder_permissions_and_saved)}
            finally {prepared?.close();busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->
        if(uris.isNotEmpty()) {
            busy=true
            scope.launch {
                try {files=withContext(Dispatchers.IO) {uris.map {uri->ensureActive();MediaFiles.retain(activity,uri);QueueMedia(uri.toString(),MediaFiles.name(activity,uri))}}}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {notice=uiResources.getString(R.string.ui_could_not_read_those_files)}
                finally {busy=false}
            }
        }
    }
    app.ember.player.PlayerSection(uiResources.getString(R.string.ui_playlists)) {
        Chip(if(busy)uiResources.getString(R.string.ui_working) else uiResources.getString(R.string.ui_new_playlist_from_files)) {if(!busy)runCatching {picker.launch(arrayOf("video/*","audio/*","application/octet-stream"))}.onFailure {notice=uiResources.getString(R.string.ui_no_document_picker_is_installed_on_this_device)}}
        values.filter {it.entries.none {entry->app.ember.player.PrivateMedia.hidden(entry.uri)}}.forEach {value->ButtonRow {
            Chip(uiResources.getString(R.string.ui_playlist_summary ,value.name,value.entries.size),maxLines=1) {if(!busy)play(value,value.index)}
            app.ember.player.PlayerIconAction(uiResources.getString(R.string.ui_more_options_for ,value.name),androidx.compose.material.icons.Icons.Outlined.MoreHoriz) {if(!busy)selected=value}
        }}
        notice?.let {app.ember.player.PlayerErrorText(it)}
        selected?.let {value->OptionMenu(value.name,listOf(
            MenuOption(uiResources.getString(R.string.ui_resume_playlist)) {play(value,value.index)},
            MenuOption(uiResources.getString(R.string.ui_play_from_beginning)) {play(value,0)},
            MenuOption(uiResources.getString(R.string.ui_reorder_entries)) {editing=value},
            MenuOption(uiResources.getString(R.string.ui_rename)) {rename=value},
            MenuOption(uiResources.getString(R.string.ui_delete_playlist)) {deleting=value},
        ),{selected=null})}
        if(files.isNotEmpty() || rename!=null) {
            var name by remember(files,rename?.id) {mutableStateOf(rename?.name ?: uiResources.getString(R.string.ui_playlist))}
            TvDialog({files=emptyList();rename=null},width=480.dp,resizeForKeyboard=true,scrollContent=true) {
                DialogHeading(if(rename==null)uiResources.getString(R.string.ui_save_playlist) else uiResources.getString(R.string.ui_rename_playlist))
                TvTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_playlist_name))
                ButtonRow {
                    app.ember.player.PlayerAction(uiResources.getString(R.string.ui_save),primary=true) {if(name.isNotBlank()&&!busy) {
                        val changing=rename;val items=files;busy=true
                        scope.launch {
                            try {
                                val saved=withContext(Dispatchers.IO) {if(changing==null)store.save(name,items) else store.rename(changing.id,name)}
                                if(saved){files=emptyList();rename=null;revision++}else notice=uiResources.getString(R.string.ui_could_not_save_playlist_check_encrypted_storage_and_the)
                            } finally {busy=false}
                        }
                    }}
                    app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel)) {if(!busy){files=emptyList();rename=null}}
                }
            }
        }
        editing?.let {value->app.ember.player.PlaylistEditor(activity,value){editing=null;revision++}}
        deleting?.let {value->app.ember.player.PlayerConfirmationDialog(uiResources.getString(R.string.ui_delete_playlist_5894f5),uiResources.getString(R.string.ui_will_be_removed_its_media_files_will_stay_on ,value.name),uiResources.getString(R.string.ui_delete_f6fdbe),{deleting=null}) {
            deleting=null;scope.launch {val saved=withContext(Dispatchers.IO){store.remove(value.id)};if(saved)revision++ else notice=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage)}
        }}
    }
}
