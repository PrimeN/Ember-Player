package app.ember.tv.player

import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun UsbFilesPanel(activity:ComponentActivity,refresh:Int) {
    val scope=rememberCoroutineScope()
    var drives by remember {mutableStateOf<List<UsbDrive>>(emptyList())}
    var selected by remember {mutableStateOf<UsbDrive?>(null)}
    var page by remember {mutableStateOf(UsbPage(emptyList(),null))}
    var after by remember {mutableLongStateOf(0)}
    var revision by remember {mutableIntStateOf(0)}
    var loading by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
    var activeSignal by remember {mutableStateOf<CancellationSignal?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(UsbMedia.permitted(activity))revision++ else {selected=null;notice=app.ember.player.PlayerFeedback("Media permission was not granted. You can still use Choose files or Add a media folder.")}
    }
    LaunchedEffect(refresh,revision) {drives=withContext(Dispatchers.IO){UsbMedia.drives(activity)};if(selected!=null&&drives.none {it.volume==selected?.volume}){selected=null;page=UsbPage(emptyList(),null)}}
    DisposableEffect(Unit){onDispose {activeSignal?.cancel()}}
    DisposableEffect(selected){
        val watch=selected?.let {drive->UsbMedia.watch(activity,"content://media/${drive.volume}/file/0"){activeSignal?.cancel();selected=null;notice=app.ember.player.PlayerFeedback("USB drive disconnected",false);revision++}}
        onDispose {watch?.close()}
    }
    LaunchedEffect(selected,after,revision) {
        page=UsbPage(emptyList(),null)
        val drive=selected ?: return@LaunchedEffect
        if(!UsbMedia.permitted(activity))return@LaunchedEffect
        val signal=CancellationSignal();activeSignal=signal;loading=true
        try {
            val job=currentCoroutineContext().job
            page=withContext(Dispatchers.IO){UsbMedia.page(activity,drive,after,signal){job.ensureActive()}}.let {it.copy(items=it.items.filterNot {item->app.ember.player.PrivateMedia.hidden(item.uri)})}
            notice=if(page.items.isEmpty())app.ember.player.PlayerFeedback("No permitted, indexed media found on this drive.",false) else null
        } catch(e:CancellationException){throw e}
        catch(_:Exception){notice=app.ember.player.PlayerFeedback("This drive is unavailable. Reconnect it, refresh, or use the system picker.")}
        finally {signal.cancel();if(activeSignal===signal){activeSignal=null;loading=false}}
    }
    app.ember.player.PlayerSection("USB drives") {
        Text("Shows media Android has mounted and indexed. Android’s supported filesystems and your media permissions determine what is visible. The system picker remains available for other files.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        Chip("Refresh drives"){activeSignal?.cancel();selected=null;revision++}
        drives.forEach {drive->Chip(drive.name,maxLines=1){
            activeSignal?.cancel();selected=drive;after=0
            if(!UsbMedia.permitted(activity))permission.launch(UsbMedia.permissions(android.os.Build.VERSION.SDK_INT)) else revision++
        }}
        if(drives.isEmpty())Text("No mounted removable drive found",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        if(loading)Chip("Cancel loading"){activeSignal?.cancel();selected=null}
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(page.items.filterNot {app.ember.player.PrivateMedia.hidden(it.uri)},key={it.uri}) {media->Chip(media.title,maxLines=1){scope.launch {
                runCatching {val visible=page.items.filterNot {app.ember.player.PrivateMedia.hidden(it.uri)};MediaFiles.launch(activity,visible,index=visible.indexOf(media))}.onFailure {notice=app.ember.player.PlayerFeedback("This media is unavailable. Refresh the drive.")}
            }}}
        }
        if(after>0)Chip("First page"){after=0}
        page.next?.let {next->Chip("Next files"){after=next}}
        notice?.let {app.ember.player.PlayerFeedbackText(it)}
    }
}
