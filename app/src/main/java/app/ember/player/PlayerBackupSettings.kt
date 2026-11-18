package app.ember.player

import app.ember.tv.R

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.data.SubtitleFiles
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun PlayerBackupSettings(restored:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<PlayerFeedback?>(null)}
    var mode by remember {mutableStateOf<String?>(null)}
    var source by remember {mutableStateOf<Uri?>(null)}
    var exportKey by remember {mutableStateOf<CharArray?>(null)}
    var restoreData by remember {mutableStateOf<PlayerBackupData?>(null)}
    DisposableEffect(Unit) {onDispose {exportKey?.fill('\u0000')}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        PrivateMedia.endPicker()
        val password=exportKey;exportKey=null
        if(uri==null || password==null)password?.fill('\u0000')
        else {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bytes=PlayerBackups.export(context,password)
                        try {context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(bytes)} ?: error("Could not open the backup file.")}
                        finally {bytes.fill(0)}
                    }
                    notice=PlayerFeedback(uiResources.getString(R.string.ui_encrypted_backup_saved),false)
                } catch(e:CancellationException) {throw e}
                catch(_:Exception) {notice=PlayerFeedback(uiResources.getString(R.string.ui_could_not_save_the_backup_check_file_access_and))}
                finally {password.fill('\u0000');busy=false}
            }
        }
    }
    val select=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->PrivateMedia.endPicker();if(uri!=null){source=uri;mode="restore"}}
    Text(uiResources.getString(R.string.ui_settings_saved_positions_bookmarks_playlists_and_connection_details_are),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    ButtonRow {
        Chip(if(busy)uiResources.getString(R.string.ui_working) else uiResources.getString(R.string.ui_export_encrypted_backup)) {if(!busy){notice=null;mode="export"}}
        Chip(uiResources.getString(R.string.ui_restore_backup)) {if(!busy)runCatching {if(PrivateMedia.enabled)PrivateMedia.beginPicker();select.launch(arrayOf("application/octet-stream","*/*"))}.onFailure {PrivateMedia.endPicker();notice=PlayerFeedback(uiResources.getString(R.string.ui_no_document_picker_is_installed_on_this_device))}}
    }
    notice?.let {PlayerFeedbackText(it)}
    mode?.let {operation->
        var password by remember(operation) {mutableStateOf("")}
        var confirm by remember(operation) {mutableStateOf("")}
        var error by remember(operation) {mutableStateOf<String?>(null)}
        TvDialog({mode=null;source=null},width=520.dp,resizeForKeyboard=true,scrollContent=true) {
            DialogHeading(if(operation=="export")uiResources.getString(R.string.ui_protect_your_backup) else uiResources.getString(R.string.ui_unlock_backup))
            TvTextField(password,{password=it.take(256)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_password_at_least_10_characters),password=true)
            if(operation=="export")TvTextField(confirm,{confirm=it.take(256)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_confirm_password),password=true)
            error?.let {PlayerErrorText(it)}
            ButtonRow {
                PlayerAction(uiResources.getString(R.string.ui_continue),primary=true) {
                    if(password.length<10)error=uiResources.getString(R.string.ui_use_at_least_10_characters)
                    else if(operation=="export" && password!=confirm)error=uiResources.getString(R.string.ui_passwords_do_not_match)
                    else {
                        val key=password.toCharArray();password="";confirm="";mode=null
                        if(operation=="export") {
                            exportKey=key
                            runCatching {if(PrivateMedia.enabled)PrivateMedia.beginPicker();export.launch(uiResources.getString(R.string.ui_ember_player_epbackup))}.onFailure {PrivateMedia.endPicker();key.fill('\u0000');exportKey=null;notice=PlayerFeedback(uiResources.getString(R.string.ui_no_document_picker_is_installed_on_this_device))}
                        } else {
                            val uri=source;source=null;busy=true
                            scope.launch {
                                try {
                                    restoreData=withContext(Dispatchers.IO) {
                                        requireNotNull(uri)
                                        val bytes=context.contentResolver.openInputStream(uri)?.use {SubtitleFiles.read(it,PlayerBackupCipher.MAX_BYTES+52)} ?: error("Backup unavailable.")
                                        try {PlayerBackups.decode(bytes,key)} finally {bytes.fill(0)}
                                    }
                                } catch(e:CancellationException) {throw e}
                                catch(_:Exception) {notice=PlayerFeedback(uiResources.getString(R.string.ui_could_not_unlock_this_backup_check_the_password_and))}
                                finally {key.fill('\u0000');busy=false}
                            }
                        }
                    }
                }
                app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel)) {password="";confirm="";source=null;mode=null}
            }
        }
    }
    restoreData?.let {data->
        var currentPin by remember(data) {mutableStateOf("")}
        var pinError by remember(data) {mutableStateOf<String?>(null)}
        PlayerConfirmationDialog(uiResources.getString(R.string.ui_restore_this_backup),
            uiResources.getString(R.string.ui_this_replaces_your_player_settings_and_saved_activity_folder),
            uiResources.getString(R.string.ui_restore),{restoreData=null},busy=busy,content={
                if(PrivateMedia.enabled)TvTextField(currentPin,{currentPin=it.take(12);pinError=null},Modifier.fillMaxWidth(),
                    placeholder=uiResources.getString(R.string.ui_current_pin),password=true,keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword,enabled=!busy)
                pinError?.let {PlayerErrorText(it)}
            }) {
            if(!busy) {
                val pin=currentPin;busy=true;pinError=null
                scope.launch {
                    try {
                        val saved=withContext(Dispatchers.IO) {PlayerBackups.restore(context,data,pin)}
                        if(saved){restoreData=null;restored();notice=PlayerFeedback(uiResources.getString(R.string.ui_backup_restored),false)}
                        else pinError=uiResources.getString(R.string.ui_incorrect_pin)
                    }catch(e:CancellationException){throw e}
                     catch(e:Exception){
                        restoreData=null
                        notice=PlayerFeedback(when {e is BackupRestoreException&&e.rollbackFailed->uiResources.getString(R.string.ui_restore_failed_and_the_previous_configuration_could_not_be);e is BackupRestoreException->uiResources.getString(R.string.ui_restore_failed_the_previous_configuration_was_kept);else->uiResources.getString(R.string.ui_restore_failed)})
                    }finally {currentPin="";busy=false}
                }
            }
        }
    }
}
