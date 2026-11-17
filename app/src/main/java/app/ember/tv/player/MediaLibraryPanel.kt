package app.ember.tv.player

import androidx.activity.ComponentActivity
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
internal fun MediaLibraryPanel(activity:ComponentActivity,refresh:Int,showTitle:Boolean=true) {
    val scope=rememberCoroutineScope()
    var query by remember {mutableStateOf("")}
    var offsets by remember {mutableStateOf(listOf(0))}
    var revision by remember {mutableIntStateOf(0)}
    var page by remember {mutableStateOf(MediaLibrary.Result(emptyList(),null))}
    var notice by remember {mutableStateOf<app.ember.player.PlayerFeedback?>(null)}
    var job by remember {mutableStateOf<Job?>(null)}
    var scanned by remember {mutableIntStateOf(0)}
    if(showTitle)app.ember.player.PlayerSectionTitle("Saved folder library")
    Text("Refresh indexes the folders you chose, including subfolders. The encrypted index stays on this device; nothing is scanned at startup.",color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    TvTextField(query,{query=it.take(120);offsets=listOf(0)},Modifier.fillMaxWidth(),placeholder="Search media library")
    ButtonRow {
        if(job?.isActive!=true)Chip("Refresh library"){job=scope.launch {
            notice=null;scanned=0
            try {
                val folders=withContext(Dispatchers.IO){DeviceFolders(activity).read()}
                if(folders.isEmpty())notice=app.ember.player.PlayerFeedback("Add a media folder before refreshing.",false)
                else {withContext(Dispatchers.IO) {MediaLibrary(activity).use {index->index.refresh(folders,{ensureActive()},{scanned=it})}};notice=app.ember.player.PlayerFeedback("Library refreshed",false)}
            }
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice=app.ember.player.PlayerFeedback("Could not finish refreshing. Check folder access; existing indexed files are kept.")}
            catch(_:LinkageError){notice=app.ember.player.PlayerFeedback("The saved folder index is unavailable on this device.")}
            finally {revision++}
        }} else Chip("Cancel refresh · $scanned files"){job?.cancel()}
        if(offsets.size>1)Chip("Previous page"){offsets=offsets.dropLast(1)}
        page.nextOffset?.let {next->Chip("Next page"){offsets=offsets+next}}
    }
    LaunchedEffect(query,offsets,revision,refresh) {
        delay(200)
        try {page=withContext(Dispatchers.IO){
            if(DeviceFolders(activity).read().none {!app.ember.player.PrivateMedia.hidden(it.uri)})MediaLibrary.Result(emptyList(),null)
            else MediaLibrary(activity).use {it.search(query,offsets.last())}
        }}
        catch(e:CancellationException){throw e}
        catch(_:Exception){page=MediaLibrary.Result(emptyList(),null);notice=app.ember.player.PlayerFeedback("Library unavailable. Add a folder and refresh its index.")}
        catch(_:LinkageError){page=MediaLibrary.Result(emptyList(),null);notice=app.ember.player.PlayerFeedback("The saved folder index is unavailable on this device.")}
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items(page.entries,key={it.key}) {media->Chip(media.title,maxLines=1,modifier=Modifier.fillMaxWidth()) {
            if(!app.ember.player.PrivateMedia.hidden(media.uri))runCatching {MediaFiles.launch(activity,listOf(media))}.onFailure {notice=app.ember.player.PlayerFeedback("This file is unavailable. Refresh the library or renew folder access.")}
        }}
    }
    notice?.let {app.ember.player.PlayerFeedbackText(it)}
}
