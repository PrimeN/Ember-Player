package app.ember.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.player.*
import app.ember.tv.ui.Focusable
import kotlinx.coroutines.*

/** Density is based on available media space and respects enlarged system text. */
internal fun playerLibraryColumns(width:Float,television:Boolean)=when {
    television->when {width>=700->7;width>=520->5;else->3}
    width>=1100->10
    width>=840->8
    width>=600->6
    width>=340->3
    else->2
}

@Composable
internal fun RowScope.PlayerVideos(activity:MediaFilesActivity,version:Int,query:String,searching:Boolean,grid:Boolean,filter:PlayerLibraryFilter,sort:PlayerLibrarySort,play:(QueueMedia)->Unit,
    background:BackgroundNowPlaying?,casting:CastingState?,notice:String?,folders:(String?)->Unit) {
    var offsets by rememberSaveable {mutableStateOf(listOf(0))}
    var snapshot by remember {mutableStateOf(PlayerLibrarySnapshot(emptyList()))}
    var savedFolders by remember {mutableStateOf(emptyList<DeviceFolder>())}
    var busy by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    var expanded by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(query,sort,filter){expanded=null}
    val actualFilter=expanded?.let(PlayerLibraryFilter::from) ?: filter
    LaunchedEffect(query,sort,actualFilter,version){offsets=listOf(0)}
    LaunchedEffect(query,version) {
        busy=true;error=null
        try {
            delay(if(query.isBlank())0 else 180)
            val loaded=withContext(Dispatchers.IO){loadPlayerLibrary(activity,query){ensureActive()} to DeviceFolders(activity).read()}
            snapshot=loaded.first;savedFolders=loaded.second.filterNot {PrivateMedia.hidden(it.uri)}
        }
        catch(e:CancellationException){throw e}
        catch(_:Exception){snapshot=PlayerLibrarySnapshot(emptyList());error="Could not load saved media."}
        finally {busy=false}
    }
    BackHandler(enabled=expanded!=null&&!searching){expanded=null}
    val page=remember(snapshot,offsets,sort,actualFilter){snapshot.page(offsets.last(),sort,actualFilter)}
    val visible=page.entries.filterNot {PrivateMedia.hidden(it.media.uri)}
    val filtered=visible
    val emptyLibrary=snapshot.entries.isEmpty()&&query.isBlank()
    val home=grid&&!searching&&filter==PlayerLibraryFilter.ALL&&query.isBlank()&&expanded==null&&offsets.size==1
    val continuing=remember(snapshot){snapshot.continuing}.filterNot {PrivateMedia.hidden(it.media.uri)}
    val unwatched=visible.filterNot {it.watched}
    val television=LocalPlayerTelevision.current
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
        val columns=playerLibraryColumns(maxWidth.value/fontScale,television)
        val gap=if(television)8.dp else 12.dp
        val cardWidth=((maxWidth-gap*(columns-1))/columns).coerceAtLeast(72.dp)
        val resumeWidth=if(television)((maxWidth-gap*5)/6).coerceAtLeast(108.dp) else if(maxWidth>=600.dp)((maxWidth-gap*4)/5).coerceAtLeast(160.dp) else 248.dp
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(top=8.dp,bottom=if(television)20.dp else 104.dp),verticalArrangement=Arrangement.spacedBy(if(television)8.dp else 20.dp)) {
            background?.let {playing->item {PlayerAction("${if(playing.playing)"Playing" else "Paused"} · ${playing.title}",Icons.Outlined.Headphones,Modifier.fillMaxWidth()) {
                activity.startActivity(Intent(activity,ResumeBackgroundAudioActivity::class.java).putExtra(BackgroundAudioService.EXTRA_ID,playing.id))
            }}}
            casting?.let {value->item {PlayerAction("Receiver · ${value.receiver}",Icons.Outlined.Cast,Modifier.fillMaxWidth()){activity.startActivity(Intent(activity,CastActivity::class.java))}}}
            if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.primary)}
            if(home) {
                if(continuing.isNotEmpty())item {
                    LibraryShelf("Continue watching",continuing,resumeWidth,gap,false,version,play){expanded=PlayerLibraryFilter.CONTINUING.id}
                }
                if(visible.isNotEmpty())item {
                    LibraryShelf(if(sort==PlayerLibrarySort.MODIFIED&&visible.any {it.modified>0})"Recently modified" else "Your videos",visible,cardWidth,gap,true,version,play){expanded=PlayerLibraryFilter.ALL.id}
                }
                if(savedFolders.isNotEmpty()&&!television)item {
                    ShelfHeading("Your folders"){folders(null)}
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(gap),contentPadding=PaddingValues(3.dp)) {
                        items(savedFolders,key={it.uri}) {folder->
                            LibraryFolderCard(folder,visible.firstOrNull {sameDocumentTree(it.media.uri,folder.uri)}?.media,
                                Modifier.width(if(maxWidth>=600.dp)(maxWidth-gap*3)/4 else 184.dp),version){folders(folder.uri)}
                        }
                    }
                }
                if(unwatched.isNotEmpty())item {LibraryShelf("Unwatched",unwatched,cardWidth,gap,true,version,play){expanded=PlayerLibraryFilter.UNWATCHED.id}}
            } else {
                item {Text(if(searching||query.isNotBlank())"Search results" else actualFilter.label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})}
                if(grid)items(filtered.chunked(columns),key={row->row.joinToString {it.media.key}}) {row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
                        row.forEach {entry->PlayerMediaCard(entry,Modifier.weight(1f),version,portrait=true){play(entry.media)}}
                        repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                    }
                } else items(filtered,key={it.media.key}) {entry->Row(verticalAlignment=Alignment.CenterVertically) {
                    PlayerNavigationRow(entry.media.title,
                        if(entry.duration>0)"${playerMediaTime(entry.position)} / ${playerMediaTime(entry.duration)}" else "",Icons.Outlined.Movie,Modifier.weight(1f)){play(entry.media)}
                    LibraryPlaylistAction(entry.media)
                }}
            }
            if(filtered.isEmpty()&&!busy)item {
                PlayerSection(if(emptyLibrary)"Your media, ready to play" else "No matching media") {
                    Text(if(page.indexUnavailable)"The saved folder index is unavailable. Check folder access or refresh it in Files."
                        else if(emptyLibrary)"Open a file or add a media folder to begin."
                        else "Try another filename or media filter.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    PlayerAction("Media folders",Icons.Outlined.CreateNewFolder){folders(null)}
                }
            }
            if(offsets.size>1||page.next!=null)item {app.ember.tv.ui.ButtonRow {
                if(offsets.size>1)PlayerAction("Previous page",enabled=!busy){offsets=offsets.dropLast(1)}
                page.next?.let {next->PlayerAction("Next page",enabled=!busy){offsets=offsets+next}}
            }}
            notice?.let {item {PlayerErrorText(it)}}
            error?.let {item {PlayerErrorText(it)}}
        }
    }
}

