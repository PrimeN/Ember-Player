package app.ember.player

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun PrivatePinPrompt(close:()->Unit,success:()->Unit,createNew:Boolean=false) {
    val scope=rememberCoroutineScope()
    val create=createNew||!PrivateMedia.enabled
    val changing=createNew&&PrivateMedia.enabled
    var pin by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    var notice by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    TvDialog({if(!busy)close()},width=460.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading(if(changing)"Change private-media PIN" else if(create)"Create private-media PIN" else "Unlock private media")
        Text(if(create)"Use 6–12 digits. Keep your PIN safe: it cannot be recovered." else "Enter your private-media PIN.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        TvTextField(pin,{pin=it.filter {c->c in '0'..'9'}.take(12)},Modifier.fillMaxWidth(),password=true,keyboardType=KeyboardType.NumberPassword,placeholder="PIN")
        if(create)TvTextField(confirm,{confirm=it.filter {c->c in '0'..'9'}.take(12)},Modifier.fillMaxWidth(),password=true,keyboardType=KeyboardType.NumberPassword,placeholder="Confirm PIN")
        notice?.let {PlayerErrorText(it)}
        ButtonRow {
            PlayerAction(if(busy)"Checking…" else if(changing)"Save PIN" else if(create)"Create PIN" else "Unlock",primary=true,enabled=!busy) {if(!busy){
                if(!PrivatePin.valid(pin)||create&&pin!=confirm)notice="Use 6–12 digits and matching PINs."
                else {val value=pin;pin="";confirm="";busy=true;scope.launch {
                    try {
                        val okay=withContext(Dispatchers.IO){if(create){PrivateMedia.configure(value);true}else PrivateMedia.authenticate(value)}
                        if(okay)success() else notice="Incorrect PIN"
                    } catch(e:CancellationException){throw e}
                    catch(e:Exception){notice=e.message?.take(180) ?: "Private storage is unavailable."}
                    finally {busy=false}
                }}
            }}
            app.ember.player.PlayerAction("Cancel"){if(!busy)close()}
        }
    }
}

@Composable
internal fun PrivateMediaPanel(activity:ComponentActivity) {
    val state by PrivateMedia.state.collectAsState()
    val unlocked by PrivateMedia.unlocked.collectAsState()
    val scope=rememberCoroutineScope()
    var open by remember {mutableStateOf(false)}
    var prompt by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var importing by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf(false)}
    var folder by remember {mutableStateOf<PrivateItem?>(null)}
    var parents by remember {mutableStateOf<List<android.net.Uri>>(emptyList())}
    var entries by remember {mutableStateOf<List<DeviceFile>>(emptyList())}
    fun importFiles(block:()->List<PrivateItem>) {
        if(importing)return
        importing=true
        scope.launch {
            try {withContext(Dispatchers.IO){check(PrivateMedia.unlocked.value);PrivateMedia.addAll(block())}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){notice=e.message?.take(180) ?: "Could not save private media."}
            finally {importing=false}
        }
    }
    val files=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris->
        PrivateMedia.endPicker()
        if(PrivateMedia.unlocked.value&&uris.isNotEmpty())importFiles {uris.take(200).map {uri->
            MediaFiles.retain(activity,uri);PrivateItem(uri.toString(),MediaFiles.name(activity,uri))
        }}
    }
    val folders=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->
        PrivateMedia.endPicker()
        if(uri!=null&&PrivateMedia.unlocked.value)importFiles {
            activity.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            listOf(PrivateItem(uri.toString(),DeviceFiles.name(activity,DeviceFiles.root(uri)),true))
        }
    }
    BackHandler(enabled=open&&unlocked){if(folder==null)open=false else if(parents.isEmpty())folder=null else parents=parents.dropLast(1)}
    LaunchedEffect(unlocked){if(!unlocked){open=false;folder=null;parents=emptyList();entries=emptyList()}}
    LaunchedEffect(folder,parents,unlocked) {
        entries=emptyList()
        val root=folder ?: return@LaunchedEffect
        if(!unlocked)return@LaunchedEffect
        try {entries=withContext(Dispatchers.IO){DeviceFiles.list(activity,android.net.Uri.parse(root.uri),parents.lastOrNull() ?: DeviceFiles.root(android.net.Uri.parse(root.uri))){ensureActive()}.entries}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){notice="Private folder is unavailable. Add it again to renew access."}
    }
    PlayerSection(PlayerUiLabels.PRIVATE_MEDIA) {
        Chip(if(open&&unlocked)"Close private media" else "Open private media"){
            if(open)open=false else if(PrivateMedia.enabled&&unlocked)open=true else prompt=true
        }
        if(prompt)PrivatePinPrompt({prompt=false},{prompt=false;open=true})
        if(open&&unlocked) {
            Text("Hidden inside Ember Player. Original files remain available to other apps; this is access protection, not file encryption. Private playback does not save activity or expose system media controls.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            ButtonRow {
                Chip(if(importing)"Adding files…" else "Add files"){if(!importing){PrivateMedia.beginPicker();runCatching {files.launch(arrayOf("video/*","audio/*","application/octet-stream"))}.onFailure {PrivateMedia.endPicker();notice="No file picker is installed."}}}
                Chip("Add folder"){if(!importing){PrivateMedia.beginPicker();runCatching {folders.launch(null)}.onFailure {PrivateMedia.endPicker();notice="No folder picker is installed."}}}
                Chip("Lock"){PrivateMedia.lock()}
            }
            if(folder==null)LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(state.items,key={it.uri}) {item->ButtonRow {
                    Chip(item.name,maxLines=1){if(item.folder){folder=item;parents=emptyList()}else runCatching {MediaFiles.launch(activity,listOf(QueueMedia(item.uri,item.name)))}.onFailure {notice="This media is unavailable. Add it again to renew access."}}
                    Chip("Make visible"){scope.launch {runCatching {withContext(Dispatchers.IO){PrivateMedia.remove(item.uri)}}.onFailure {notice="Could not update private media."}}}
                }}
            } else {
                Chip("Back"){if(parents.isEmpty())folder=null else parents=parents.dropLast(1)}
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(entries,key={it.uri.toString()}) {entry->PlayerFileRow(entry.name,entry.directory){
                        if(entry.directory)parents=parents+entry.uri else runCatching {MediaFiles.launch(activity,listOf(QueueMedia(entry.uri.toString(),entry.name,directory=entry.parent.toString())))}.onFailure {notice="Could not open private media."}
                    }}
                }
            }
            PlayerAction("Remove PIN protection",destructive=true){removing=true}
        }
        if(removing&&unlocked)RemovePrivateProtectionPrompt({removing=false}){notice=it}
        notice?.let {PlayerErrorText(it)}
    }
}

