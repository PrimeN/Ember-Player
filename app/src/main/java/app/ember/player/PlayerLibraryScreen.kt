package app.ember.player

import app.ember.tv.R

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
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
    val browseFocus = remember { FocusRequester() }
    var restoreBrowseFocus by remember { mutableStateOf(false) }
    var browseMenu by remember { mutableStateOf(false) }
    var folderUri by rememberSaveable {mutableStateOf<String?>(null)}
    var query by rememberSaveable {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf(false)}
    fun go(value:PlayerLibraryDestination,restoreFocus:Boolean=false) {
        destinationName=value.name;search=false;query=""
        if (restoreFocus && television) restoreBrowseFocus = true
    }
    LaunchedEffect(restoreBrowseFocus) {
        if (restoreBrowseFocus) {
            withFrameNanos {}
            runCatching { browseFocus.requestFocus() }
            restoreBrowseFocus = false
        }
    }
    if (television) RestoreToolbarFocus(browseMenu, browseFocus)
    val moreFocus = remember { FocusRequester() }
    var menu by remember {mutableStateOf(false)}
    var stream by remember {mutableStateOf(false)}
    var clearingRecent by remember {mutableStateOf(false)}
    var filterMenu by remember {mutableStateOf(false)}
    var sortMenu by remember {mutableStateOf(false)}
    if (television) RestoreToolbarFocus(menu || filterMenu || sortMenu || stream || clearingRecent, moreFocus)
    var notice by remember {mutableStateOf<String?>(null)}
    var localRevision by rememberSaveable {mutableIntStateOf(0)}
    var grid by remember {mutableStateOf(prefs.playerLibraryGrid)}
    var filter by remember {mutableStateOf(PlayerLibraryFilter.from(prefs.playerLibraryFilter))}
    var sort by remember {mutableStateOf(PlayerLibrarySort.from(prefs.playerLibrarySort))}
    val scope=rememberCoroutineScope()
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
        else if(search){search=false;query="";if (television) restoreBrowseFocus = true}
        else go(PlayerLibraryDestination.VIDEOS,restoreFocus=true)
    }
    val tablet = !television && isPlayerTablet()
    val phone = !television && !tablet
    val videos = destination == PlayerLibraryDestination.VIDEOS
    val canOpenFiles = !preparing && !opening
    val title = uiResources.getString(when {
        search -> R.string.ui_search
        videos -> R.string.ui_library_home
        television -> destination.label
        else -> R.string.ui_library_files
    })
    val tools: @Composable RowScope.() -> Unit = {
        if (television) {
            LibraryToolbarAction(uiResources.getString(R.string.ui_browse_library), Icons.Outlined.FolderOpen,
                Modifier.focusRequester(browseFocus)) { browseMenu = true }
            LibraryToolbarAction(uiResources.getString(R.string.ui_search_media), Icons.Outlined.Search,
                Modifier.semantics { selected = search }, onClick = ::toggleSearch)
        } else {
            LibraryToolbarAction(uiResources.getString(if (canOpenFiles) R.string.ui_open_file else R.string.ui_preparing_files),
                Icons.AutoMirrored.Outlined.InsertDriveFile, enabled = canOpenFiles, onClick = openFiles)
        }
        LibraryToolbarAction(uiResources.getString(R.string.ui_more_options), Icons.Outlined.MoreVert,
            Modifier.focusRequester(moreFocus)) { menu = true }
    }
    val scheme = MaterialTheme.colorScheme
    val backgroundColor = if (television && LocalNight.current && scheme.background != Color.Black) {
        Modifier.background(Brush.linearGradient(listOf(scheme.surface, scheme.background)))
    } else Modifier.background(scheme.background)
    val horizontal = when {
        television -> 28.dp
        tablet -> 20.dp
        else -> 16.dp
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().then(backgroundColor).safeDrawingPadding().padding(horizontal = horizontal),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!television) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TouchLibraryDock(
                    modifier = if (tablet) Modifier.widthIn(max = 380.dp) else Modifier,
                    homeSelected = videos && !search,
                    filesSelected = !search && !videos,
                    searchSelected = search,
                    home = { go(PlayerLibraryDestination.VIDEOS) },
                    files = {
                        if (destination != PlayerLibraryDestination.FOLDERS) {
                            folderUri = null
                            go(PlayerLibraryDestination.FOLDERS)
                        }
                    },
                    settings = ::settings,
                    search = ::toggleSearch,
                )
            }
        },
    ) { padding ->
        val dockPadding = padding.calculateBottomPadding()
        Column(Modifier.fillMaxSize()) {
            if (television) TvLibraryHeading(title, tools) else TouchLibraryHeading(title, tools)
            if (!television && !videos) {
                LibraryFilesNavigation(destination, Modifier.padding(bottom = 8.dp)) {
                    folderUri = null
                    go(it)
                }
            }
            if(search) {
                PlayerSearchField(query,{query=it.take(120)},uiResources.getString(R.string.ui_search_videos),searchFocus)
                LaunchedEffect(Unit){withFrameNanos {};runCatching {searchFocus.requestFocus()}}
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                stateHolder.SaveableStateProvider(destination.name) {
                    if(destination==PlayerLibraryDestination.VIDEOS)PlayerVideos(activity,version,query,search,grid,filter,sort,::play,
                        background?.takeUnless {BackgroundAudioService.currentIsPrivate()},casting?.takeIf {it.ready&&it.error==null},notice,
                        {uri->folderUri=uri;go(PlayerLibraryDestination.FOLDERS, restoreFocus=television)},catalogRevision=localRevision,
                        addFiles=openFiles.takeIf { canOpenFiles }, phone=phone, bottomPadding=dockPadding,
                        shortcuts={ target -> folderUri=null;go(target, restoreFocus=television) })
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
    LaunchedEffect(Unit) {
        if (television) {
            withFrameNanos {}
            runCatching { browseFocus.requestFocus() }
        }
    }
    if (browseMenu) OptionMenu(uiResources.getString(R.string.ui_browse_library),
        PlayerLibraryDestination.entries.map { target ->
            val label = if (target == PlayerLibraryDestination.VIDEOS) R.string.ui_library_home else target.label
            MenuOption(uiResources.getString(label), !search && destination == target, target.icon) {
                folderUri = null
                go(target)
            }
        }, { browseMenu = false })
    if(filterMenu)OptionMenu(uiResources.getString(R.string.ui_show_media),PlayerLibraryFilter.entries.map {value->MenuOption(uiResources.getString(value.label),filter==value){filter=value;prefs.playerLibraryFilter=value.id}},{filterMenu=false})
    if(sortMenu)OptionMenu(uiResources.getString(R.string.ui_sort_media),PlayerLibrarySort.entries.map {value->MenuOption(uiResources.getString(value.label),sort==value){sort=value;prefs.playerLibrarySort=value.id}},{sortMenu=false})
    if (menu) {
        val options = buildList {
            if (television && canOpenFiles) {
                add(MenuOption(uiResources.getString(R.string.ui_open_file), icon = Icons.AutoMirrored.Outlined.InsertDriveFile, onPick = openFiles))
            }
            if (videos) {
                val filterLabel = uiResources.getString(R.string.ui_label_value,
                    uiResources.getString(R.string.ui_show_media), uiResources.getString(filter.label))
                val sortLabel = uiResources.getString(R.string.ui_label_value,
                    uiResources.getString(R.string.ui_sort_media), uiResources.getString(sort.label))
                add(MenuOption(filterLabel, icon = Icons.Outlined.FilterAlt) { filterMenu = true })
                add(MenuOption(sortLabel, icon = Icons.AutoMirrored.Outlined.Sort) { sortMenu = true })
                val viewLabel = uiResources.getString(if (grid) R.string.ui_list_view else R.string.ui_grid_view)
                add(MenuOption(viewLabel, icon = if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView) {
                    grid = !grid
                    prefs.playerLibraryGrid = grid
                })
            }
            if (television) add(MenuOption(uiResources.getString(R.string.ui_settings), icon = Icons.Outlined.Settings, onPick = ::settings))
            add(MenuOption(uiResources.getString(R.string.ui_open_stream_url), icon = Icons.Outlined.Link) { stream = true })
            add(MenuOption(uiResources.getString(R.string.ui_refresh_library_view), icon = Icons.Outlined.Refresh) { localRevision++ })
            add(MenuOption(uiResources.getString(R.string.ui_clear_recent_media), icon = Icons.Outlined.History) { clearingRecent = true })
        }
        OptionMenu(uiResources.getString(R.string.ui_more_options), options) { menu = false }
    }
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
