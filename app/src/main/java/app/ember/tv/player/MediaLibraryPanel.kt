package app.ember.tv.player

import app.ember.player.PlayerAction

import app.ember.tv.R

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun MediaLibraryPanel(activity:ComponentActivity,refresh:Int,showTitle:Boolean=true) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scan by MediaLibraryScanner.state.collectAsState()
    var query by remember {mutableStateOf("")}
    var offsets by remember {mutableStateOf(listOf(0))}
    var revision by remember {mutableIntStateOf(0)}
    var page by remember {mutableStateOf(MediaLibrary.Result(emptyList(),null))}
    var notice by remember {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
    LaunchedEffect(scan.revision,scan.result) {
        revision=scan.revision
        notice=when(scan.result) {
            ScanResult.COMPLETE->app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_library_refreshed),false)
            ScanResult.EMPTY->app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_add_a_media_folder_before_refreshing),false)
            ScanResult.FAILED->app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_finish_refreshing_check_folder_access_existing_indexed))
            ScanResult.UNAVAILABLE->app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_the_saved_folder_index_is_unavailable_on_this_device))
            null->null
        }
    }
    if(showTitle)app.ember.player.PlayerSectionTitle(uiResources.getString(R.string.ui_saved_folder_library))
    Text(uiResources.getString(R.string.ui_refresh_indexes_the_folders_you_chose_including_subfolders_the),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    TvTextField(query,{query=it.take(120);offsets=listOf(0)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_search_media_library))
    ButtonRow {
        if(!scan.active)PlayerAction(uiResources.getString(R.string.ui_refresh_library)){MediaLibraryScanner.request(activity,true)}
        else PlayerAction(uiResources.getQuantityString(R.plurals.ui_cancel_refresh_files,scan.scanned,scan.scanned)){MediaLibraryScanner.cancel(activity)}
        if(offsets.size>1)PlayerAction(uiResources.getString(R.string.ui_previous_page)){offsets=offsets.dropLast(1)}
        page.nextOffset?.let {next->PlayerAction(uiResources.getString(R.string.ui_next_page)){offsets=offsets+next}}
    }
    LaunchedEffect(query,offsets,revision,refresh) {
        delay(200)
        try {page=withContext(Dispatchers.IO){
            if(DeviceFolders(activity).read().none {!app.ember.player.PrivateMedia.hidden(it.uri)})MediaLibrary.Result(emptyList(),null)
            else MediaLibrary(activity).use {it.search(query,offsets.last())}
        }}
        catch(e:CancellationException){throw e}
        catch(_:Exception){page=MediaLibrary.Result(emptyList(),null);notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_library_unavailable_add_a_folder_and_refresh_its_index))}
        catch(_:LinkageError){page=MediaLibrary.Result(emptyList(),null);notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_the_saved_folder_index_is_unavailable_on_this_device))}
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items(page.entries,key={it.key}) {media->PlayerAction(media.title,maxLines=1,modifier=Modifier.fillMaxWidth()) {
            if(!app.ember.player.PrivateMedia.hidden(media.uri))runCatching {MediaFiles.launch(activity,listOf(media))}.onFailure {notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_this_file_is_unavailable_refresh_the_library_or_renew))}
        }}
    }
    notice?.let {app.ember.player.PlayerFeedbackText(it)}
}
