package app.ember.tv.player

import app.ember.tv.R

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import app.ember.tv.ui.*
import kotlinx.coroutines.*
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.UsbFile

@Composable
internal fun DirectUsbPanel(activity:ComponentActivity,refresh:Int) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scope=rememberCoroutineScope()
    val manager=remember {activity.getSystemService(UsbManager::class.java)}
    var devices by remember {mutableStateOf<List<UsbMassStorageDevice>>(emptyList())}
    var session by remember {mutableStateOf<DirectUsbSession?>(null)}
    var pending by remember {mutableStateOf<UsbMassStorageDevice?>(null)}
    var directory by remember {mutableStateOf<UsbFile?>(null)}
    var files by remember {mutableStateOf<List<UsbFile>>(emptyList())}
    var offset by remember {mutableIntStateOf(0)}
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
    val private=app.ember.player.PrivateMedia.enabled
    fun operation(action:suspend ()->Unit) {
        if(busy)return
        busy=true;notice=null
        scope.launch {try {action()}catch(e:CancellationException){throw e}catch(_:Exception){notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_usb_access_failed_check_the_drive_and_permission_fat32))}finally{busy=false}}
    }
    fun connect(device:UsbMassStorageDevice)=operation {
        var opened:DirectUsbSession?=null
        try {
            val value=withContext(Dispatchers.IO){DirectUsbSession.open(activity,device).also {opened=it}}
            currentCoroutineContext().ensureActive()
            if(app.ember.player.PrivateMedia.enabled)return@operation
            session=value;opened=null;directory=null;files=emptyList();offset=0
        } finally {opened?.close()}
    }
    val action=activity.packageName+".USB_BROWSER_PERMISSION"
    DisposableEffect(Unit) {
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent) {
            val device=pending ?: return;pending=null
            if(manager.hasPermission(device.usbDevice))connect(device) else notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_usb_permission_was_not_granted))
        }}
        ContextCompat.registerReceiver(activity,receiver,IntentFilter(action),ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {activity.unregisterReceiver(receiver)}
    }
    DisposableEffect(session) {val opened=session;onDispose {opened?.close()}}
    LaunchedEffect(refresh,private) {
        if(private){session=null;directory=null;files=emptyList();devices=emptyList()}
        else try {devices=withContext(Dispatchers.IO){UsbMassStorageDevice.getMassStorageDevices(activity).toList()}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_could_not_list_connected_usb_drives))}
    }
    val opened=session
    if(opened!=null) {
        val connected by opened.connected.collectAsState()
        LaunchedEffect(connected){if(!connected){notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_usb_drive_disconnected),false);session=null;directory=null;files=emptyList()}}
    }
    LaunchedEffect(session,directory) {
        val current=session ?: return@LaunchedEffect
        val folder=directory ?: return@LaunchedEffect
        try {files=withContext(Dispatchers.IO){current.files(folder)};offset=0}
        catch(e:CancellationException){throw e}
        catch(_:Exception){files=emptyList();notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_this_folder_is_unavailable))}
    }
    fun play(chosen:List<UsbFile>)=operation {
        val current=session ?: return@operation
        require(chosen.size in 1..MediaQueue.MAX_ITEMS)
        val lease=current.lease();val relay=MediaRelay()
        try {
            val queue=withContext(Dispatchers.IO) {
                val device=current.device.usbDevice
                val serial=runCatching {device.serialNumber}.getOrNull() ?: device.deviceName
                val root="${device.vendorId}:${device.productId}:$serial"
                chosen.map {file->QueueMedia(relay.register(current.resource(file),file.name),file.name,DirectUsbIdentity.key(root,file.absolutePath))}
            }
            currentCoroutineContext().ensureActive();check(!app.ember.player.PrivateMedia.enabled)
            MediaFiles.launch(activity,queue,listOf(relay,lease))
        } catch(e:Exception){relay.close();lease.close();throw e}
    }
    app.ember.player.PlayerSection(uiResources.getString(R.string.ui_direct_usb)) {
        if(private){Text(uiResources.getString(R.string.ui_use_the_mounted_drive_or_saved_folder_browser_while),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall);return@PlayerSection}
        Text(uiResources.getString(R.string.ui_read_only_fat32_browsing_including_files_android_has_not),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        if(session==null) {
            Chip(if(busy)uiResources.getString(R.string.ui_checking_drives) else uiResources.getString(R.string.ui_refresh_usb_drives)){operation {devices=withContext(Dispatchers.IO){UsbMassStorageDevice.getMassStorageDevices(activity).toList()};if(devices.isEmpty())notice=app.ember.player.PlayerFeedback(uiResources.getString(R.string.ui_no_supported_usb_mass_storage_drive_found),false)}}
            devices.forEach {device->Chip(device.usbDevice.productName ?: uiResources.getString(R.string.ui_usb_drive)) {
                if(!busy) {
                    if(manager.hasPermission(device.usbDevice))connect(device)
                    else {pending=device;manager.requestPermission(device.usbDevice,PendingIntent.getBroadcast(activity,device.usbDevice.deviceId,Intent(action).setPackage(activity.packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))}
                }
            }}
        } else if(directory==null) {
            session!!.roots.forEachIndexed {index,root->Chip(uiResources.getString(R.string.ui_partition ,(index+1).toString())){directory=root}}
            Chip(uiResources.getString(R.string.ui_close_browser)){session=null;files=emptyList()}
        } else {
            Text(directory!!.name,maxLines=1,style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
            ButtonRow {
                Chip(uiResources.getString(R.string.ui_back)){directory=directory?.parent;files=emptyList();offset=0}
                if(offset>0)Chip(uiResources.getString(R.string.ui_previous_page)){offset=(offset-100).coerceAtLeast(0)}
                if(offset+100<files.size)Chip(uiResources.getString(R.string.ui_next_page)){offset+=100}
                Chip(uiResources.getString(R.string.ui_play_folder)){play(files.filter {!it.isDirectory&&it.length>0})}
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(files.drop(offset).take(100),key={it.absolutePath}) {file->app.ember.player.PlayerFileRow(file.name,file.isDirectory) {
                    if(!busy){if(file.isDirectory){directory=file;files=emptyList()}else play(listOf(file))}
                }}
            }
        }
        notice?.let {app.ember.player.PlayerFeedbackText(it)}
    }
}

internal object DirectUsbIdentity {
    const val PREFIX="usb-direct:"
    fun key(device:String,path:String)=PREFIX+java.security.MessageDigest.getInstance("SHA-256").digest((device+"\u0000"+path).toByteArray()).joinToString(""){"%02x".format(it)}
}
