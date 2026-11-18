package app.ember.tv.player

import app.ember.player.PlayerAction

import androidx.compose.ui.res.stringResource
import app.ember.tv.R

import app.ember.tv.data.toHex
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.data.Prefs
import app.ember.tv.ui.TvDialog
import app.ember.tv.ui.DialogHeading
import app.ember.tv.ui.TvTextField
import app.ember.tv.ui.Chip
import app.ember.tv.ui.ButtonRow
import kotlinx.coroutines.*

@Composable
internal fun NetworkFilesPanel(activity:MediaFilesActivity,refresh:Int=0) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scope=rememberCoroutineScope()
    val profile=remember {Prefs(activity).currentProfile}
    val store=remember(profile) {SavedNetworkFolders(activity,profile)}
    var readError by remember {mutableStateOf(false)}
    var revision by remember {mutableIntStateOf(0)}
    var accounts by remember {mutableStateOf<List<NetworkAccount>>(emptyList())}
    var edit by remember {mutableStateOf(false)}
    var browse by remember {mutableStateOf<NetworkAccount?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var handledRequest by remember {mutableStateOf(false)}
    LaunchedEffect(store,refresh,revision) {
        val loaded=withContext(Dispatchers.IO){runCatching {store.read()}.getOrNull()}
        readError=loaded==null
        if(loaded!=null){accounts=loaded;error=null} else error=uiResources.getString(R.string.ui_could_not_update_encrypted_folder_storage)
        if(loaded!=null && !handledRequest && activity.intent.getStringExtra("network.profile")==profile) {
            handledRequest=true
            val id=activity.intent.getStringExtra("network.account")
            if(id!=null){browse=accounts.firstOrNull {it.id==id};if(browse==null)error=uiResources.getString(R.string.ui_this_saved_folder_was_removed_add_it_again_to)}
        }
    }
    app.ember.player.PlayerSection(stringResource(R.string.tx_network_folders)) {
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            item {PlayerAction(uiResources.getString(R.string.ui_add_network_folder)){edit=true}}
            items(accounts,key={it.id}) {a->PlayerAction(a.label){browse=a}}
        }
        error?.let {app.ember.player.PlayerErrorText(it)}
        if(readError)app.ember.player.SavedListRecoveryControls(R.string.ui_reset_saved_network_folders,R.string.ui_reset_saved_network_folders_warning,store::resetUnreadable){revision++}
        if(edit) {
            var label by remember {mutableStateOf("")};var address by remember {mutableStateOf("")}
            var user by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
            var domain by remember {mutableStateOf("")};var fingerprint by remember {mutableStateOf("")}
            var unencryptedSmb by remember {mutableStateOf(false)}
            var unsignedSmb by remember {mutableStateOf(false)}
            var saving by remember {mutableStateOf(false)};var note by remember {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
            var authentication by remember {mutableStateOf("password")}
            var privateKey by remember {mutableStateOf("")};var keyPassphrase by remember {mutableStateOf("")}
            var importing by remember {mutableStateOf(false)}
            val importKey=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
                app.ember.player.PrivateMedia.endPicker()
                if(uri!=null)scope.launch {
                    importing=true
                    try {
                        privateKey=withContext(Dispatchers.IO){activity.contentResolver.openInputStream(uri)?.use(SftpCredentials::read) ?: error("Unavailable key file")}
                        note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_private_key_imported_it_will_be_encrypted_when_you),false)
                    }catch(e:CancellationException){throw e}
                    catch(_:Exception){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_import_this_key_choose_an_openssh_or))}
                    finally {importing=false}
                }
            }
            val editScope=rememberCoroutineScope()
            var finding by remember {mutableStateOf(false)}
            var servers by remember {mutableStateOf<List<SmbServer>>(emptyList())}
            var shares by remember {mutableStateOf<List<String>>(emptyList())}
            var shareJob by remember {mutableStateOf<Job?>(null)}
            LaunchedEffect(finding) {
                if(finding) {
                    val discovery=SmbDiscovery(activity,{servers=it},{failed->finding=false;note=app.ember.player.PlayerFeedback(if(failed)uiResources.getString(R.string.ui_discovery_unavailable_enter_a_server_address_manually) else uiResources.getString(R.string.ui_discovery_finished_servers_must_advertise_smb_on_your_local),failed)})
                    try {awaitCancellation()} finally {discovery.close()}
                }
            }
            DisposableEffect(Unit) {onDispose {shareJob?.cancel()}}
            LaunchedEffect(address,user,password,domain,unencryptedSmb,unsignedSmb) {shareJob?.cancel();shares=emptyList();note=null}
            TvDialog({if(!saving)edit=false},width=540.dp,resizeForKeyboard=true) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f,false),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    item {DialogHeading(uiResources.getString(R.string.ui_add_network_folder))}
                    item {Text(stringResource(R.string.tx_smb3_encrypted_smb_server_share_sftp),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)}
                    item {NetworkField(uiResources.getString(R.string.ui_name),label){label=it}}
                    item {NetworkField(uiResources.getString(R.string.ui_address),address){address=it}}
                    item {ButtonRow(spacing=8.dp) {
                        PlayerAction(if(finding)uiResources.getString(R.string.ui_stop_discovery) else uiResources.getString(R.string.ui_find_smb_servers)) {if(!finding)servers=emptyList();note=null;finding=!finding}
                        PlayerAction(if(shareJob?.isActive==true)uiResources.getString(R.string.ui_cancel_share_browsing) else uiResources.getString(R.string.ui_browse_smb_shares)) {
                            if(shareJob?.isActive==true){shareJob?.cancel();shareJob=null;note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_share_browsing_canceled),false)}
                            else {
                                val endpoint=runCatching {NetworkLocation.parse(address,allowSmbRoot=true).also {require(it.scheme=="smb")}}.getOrNull()
                                if(endpoint==null)note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_enter_an_smb_server_address_first_for_example_smb))
                                else {
                                    val account=NetworkAccount(label="",address=address,username=user,password=password,domain=domain,allowUnencryptedSmb=unencryptedSmb,allowUnsignedSmb=unsignedSmb)
                                    shareJob=editScope.launch {
                                        note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_opening_shares),false);shares=emptyList()
                                        try {
                                            val names=withTimeout(20_000) {runInterruptible(Dispatchers.IO) {NetworkFiles.smbShares(account)}}
                                            ensureActive();shares=names;note=app.ember.player.PlayerFeedback(if(names.isEmpty())uiResources.getString(R.string.ui_no_visible_disk_shares_enter_the_share_path_manually) else uiResources.getString(R.string.ui_select_a_share_below),false)
                                        } catch(_:TimeoutCancellationException){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_share_browsing_timed_out_check_the_server_or_enter))}
                                          catch(e:CancellationException){throw e}
                                          catch(_:Exception){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_browse_shares_check_credentials_smb_encryption_and))}
                                    }
                                }
                            }
                        }
                    }}
                    items(servers,key={"server:"+it.address}) {server->PlayerAction(server.name){finding=false;address=server.address;if(label.isBlank())label=server.name}}
                    items(shares,key={"share:"+it}) {share->PlayerAction(share){address=NetworkLocation.parse(address,allowSmbRoot=true).smbShare(share);if(label.isBlank())label=share;shares=emptyList()}}
                    item {NetworkField(uiResources.getString(R.string.ui_username),user){user=it}}
                    val sftp=address.trim().startsWith("sftp://")
                    if(sftp)item {ButtonRow {
                        Chip(uiResources.getString(R.string.ui_password),selected=authentication=="password"){authentication="password"}
                        Chip(uiResources.getString(R.string.ui_private_key),selected=authentication=="key"){authentication="key"}
                    }}
                    if(!sftp||authentication=="password")item {NetworkField(uiResources.getString(R.string.ui_password),password,true){password=it}}
                    if(sftp&&authentication=="key") {
                        item {PlayerAction(if(importing)uiResources.getString(R.string.ui_importing_key) else if(privateKey.isEmpty())uiResources.getString(R.string.ui_import_private_key) else uiResources.getString(R.string.ui_replace_private_key)) {
                            if(!importing)runCatching {
                                if(app.ember.player.PrivateMedia.enabled&&app.ember.player.PrivateMedia.unlocked.value)app.ember.player.PrivateMedia.beginPicker()
                                importKey.launch(arrayOf("*/*"))
                            }.onFailure {app.ember.player.PrivateMedia.endPicker();note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_no_file_picker_is_available))}
                        }}
                        item {NetworkField(uiResources.getString(R.string.ui_private_key_passphrase_optional),keyPassphrase,true){keyPassphrase=it}}
                    }
                    item {NetworkField(uiResources.getString(R.string.ui_smb_domain_optional),domain){domain=it}}
                    item {NetworkField(uiResources.getString(R.string.ui_sftp_verified_sha256_fingerprint_optional),fingerprint){fingerprint=it}}
                    if(sftp)item {Text(uiResources.getString(R.string.ui_leave_the_fingerprint_empty_to_verify_and_confirm_the),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)}
                    if(address.trim().startsWith("smb://",ignoreCase=true))item {
                        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Chip(uiResources.getString(R.string.ui_smb_guest_older),selected=unsignedSmb){unsignedSmb=!unsignedSmb}
                            if(unsignedSmb)Text(uiResources.getString(R.string.ui_smb_unsigned_help),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
                            Chip(if(unencryptedSmb)uiResources.getString(R.string.ui_smb_encryption_not_required) else uiResources.getString(R.string.ui_smb_encryption_required),selected=unencryptedSmb) {unencryptedSmb=!unencryptedSmb}
                            if(unencryptedSmb)Text(stringResource(R.string.tx_for_older_nas_devices_without_smb3),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                    note?.let {item {app.ember.player.PlayerFeedbackText(it)}}
                    item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        app.ember.player.PlayerAction(if(saving)uiResources.getString(R.string.ui_saving) else uiResources.getString(R.string.ui_save),primary=true,enabled=!saving) {if(!saving&&!importing)scope.launch {
                            note=null
                            val pin=NetworkLocation.normalizeFingerprint(fingerprint)
                            if(sftp&&fingerprint.isNotBlank()&&pin==null){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_enter_a_valid_sha256_host_key_fingerprint_verified_with));return@launch}
                            val valid=runCatching {val l=NetworkLocation.parse(address);require(label.isNotBlank());require(user.none {it.isISOControl()});NetworkAccount(label=label.trim().take(80),address=address.trim(),username=user,password=if(sftp&&authentication=="key")"" else password,domain=domain,fingerprint=pin ?: "",allowUnencryptedSmb=unencryptedSmb&&l.scheme=="smb",allowUnsignedSmb=unsignedSmb&&l.scheme=="smb",authentication=if(sftp)authentication else "password",privateKey=if(sftp&&authentication=="key")privateKey else "",keyPassphrase=if(sftp&&authentication=="key")keyPassphrase else "")}.getOrNull()
                            if(valid==null){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_check_the_name_address_and_sftp_host_fingerprint));return@launch}
                            saving=true
                            if(sftp&&authentication=="key") {
                                val validKey=withContext(Dispatchers.IO){runCatching {val ssh=com.jcraft.jsch.JSch();try {SftpCredentials.install(ssh,valid)}finally {ssh.removeAllIdentity()}}.isSuccess}
                                if(!validKey){saving=false;note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_unlock_this_private_key_check_its_format));return@launch}
                            }
                            val updated=withContext(Dispatchers.IO){store.add(valid)}
                            saving=false
                            if(updated!=null){accounts=updated;edit=false}else note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_encrypted_storage_is_unavailable_the_folder_was_not_saved))
                        }}
                        app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel)){if(!saving)edit=false}
                    }}
                }
            }
        }
        browse?.let {account ->
            var path by remember(account.id) {mutableStateOf(if(activity.intent.getStringExtra("network.account")==account.id && activity.intent.getStringExtra("network.profile")==profile)activity.intent.getStringExtra("network.path").orEmpty() else "")};var entries by remember {mutableStateOf<List<NetworkEntry>>(emptyList())}
            var selected by remember {mutableStateOf<Set<String>>(emptySet())};var busy by remember {mutableStateOf(true)}
            var note by remember {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
            var testJob by remember {mutableStateOf<Job?>(null)}
            var probeNote by remember(account.id,path) {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
            DisposableEffect(account.id,path) {onDispose {testJob?.cancel()}}
            var offset by remember(account.id,path) {mutableIntStateOf(0)}
            var next by remember(account.id,path) {mutableStateOf<Int?>(null)}
            var reload by remember {mutableIntStateOf(0)}
            var hostKey by remember(account.id) {mutableStateOf<String?>(null)}
            var trusting by remember(account.id) {mutableStateOf(false)}
            var removing by remember(account.id) {mutableStateOf(false)}
            LaunchedEffect(account.id,path,offset,reload) {
                busy=true;note=null;entries=emptyList();selected=emptySet()
                try {val context=currentCoroutineContext()
                    val page=withContext(Dispatchers.IO){NetworkFiles.listPage(account,path,offset){context.ensureActive()}}
                    ensureActive();entries=page.entries;next=page.next}
                catch(e:CancellationException){throw e}
                catch(_:ListingLimitException){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_this_directory_listing_is_too_large_open_a_smaller))}
                catch(e:SftpHostKeyConfirmationException){hostKey=e.fingerprint}
                catch(e:SftpHostKeyMismatchException){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_the_sftp_server_host_key_does_not_match_the))}
                catch(_:Exception){note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_open_this_folder_check_its_address_credentials))}
                finally {busy=false}
            }
            hostKey?.let {fingerprint->TvDialog({if(!trusting){hostKey=null;browse=null}},width=560.dp,scrollContent=true) {
                DialogHeading(uiResources.getString(R.string.ui_verify_sftp_server))
                val server=NetworkLocation.parse(account.address)
                Text("${server.host}:${server.port}")
                androidx.compose.foundation.text.selection.SelectionContainer {Text(fingerprint)}
                Text(uiResources.getString(R.string.ui_verify_this_fingerprint_with_the_server_owner_through_another),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
                note?.let {app.ember.player.PlayerFeedbackText(it)}
                ButtonRow {
                    app.ember.player.PlayerAction(if(trusting)uiResources.getString(R.string.ui_saving) else uiResources.getString(R.string.ui_trust_this_key),primary=true,enabled=!trusting) {if(!trusting)scope.launch {
                        trusting=true
                        val updated=withContext(Dispatchers.IO){store.trust(account,fingerprint)}
                        trusting=false
                        if(updated!=null){accounts=updated;browse=updated.first {it.id==account.id};hostKey=null;reload++}
                        else note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_save_the_trusted_key_in_encrypted_storage))
                    }}
                    app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel)){if(!trusting){hostKey=null;browse=null}}
                }
            }}
            fun play(all:Boolean) {
                val chosen=entries.filter {!it.directory&&it.size>0&&(if(all)MediaFileTypes.playable(it) else it.name in selected)}
                if(chosen.isEmpty())return
                var cleanup:MediaRelay?=null
                try {
                    val relay=MediaRelay().also {cleanup=it}
                    val items=chosen.map {e->
                        val relative=NetworkLocation.child(path,e.name)
                        val identity="network:"+java.security.MessageDigest.getInstance("SHA-256").digest((account.id+":"+relative).toByteArray()).toHex()
                        QueueMedia(relay.register(NetworkFiles.resource(account,relative,e.size),e.name),e.name,identity,NetworkQueueEntry(account.id,relative,e.size))
                    }
                    MediaFiles.launch(activity,items,listOf(relay))
                } catch(e:SftpHostKeyMismatchException) {cleanup?.close();note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_the_sftp_server_host_key_does_not_match_the))}
                  catch(_:Exception) {cleanup?.close();note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_start_file_playback))}
            }
            if(hostKey==null)TvDialog({browse=null},width=620.dp,scrollContent=true) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    DialogHeading(account.label,Modifier.weight(1f))
                    app.ember.tv.ui.DialogClose({browse=null})
                }
                Text(if(path.isEmpty())uiResources.getString(R.string.ui_root_folder) else path,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
                ButtonRow(spacing=8.dp) {
                    if(path.isNotEmpty())PlayerAction(uiResources.getString(R.string.ui_up)){path=path.substringBeforeLast('/',"")}
                    PlayerAction(uiResources.getString(R.string.ui_play_selected ,selected.size.toString())){play(false)}
                    PlayerAction(uiResources.getString(R.string.ui_play_media_on_page)){play(true)}
                }
                ButtonRow(spacing=8.dp) {
                    if(offset>0)PlayerAction(uiResources.getString(R.string.ui_previous_page)){offset=(offset-200).coerceAtLeast(0)}
                    next?.let {at->PlayerAction(uiResources.getString(R.string.ui_next_page)){if(!busy)offset=at}}
                    PlayerAction(uiResources.getString(R.string.ui_refresh)){if(!busy)reload++}
                }
                ButtonRow(spacing=8.dp) {
                    PlayerAction(if(testJob?.isActive==true)uiResources.getString(R.string.ui_cancel_test) else uiResources.getString(R.string.ui_test_connection)) {
                        if(testJob?.isActive==true){testJob?.cancel();probeNote=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_test_canceled),false)}
                        else {
                            val sample=entries.firstOrNull {MediaFileTypes.playable(it)}
                            if(sample==null)probeNote=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_open_a_folder_containing_a_media_file_to_test))
                            else testJob=scope.launch {
                                probeNote=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_reading_up_to_8_mib_no_file_is_saved),false)
                                try {
                                    val result=ConnectionProbe.run(NetworkFiles.resource(account,NetworkLocation.child(path,sample.name),sample.size))
                                    probeNote=app.ember.player.PlayerFeedback(String.format(java.util.Locale.US,uiResources.getString(R.string.ui_1f_mbps_average_1f_mib_sampled_first_data_d),result.averageMbps,result.bytes/(1024.0*1024),result.firstByteMs)+
                                        (result.slowestMbps?.let {String.format(java.util.Locale.US,uiResources.getString(R.string.ui_slowest_sample_1f_mbps),it)}?:"")+uiResources.getString(R.string.ui_short_sample_actual_playback_may_vary),false)
                                } catch(_:TimeoutCancellationException){probeNote=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_test_timed_out_check_the_connection_and_try_again))}
                                  catch(e:CancellationException){throw e}
                                  catch(e:SftpHostKeyMismatchException){probeNote=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_the_sftp_server_host_key_does_not_match_the))}
                                  catch(_:Exception){probeNote=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_read_the_sample_check_server_access_and))}
                            }
                        }
                    }
                }
                probeNote?.let {app.ember.player.PlayerFeedbackText(it)}
                if(busy)Text(stringResource(R.string.tx_opening_folder))
                else Text(uiResources.getQuantityString(R.plurals.ui_entries_page,entries.size,entries.size,offset/200+1),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
                note?.let {app.ember.player.PlayerFeedbackText(it)}
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    items(entries,key={it.name}) {entry->
                        app.ember.player.NetworkMediaRow(account,path,entry,entry.name in selected) {
                            if(entry.directory)path=NetworkLocation.child(path,entry.name)
                            else selected=if(entry.name in selected)selected-entry.name else selected+entry.name
                        }
                    }
                }
                app.ember.player.PlayerAction(uiResources.getString(R.string.ui_remove_saved_folder),destructive=true){removing=true}
                if(removing)app.ember.player.PlayerConfirmationDialog(uiResources.getString(R.string.ui_remove_network_folder),uiResources.getString(R.string.ui_remove_its_saved_credentials_and_any_private_key_from ,account.label),uiResources.getString(R.string.ui_remove_folder),{removing=false}) {
                  removing=false;scope.launch {
                    val updated=withContext(Dispatchers.IO){store.remove(account.id)}
                    if(updated!=null){accounts=updated;browse=null}
                    else note=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_update_encrypted_folder_storage))
                  }
                }

            }
        }
    }
}
@Composable
private fun NetworkField(label:String,value:String,secret:Boolean=false,change:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        TvTextField(value,change,Modifier.fillMaxWidth(),placeholder=label,password=secret)
    }
}
