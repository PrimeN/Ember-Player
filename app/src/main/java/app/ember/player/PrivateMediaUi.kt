package app.ember.player

import app.ember.tv.R

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scope=rememberCoroutineScope()
    val create=createNew||!PrivateMedia.enabled
    val changing=createNew&&PrivateMedia.enabled
    var currentPin by remember {mutableStateOf("")}
    var pin by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    var notice by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    TvDialog({if(!busy)close()},width=460.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading(if(changing)uiResources.getString(R.string.ui_change_private_media_pin) else if(create)uiResources.getString(R.string.ui_create_private_media_pin) else uiResources.getString(R.string.ui_unlock_private_media))
        Text(if(create)uiResources.getString(R.string.ui_use_6_12_digits_keep_your_pin_safe_it) else uiResources.getString(R.string.ui_enter_your_private_media_pin),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        if(changing)TvTextField(currentPin,{currentPin=it.filter {c->c in '0'..'9'}.take(12)},Modifier.fillMaxWidth().semantics {contentDescription=uiResources.getString(R.string.ui_current_pin)},password=true,keyboardType=KeyboardType.NumberPassword,placeholder=uiResources.getString(R.string.ui_current_pin))
        TvTextField(pin,{pin=it.filter {c->c in '0'..'9'}.take(12)},Modifier.fillMaxWidth(),password=true,keyboardType=KeyboardType.NumberPassword,placeholder=uiResources.getString(if(changing)R.string.ui_new_pin else R.string.ui_pin))
        if(create)TvTextField(confirm,{confirm=it.filter {c->c in '0'..'9'}.take(12)},Modifier.fillMaxWidth(),password=true,keyboardType=KeyboardType.NumberPassword,placeholder=uiResources.getString(R.string.ui_confirm_pin))
        notice?.let {PlayerErrorText(it)}
        ButtonRow {
            PlayerAction(if(busy)uiResources.getString(R.string.ui_checking) else if(changing)uiResources.getString(R.string.ui_save_pin) else if(create)uiResources.getString(R.string.ui_create_pin) else uiResources.getString(R.string.ui_unlock),primary=true,enabled=!busy) {if(!busy){
                if(!PrivatePin.valid(pin)||create&&pin!=confirm||changing&&!PrivatePin.valid(currentPin))notice=uiResources.getString(R.string.ui_use_6_12_digits_and_matching_pins)
                else {val value=pin;val previous=currentPin;pin="";confirm="";currentPin="";busy=true;scope.launch {
                    try {
                        val okay=withContext(Dispatchers.IO){if(create)PrivateMedia.configure(value,previous.takeIf {changing}) else PrivateMedia.authenticate(value)}
                        if(okay)success() else notice=uiResources.getString(R.string.ui_incorrect_pin)
                    } catch(e:CancellationException){throw e}
                    catch(e:Exception){notice=if(PrivateMedia.state.value.retryElapsed>android.os.SystemClock.elapsedRealtime())uiResources.getString(R.string.ui_too_many_attempts_wait_before_trying_again) else uiResources.getString(R.string.ui_private_storage_is_unavailable)}
                    finally {busy=false}
                }}
            }}
            app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel)){if(!busy)close()}
        }
    }
}

