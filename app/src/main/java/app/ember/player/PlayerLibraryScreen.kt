package app.ember.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

internal enum class PlayerLibraryDestination(val label:String,val icon:ImageVector) {
    VIDEOS(PlayerUiLabels.VIDEOS,Icons.Outlined.PlayArrow),FOLDERS(PlayerUiLabels.FOLDERS,Icons.Outlined.Folder),NETWORK("Network",Icons.Outlined.Wifi),
    PLAYLISTS("Playlists",Icons.AutoMirrored.Outlined.ViewList),DOWNLOADS("Downloads",Icons.Outlined.Download),PRIVATE(PlayerUiLabels.PRIVATE_MEDIA,Icons.Outlined.Lock)
}

@Composable
internal fun PlayerLibraryScreen(activity:MediaFilesActivity,version:Int,preparing:Boolean,openFiles:()->Unit) {
    val prefs=remember {Prefs(activity)}
    var destinationName by rememberSaveable {mutableStateOf(if(activity.intent.hasExtra("network.account"))"NETWORK" else "VIDEOS")}
    val destination=PlayerLibraryDestination.entries.firstOrNull {it.name==destinationName} ?: PlayerLibraryDestination.VIDEOS
    val television=LocalPlayerTelevision.current
    val navigationFocus=remember {PlayerLibraryDestination.entries.associateWith {FocusRequester()}}
    var pendingNavigation by remember {mutableStateOf<PlayerLibraryDestination?>(null)}
    var folderUri by rememberSaveable {mutableStateOf<String?>(null)}
    var query by rememberSaveable {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf(false)}
    fun go(value:PlayerLibraryDestination,restoreFocus:Boolean=false) {
        destinationName=value.name;search=false;query=""
        if(restoreFocus&&television)pendingNavigation=value
    }
    LaunchedEffect(pendingNavigation){pendingNavigation?.let {target->withFrameNanos {};runCatching {navigationFocus.getValue(target).requestFocus()};pendingNavigation=null}}
    var menu by remember {mutableStateOf(false)}
    var stream by remember {mutableStateOf(false)}
    var clearingRecent by remember {mutableStateOf(false)}
    var filterMenu by remember {mutableStateOf(false)}
    var sortMenu by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var localRevision by remember {mutableIntStateOf(0)}
    var grid by remember {mutableStateOf(prefs.playerLibraryGrid)}
    var filter by remember {mutableStateOf(PlayerLibraryFilter.from(prefs.playerLibraryFilter))}
    var sort by remember {mutableStateOf(PlayerLibrarySort.from(prefs.playerLibrarySort))}
    val scope=rememberCoroutineScope()
    val openFocus=remember {FocusRequester()}
    val searchFocus=remember {FocusRequester()}
    val background by BackgroundAudioService.nowPlaying.collectAsState()
    val casting by CastPlaybackService.state.collectAsState()
    val stateHolder=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var opening by remember {mutableStateOf(false)}
    var openingJob by remember {mutableStateOf<Job?>(null)}
    fun settings(){activity.startActivity(Intent(activity,PlayerSettingsActivity::class.java))}
    fun toggleSearch(){destinationName=PlayerLibraryDestination.VIDEOS.name;search=!search;if(!search)query=""}
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
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        val narrow=maxWidth<600.dp
        val horizontal=if(television)20.dp else 16.dp
        Box(Modifier.fillMaxSize().padding(horizontal=horizontal)) {
            Column(Modifier.fillMaxSize()) {
                PlayerHeading("Ember Player",modifier=if(television)Modifier.height(48.dp) else Modifier) {
                    if(!narrow&&destination==PlayerLibraryDestination.VIDEOS) {
                        LibraryUtility(filter.label,Icons.Outlined.FilterList){filterMenu=true}
                        LibraryUtility(sort.label,Icons.AutoMirrored.Outlined.Sort){sortMenu=true}
                        PlayerIconAction(if(grid)"List view" else "Grid view",if(grid)Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView){grid=!grid;prefs.playerLibraryGrid=grid}
                    }
                    PlayerIconAction(if(preparing||opening)"Preparing files" else "Open file",Icons.AutoMirrored.Outlined.InsertDriveFile,Modifier.focusRequester(openFocus),enabled=!preparing&&!opening,onClick=openFiles)
                    if(television) {
                        PlayerIconAction("Search media",Icons.Outlined.Search,onClick=::toggleSearch)
                        PlayerIconAction("Settings",Icons.Outlined.Settings,onClick=::settings)
                    }
                    PlayerIconAction("More options",Icons.Outlined.MoreVert){menu=true}
                }
                if(!television)PhoneLibraryNavigation(destination){folderUri=null;go(it)}
                if(narrow&&destination==PlayerLibraryDestination.VIDEOS)Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    LibraryUtility(filter.label,Icons.Outlined.FilterList,Modifier.weight(1f)){filterMenu=true}
                    LibraryUtility(sort.label,Icons.AutoMirrored.Outlined.Sort,Modifier.weight(1f)){sortMenu=true}
                    PlayerIconAction(if(grid)"List view" else "Grid view",if(grid)Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView){grid=!grid;prefs.playerLibraryGrid=grid}
                }
                if(search) {
                    PlayerSearchField(query,{query=it.take(120)},"Search videos",searchFocus)
                    LaunchedEffect(Unit){withFrameNanos {};runCatching {searchFocus.requestFocus()}}
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(television)LazyColumn(Modifier.width(160.dp),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        items(PlayerLibraryDestination.entries,key={it.name}) {item->
                            LibraryNavigation(item.label,item.icon,Modifier.fillMaxWidth().focusRequester(navigationFocus.getValue(item)),destination==item){go(item)}
                        }
                    }
                    stateHolder.SaveableStateProvider(destination.name) {
                        if(destination==PlayerLibraryDestination.VIDEOS)PlayerVideos(activity,version+localRevision,query,search,grid,filter,sort,::play,
                            background?.takeUnless {BackgroundAudioService.currentIsPrivate()},casting?.takeIf {it.ready&&it.error==null},notice,
                            {uri->folderUri=uri;go(PlayerLibraryDestination.FOLDERS)})
                        else Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(top=8.dp,bottom=if(television)20.dp else 100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            when(destination) {
                                PlayerLibraryDestination.FOLDERS->{DeviceFilesPanel(activity,version,folderUri);UsbFilesPanel(activity,version);DirectUsbPanel(activity,version);PlayerSection("Saved folder library"){MediaLibraryPanel(activity,version,showTitle=false)}}
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
            if(!television)LibraryDock(Modifier.align(Alignment.BottomCenter).padding(bottom=12.dp),
                when {search->"search";destination==PlayerLibraryDestination.VIDEOS->"home";destination==PlayerLibraryDestination.FOLDERS->"files";else->""},
                {go(PlayerLibraryDestination.VIDEOS)}, {folderUri=null;go(PlayerLibraryDestination.FOLDERS)},::toggleSearch,::settings)
        }
    }
    LaunchedEffect(Unit){if(television){withFrameNanos {};runCatching {openFocus.requestFocus()}}}
    if(filterMenu)OptionMenu("Show media",PlayerLibraryFilter.entries.map {value->MenuOption(value.label,filter==value){filter=value;prefs.playerLibraryFilter=value.id}},{filterMenu=false})
    if(sortMenu)OptionMenu("Sort media",PlayerLibrarySort.entries.map {value->MenuOption(value.label,sort==value){sort=value;prefs.playerLibrarySort=value.id}},{sortMenu=false})
    if(menu)OptionMenu("Ember Player",PlayerLibraryDestination.entries.map {item->MenuOption(item.label,destination==item){folderUri=null;go(item,restoreFocus=true)}}+
        listOf(MenuOption("Settings"){settings()},MenuOption("Open stream URL"){stream=true},MenuOption("Refresh library view"){localRevision++},
            MenuOption("Clear recent media"){clearingRecent=true}),{menu=false})
    if(clearingRecent)PlayerConfirmationDialog("Clear recent media?","Clear the recent-media list? Resume positions, playlists and bookmarks will be kept.","Clear recent media",{clearingRecent=false}) {
        clearingRecent=false
        scope.launch {val ok=withContext(Dispatchers.IO){MediaHistory(activity).clear()};if(ok)localRevision++ else notice="Could not clear saved history."}
    }
    if(stream)PlayerStreamDialog(activity,{stream=false}){notice=it}
}

@Composable
internal fun PhoneLibraryNavigation(destination:PlayerLibraryDestination,go:(PlayerLibraryDestination)->Unit) {
    val destinations=remember {PlayerLibraryDestination.entries.filterNot {it==PlayerLibraryDestination.VIDEOS||it==PlayerLibraryDestination.FOLDERS}}
    val state=rememberLazyListState()
    LaunchedEffect(destination){state.animateScrollToItem(destinations.indexOf(destination).coerceAtLeast(0))}
    LazyRow(Modifier.fillMaxWidth().padding(bottom=8.dp),state=state,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        items(destinations,key={it.name}) {item->
            LibraryNavigation(item.label,item.icon,isSelected=destination==item){go(item)}
        }
    }
}

@Composable
private fun LibraryUtility(label:String,icon:ImageVector,modifier:Modifier=Modifier,click:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Focusable(click,modifier.heightIn(min=48.dp).semantics {contentDescription=label},radius=24,container=scheme.surfaceVariant.copy(alpha=.65f),
        focusedContainer=scheme.primary,focusedContent=scheme.onPrimary,outline=false,focusedScale=1f) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(LocalPlayerTelevision.current)Icon(icon,null,Modifier.size(18.dp))
            Text(label,Modifier.weight(1f,fill=false),style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LibraryNavigation(label:String,icon:ImageVector,modifier:Modifier=Modifier,isSelected:Boolean,click:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Focusable(click,modifier.heightIn(min=48.dp).semantics {selected=isSelected},radius=14,
        container=if(isSelected)scheme.primaryContainer else Color.Transparent,focusedContainer=scheme.primary,focusedContent=scheme.onPrimary,outline=false,focusedScale=1f) {
        Row(Modifier.padding(horizontal=10.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(icon,null,Modifier.size(18.dp));Text(label,style=if(LocalPlayerTelevision.current)MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,maxLines=if(LocalPlayerTelevision.current)2 else 1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryDock(modifier:Modifier,selected:String,home:()->Unit,files:()->Unit,search:()->Unit,settings:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Surface(modifier.widthIn(max=440.dp),shape=RoundedCornerShape(32.dp),color=scheme.surfaceVariant.copy(alpha=if(LocalRenderOptions.current.glass).96f else 1f),
        border=androidx.compose.foundation.BorderStroke(1.dp,scheme.outlineVariant),shadowElevation=4.dp) {
        Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            listOf(Triple("home",PlayerLibraryDestination.VIDEOS.icon,home),Triple("files",PlayerLibraryDestination.FOLDERS.icon,files),Triple("search",Icons.Outlined.Search,search),Triple("settings",Icons.Outlined.Settings,settings)).forEach {(id,icon,action)->
                val label=when(id){"home"->PlayerUiLabels.VIDEOS;"files"->PlayerUiLabels.FOLDERS;else->id.replaceFirstChar {it.uppercaseChar()}}
                Surface(action,Modifier.weight(1f).heightIn(min=56.dp).semantics {this.selected=selected==id},shape=RoundedCornerShape(26.dp),
                    color=if(selected==id)scheme.primaryContainer else Color.Transparent,contentColor=if(selected==id)scheme.onPrimaryContainer else scheme.onSurface) {
                    Column(Modifier.padding(vertical=6.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Icon(icon,null,Modifier.size(22.dp));Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStreamDialog(activity:MediaFilesActivity,close:()->Unit,notice:(String)->Unit) {
    var url by rememberSaveable {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    TvDialog({if(!busy)close()},width=560.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading("Open stream URL")
        TvTextField(url,{url=it.take(16384)},Modifier.fillMaxWidth(),placeholder="https:// or rtsp://",keyboardType=androidx.compose.ui.text.input.KeyboardType.Uri)
        error?.let {PlayerErrorText(it)}
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
