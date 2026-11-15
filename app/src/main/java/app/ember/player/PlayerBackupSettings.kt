package app.ember.player

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
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var mode by remember {mutableStateOf<String?>(null)}
    var source by remember {mutableStateOf<Uri?>(null)}
    var exportKey by remember {mutableStateOf<CharArray?>(null)}
    var restoreData by remember {mutableStateOf<PlayerBackupData?>(null)}
    DisposableEffect(Unit) {onDispose {exportKey?.fill('\u0000')}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
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
                    notice="Encrypted backup saved"
                } catch(e:CancellationException) {throw e}
                catch(_:Exception) {notice="Could not save the backup. Check file access and available storage."}
                finally {password.fill('\u0000');busy=false}
            }
        }
    }
    val select=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null){source=uri;mode="restore"}}
    Text("Settings, saved positions, bookmarks, playlists and connection details are protected by your backup password. Video files are not copied. Keep the password safe; it cannot be recovered.",fontSize=12.sp,color=Mute)
    ButtonRow {
        Chip(if(busy)"Working…" else "Export encrypted backup") {if(!busy){notice=null;mode="export"}}
        Chip("Restore backup") {if(!busy)runCatching {select.launch(arrayOf("application/octet-stream","*/*"))}.onFailure {notice="No document picker is installed on this device."}}
    }
    notice?.let {Text(it,fontSize=13.sp,color=Mute)}
    mode?.let {operation->
        var password by remember(operation) {mutableStateOf("")}
        var confirm by remember(operation) {mutableStateOf("")}
        var error by remember(operation) {mutableStateOf<String?>(null)}
        TvDialog({mode=null;source=null},width=520.dp,resizeForKeyboard=true) {
            DialogHeading(if(operation=="export")"Protect your backup" else "Unlock backup")
            TvTextField(password,{password=it.take(256)},Modifier.fillMaxWidth(),placeholder="Password · at least 10 characters",password=true)
            if(operation=="export")TvTextField(confirm,{confirm=it.take(256)},Modifier.fillMaxWidth(),placeholder="Confirm password",password=true)
            error?.let {Text(it,fontSize=12.sp)}
            ButtonRow {
                Chip("Continue") {
                    if(password.length<10)error="Use at least 10 characters."
                    else if(operation=="export" && password!=confirm)error="Passwords do not match."
                    else {
                        val key=password.toCharArray();password="";confirm="";mode=null
                        if(operation=="export") {
                            exportKey=key
                            runCatching {export.launch("Ember Player.epbackup")}.onFailure {key.fill('\u0000');exportKey=null;notice="No document picker is installed on this device."}
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
                                catch(_:Exception) {notice="Could not unlock this backup. Check the password and make sure the file is complete."}
                                finally {key.fill('\u0000');busy=false}
                            }
                        }
                    }
                }
                Chip("Cancel") {password="";confirm="";source=null;mode=null}
            }
        }
    }
    restoreData?.let {data->TvDialog({restoreData=null},width=520.dp) {
        DialogHeading("Restore this backup?")
        Text("This replaces your player settings and saved activity. Folder permissions may need to be granted again on another device.",fontSize=14.sp)
        ButtonRow {
            Chip("Restore") {
                restoreData=null;busy=true
                scope.launch {
                    try {withContext(Dispatchers.IO) {PlayerBackups.restore(context,data)};restored();notice="Backup restored"}
                    catch(e:CancellationException) {throw e}
                    catch(e:Exception) {notice=e.message?.take(180) ?: "Restore failed."}
                    finally {busy=false}
                }
            }
            Chip("Cancel") {restoreData=null}
        }
    }}
}