@Composable
internal fun PrivateMediaPanel(activity:ComponentActivity) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

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
            catch(e:Exception){notice=uiResources.getString(R.string.ui_could_not_save_private_media_check_access_and_the)}
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
        try {entries=withContext(Dispatchers.IO){DeviceFiles.list(activity,android.net.Uri.parse(root.uri),parents.lastOrNull() ?: DeviceFiles.root(android.net.Uri.parse(root.uri))){ensureActive()}.entries.sortedWith(compareByDescending<DeviceFile> {it.directory}.thenBy(NaturalOrder) {it.name})}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){notice=uiResources.getString(R.string.ui_private_folder_is_unavailable_add_it_again_to_renew)}
    }
    PlayerSection(uiResources.getString(PlayerUiLabels.PRIVATE_MEDIA)) {
        PlayerAction(if(open&&unlocked)uiResources.getString(R.string.ui_close_private_media) else uiResources.getString(R.string.ui_open_private_media)){
            if(open)open=false else if(PrivateMedia.enabled&&unlocked)open=true else prompt=true
        }
        if(prompt)PrivatePinPrompt({prompt=false},{prompt=false;open=true})
        if(open&&unlocked) {
            Text(uiResources.getString(R.string.ui_hidden_inside_ember_player_original_files_remain_available_to),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            ButtonRow {
                PlayerAction(if(importing)uiResources.getString(R.string.ui_adding_files) else uiResources.getString(R.string.ui_add_files)){if(!importing){PrivateMedia.beginPicker();runCatching {files.launch(arrayOf("video/*","audio/*","application/octet-stream"))}.onFailure {PrivateMedia.endPicker();notice=uiResources.getString(R.string.ui_no_file_picker_is_installed)}}}
                PlayerAction(uiResources.getString(R.string.ui_add_folder)){if(!importing){PrivateMedia.beginPicker();runCatching {folders.launch(null)}.onFailure {PrivateMedia.endPicker();notice=uiResources.getString(R.string.ui_no_folder_picker_is_installed)}}}
                PlayerAction(uiResources.getString(R.string.ui_lock)){PrivateMedia.lock()}
            }
            if(folder==null)LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(state.items.sortedWith(compareByDescending<PrivateItem> {it.folder}.thenBy(NaturalOrder) {it.name}),key={it.uri}) {item->ButtonRow {
                    DeviceMediaRow(DeviceFile(android.net.Uri.parse(item.uri),android.net.Uri.parse(item.uri),item.name,if(item.folder)android.provider.DocumentsContract.Document.MIME_TYPE_DIR else "video/*",-1,0,0)){if(item.folder){folder=item;parents=emptyList()}else runCatching {MediaFiles.launch(activity,listOf(QueueMedia(item.uri,item.name)))}.onFailure {notice=uiResources.getString(R.string.ui_this_media_is_unavailable_add_it_again_to_renew)}}
                    PlayerAction(uiResources.getString(R.string.ui_make_visible)){scope.launch {runCatching {withContext(Dispatchers.IO){PrivateMedia.remove(item.uri)}}.onFailure {notice=uiResources.getString(R.string.ui_could_not_update_private_media)}}}
                }}
            } else {
                PlayerAction(uiResources.getString(R.string.ui_back)){if(parents.isEmpty())folder=null else parents=parents.dropLast(1)}
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(entries,key={it.uri.toString()}) {entry->DeviceMediaRow(entry){
                        if(entry.directory)parents=parents+entry.uri else runCatching {MediaFiles.launch(activity,listOf(QueueMedia(entry.uri.toString(),entry.name,directory=entry.parent.toString())))}.onFailure {notice=uiResources.getString(R.string.ui_could_not_open_private_media)}
                    }}
                }
            }
            PlayerAction(uiResources.getString(R.string.ui_remove_pin_protection),destructive=true){removing=true}
        }
        if(removing&&unlocked)RemovePrivateProtectionPrompt({removing=false})
        notice?.let {PlayerErrorText(it)}
    }
}