@Composable
private fun RemovePrivateProtectionPrompt(close:()->Unit,error:(String)->Unit) {
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    PlayerConfirmationDialog("Make private media visible?","Removes the PIN and makes all private entries visible in normal browsing. Original files are kept.","Remove protection",close,busy) {
        if(!busy){busy=true;scope.launch {
            try {withContext(Dispatchers.IO){PrivateMedia.removeProtection()};close()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){error("Could not remove protection.")}
            finally {busy=false}
        }}
    }
}

@Composable internal fun PrivacySettings() {
    val state by PrivateMedia.state.collectAsState();val scope=rememberCoroutineScope()
    val unlocked by PrivateMedia.unlocked.collectAsState()
    var notice by remember {mutableStateOf<String?>(null)}
    var editingPin by remember {mutableStateOf(false)}
    var unlocking by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf(false)}
    var clearing by remember {mutableStateOf(false)}
    var disabling by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf<String?>(null)}
    Text("Private media PIN",style=androidx.tv.material3.MaterialTheme.typography.bodyLarge)
    Text("Protect access inside Ember Player. Add and browse protected files from Private media in the library.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    when {
        !PrivateMedia.enabled->Chip("Create PIN"){editingPin=true}
        unlocked->ButtonRow {
            Chip("Change PIN"){editingPin=true}
            PlayerAction("Remove PIN protection",destructive=true){removing=true}
        }
        else->Chip("Unlock private media"){unlocking=true}
    }
    if(editingPin)PrivatePinPrompt({editingPin=false},{editingPin=false},createNew=true)
    if(unlocking)PrivatePinPrompt({unlocking=false},{unlocking=false})
    if(removing&&unlocked)RemovePrivateProtectionPrompt({removing=false}){notice=it}
    SwitchChip("Save recent activity and resume positions",state.history){
        if(state.history)disabling=true else scope.launch {
            try {withContext(Dispatchers.IO){PrivateMedia.recordHistory(true)}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Could not update privacy settings."}
        }
    }
    Text("Turning this off clears recent activity and resume positions. Playlists and bookmarks are kept.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    PlayerAction("Clear recent activity and resume positions",destructive=true){clearing=true}
    if(clearing||disabling)PlayerConfirmationDialog(if(disabling)"Turn off activity history?" else "Clear recent activity?",
        "Recent activity and resume positions will be cleared. Playlists and bookmarks will be kept."+(if(disabling)" New activity will no longer be saved." else ""),
        if(disabling)"Turn off and clear" else "Clear activity",{clearing=false;disabling=false}) {
        val stopRecording=disabling;clearing=false;disabling=false;notice=null;status=null
        scope.launch {
            try {withContext(Dispatchers.IO){if(stopRecording)PrivateMedia.recordHistory(false) else PrivateMedia.clearHistory()};status="Recent activity cleared"}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Could not clear all recent activity."}
        }
    }
    notice?.let {PlayerErrorText(it)}
    status?.let {PlayerStatusText(it)}
}
