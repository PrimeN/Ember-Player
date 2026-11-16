package app.ember.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

internal enum class PlayerLibraryDestination(val label:String,val icon:ImageVector) {
    VIDEOS("Videos",Icons.Outlined.PlayArrow),FOLDERS("Folders",Icons.Outlined.Folder),NETWORK("Network",Icons.Outlined.Wifi),
    PLAYLISTS("Playlists",Icons.AutoMirrored.Outlined.ViewList),DOWNLOADS("Downloads",Icons.Outlined.Download),PRIVATE("Private media",Icons.Outlined.Lock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerLibraryScreen(activity:MediaFilesActivity,version:Int,preparing:Boolean,openFiles:()->Unit) {
    val prefs=remember {Prefs(activity)}
    var destinationName by rememberSaveable {mutableStateOf(if(activity.intent.hasExtra("network.account"))"NETWORK" else "VIDEOS")}
    val destination=PlayerLibraryDestination.entries.firstOrNull {it.name==destinationName} ?: PlayerLibraryDestination.VIDEOS
    val television=LocalPlayerTelevision.current
    val navigationFocus=remember {PlayerLibraryDestination.entries.associateWith {FocusRequester()}}
    var pendingNavigation by remember {mutableStateOf<PlayerLibraryDestination?>(null)}
    fun go(value:PlayerLibraryDestination,restoreFocus:Boolean=false){destinationName=value.name;if(restoreFocus&&television)pendingNavigation=value}
    LaunchedEffect(pendingNavigation){pendingNavigation?.let {target->withFrameNanos {};runCatching {navigationFocus.getValue(target).requestFocus()};pendingNavigation=null}}

    var query by rememberSaveable {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var stream by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var localRevision by remember {mutableIntStateOf(0)}
    var grid by remember {mutableStateOf(prefs.playerLibraryGrid)}
    val scope=rememberCoroutineScope()
    val openFocus=remember {FocusRequester()}
    val searchFocus=remember {FocusRequester()}
    val background by BackgroundAudioService.nowPlaying.collectAsState()
    val casting by CastPlaybackService.state.collectAsState()
    val stateHolder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var opening by remember {mutableStateOf(false)}
    var openingJob by remember {mutableStateOf<Job?>(null)}
    fun play(media:QueueMedia) {
        if(opening||PrivateMedia.hidden(media.uri))return
        opening=true;notice=null
        openingJob=scope.launch {
            try {
                withContext(Dispatchers.IO){MediaHistory(activity).add(listOf(media))}
                ensureActive();MediaFiles.launch(activity,listOf(media))
            }catch(e:CancellationException){throw e}
             catch(_:Exception){notice="This file is unavailable. Renew folder access or select it again."}
            finally {opening=false}
        }
    }
    BackHandler(enabled=opening||search||destination!=PlayerLibraryDestination.VIDEOS) {
        if(opening){openingJob?.cancel();opening=false}
        else if(search){search=false;query="";if(television)pendingNavigation=PlayerLibraryDestination.VIDEOS}
        else go(PlayerLibraryDestination.VIDEOS,restoreFocus=true)
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal=if(television)32.dp else 16.dp)) {
        PlayerHeading("Ember Player") {
            PlayerIconAction("Search media",Icons.Outlined.Search) {go(PlayerLibraryDestination.VIDEOS);search=!search;if(!search)query=""}
            if(television)PlayerIconAction("Settings",Icons.Outlined.Settings) {activity.startActivity(Intent(activity,PlayerSettingsActivity::class.java))}
            PlayerIconAction("More options",Icons.Outlined.MoreVert){menu=true}
        }
        if(search) {
            PlayerSearchField(query,{query=it.take(120)},"Search videos",searchFocus)
            LaunchedEffect(Unit){withFrameNanos {};runCatching {searchFocus.requestFocus()}}
            Spacer(Modifier.height(12.dp))
        }
        if(!television) {
            val primary=PlayerLibraryDestination.entries.take(3)
            if(destination in primary)PrimaryTabRow(primary.indexOf(destination),containerColor=Color.Transparent,divider={}) {
                primary.forEach {item->Tab(destination==item,onClick={go(item)},text={Text(item.label,maxLines=1)})}
            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                PlayerIconAction("Back to videos",Icons.AutoMirrored.Outlined.ArrowBack){go(PlayerLibraryDestination.VIDEOS)}
                Text(destination.label,style=MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            if(television)LazyColumn(Modifier.width(212.dp),contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                items(PlayerLibraryDestination.entries,key={it.name}) {item->PlayerNavigationRow(item.label,"",item.icon,Modifier.focusRequester(navigationFocus.getValue(item)),selected=destination==item){go(item)}}
            }
            stateHolder.SaveableStateProvider(destination.name) {
                when(destination) {
                    PlayerLibraryDestination.VIDEOS->PlayerVideos(activity,version+localRevision,query,grid,{grid=!grid;prefs.playerLibraryGrid=grid},::play,
                        background?.takeUnless {BackgroundAudioService.currentIsPrivate()},casting?.takeIf {it.ready&&it.error==null},notice,{go(PlayerLibraryDestination.FOLDERS)})
                    else->Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        when(destination) {
                            PlayerLibraryDestination.FOLDERS->{DeviceFilesPanel(activity,version);UsbFilesPanel(activity,version);DirectUsbPanel(activity,version)}
                            PlayerLibraryDestination.NETWORK->NetworkFilesPanel(activity,version)
                            PlayerLibraryDestination.PLAYLISTS->PlaylistsPanel(activity,version)
                            PlayerLibraryDestination.DOWNLOADS->DownloadsPanel(activity)
                            PlayerLibraryDestination.PRIVATE->PrivateMediaPanel(activity)
                            else->Unit
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.Center) {
            PlayerAction(if(preparing||opening)"Preparing files…" else "Open file",Icons.Outlined.FolderOpen,Modifier.focusRequester(openFocus).then(if(!television)Modifier.fillMaxWidth() else Modifier),primary=true,enabled=!preparing&&!opening,onClick=openFiles)
        }
        LaunchedEffect(Unit){if(television){withFrameNanos {};runCatching {openFocus.requestFocus()}}}
    }
    if(menu)OptionMenu("Ember Player",PlayerLibraryDestination.entries.map {item->MenuOption(item.label,destination==item){go(item,restoreFocus=true)}}+
        listOf(MenuOption("Settings"){activity.startActivity(Intent(activity,PlayerSettingsActivity::class.java))},MenuOption("Open stream URL"){stream=true},MenuOption("Refresh library view"){localRevision++},
            MenuOption("Clear recent media"){scope.launch {val ok=withContext(Dispatchers.IO){MediaHistory(activity).clear()};if(ok)localRevision++ else notice="Could not clear saved history."}}),{menu=false})
    if(stream)PlayerStreamDialog(activity,{stream=false}){notice=it}
}

@Composable
private fun RowScope.PlayerVideos(activity:MediaFilesActivity,version:Int,query:String,grid:Boolean,toggleGrid:()->Unit,play:(QueueMedia)->Unit,
    background:BackgroundNowPlaying?,casting:CastingState?,notice:String?,folders:()->Unit) {
    var offsets by rememberSaveable {mutableStateOf(listOf(0))}
    var page by remember {mutableStateOf(PlayerLibraryPage(emptyList(),null))}
    var busy by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(query){offsets=listOf(0)}
    LaunchedEffect(query,version,offsets) {
        busy=true;error=null
        try {delay(if(query.isBlank())0 else 180);page=withContext(Dispatchers.IO){loadPlayerLibrary(activity,query,offsets.last())}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){page=PlayerLibraryPage(emptyList(),null);error="Could not load saved media."}
        finally {busy=false}
    }
    val visible=page.entries.filterNot {PrivateMedia.hidden(it.media.uri)}
    val continuing=visible.filter {it.continuing}
    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
        val columns=if(!grid)1 else if(maxWidth>=900.dp)4 else if(maxWidth>=600.dp)3 else if(maxWidth>=320.dp)2 else 1
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            background?.let {playing->item {PlayerAction("${if(playing.playing)"Playing" else "Paused"} · ${playing.title}",Icons.Outlined.Headphones,Modifier.fillMaxWidth()) {
                activity.startActivity(Intent(activity,ResumeBackgroundAudioActivity::class.java).putExtra(BackgroundAudioService.EXTRA_ID,playing.id))
            }}}
            casting?.let {value->item {PlayerAction("Receiver · ${value.receiver}",Icons.Outlined.Cast,Modifier.fillMaxWidth()){activity.startActivity(Intent(activity,CastActivity::class.java))}}}
            if(continuing.isNotEmpty()&&query.isBlank())item {
                Text("Continue watching",style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(4.dp)) {
                    items(continuing,key={it.media.key}) {entry->PlayerMediaCard(entry,Modifier.width(if(LocalPlayerTelevision.current)256.dp else 264.dp),version){play(entry.media)}}
                }
            }
            item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Your videos",Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium)
                PlayerIconAction(if(grid)"List view" else "Grid view",if(grid)Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,onClick=toggleGrid)
            }}
            if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary)}
            if(visible.isEmpty()&&!busy)item {
                PlayerSection(if(query.isBlank())"Your media, ready to play" else "No matching videos") {
                    Text(if(page.indexUnavailable)"The saved folder index is unavailable. Check folder access or refresh the library below." else if(query.isBlank())"Open a file or add a media folder to begin." else "Try another filename or search your saved folders.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    PlayerAction("Media folders",Icons.Outlined.CreateNewFolder,onClick=folders)
                }
            }
            items(visible.chunked(columns),key={row->row.joinToString {it.media.key}}) {row->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    row.forEach {entry->if(grid)PlayerMediaCard(entry,Modifier.weight(1f),version){play(entry.media)}
                        else PlayerNavigationRow(entry.media.title,if(entry.duration>0)"${playerMediaTime(entry.position)} / ${playerMediaTime(entry.duration)}" else "",Icons.Outlined.Movie){play(entry.media)}}
                    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                }
            }
            if(offsets.size>1||page.next!=null)item {ButtonRow {
                if(offsets.size>1)PlayerAction("Previous page",onClick={offsets=offsets.dropLast(1)})
                page.next?.let {next->PlayerAction("Next page",enabled=!busy,onClick={offsets=offsets+next})}
            }}
            item {PlayerSection("Saved folder library"){MediaLibraryPanel(activity,version,showTitle=false)}}
            notice?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
        }
    }
}

@Composable
internal fun PlayerMediaCard(entry:PlayerLibraryMedia,modifier:Modifier,version:Int,play:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Focusable(play,modifier,container=scheme.surface,focusedContainer=scheme.surface,outline=true,card=true,focusedScale=1.035f) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f/9).clip(RoundedCornerShape(12.dp))) {
                PlayerMediaArtwork(entry.media,version,Modifier.fillMaxSize())
                if(entry.duration>0)Text(playerMediaTime(entry.duration),Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0xCC111111),RoundedCornerShape(6.dp)).padding(horizontal=6.dp,vertical=3.dp),color=Color.White,style=MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(entry.media.title,style=MaterialTheme.typography.bodyMedium,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
                if(entry.continuing) {
                    Text("${playerMediaTime(entry.position)} / ${playerMediaTime(entry.duration)}",style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant)
                    LinearProgressIndicator(progress={entry.progress},modifier=Modifier.fillMaxWidth().height(3.dp),color=scheme.primary,trackColor=scheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlayerMediaArtwork(media:QueueMedia,version:Int,modifier:Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,media.key,version) {
        value=null
        if(!PrivateMedia.hidden(media.uri))value=withContext(Dispatchers.IO) {
            val uri=android.net.Uri.parse(media.uri)
            if(uri.scheme !in setOf("content","file"))return@withContext null
            var size=-1L;var modified=0L
            runCatching {context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE,android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {c->if(c.moveToFirst()){size=c.getLong(0);modified=c.getLong(1)}}}
            DeviceThumbnails.load(context,DeviceFile(uri,uri,media.title,"video/*",size,modified,0),320)
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        bitmap?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
            ?: Icon(Icons.Outlined.Movie,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayerStreamDialog(activity:MediaFilesActivity,close:()->Unit,notice:(String)->Unit) {
    var url by rememberSaveable {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    TvDialog({if(!busy)close()},width=560.dp,resizeForKeyboard=true) {
        DialogHeading("Open stream URL")
        TvTextField(url,{url=it.take(16384)},Modifier.fillMaxWidth(),placeholder="https:// or rtsp://",keyboardType=androidx.compose.ui.text.input.KeyboardType.Uri)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        ButtonRow {
            PlayerAction(if(busy)"Opening…" else "Play",Icons.Outlined.PlayArrow,primary=true,enabled=!busy) {
                val value=url.trim()
                if(!ExternalMedia.allowed(value)||value.startsWith("content:",true))error="Enter a supported network stream URL."
                else {busy=true;scope.launch {
                    try {
                        val name=runCatching {java.net.URI(value).path?.substringAfterLast('/')?.takeIf(String::isNotBlank)?.take(120)}.getOrNull() ?: "Stream"
                        val queue=listOf(QueueMedia(value,name));withContext(Dispatchers.IO){MediaHistory(activity).add(queue)}
                        MediaFiles.launch(activity,queue);close()
                    } catch(e:CancellationException){throw e}
                      catch(_:Exception){error="Could not open this stream."}
                    finally {busy=false}
                }}
            }
            PlayerAction("Cancel",enabled=!busy,onClick=close)
        }
    }
}
