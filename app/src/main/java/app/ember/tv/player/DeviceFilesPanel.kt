package app.ember.tv.player

import app.ember.player.PlayerAction

import app.ember.tv.R

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
internal fun DeviceFilesPanel(activity: ComponentActivity,refresh:Int=0,initialFolderUri:String?=null) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val store = remember { DeviceFolders(activity) }
    val scope = rememberCoroutineScope()
    val history=remember {FolderPlaybackHistory(activity)}
    var historyError by remember {mutableStateOf(false)}
    var readError by remember {mutableStateOf(false)}
    var folders by remember { mutableStateOf<List<DeviceFolder>>(emptyList()) }
    var root by remember { mutableStateOf<DeviceFolder?>(null) }
    var parents by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
    var offsets by remember {mutableStateOf(listOf<DeviceFile?>(null))}
    var page by remember { mutableStateOf(DeviceFiles.BrowsePage()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("name") }
    var hidden by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var status by remember {mutableStateOf<String?>(null)}
    var revision by remember { mutableIntStateOf(0) }
    var select by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var protect by remember {mutableStateOf<DeviceFile?>(null)}
    var protectPin by remember {mutableStateOf(false)}
    var menu by remember { mutableStateOf<DeviceFile?>(null) }
    var rename by remember { mutableStateOf<DeviceFile?>(null) }
    var deleting by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var moving by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    var lastPlayed by remember {mutableStateOf<RecentMedia?>(null)}
    val current = parents.lastOrNull()
    LaunchedEffect(initialFolderUri) {
        if(initialFolderUri!=null) {
            val chosen=withContext(Dispatchers.IO){store.read().firstOrNull {it.uri==initialFolderUri && !app.ember.player.PrivateMedia.hidden(it.uri)}}
            if(chosen!=null){root=chosen;parents=emptyList();query=""}
        }
    }

    fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = null;status=null
        scope.launch {
            try {block(); selected = emptySet(); revision++}
            catch (e: CancellationException) {throw e}
            catch (_:SavedFolderLimitException) {notice=uiResources.getQuantityString(R.plurals.ui_saved_folder_limit,DeviceFolders.MAX_FOLDERS,DeviceFolders.MAX_FOLDERS);revision++}
            catch (e: Exception) {notice = uiResources.getString(R.string.ui_the_file_operation_could_not_be_completed); revision++}
            finally {busy = false}
        }
    }
    fun open(items: List<DeviceFile>) = operation {
        val queue = items.filterNot {it.directory||app.ember.player.PrivateMedia.hidden(it.uri.toString())}.map { QueueMedia(it.uri.toString(), it.name,directory=it.parent.toString()) }
        if (queue.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                MediaHistory(activity).add(queue)
                FolderPlaybackHistory(activity).remember(items.first().parent,RecentMedia(queue.first().uri,queue.first().title))
            }
            MediaFiles.launch(activity, queue)
        }
    }
    fun back() {
        if (parents.size > 1) parents = parents.dropLast(1) else {root = null; parents = emptyList()}
        selected = emptySet(); query = ""; select = false
    }
    BackHandler(enabled = root != null && !busy) {back()}
    LaunchedEffect(refresh,revision) {
        selected=emptySet()
        historyError=withContext(Dispatchers.IO){runCatching {history.readForDisplay()}.isFailure}
        val loaded=withContext(Dispatchers.IO) {runCatching {store.readForDisplay()}.getOrNull()}
        readError=loaded==null
        folders=loaded.orEmpty().filterNot {app.ember.player.PrivateMedia.hidden(it.uri)}
        if(root!=null && folders.none {it.uri==root?.uri}){root=null;parents=emptyList()}
    }
    val privacy by app.ember.player.PrivateMedia.state.collectAsState()
    val unlocked by app.ember.player.PrivateMedia.unlocked.collectAsState()
    val visit=remember(root?.uri,current?.first,revision,refresh,privacy,unlocked){FolderBrowseVisit()}
    DisposableEffect(visit){onDispose {visit.close()}}
    LaunchedEffect(root,current,query,sort,hidden) {offsets=listOf(null)}
    LaunchedEffect(root, current, revision, offsets,query,sort,hidden,visit) {
        selected = emptySet(); page = DeviceFiles.BrowsePage();lastPlayed=null
        val folder = root ?: return@LaunchedEffect
        val directory = current?.first ?: DeviceFiles.root(Uri.parse(folder.uri))
        loading = true
        try {
            delay(if(query.isBlank())0 else 180)
            val loaded=withContext(Dispatchers.IO) {visit.page({offset->DeviceFiles.list(activity,Uri.parse(folder.uri),directory,offset){ensureActive()}},query,sort,hidden,offsets.last(),app.ember.player.PrivateMedia::hidden){ensureActive()} to history.get(directory)}
            page=loaded.first.copy(entries=loaded.first.entries.filterNot {app.ember.player.PrivateMedia.hidden(it.uri.toString())});lastPlayed=loaded.second
        }
        catch (e: CancellationException) {throw e}
        catch (_: Exception) {notice = uiResources.getString(R.string.ui_this_folder_is_unavailable_choose_it_again_to_renew)}
        finally {loading = false}
    }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) operation {
            val folder = withContext(Dispatchers.IO) {saveDeviceFolder(activity,tree,store){ensureActive()}.also {MediaLibraryScanner.request(activity,true)}}
            folders = withContext(Dispatchers.IO) {store.read().filterNot {app.ember.player.PrivateMedia.hidden(it.uri)}}
            root = folder; parents = emptyList(); query = ""
        }
    }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val files = moving; moving = emptyList()
        if (tree != null && files.isNotEmpty()) operation {
            withContext(Dispatchers.IO) {
                DeviceFiles.retain(activity, tree)
                files.forEach {file ->
                    ensureActive()
                    val moved=DeviceFiles.move(activity,file,tree) {ensureActive()}
                    transferFilePlaybackState(activity,file,moved,DeviceFiles.root(tree),file.name)
                }
            }
            status = uiResources.getString(R.string.ui_files_moved)
        }
    }
    fun share(files: List<DeviceFile>) {
        if (files.isEmpty()) return
        if(files.any {app.ember.player.PrivateMedia.hidden(it.uri.toString())}){notice=uiResources.getString(R.string.ui_use_the_private_media_area_to_access_protected_files);return}
        val uris = ArrayList(files.map {it.uri})
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri(uiResources.getString(R.string.ui_media), uris.first()).apply {uris.drop(1).forEach {addItem(android.content.ClipData.Item(it))}}
        runCatching {activity.startActivity(Intent.createChooser(intent, uiResources.getString(R.string.ui_share_media)))}.onFailure {notice = uiResources.getString(R.string.ui_no_app_is_available_to_share_these_files)}
    }
    app.ember.player.PlayerSection(uiResources.getString(R.string.ui_device_folders)) {
        if(readError) {
            app.ember.player.PlayerErrorText(uiResources.getString(R.string.ui_saved_folders_unreadable))
            app.ember.player.SavedListRecoveryControls(R.string.ui_reset_saved_folders,R.string.ui_reset_saved_folders_warning,store::resetUnreadable){revision++}
        }
        notice?.let {app.ember.player.PlayerErrorText(it)}
        status?.let {app.ember.player.PlayerStatusText(it)}
        if(historyError) {
            app.ember.player.PlayerErrorText(uiResources.getString(R.string.ui_folder_history_unreadable))
            app.ember.player.SavedListRecoveryControls(R.string.ui_reset_folder_history,R.string.ui_reset_folder_history_warning,history::resetUnreadable){revision++}
        }
        if (root == null) {
            PlayerAction(uiResources.getString(R.string.ui_add_a_media_folder)) {if (!busy) runCatching {addFolder.launch(null)}.onFailure {notice = uiResources.getString(R.string.ui_no_document_picker_is_installed_on_this_device)}}
            folders.forEach {folder -> ButtonRow {
                PlayerAction(folder.name, maxLines = 1) {root = folder; parents = emptyList(); query = ""}
                PlayerAction(uiResources.getString(R.string.ui_forget_folder)) {operation {withContext(Dispatchers.IO) {check(store.remove(folder.uri)) {"Could not update saved folders."};MediaLibraryScanner.request(activity,true)}; folders = withContext(Dispatchers.IO) {store.read().filterNot {app.ember.player.PrivateMedia.hidden(it.uri)}}}}
            }}
        } else {
            Text(current?.second ?: root!!.name, style=androidx.tv.material3.MaterialTheme.typography.bodyLarge, maxLines = 1)
            TvTextField(query, {query = it.take(120)}, Modifier.fillMaxWidth(), placeholder = uiResources.getString(R.string.ui_search_this_folder))
            ButtonRow {
                PlayerAction(uiResources.getString(R.string.ui_back)) {if (!busy) back()}
                PlayerAction(uiResources.getString(R.string.ui_refresh)) {if (!busy) {DeviceThumbnails.clear();revision++}}
                PlayerAction(when(sort) {"date" -> uiResources.getString(R.string.ui_newest_first); "size" -> uiResources.getString(R.string.ui_largest_first); else -> uiResources.getString(R.string.ui_name_a_z)}) {sort = when(sort) {"name" -> "date"; "date" -> "size"; else -> "name"}}
                Chip(uiResources.getString(R.string.ui_hidden_files), selected = hidden) {hidden = !hidden}
                Chip(if (select) uiResources.getString(R.string.ui_done_selecting) else uiResources.getString(R.string.ui_select_files), selected = select) {select = !select; selected = emptySet()}
            }
            val entries = DeviceFiles.sorted(page.entries.filterNot {app.ember.player.PrivateMedia.hidden(it.uri.toString())}, query, sort, hidden)
            lastPlayed?.let {last->page.entries.firstOrNull {it.uri.toString()==last.uri && !app.ember.player.PrivateMedia.hidden(it.uri.toString()) && (hidden || !it.name.startsWith('.'))}?.let {file->PlayerAction(uiResources.getString(R.string.ui_resume_c596cf ,last.title),maxLines=1) {open(listOf(file))}}}
            val chosen = page.entries.filter {it.uri in selected && !it.directory && !app.ember.player.PrivateMedia.hidden(it.uri.toString())}
            if (chosen.isNotEmpty()) ButtonRow {
                PlayerAction(uiResources.getString(R.string.ui_play_9507b9 ,chosen.size.toString())) {open(chosen)}
                PlayerAction(uiResources.getString(R.string.ui_share)) {share(chosen)}
                PlayerAction(uiResources.getString(R.string.ui_move)) {if (!busy) {moving = chosen; runCatching {destination.launch(null)}.onFailure {moving = emptyList(); notice = uiResources.getString(R.string.ui_no_document_picker_is_installed)}}}
                app.ember.player.PlayerAction(uiResources.getString(R.string.ui_delete_f6fdbe),destructive=true) {deleting = chosen}
            }
            if (loading || busy) Text(if (busy) uiResources.getString(R.string.ui_working) else uiResources.getString(R.string.ui_loading_folder), color = Mute, style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            else if (entries.isEmpty()) Text(uiResources.getString(R.string.ui_no_matching_media_in_this_folder), color = Mute, style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = {it.uri.toString()}) {entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        app.ember.player.DeviceMediaRow(entry,selected=entry.uri in selected,modifier=Modifier.weight(1f)) {
                            if (!busy) when {
                                entry.directory -> {parents = parents + (entry.uri to entry.name); query = ""; selected = emptySet()}
                                select -> selected = if (entry.uri in selected) selected - entry.uri else selected + entry.uri
                                else -> open(listOf(entry))
                            }
                        }
                        if (!entry.directory) app.ember.player.PlayerIconAction(uiResources.getString(R.string.ui_more_options_for ,entry.name),androidx.compose.material.icons.Icons.Outlined.MoreHoriz) {menu = entry}
                    }
                }
            }
            ButtonRow {
                if(offsets.size>1)PlayerAction(uiResources.getString(R.string.ui_previous_page),enabled=!loading&&!busy){offsets=offsets.dropLast(1)}
                page.next?.let {next->PlayerAction(uiResources.getString(R.string.ui_next_page),enabled=!loading&&!busy){offsets=offsets+listOf(next)}}
            }
            Text(uiResources.getString(R.string.ui_folder_search_scope),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            if (entries.any {!it.directory}) PlayerAction(uiResources.getString(R.string.ui_play_this_page)) {open(entries.filterNot {it.directory})}
        }
        menu?.takeUnless {app.ember.player.PrivateMedia.hidden(it.uri.toString())}?.let {file -> OptionMenu(file.name, listOf(
            MenuOption(uiResources.getString(R.string.ui_play)) {open(listOf(file))},
            MenuOption(uiResources.getString(R.string.ui_keep_private)) {protect=file;protectPin=true},
            MenuOption(uiResources.getString(R.string.ui_rename)) {rename = file},
            MenuOption(uiResources.getString(R.string.ui_share)) {share(listOf(file))},
            MenuOption(uiResources.getString(R.string.ui_move)) {moving = listOf(file); runCatching {destination.launch(null)}.onFailure {notice = uiResources.getString(R.string.ui_no_document_picker_is_installed)}},
            MenuOption(uiResources.getString(R.string.ui_delete_f6fdbe)) {deleting = listOf(file)},
        ), {menu = null})}
        if(protectPin)app.ember.player.PrivatePinPrompt({protectPin=false;protect=null},{
            val file=protect;protectPin=false;protect=null
            if(file!=null)operation {withContext(Dispatchers.IO){app.ember.player.PrivateMedia.add(app.ember.player.PrivateItem(file.uri.toString(),file.name,file.directory))}}
        })
        rename?.takeUnless {app.ember.player.PrivateMedia.hidden(it.uri.toString())}?.let {file ->
            var name by remember(file) {mutableStateOf(file.name)}
            TvDialog({rename = null}, width = 520.dp, resizeForKeyboard = true,scrollContent=true) {
                DialogHeading(uiResources.getString(R.string.ui_rename_file))
                TvTextField(name, {name = it.take(180)}, Modifier.fillMaxWidth())
                ButtonRow {
                    app.ember.player.PlayerAction(uiResources.getString(R.string.ui_save),primary=true) {if (DeviceFiles.validName(name)) {rename = null; operation {withContext(Dispatchers.IO) {
                        val renamed=DeviceFiles.rename(activity,file,name)
                        transferFilePlaybackState(activity,file,renamed,file.parent,name)
                    }}} else notice = uiResources.getString(R.string.ui_enter_a_valid_filename)}
                    app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel)) {rename = null}
                }
            }
        }
        if (deleting.isNotEmpty()&&deleting.none {app.ember.player.PrivateMedia.hidden(it.uri.toString())}) app.ember.player.PlayerConfirmationDialog(
            uiResources.getQuantityString(R.plurals.ui_delete_files,deleting.size,deleting.size),uiResources.getString(R.string.ui_this_removes_the_selected_files_from_storage),uiResources.getString(R.string.ui_delete_f6fdbe),{deleting=emptyList()}) {
            val files=deleting;deleting=emptyList();operation {withContext(Dispatchers.IO){DeviceFiles.delete(activity,files)}}
        }
    }
}
