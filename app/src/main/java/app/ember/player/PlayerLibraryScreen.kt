package app.ember.player

import app.ember.tv.R

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.data.Prefs
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

internal enum class PlayerLibraryDestination(@androidx.annotation.StringRes val label:Int,val icon:ImageVector) {
    VIDEOS(PlayerUiLabels.VIDEOS,Icons.Outlined.PlayArrow),FOLDERS(PlayerUiLabels.FOLDERS,Icons.Outlined.Folder),NETWORK(R.string.ui_network,Icons.Outlined.Wifi),
    PLAYLISTS(R.string.ui_playlists,Icons.AutoMirrored.Outlined.ViewList),DOWNLOADS(R.string.ui_downloads,Icons.Outlined.Download),PRIVATE(PlayerUiLabels.PRIVATE_MEDIA,Icons.Outlined.Lock)
}

@Composable
internal fun PlayerLibraryScreen(activity:MediaFilesActivity,version:Int,preparing:Boolean,openFiles:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

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
    var localRevision by rememberSaveable {mutableIntStateOf(0)}
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
             catch(_:Exception){notice=uiResources.getString(R.string.ui_this_file_is_unavailable_renew_folder_access_or_select)}
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
        Scaffold(
            modifier=Modifier.fillMaxSize().padding(horizontal=horizontal),
            containerColor=Color.Transparent,
            contentWindowInsets=WindowInsets(0,0,0,0),
            bottomBar={
                if(!television)Box(Modifier.fillMaxWidth().padding(bottom=12.dp),contentAlignment=Alignment.Center) {
                    LibraryDock(
                        destination,search,
                        home={go(PlayerLibraryDestination.VIDEOS)},
                        files={if(destination!=PlayerLibraryDestination.FOLDERS){folderUri=null;go(PlayerLibraryDestination.FOLDERS)}},
                        search=::toggleSearch,settings=::settings
                    )
                }
            }
        ) {padding->
            val dockPadding=padding.calculateBottomPadding()
            Column(Modifier.fillMaxSize()) {
                PlayerHeading(uiResources.getString(R.string.ui_ember_player),modifier=if(television)Modifier.height(48.dp) else Modifier,compact=narrow&&!television) {
                    if(!narrow&&destination==PlayerLibraryDestination.VIDEOS) {
                        LibraryUtility(uiResources.getString(filter.label),Icons.Outlined.FilterList){filterMenu=true}
                        LibraryUtility(uiResources.getString(sort.label),Icons.AutoMirrored.Outlined.Sort){sortMenu=true}
                        PlayerIconAction(if(grid)uiResources.getString(R.string.ui_list_view) else uiResources.getString(R.string.ui_grid_view),if(grid)Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView){grid=!grid;prefs.playerLibraryGrid=grid}
                    }
                    PlayerIconAction(if(preparing||opening)uiResources.getString(R.string.ui_preparing_files) else uiResources.getString(R.string.ui_open_file),Icons.AutoMirrored.Outlined.InsertDriveFile,Modifier.focusRequester(openFocus),enabled=!preparing&&!opening,onClick=openFiles)
                    if(television) {
                        PlayerIconAction(uiResources.getString(R.string.ui_search_media),Icons.Outlined.Search,Modifier.semantics {selected=search},onClick=::toggleSearch)
                        PlayerIconAction(uiResources.getString(R.string.ui_settings),Icons.Outlined.Settings,onClick=::settings)
                    }
                    PlayerIconAction(uiResources.getString(R.string.ui_more_options),Icons.Outlined.MoreVert){menu=true}
                }
                if(!television&&destination!=PlayerLibraryDestination.VIDEOS) {
                    LibraryFilesNavigation(destination,Modifier.padding(bottom=8.dp)){folderUri=null;go(it)}
                }
                if(narrow&&destination==PlayerLibraryDestination.VIDEOS)Column(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val enlarged=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.2f
                    if(enlarged)LibraryUtility(uiResources.getString(filter.label),Icons.Outlined.FilterList,Modifier.fillMaxWidth(),expanded=true){filterMenu=true}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(!enlarged)LibraryUtility(uiResources.getString(filter.label),Icons.Outlined.FilterList,Modifier.weight(1f),expanded=true){filterMenu=true}
                        LibraryUtility(uiResources.getString(sort.label),Icons.AutoMirrored.Outlined.Sort,Modifier.weight(1f),expanded=true){sortMenu=true}
                        PlayerIconAction(if(grid)uiResources.getString(R.string.ui_list_view) else uiResources.getString(R.string.ui_grid_view),if(grid)Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView){grid=!grid;prefs.playerLibraryGrid=grid}
                    }
                }
                if(search) {
                    PlayerSearchField(query,{query=it.take(120)},uiResources.getString(R.string.ui_search_videos),searchFocus)
                    LaunchedEffect(Unit){withFrameNanos {};runCatching {searchFocus.requestFocus()}}
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(television)LazyColumn(Modifier.width(160.dp),contentPadding=PaddingValues(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        items(PlayerLibraryDestination.entries,key={it.name}) {item->
                            LibraryNavigation(uiResources.getString(item.label),item.icon,Modifier.fillMaxWidth().focusRequester(navigationFocus.getValue(item)),!search&&destination==item){go(item)}
                        }
                    }
                    stateHolder.SaveableStateProvider(destination.name) {
                        if(destination==PlayerLibraryDestination.VIDEOS)PlayerVideos(activity,version,query,search,grid,filter,sort,::play,
                            background?.takeUnless {BackgroundAudioService.currentIsPrivate()},casting?.takeIf {it.ready&&it.error==null},notice,
                            {uri->folderUri=uri;go(PlayerLibraryDestination.FOLDERS)},catalogRevision=localRevision,bottomPadding=dockPadding)
                        else Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(top=8.dp,bottom=20.dp+dockPadding),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            when(destination) {
                                PlayerLibraryDestination.FOLDERS->{DeviceFilesPanel(activity,version,folderUri);UsbFilesPanel(activity,version);DirectUsbPanel(activity,version);PlayerSection(uiResources.getString(R.string.ui_saved_folder_library)){MediaLibraryPanel(activity,version,showTitle=false)}}
                                PlayerLibraryDestination.NETWORK->NetworkFilesPanel(activity,version)
                                PlayerLibraryDestination.PLAYLISTS->PlaylistsPanel(activity,version+localRevision)
                                PlayerLibraryDestination.DOWNLOADS->DownloadsPanel(activity)
                                PlayerLibraryDestination.PRIVATE->PrivateMediaPanel(activity)
                                else->Unit
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit){if(television){withFrameNanos {};runCatching {openFocus.requestFocus()}}}
    if(filterMenu)OptionMenu(uiResources.getString(R.string.ui_show_media),PlayerLibraryFilter.entries.map {value->MenuOption(uiResources.getString(value.label),filter==value){filter=value;prefs.playerLibraryFilter=value.id}},{filterMenu=false})
    if(sortMenu)OptionMenu(uiResources.getString(R.string.ui_sort_media),PlayerLibrarySort.entries.map {value->MenuOption(uiResources.getString(value.label),sort==value){sort=value;prefs.playerLibrarySort=value.id}},{sortMenu=false})
    if(menu)OptionMenu(uiResources.getString(R.string.ui_ember_player),
        listOf(MenuOption(uiResources.getString(R.string.ui_open_file)){if(!preparing&&!opening)openFiles()},MenuOption(uiResources.getString(R.string.ui_open_stream_url)){stream=true},MenuOption(uiResources.getString(R.string.ui_refresh_library_view)){localRevision++},
            MenuOption(uiResources.getString(R.string.ui_clear_recent_media)){clearingRecent=true}),{menu=false})
    if(clearingRecent)PlayerConfirmationDialog(uiResources.getString(R.string.ui_clear_recent_media_6abe06),uiResources.getString(R.string.ui_clear_the_recent_media_list_resume_positions_playlists_and),uiResources.getString(R.string.ui_clear_recent_media),{clearingRecent=false}) {
        clearingRecent=false
        scope.launch {val ok=withContext(Dispatchers.IO){MediaHistory(activity).clear()};if(ok)localRevision++ else notice=uiResources.getString(R.string.ui_could_not_clear_saved_history)}
    }
    if(stream)PlayerStreamDialog(activity,{stream=false}){notice=it}
}

@Composable
internal fun LibraryFilesNavigation(destination:PlayerLibraryDestination,modifier:Modifier=Modifier,go:(PlayerLibraryDestination)->Unit) {
    val resources=androidx.compose.ui.platform.LocalResources.current
    var expanded by remember {mutableStateOf(false)}
    Box(modifier) {
        LibraryUtility(resources.getString(destination.label),destination.icon){expanded=true}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            for(section in PlayerLibraryDestination.entries) {
                if(section==PlayerLibraryDestination.VIDEOS)continue
                DropdownMenuItem(
                    text={Text(resources.getString(section.label))},
                    leadingIcon={Icon(section.icon,null)},
                    modifier=Modifier.semantics {selected=section==destination},
                    onClick={
                        expanded=false
                        if(section!=destination)go(section)
                    }
                )
            }
        }
    }
}

@Composable
internal fun LibraryDock(destination:PlayerLibraryDestination,searching:Boolean,home:()->Unit,files:()->Unit,search:()->Unit,settings:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Surface(
        modifier=Modifier.widthIn(max=440.dp).fillMaxWidth(),
        shape=RoundedCornerShape(32.dp),
        color=scheme.surfaceVariant.copy(alpha=if(LocalRenderOptions.current.glass).96f else 1f),
        border=BorderStroke(1.dp,scheme.outlineVariant),
        shadowElevation=4.dp
    ) {
        Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            LibraryDockButton(R.string.ui_library_home,Icons.Outlined.Home,!searching&&destination==PlayerLibraryDestination.VIDEOS,home)
            LibraryDockButton(R.string.ui_library_files,Icons.Outlined.Folder,!searching&&destination!=PlayerLibraryDestination.VIDEOS,files)
            LibraryDockButton(R.string.ui_search,Icons.Outlined.Search,searching,search)
            LibraryDockButton(R.string.ui_settings,Icons.Outlined.Settings,false,settings)
        }
    }
}

@Composable
private fun RowScope.LibraryDockButton(@androidx.annotation.StringRes label:Int,icon:ImageVector,isSelected:Boolean,onClick:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Surface(
        onClick=onClick,
        modifier=Modifier.weight(1f).heightIn(min=56.dp).semantics {selected=isSelected},
        shape=RoundedCornerShape(26.dp),
        color=if(isSelected)scheme.primaryContainer else Color.Transparent,
        contentColor=if(isSelected)scheme.onPrimaryContainer else scheme.onSurface
    ) {
        Column(
            Modifier.padding(horizontal=4.dp,vertical=6.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(2.dp,Alignment.CenterVertically)
        ) {
            Icon(icon,null,Modifier.size(22.dp))
            Text(
                androidx.compose.ui.res.stringResource(label),
                style=MaterialTheme.typography.labelMedium,
                textAlign=TextAlign.Center,
                maxLines=2,
                overflow=TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun LibraryUtility(label:String,icon:ImageVector,modifier:Modifier=Modifier,expanded:Boolean=false,click:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Focusable(click,modifier.heightIn(min=48.dp),radius=TvTokens.Shape.Control,container=scheme.surfaceVariant.copy(alpha=.65f),
        focusedContainer=scheme.primary,focusedContent=scheme.onPrimary,outline=false,focusedScale=1f) {
        Row((if(expanded)Modifier.fillMaxWidth() else Modifier).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(LocalPlayerTelevision.current)Icon(icon,null,Modifier.size(18.dp))
            Text(label,Modifier.weight(1f,fill=expanded),style=MaterialTheme.typography.labelLarge,maxLines=if(expanded)2 else 1,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LibraryNavigation(label:String,icon:ImageVector,modifier:Modifier=Modifier,isSelected:Boolean,click:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Focusable(click,modifier.heightIn(min=48.dp).semantics {selected=isSelected},radius=TvTokens.Shape.Control,
        container=if(isSelected)scheme.primaryContainer else Color.Transparent,focusedContainer=scheme.primary,focusedContent=scheme.onPrimary,outline=false,focusedScale=1f) {
        Row(Modifier.padding(horizontal=10.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(icon,null,Modifier.size(18.dp));Text(label,style=if(LocalPlayerTelevision.current)MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,maxLines=if(LocalPlayerTelevision.current)2 else 1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlayerStreamDialog(activity:MediaFilesActivity,close:()->Unit,notice:(String)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    var url by rememberSaveable {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    TvDialog({if(!busy)close()},width=560.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading(uiResources.getString(R.string.ui_open_stream_url))
        TvTextField(url,{url=it.take(16384)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_https_or_rtsp),keyboardType=androidx.compose.ui.text.input.KeyboardType.Uri)
        error?.let {PlayerErrorText(it)}
        ButtonRow {
            PlayerAction(if(busy)uiResources.getString(R.string.ui_opening) else uiResources.getString(R.string.ui_play),Icons.Outlined.PlayArrow,primary=true,enabled=!busy) {
                val value=url.trim()
                if(!ExternalMedia.allowed(value)||value.startsWith("content:",true))error=uiResources.getString(R.string.ui_enter_a_supported_network_stream_url)
                else {busy=true;scope.launch {
                    try {
                        val name=runCatching {java.net.URI(value).path?.substringAfterLast('/')?.takeIf(String::isNotBlank)?.take(120)}.getOrNull() ?: uiResources.getString(R.string.ui_stream)
                        val queue=listOf(QueueMedia(value,name));withContext(Dispatchers.IO){MediaHistory(activity).add(queue)}
                        MediaFiles.launch(activity,queue);close()
                    } catch(e:CancellationException){throw e}
                      catch(_:Exception){error=uiResources.getString(R.string.ui_could_not_open_this_stream)}
                    finally {busy=false}
                }}
            }
            PlayerAction(uiResources.getString(R.string.ui_cancel),enabled=!busy,onClick=close)
        }
    }
}
