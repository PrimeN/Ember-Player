package app.ember.tv.player

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
    val store = remember { DeviceFolders(activity) }
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<DeviceFolder>>(emptyList()) }
    var root by remember { mutableStateOf<DeviceFolder?>(null) }
    var parents by remember { mutableStateOf<List<Pair<Uri,String>>>(emptyList()) }
    var offsets by remember {mutableStateOf(listOf(0))}
    var page by remember { mutableStateOf(DeviceFiles.Page(emptyList(), false)) }
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
            catch (e: Exception) {notice = e.message?.take(180) ?: "The file operation could not be completed."; revision++}
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
    LaunchedEffect(refresh) {
        selected=emptySet()
        folders = withContext(Dispatchers.IO) {store.read().filterNot {app.ember.player.PrivateMedia.hidden(it.uri)}}
        if(root!=null && folders.none {it.uri==root?.uri}){root=null;parents=emptyList()}
    }
    LaunchedEffect(root,current) {offsets=listOf(0)}
    LaunchedEffect(root, current, revision, offsets) {
        selected = emptySet(); page = DeviceFiles.Page(emptyList(), false);lastPlayed=null
        val folder = root ?: return@LaunchedEffect
        val directory = current?.first ?: DeviceFiles.root(Uri.parse(folder.uri))
        loading = true
        try {
            val loaded=withContext(Dispatchers.IO) {DeviceFiles.list(activity,Uri.parse(folder.uri),directory,offsets.last()) {ensureActive()} to FolderPlaybackHistory(activity).get(directory)}
            page=loaded.first.copy(entries=loaded.first.entries.filterNot {app.ember.player.PrivateMedia.hidden(it.uri.toString())});lastPlayed=loaded.second
        }
        catch (e: CancellationException) {throw e}
        catch (_: Exception) {notice = "This folder is unavailable. Choose it again to renew access."}
        finally {loading = false}
    }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) operation {
            val folder = withContext(Dispatchers.IO) {
                DeviceFiles.retain(activity, tree)
                DeviceFolder(tree.toString(), DeviceFiles.name(activity, DeviceFiles.root(tree))).also {
                    check(store.add(it)) {"Encrypted folder storage is unavailable."}
                }
            }
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
            status = "Files moved"
        }
    }
    fun share(files: List<DeviceFile>) {
        if (files.isEmpty()) return
        if(files.any {app.ember.player.PrivateMedia.hidden(it.uri.toString())}){notice="Use the private-media area to access protected files.";return}
        val uris = ArrayList(files.map {it.uri})
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newRawUri("Media", uris.first()).apply {uris.drop(1).forEach {addItem(android.content.ClipData.Item(it))}}
        runCatching {activity.startActivity(Intent.createChooser(intent, "Share media"))}.onFailure {notice = "No app is available to share these files."}
    }
    app.ember.player.PlayerSection("Device folders") {
        if (root == null) {
            Chip("Add a media folder") {if (!busy) runCatching {addFolder.launch(null)}.onFailure {notice = "No document picker is installed on this device."}}
            folders.forEach {folder -> ButtonRow {
                Chip(folder.name, maxLines = 1) {root = folder; parents = emptyList(); query = ""}
                Chip("Forget folder") {operation {withContext(Dispatchers.IO) {check(store.remove(folder.uri)) {"Could not update saved folders."}}; folders = withContext(Dispatchers.IO) {store.read().filterNot {app.ember.player.PrivateMedia.hidden(it.uri)}}}}
            }}
        } else {
            Text(current?.second ?: root!!.name, style=androidx.tv.material3.MaterialTheme.typography.bodyLarge, maxLines = 1)
            TvTextField(query, {query = it.take(120)}, Modifier.fillMaxWidth(), placeholder = "Search this folder")
            ButtonRow {
                Chip("Back") {if (!busy) back()}
                Chip("Refresh") {if (!busy) {DeviceThumbnails.clear();revision++}}
                Chip(when(sort) {"date" -> "Newest first"; "size" -> "Largest first"; else -> "Name A–Z"}) {sort = when(sort) {"name" -> "date"; "date" -> "size"; else -> "name"}}
                Chip("Hidden files", selected = hidden) {hidden = !hidden}
                Chip(if (select) "Done selecting" else "Select files", selected = select) {select = !select; selected = emptySet()}
            }
            val entries = DeviceFiles.sorted(page.entries.filterNot {app.ember.player.PrivateMedia.hidden(it.uri.toString())}, query, sort, hidden)
            lastPlayed?.let {last->page.entries.firstOrNull {it.uri.toString()==last.uri && !app.ember.player.PrivateMedia.hidden(it.uri.toString()) && (hidden || !it.name.startsWith('.'))}?.let {file->Chip("Resume · ${last.title}",maxLines=1) {open(listOf(file))}}}
            val chosen = page.entries.filter {it.uri in selected && !it.directory && !app.ember.player.PrivateMedia.hidden(it.uri.toString())}
            if (chosen.isNotEmpty()) ButtonRow {
                Chip("Play ${chosen.size}") {open(chosen)}
                Chip("Share") {share(chosen)}
                Chip("Move") {if (!busy) {moving = chosen; runCatching {destination.launch(null)}.onFailure {moving = emptyList(); notice = "No document picker is installed."}}}
                app.ember.player.PlayerAction("Delete",destructive=true) {deleting = chosen}
            }
            if (loading || busy) Text(if (busy) "Working…" else "Loading folder…", color = Mute, style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            else if (entries.isEmpty()) Text("No matching media in this folder", color = Mute, style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
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
                        if (!entry.directory) app.ember.player.PlayerIconAction("More options for ${entry.name}",androidx.compose.material.icons.Icons.Outlined.MoreHoriz) {menu = entry}
                    }
                }
            }
            ButtonRow {
                if(offsets.size>1)Chip("Previous page"){offsets=offsets.dropLast(1)}
                if(page.limited)Chip("Next page"){offsets=offsets+page.nextOffset}
            }
            Text("Search and sorting apply to this page. Use Library search to find files across folders.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            if (entries.any {!it.directory}) Chip("Play this page") {open(entries.filterNot {it.directory})}
        }
        notice?.let {app.ember.player.PlayerErrorText(it)}
        status?.let {app.ember.player.PlayerStatusText(it)}
        menu?.takeUnless {app.ember.player.PrivateMedia.hidden(it.uri.toString())}?.let {file -> OptionMenu(file.name, listOf(
            MenuOption("Play") {open(listOf(file))},
            MenuOption("Keep private") {protect=file;protectPin=true},
            MenuOption("Rename") {rename = file},
            MenuOption("Share") {share(listOf(file))},
            MenuOption("Move") {moving = listOf(file); runCatching {destination.launch(null)}.onFailure {notice = "No document picker is installed."}},
            MenuOption("Delete") {deleting = listOf(file)},
        ), {menu = null})}
        if(protectPin)app.ember.player.PrivatePinPrompt({protectPin=false;protect=null},{
            val file=protect;protectPin=false;protect=null
            if(file!=null)operation {withContext(Dispatchers.IO){app.ember.player.PrivateMedia.add(app.ember.player.PrivateItem(file.uri.toString(),file.name,file.directory))}}
        })
        rename?.takeUnless {app.ember.player.PrivateMedia.hidden(it.uri.toString())}?.let {file ->
            var name by remember(file) {mutableStateOf(file.name)}
            TvDialog({rename = null}, width = 520.dp, resizeForKeyboard = true,scrollContent=true) {
                DialogHeading("Rename file")
                TvTextField(name, {name = it.take(180)}, Modifier.fillMaxWidth())
                ButtonRow {
                    app.ember.player.PlayerAction("Save",primary=true) {if (DeviceFiles.validName(name)) {rename = null; operation {withContext(Dispatchers.IO) {
                        val renamed=DeviceFiles.rename(activity,file,name)
                        transferFilePlaybackState(activity,file,renamed,file.parent,name)
                    }}} else notice = "Enter a valid filename."}
                    app.ember.player.PlayerAction("Cancel") {rename = null}
                }
            }
        }
        if (deleting.isNotEmpty()&&deleting.none {app.ember.player.PrivateMedia.hidden(it.uri.toString())}) app.ember.player.PlayerConfirmationDialog(
            "Delete ${deleting.size} file${if(deleting.size==1)"" else "s"}?","This removes the selected files from storage.","Delete",{deleting=emptyList()}) {
            val files=deleting;deleting=emptyList();operation {withContext(Dispatchers.IO){DeviceFiles.delete(activity,files)}}
        }
    }
}