private fun sameDocumentTree(media:String,folder:String):Boolean=runCatching {
    val source=Uri.parse(media);val root=Uri.parse(folder)
    source.authority==root.authority && android.provider.DocumentsContract.getTreeDocumentId(source)==android.provider.DocumentsContract.getTreeDocumentId(root)
}.getOrDefault(false)

@Composable
private fun LibraryShelf(title:String,entries:List<PlayerLibraryMedia>,width:androidx.compose.ui.unit.Dp,gap:androidx.compose.ui.unit.Dp,portrait:Boolean,version:Int,play:(QueueMedia)->Unit,all:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        ShelfHeading(title,all)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(gap),contentPadding=PaddingValues(3.dp)) {
            items(entries,key={it.media.key}) {entry->PlayerMediaCard(entry,Modifier.width(width),version,portrait){play(entry.media)}}
        }
    }
}

@Composable
private fun ShelfHeading(title:String,all:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f).semantics {heading()},style=if(LocalPlayerTelevision.current)MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(LocalPlayerTelevision.current)Focusable(all,Modifier.size(28.dp).semantics {contentDescription="See all · $title"},radius=50,
            container=Color.Transparent,focusedContainer=MaterialTheme.colorScheme.primary,focusedContent=MaterialTheme.colorScheme.onPrimary,outline=false,focusedScale=1f) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.size(20.dp))}
        } else PlayerIconAction("See all · $title",Icons.AutoMirrored.Outlined.ArrowForward,onClick=all)
    }
}

