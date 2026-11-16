package app.ember.tv.player

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
    val scope=rememberCoroutineScope()
    val manager=remember {activity.getSystemService(UsbManager::class.java)}
    var devices by remember {mutableStateOf<List<UsbMassStorageDevice>>(emptyList())}
    var session by remember {mutableStateOf<DirectUsbSession?>(null)}
    var pending by remember {mutableStateOf<UsbMassStorageDevice?>(null)}
    var directory by remember {mutableStateOf<UsbFile?>(null)}
    var files by remember {mutableStateOf<List<UsbFile>>(emptyList())}
    var offset by remember {mutableIntStateOf(0)}
    var expanded by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    val private=app.ember.player.PrivateMedia.enabled
    fun operation(action:suspend ()->Unit) {
        if(busy)return
        busy=true;notice=null
        scope.launch {try {action()}catch(e:CancellationException){throw e}catch(_:Exception){notice="USB access failed. Check the drive and permission. FAT32 is supported; use Android's mounted-drive browser for other formats."}finally{busy=false}}
    }
    fun connect(device:UsbMassStorageDevice)=operation {
        var opened:DirectUsbSession?=null
        try {
            val value=withContext(Dispatchers.IO){DirectUsbSession.open(activity,device).also {opened=it}}
            currentCoroutineContext().ensureActive()
            if(!expanded||app.ember.player.PrivateMedia.enabled)return@operation
            session=value;opened=null;directory=null;files=emptyList();offset=0
        } finally {opened?.close()}
    }
    val action=activity.packageName+".USB_BROWSER_PERMISSION"
    DisposableEffect(Unit) {
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent) {
            val device=pending ?: return;pending=null
            if(manager.hasPermission(device.usbDevice))connect(device) else notice="USB permission was not granted."
        }}
        ContextCompat.registerReceiver(activity,receiver,IntentFilter(action),ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {activity.unregisterReceiver(receiver)}
    }
    DisposableEffect(session) {val opened=session;onDispose {opened?.close()}}
    LaunchedEffect(refresh,private) {if(private){session=null;directory=null;files=emptyList();devices=emptyList()}}
    val opened=session
    if(opened!=null) {
        val connected by opened.connected.collectAsState()
        LaunchedEffect(connected){if(!connected){notice="USB drive disconnected";session=null;directory=null;files=emptyList()}}
    }
    LaunchedEffect(session,directory) {
        val current=session ?: return@LaunchedEffect
        val folder=directory ?: return@LaunchedEffect
        try {files=withContext(Dispatchers.IO){current.files(folder)};offset=0}
        catch(e:CancellationException){throw e}
        catch(_:Exception){files=emptyList();notice="This folder is unavailable."}
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
            currentCoroutineContext().ensureActive();check(expanded&&!app.ember.player.PrivateMedia.enabled)
            MediaFiles.launch(activity,queue,listOf(relay,lease))
        } catch(e:Exception){relay.close();lease.close();throw e}
    }
    Text("Direct USB",fontSize=20.sp)
    Chip(if(expanded)"Hide USB browser" else "Browse a USB drive directly"){expanded=!expanded;if(!expanded){session=null;directory=null;files=emptyList()}}
    if(!expanded)return
    if(private){Text("Use the mounted-drive or saved-folder browser while private media is enabled, so protected files keep their Android document identities.",color=Mute,fontSize=12.sp);return}
    Text("Read-only FAT32 browsing, including files Android has not indexed. Drive access is requested only when you select one.",color=Mute,fontSize=12.sp)
    if(session==null) {
        Chip(if(busy)"Checking drives…" else "Find USB drives"){operation {devices=withContext(Dispatchers.IO){UsbMassStorageDevice.getMassStorageDevices(activity).toList()};if(devices.isEmpty())notice="No supported USB mass-storage drive found."}}
        devices.forEach {device->Chip(device.usbDevice.productName ?: "USB drive") {
            if(!busy) {
                if(manager.hasPermission(device.usbDevice))connect(device)
                else {pending=device;manager.requestPermission(device.usbDevice,PendingIntent.getBroadcast(activity,device.usbDevice.deviceId,Intent(action).setPackage(activity.packageName),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))}
            }
        }}
    } else if(directory==null) {
        session!!.roots.forEachIndexed {index,root->Chip("Partition ${index+1}"){directory=root}}
        Chip("Close browser"){session=null;files=emptyList()}
    } else {
        Text(directory!!.name,maxLines=1,fontSize=14.sp)
        ButtonRow {
            Chip("Back"){directory=directory?.parent;files=emptyList();offset=0}
            if(offset>0)Chip("Previous page"){offset=(offset-100).coerceAtLeast(0)}
            if(offset+100<files.size)Chip("Next page"){offset+=100}
            Chip("Play folder"){play(files.filter {!it.isDirectory&&it.length>0})}
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(files.drop(offset).take(100),key={it.absolutePath}) {file->Chip((if(file.isDirectory)"▸ " else "")+file.name,maxLines=1,modifier=Modifier.fillMaxWidth()) {
                if(!busy){if(file.isDirectory){directory=file;files=emptyList()}else play(listOf(file))}
            }}
        }
    }
    notice?.let {Text(it,color=Mute,fontSize=12.sp)}
}

internal object DirectUsbIdentity {
    const val PREFIX="usb-direct:"
    fun key(device:String,path:String)=PREFIX+java.security.MessageDigest.getInstance("SHA-256").digest((device+"\u0000"+path).toByteArray()).joinToString(""){"%02x".format(it)}
}
