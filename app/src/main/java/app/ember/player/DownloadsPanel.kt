package app.ember.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.player.MediaFiles
import app.ember.tv.player.QueueMedia
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun DownloadsPanel(activity:ComponentActivity) {
    val scope=rememberCoroutineScope()
    var pending by remember {mutableStateOf<String?>(null)}
    val notifications=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        pending?.let {id->runCatching {MediaDownloadService.start(activity,id)}};pending=null
    }
    fun start(id:String) {
        if(android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pending=id;notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
        else MediaDownloadService.start(activity,id)
    }
    var expanded by remember {mutableStateOf(false)}
    var adding by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var entries by remember {mutableStateOf<List<MediaDownload>>(emptyList())}
    var version by remember {mutableIntStateOf(0)}
    Text("Offline downloads",fontSize=20.sp)
    Chip(if(expanded)"Hide downloads" else "Manage downloads"){expanded=!expanded}
    if(!expanded)return
    LaunchedEffect(version) {while(true){entries=withContext(Dispatchers.IO){DownloadStore(activity).read()};delay(1500)}}
    fun action(block:()->Unit){scope.launch {try {withContext(Dispatchers.IO){block()};version++}catch(e:CancellationException){throw e}catch(_:Exception){notice="Could not update this download."}}}
    ButtonRow {Chip("Download media URL"){adding=true};Chip("Pause download"){MediaDownloadService.pause(activity)}}
    Text("Direct media files only. Adaptive playlists and protected streams are not downloaded. Downloads pause if Android stops the task; choose Resume to continue.",fontSize=12.sp,color=Mute)
    entries.filterNot {PrivateMedia.hidden(it.url)||PrivateMedia.hidden(DownloadStore.uri(activity,it).toString())}.forEach {entry->
        Text(entry.name,fontSize=14.sp,maxLines=1)
        Text("${entry.state} · ${entry.bytes/1024/1024} MB"+(if(entry.total>0)" / ${entry.total/1024/1024} MB" else ""),fontSize=12.sp,color=Mute)
        ButtonRow {
            if(entry.state=="complete")Chip("Play"){runCatching {MediaFiles.launch(activity,listOf(QueueMedia(DownloadStore.uri(activity,entry).toString(),entry.name)))}.onFailure {notice="The downloaded file is unavailable."}}
            else Chip("Resume"){runCatching {start(entry.id)}.onFailure {notice="Android could not start this download."}}
            Chip("Remove"){action {DownloadStore(activity).remove(entry.id)}}
        }
    }
    notice?.let {Text(it,fontSize=12.sp,color=Mute)}
    if(adding) {
        var url by remember {mutableStateOf("")}
        var name by remember {mutableStateOf("media.mp4")}
        var error by remember {mutableStateOf<String?>(null)}
        TvDialog({adding=false},width=560.dp,resizeForKeyboard=true) {
            DialogHeading("Download media")
            TvTextField(url,{url=it.take(16384)},Modifier.fillMaxWidth(),placeholder="https://…/video.mp4")
            TvTextField(name,{name=it.take(120)},Modifier.fillMaxWidth(),placeholder="Filename")
            error?.let {Text(it,color=Mute,fontSize=12.sp)}
            ButtonRow {
                Chip("Download"){if(!DownloadStore.supported(url.trim())||PrivateMedia.hidden(url.trim()))error="Enter a direct, non-private media URL."
                    else scope.launch {
                        try {
                            val item=withContext(Dispatchers.IO){DownloadStore(activity).add(url.trim(),name)}
                            start(item.id);adding=false;version++
                        }catch(e:CancellationException){throw e}catch(_:Exception){error="Could not start this download. Check available space and saved downloads."}
                    }
                }
                Chip("Cancel"){adding=false}
            }
        }
    }
}