@Composable
internal fun PlayerMediaCard(entry:PlayerLibraryMedia,modifier:Modifier,version:Int,portrait:Boolean=false,play:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    val television=LocalPlayerTelevision.current
    Focusable(play,modifier,container=scheme.surface,focusedContainer=scheme.surface,outline=true,card=true,focusedScale=1.025f) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(if(portrait){if(television)1f else 2f/3} else 16f/9).clip(RoundedCornerShape(10.dp))) {
                PlayerMediaArtwork(entry.media,version,Modifier.fillMaxSize())
                LibraryPlaylistAction(entry.media,Modifier.align(Alignment.TopStart).background(scheme.surface.copy(alpha=.9f),RoundedCornerShape(24.dp)))
                if(entry.watched)Icon(Icons.Outlined.CheckCircle,"Watched",Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).background(scheme.surface.copy(alpha=.85f),RoundedCornerShape(10.dp)),tint=scheme.primary)
                if(!portrait)Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.8f)))).padding(if(television)6.dp else 8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(entry.media.title,style=if(television)MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(entry.continuing) {
                        Text("${playerMediaTime(entry.position)} / ${playerMediaTime(entry.duration)}",style=MaterialTheme.typography.labelSmall,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress={entry.progress},modifier=Modifier.fillMaxWidth().height(3.dp),color=scheme.primary,trackColor=Color.White.copy(alpha=.3f),drawStopIndicator={})
                    }
                }
            }
            if(portrait)Column(Modifier.fillMaxWidth().padding(if(television)6.dp else 8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(entry.media.title,style=if(television)MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(entry.duration>0)Text(playerMediaTime(entry.duration),style=MaterialTheme.typography.labelSmall,color=scheme.onSurfaceVariant,maxLines=1)
            }
        }
    }
}

@Composable
private fun LibraryFolderCard(folder:DeviceFolder,media:QueueMedia?,modifier:Modifier,version:Int,open:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    Focusable(open,modifier,container=scheme.primaryContainer,focusedContainer=scheme.primaryContainer,card=true,focusedScale=1.025f) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.6f),contentAlignment=Alignment.Center) {
            if(media!=null)PlayerMediaArtwork(media,version,Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(scheme.primaryContainer.copy(alpha=.9f),scheme.surface.copy(alpha=.85f)))))
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Folder,null,Modifier.size(24.dp),tint=scheme.onPrimaryContainer)
                Text(folder.name,style=MaterialTheme.typography.titleSmall,color=scheme.onPrimaryContainer,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlayerMediaArtwork(media:QueueMedia,version:Int,modifier:Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,media.key,version) {
        if(!PrivateMedia.hidden(media.uri))value=withContext(Dispatchers.IO) {
            val uri=Uri.parse(media.uri)
            if(uri.scheme !in setOf("content","file"))return@withContext null
            var size=-1L;var modified=0L
            runCatching {context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE,android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use {c->if(c.moveToFirst()){size=c.getLong(0);modified=c.getLong(1)}}}
            DeviceThumbnails.load(context,DeviceFile(uri,uri,media.title,"video/*",size,modified,0),320)
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        bitmap?.let {Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
            ?: Icon(Icons.Outlined.Movie,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
