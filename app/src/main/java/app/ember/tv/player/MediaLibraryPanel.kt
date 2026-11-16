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
    var expanded by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    var offsets by remember {mutableStateOf(listOf(0))}
    var revision by remember {mutableIntStateOf(0)}
    var page by remember {mutableStateOf(MediaLibrary.Result(emptyList(),null))}
    var notice by remember {mutableStateOf<String?>(null)}
    var job by remember {mutableStateOf<Job?>(null)}
    var scanned by remember {mutableIntStateOf(0)}
    if(showTitle)Text("Library",fontSize=20.sp)
    Chip(if(expanded)"Hide library search" else "Search saved media folders"){expanded=!expanded}
    if(!expanded)return
    Text("Refresh indexes the folders you chose, including subfolders. The encrypted index stays on this device; nothing is scanned at startup.",color=Mute,fontSize=12.sp)
    TvTextField(query,{query=it.take(120);offsets=listOf(0)},Modifier.fillMaxWidth(),placeholder="Search media library")
    ButtonRow {
        if(job?.isActive!=true)Chip("Refresh library"){job=scope.launch {
            notice=null;scanned=0
            try {withContext(Dispatchers.IO) {MediaLibrary(activity).use {index->index.refresh(DeviceFolders(activity).read(),{ensureActive()},{scanned=it})}};notice="Library refreshed"}
            catch(e:CancellationException){throw e}
            catch(_:Exception){notice="Could not finish refreshing. Check folder access; existing indexed files are kept."}
            finally {revision++}
        }} else Chip("Cancel refresh · $scanned files"){job?.cancel()}
        if(offsets.size>1)Chip("Previous page"){offsets=offsets.dropLast(1)}
        page.nextOffset?.let {next->Chip("Next page"){offsets=offsets+next}}
    }
    LaunchedEffect(query,offsets,revision,refresh) {
        delay(200)
        try {page=withContext(Dispatchers.IO){MediaLibrary(activity).use {it.search(query,offsets.last())}}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){page=MediaLibrary.Result(emptyList(),null);notice="Library unavailable. Add a folder and refresh its index."}
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        items(page.entries,key={it.key}) {media->Chip(media.title,maxLines=1,modifier=Modifier.fillMaxWidth()) {
            if(!app.ember.player.PrivateMedia.hidden(media.uri))runCatching {MediaFiles.launch(activity,listOf(media))}.onFailure {notice="This file is unavailable. Refresh the library or renew folder access."}
        }}
    }
    notice?.let {Text(it,color=Mute,fontSize=12.sp)}
}