@Composable
private fun RemovePrivateProtectionPrompt(close:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var pin by remember {mutableStateOf("")}
    var notice by remember {mutableStateOf<String?>(null)}
    PlayerConfirmationDialog(uiResources.getString(R.string.ui_make_private_media_visible),uiResources.getString(R.string.ui_removes_the_pin_and_makes_all_private_entries_visible),uiResources.getString(R.string.ui_remove_protection),close,busy,content={
        TvTextField(pin,{pin=it.filter {c->c in '0'..'9'}.take(12)},Modifier.fillMaxWidth().semantics {contentDescription=uiResources.getString(R.string.ui_current_pin)},password=true,keyboardType=KeyboardType.NumberPassword,placeholder=uiResources.getString(R.string.ui_current_pin))
        notice?.let {PlayerErrorText(it)}
    }) {
        if(!busy){
            val current=pin;pin="";busy=true
            scope.launch {
                try {
                    if(withContext(Dispatchers.IO){PrivateMedia.removeProtection(current)})close()
                    else notice=uiResources.getString(R.string.ui_incorrect_pin)
                } catch(e:CancellationException){throw e}
                  catch(_:Exception){notice=uiResources.getString(if(PrivateMedia.state.value.retryElapsed>android.os.SystemClock.elapsedRealtime())R.string.ui_too_many_attempts_wait_before_trying_again else R.string.ui_could_not_remove_protection)}
                finally {busy=false}
            }
        }
    }
}

@Composable internal fun PrivacySettings() {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val state by PrivateMedia.state.collectAsState();val scope=rememberCoroutineScope()
    val unlocked by PrivateMedia.unlocked.collectAsState()
    var notice by remember {mutableStateOf<String?>(null)}
    var editingPin by remember {mutableStateOf(false)}
    var unlocking by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf(false)}
    var clearing by remember {mutableStateOf(false)}
    var disabling by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf<String?>(null)}
    Text(uiResources.getString(R.string.ui_private_media_pin),style=androidx.tv.material3.MaterialTheme.typography.bodyLarge)
    Text(uiResources.getString(R.string.ui_protect_access_inside_ember_player_add_and_browse_protected),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    when {
        !PrivateMedia.enabled->PlayerAction(uiResources.getString(R.string.ui_create_pin)){editingPin=true}
        unlocked->ButtonRow {
            PlayerAction(uiResources.getString(R.string.ui_change_pin)){editingPin=true}
            PlayerAction(uiResources.getString(R.string.ui_remove_pin_protection),destructive=true){removing=true}
        }
        else->PlayerAction(uiResources.getString(R.string.ui_unlock_private_media)){unlocking=true}
    }
    if(editingPin)PrivatePinPrompt({editingPin=false},{editingPin=false},createNew=true)
    if(unlocking)PrivatePinPrompt({unlocking=false},{unlocking=false})
    if(removing&&unlocked)RemovePrivateProtectionPrompt({removing=false})
    SwitchChip(uiResources.getString(R.string.ui_save_recent_activity_and_resume_positions),state.history){
        if(state.history)disabling=true else scope.launch {
            try {withContext(Dispatchers.IO){PrivateMedia.recordHistory(true)}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice=uiResources.getString(R.string.ui_could_not_update_privacy_settings)}
        }
    }
    Text(uiResources.getString(R.string.ui_turning_this_off_clears_recent_activity_and_resume_positions),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    PlayerAction(uiResources.getString(R.string.ui_clear_recent_activity_and_resume_positions),destructive=true){clearing=true}
    if(clearing||disabling)PlayerConfirmationDialog(if(disabling)uiResources.getString(R.string.ui_turn_off_activity_history) else uiResources.getString(R.string.ui_clear_recent_activity),
        uiResources.getString(R.string.ui_recent_activity_and_resume_positions_will_be_cleared_playlists)+(if(disabling)uiResources.getString(R.string.ui_new_activity_will_no_longer_be_saved) else ""),
        if(disabling)uiResources.getString(R.string.ui_turn_off_and_clear) else uiResources.getString(R.string.ui_clear_activity),{clearing=false;disabling=false}) {
        val stopRecording=disabling;clearing=false;disabling=false;notice=null;status=null
        scope.launch {
            try {withContext(Dispatchers.IO){if(stopRecording)PrivateMedia.recordHistory(false) else PrivateMedia.clearHistory()};status=uiResources.getString(R.string.ui_recent_activity_cleared)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice=uiResources.getString(R.string.ui_could_not_clear_all_recent_activity)}
        }
    }
    notice?.let {PlayerErrorText(it)}
    status?.let {PlayerStatusText(it)}
}
