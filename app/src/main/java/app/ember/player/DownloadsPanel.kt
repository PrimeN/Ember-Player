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
import androidx.lifecycle.repeatOnLifecycle

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
    var adding by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf<MediaDownload?>(null)}
    var notice by remember {mutableStateOf<String?>(null)}
    var entries by remember {mutableStateOf<List<MediaDownload>>(emptyList())}
    var version by remember {mutableIntStateOf(0)}
    app.ember.player.PlayerSection("Downloads") {
        LaunchedEffect(version) {activity.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED){while(true){entries=withContext(Dispatchers.IO){DownloadStore(activity).read()};delay(1500)}}}
        ButtonRow {Chip("Download media URL"){adding=true};Chip("Pause downloads"){MediaDownloadService.pause(activity)}}
        Text("Direct media files download one at a time; additional requests wait in the queue. Adaptive playlists and protected streams are not downloaded. Pause stops the active download and clears the waiting queue. If Android stops the task, choose Resume to continue.",style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
        entries.filterNot {PrivateMedia.hidden(it.url)||PrivateMedia.hidden(DownloadStore.uri(activity,it).toString())}.forEach {entry->
            DownloadRow(entry,
                {runCatching {MediaFiles.launch(activity,listOf(QueueMedia(DownloadStore.uri(activity,entry).toString(),entry.name)))}.onFailure {notice="The downloaded file is unavailable."}},
                {runCatching {start(entry.id)}.onFailure {notice="Android could not start this download."}},
                {removing=entry})
        }
        notice?.let {PlayerErrorText(it)}
        removing?.let {entry->PlayerConfirmationDialog("Delete download?","${entry.name} and its downloaded file will be removed from this device.","Delete download",{removing=null}) {
            removing=null
            runCatching {MediaDownloadService.remove(activity,entry.id);version++}.onFailure {notice="Could not remove this download."}
        }}
        if(adding) {
            var url by remember {mutableStateOf("")}
            var editedName by remember {mutableStateOf<String?>(null)}
            val name=editedName ?: DownloadStore.suggestedName(url)
            var error by remember {mutableStateOf<String?>(null)}
            TvDialog({adding=false},width=560.dp,resizeForKeyboard=true,scrollContent=true) {
                DialogHeading("Download media")
                TvTextField(url,{url=it.take(16384)},Modifier.fillMaxWidth(),placeholder="https://…/video.mp4")
                TvTextField(name,{editedName=it.take(120)},Modifier.fillMaxWidth(),placeholder="Filename")
                error?.let {PlayerErrorText(it)}
                ButtonRow {
                    PlayerAction("Download",primary=true){if(!DownloadStore.supported(url.trim())||PrivateMedia.hidden(url.trim()))error="Enter a direct, non-private media URL."
                        else scope.launch {
                            try {
                                val item=withContext(Dispatchers.IO){DownloadStore(activity).add(url.trim(),name)}
                                start(item.id);adding=false;version++
                            }catch(e:CancellationException){throw e}catch(_:Exception){error="Could not start this download. Check available space and saved downloads."}
                        }
                    }
                    app.ember.player.PlayerAction("Cancel"){adding=false}
                }
            }
        }
    }
}
