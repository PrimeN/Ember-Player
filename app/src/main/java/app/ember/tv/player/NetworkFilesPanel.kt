package app.ember.tv.player

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
import app.ember.player.storage.TokenStore
import app.ember.tv.ui.TvDialog
import app.ember.tv.ui.DialogHeading
import app.ember.tv.ui.TvTextField
import app.ember.tv.ui.Chip
import app.ember.tv.ui.ButtonRow
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
internal fun NetworkFilesPanel(activity:MediaFilesActivity,refresh:Int=0) {
    val scope=rememberCoroutineScope();val json=remember {Json {ignoreUnknownKeys=true}}
    val profile=remember {Prefs(activity).currentProfile}
    val key=remember {"media.network."+profile}
    var accounts by remember {mutableStateOf<List<NetworkAccount>>(emptyList())}
    var edit by remember {mutableStateOf(false)}
    var browse by remember {mutableStateOf<NetworkAccount?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var handledRequest by remember {mutableStateOf(false)}
    LaunchedEffect(key,refresh) {
        accounts=withContext(Dispatchers.IO){runCatching {json.decodeFromString<List<NetworkAccount>>(TokenStore(activity).get(key)?:"[]")}.getOrDefault(emptyList())}
        if(!handledRequest && activity.intent.getStringExtra("network.profile")==profile) {
            handledRequest=true
            val id=activity.intent.getStringExtra("network.account")
            if(id!=null){browse=accounts.firstOrNull {it.id==id};if(browse==null)error="This saved folder was removed. Add it again to use this shortcut."}
        }
    }
    Text(stringResource(R.string.tx_network_folders),fontSize=20.sp)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Chip("Add network folder"){edit=true}}
        items(accounts,key={it.id}) {a->Chip(a.label){browse=a}}
    }
    error?.let {Text(it)}
    if(edit) {
        var label by remember {mutableStateOf("")};var address by remember {mutableStateOf("")}
        var user by remember {mutableStateOf("")};var password by remember {mutableStateOf("")}
        var domain by remember {mutableStateOf("")};var fingerprint by remember {mutableStateOf("")}
        var unencryptedSmb by remember {mutableStateOf(false)}
        var saving by remember {mutableStateOf(false)};var note by remember {mutableStateOf<String?>(null)}
        var authentication by remember {mutableStateOf("password")}
        var privateKey by remember {mutableStateOf("")};var keyPassphrase by remember {mutableStateOf("")}
        var importing by remember {mutableStateOf(false)}
        val importKey=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
            app.ember.player.PrivateMedia.endPicker()
            if(uri!=null)scope.launch {
                importing=true
                try {
                    privateKey=withContext(Dispatchers.IO){activity.contentResolver.openInputStream(uri)?.use(SftpCredentials::read) ?: error("Unavailable key file")}
                    note="Private key imported. It will be encrypted when you save the folder."
                }catch(e:CancellationException){throw e}
                catch(_:Exception){note="Could not import this key. Choose an OpenSSH or PEM private key smaller than 64 KiB."}
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
                val discovery=SmbDiscovery(activity,{servers=it},{failed->finding=false;note=if(failed)"Discovery unavailable. Enter a server address manually." else "Discovery finished. Servers must advertise SMB on your local network; you can also enter an address manually."})
                try {awaitCancellation()} finally {discovery.close()}
            }
        }
        DisposableEffect(Unit) {onDispose {shareJob?.cancel()}}
        LaunchedEffect(address,user,password,domain,unencryptedSmb) {shareJob?.cancel();shares=emptyList();note=null}
        TvDialog({if(!saving)edit=false},width=540.dp,resizeForKeyboard=true) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f,false),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {DialogHeading("Add network folder")}
                item {Text(stringResource(R.string.tx_smb3_encrypted_smb_server_share_sftp),fontSize=13.sp)}
                item {NetworkField("Name",label){label=it}}
                item {NetworkField("Address",address){address=it}}
                item {ButtonRow(spacing=8.dp) {
                    Chip(if(finding)"Stop discovery" else "Find SMB servers") {if(!finding)servers=emptyList();note=null;finding=!finding}
                    Chip(if(shareJob?.isActive==true)"Cancel share browsing" else "Browse SMB shares") {
                        if(shareJob?.isActive==true){shareJob?.cancel();shareJob=null;note="Share browsing cancelled."}
                        else {
                            val endpoint=runCatching {NetworkLocation.parse(address,allowSmbRoot=true).also {require(it.scheme=="smb")}}.getOrNull()
                            if(endpoint==null)note="Enter an SMB server address first, for example smb://server."
                            else {
                                val account=NetworkAccount(label="",address=address,username=user,password=password,domain=domain,allowUnencryptedSmb=unencryptedSmb)
                                shareJob=editScope.launch {
                                    note="Opening shares…";shares=emptyList()
                                    try {
                                        val names=withTimeout(20_000) {runInterruptible(Dispatchers.IO) {NetworkFiles.smbShares(account)}}
                                        ensureActive();shares=names;note=if(names.isEmpty())"No visible disk shares. Enter the share path manually if it is hidden." else "Select a share below."
                                    } catch(_:TimeoutCancellationException){note="Share browsing timed out. Check the server or enter its share path manually."}
                                      catch(e:CancellationException){throw e}
                                      catch(_:Exception){note="Could not browse shares. Check credentials, SMB encryption and server permissions, or enter the share path manually."}
                                }
                            }
                        }
                    }
                }}
                items(servers,key={"server:"+it.address}) {server->Chip(server.name){finding=false;address=server.address;if(label.isBlank())label=server.name}}
                items(shares,key={"share:"+it}) {share->Chip(share){address=NetworkLocation.parse(address,allowSmbRoot=true).smbShare(share);if(label.isBlank())label=share;shares=emptyList()}}
                item {NetworkField("Username",user){user=it}}
                val sftp=address.trim().startsWith("sftp://")
                if(sftp)item {ButtonRow {
                    Chip("Password",selected=authentication=="password"){authentication="password"}
                    Chip("Private key",selected=authentication=="key"){authentication="key"}
                }}
                if(!sftp||authentication=="password")item {NetworkField("Password",password,true){password=it}}
                if(sftp&&authentication=="key") {
                    item {Chip(if(importing)"Importing key…" else if(privateKey.isEmpty())"Import private key" else "Replace private key") {
                        if(!importing)runCatching {
                            if(app.ember.player.PrivateMedia.enabled&&app.ember.player.PrivateMedia.unlocked.value)app.ember.player.PrivateMedia.beginPicker()
                            importKey.launch(arrayOf("*/*"))
                        }.onFailure {app.ember.player.PrivateMedia.endPicker();note="No file picker is available."}
                    }}
                    item {NetworkField("Private key passphrase (optional)",keyPassphrase,true){keyPassphrase=it}}
                }
                item {NetworkField("SMB domain (optional)",domain){domain=it}}
                item {NetworkField("SFTP verified SHA256 fingerprint (optional)",fingerprint){fingerprint=it}}
                if(sftp)item {Text("Leave the fingerprint empty to verify and confirm the server's key on your first connection.",fontSize=13.sp)}
                if(address.trim().startsWith("smb://",ignoreCase=true))item {
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Chip(if(unencryptedSmb)"SMB encryption: not required" else "SMB encryption: required",selected=unencryptedSmb) {unencryptedSmb=!unencryptedSmb}
                        if(unencryptedSmb)Text(stringResource(R.string.tx_for_older_nas_devices_without_smb3),fontSize=13.sp)
                    }
                }
                note?.let {item {Text(it)}}
                item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Chip(if(saving)"Saving…" else "Save") {if(!saving&&!importing)scope.launch {
                        note=null
                        val pin=NetworkLocation.normalizeFingerprint(fingerprint)
                        if(sftp&&fingerprint.isNotBlank()&&pin==null){note="Enter a valid SHA256 host key fingerprint verified with the server owner.";return@launch}
                        val valid=runCatching {val l=NetworkLocation.parse(address);require(label.isNotBlank());require(user.none {it.isISOControl()});NetworkAccount(label=label.trim().take(80),address=address.trim(),username=user,password=if(sftp&&authentication=="key")"" else password,domain=domain,fingerprint=pin ?: "",allowUnencryptedSmb=unencryptedSmb&&l.scheme=="smb",authentication=if(sftp)authentication else "password",privateKey=if(sftp&&authentication=="key")privateKey else "",keyPassphrase=if(sftp&&authentication=="key")keyPassphrase else "")}.getOrNull()
                        if(valid==null){note="Check the name, address and SFTP host fingerprint.";return@launch}
                        saving=true
                        if(sftp&&authentication=="key") {
                            val validKey=withContext(Dispatchers.IO){runCatching {val ssh=com.jcraft.jsch.JSch();try {SftpCredentials.install(ssh,valid)}finally {ssh.removeAllIdentity()}}.isSuccess}
                            if(!validKey){saving=false;note="Could not unlock this private key. Check its format and passphrase.";return@launch}
                        }
                        val updated=accounts+valid
                        val saved=withContext(Dispatchers.IO){TokenStore(activity).putEncrypted(key,json.encodeToString(updated))}
                        saving=false
                        if(saved){accounts=updated;edit=false}else note="Encrypted storage is unavailable. The folder was not saved."
                    }}
                    Chip("Cancel"){if(!saving)edit=false}
                }}
            }
        }
    }
    browse?.let {account ->
        var path by remember(account.id) {mutableStateOf(if(activity.intent.getStringExtra("network.account")==account.id && activity.intent.getStringExtra("network.profile")==profile)activity.intent.getStringExtra("network.path").orEmpty() else "")};var entries by remember {mutableStateOf<List<NetworkEntry>>(emptyList())}
        var selected by remember {mutableStateOf<Set<String>>(emptySet())};var busy by remember {mutableStateOf(true)}
        var note by remember {mutableStateOf<String?>(null)}
        var testJob by remember {mutableStateOf<Job?>(null)}
        var probeNote by remember(account.id,path) {mutableStateOf<String?>(null)}
        DisposableEffect(account.id,path) {onDispose {testJob?.cancel()}}
        var offset by remember(account.id,path) {mutableIntStateOf(0)}
        var next by remember(account.id,path) {mutableStateOf<Int?>(null)}
        var reload by remember {mutableIntStateOf(0)}
        var hostKey by remember(account.id) {mutableStateOf<String?>(null)}
        var trusting by remember(account.id) {mutableStateOf(false)}
        LaunchedEffect(account.id,path,offset,reload) {
            busy=true;note=null;entries=emptyList();selected=emptySet()
            try {val context=currentCoroutineContext()
                val page=withContext(Dispatchers.IO){NetworkFiles.listPage(account,path,offset){context.ensureActive()}}
                ensureActive();entries=page.entries;next=page.next}
            catch(e:CancellationException){throw e}
            catch(_:ListingLimitException){note="This directory listing is too large. Open a smaller folder."}
            catch(e:SftpHostKeyConfirmationException){hostKey=e.fingerprint}
            catch(e:SftpHostKeyMismatchException){note=e.message}
            catch(_:Exception){note="Could not open this folder. Check its address, credentials, permissions and host key."}
            finally {busy=false}
        }
        hostKey?.let {fingerprint->TvDialog({if(!trusting){hostKey=null;browse=null}},width=560.dp) {
            DialogHeading("Verify SFTP server")
            val server=NetworkLocation.parse(account.address)
            Text("${server.host}:${server.port}")
            androidx.compose.foundation.text.selection.SelectionContainer {Text(fingerprint)}
            Text("Verify this fingerprint with the server owner through another trusted channel. Trusting an unverified key could connect you to the wrong server. A changed key will be rejected.",fontSize=13.sp)
            note?.let {Text(it)}
            ButtonRow {
                Chip(if(trusting)"Saving…" else "Trust this key") {if(!trusting)scope.launch {
                    trusting=true
                    val current=accounts.firstOrNull {it.id==account.id}
                    if(current==null||current.fingerprint.isNotBlank()){trusting=false;hostKey=null;note="The saved folder changed. Reopen it before verifying its key.";return@launch}
                    val trusted=current.copy(fingerprint=fingerprint)
                    val updated=accounts.map {if(it.id==trusted.id)trusted else it}
                    val saved=withContext(Dispatchers.IO){TokenStore(activity).putEncrypted(key,json.encodeToString(updated))}
                    trusting=false
                    if(saved){accounts=updated;browse=trusted;hostKey=null;reload++}else note="Could not save the trusted key in encrypted storage."
                }}
                Chip("Cancel"){if(!trusting){hostKey=null;browse=null}}
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
            } catch(e:SftpHostKeyMismatchException) {cleanup?.close();note=e.message}
              catch(_:Exception) {cleanup?.close();note="Could not start file playback."}
        }
        if(hostKey==null)TvDialog({browse=null},width=620.dp) {
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                DialogHeading(account.label,Modifier.weight(1f))
                app.ember.tv.ui.DialogClose({browse=null})
            }
            Text(if(path.isEmpty())"Root folder" else path,fontSize=13.sp)
            ButtonRow(spacing=8.dp) {
                if(path.isNotEmpty())Chip("Up"){path=path.substringBeforeLast('/',"")}
                Chip("Play selected (${selected.size})"){play(false)}
                Chip("Play media on page"){play(true)}
            }
            ButtonRow(spacing=8.dp) {
                if(offset>0)Chip("Previous page"){offset=(offset-200).coerceAtLeast(0)}
                next?.let {at->Chip("Next page"){if(!busy)offset=at}}
                Chip("Refresh"){if(!busy)reload++}
            }
            ButtonRow(spacing=8.dp) {
                Chip(if(testJob?.isActive==true)"Cancel test" else "Test connection") {
                    if(testJob?.isActive==true){testJob?.cancel();probeNote="Test cancelled."}
                    else {
                        val sample=entries.firstOrNull {MediaFileTypes.playable(it)}
                        if(sample==null)probeNote="Open a folder containing a media file to test its connection."
                        else testJob=scope.launch {
                            probeNote="Reading up to 8 MiB. No file is saved…"
                            try {
                                val result=ConnectionProbe.run(NetworkFiles.resource(account,NetworkLocation.child(path,sample.name),sample.size))
                                probeNote=String.format(java.util.Locale.US,"%.1f Mbps average · %.1f MiB sampled · first data %d ms",result.averageMbps,result.bytes/(1024.0*1024),result.firstByteMs)+
                                    (result.slowestMbps?.let {String.format(java.util.Locale.US," · slowest sample %.1f Mbps",it)}?:"")+". Short sample; actual playback may vary."
                            } catch(_:TimeoutCancellationException){probeNote="Test timed out. Check the connection and try again."}
                              catch(e:CancellationException){throw e}
                              catch(e:SftpHostKeyMismatchException){probeNote=e.message}
                              catch(_:Exception){probeNote="Could not read the sample. Check server access and connection."}
                        }
                    }
                }
            }
            probeNote?.let {Text(it,fontSize=12.sp)}
            if(busy)Text(stringResource(R.string.tx_opening_folder))
            else Text(stringResource(R.string.tx_entries_page, entries.size, (offset/200+1)),fontSize=12.sp)
            note?.let {Text(it)}
            LazyColumn(Modifier.weight(1f,false),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                items(entries,key={it.name}) {entry->
                    Chip((if(entry.directory)"Folder · " else if(entry.name in selected)"✓ " else "")+entry.name) {
                        if(entry.directory)path=NetworkLocation.child(path,entry.name)
                        else selected=if(entry.name in selected)selected-entry.name else selected+entry.name
                    }
                }
            }
            Chip("Remove saved folder") {scope.launch {
                val updated=accounts.filterNot {it.id==account.id}
                if(withContext(Dispatchers.IO){TokenStore(activity).putEncrypted(key,json.encodeToString(updated))}){accounts=updated;browse=null}
                else note="Could not update encrypted folder storage."
            }}

        }
    }
}
@Composable
private fun NetworkField(label:String,value:String,secret:Boolean=false,change:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,fontSize=13.sp)
        TvTextField(value,change,Modifier.fillMaxWidth(),placeholder=label,password=secret)
    }
}
