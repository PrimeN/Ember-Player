package app.ember.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.Focusable
import app.ember.tv.ui.TvTextField

internal data class PlayerSettingsCategory(val id:String,val label:String,val description:String,val icon:ImageVector)
internal val playerSettingsCategories=listOf(
    PlayerSettingsCategory("playback","Playback","Resume, controls, seeking",Icons.Outlined.PlayArrow),
    PlayerSettingsCategory("picture","Picture & HDR","Processing, display, decoding",Icons.Outlined.Image),
    PlayerSettingsCategory("audio","Audio","Output, tracks, sound effects",Icons.AutoMirrored.Outlined.VolumeUp),
    PlayerSettingsCategory("subtitles","Subtitles","Language, style, timing",Icons.Outlined.Subtitles),
    PlayerSettingsCategory("network","Network & cache","Streaming, buffering, cached data",Icons.Outlined.Wifi),
    PlayerSettingsCategory("appearance","Appearance","Theme, performance, layout",Icons.Outlined.Palette),
    PlayerSettingsCategory("privacy","Privacy","Private media, history, backups",Icons.Outlined.Lock),
    PlayerSettingsCategory("about","About","App version, credits, licenses",Icons.Outlined.Info),
)

@Composable
internal fun PlayerNavigationRow(title:String,description:String,icon:ImageVector,modifier:Modifier=Modifier,selected:Boolean=false,onClick:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    val fill=if(selected)scheme.primaryContainer else Color.Transparent
    val body:@Composable ()->Unit={
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(icon,null,Modifier.size(24.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(description.isNotBlank())Text(description,style=MaterialTheme.typography.bodySmall,color=if(LocalPlayerTelevision.current)LocalContentColor.current.copy(alpha=.8f) else scheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(20.dp))
        }
    }
    if(LocalPlayerTelevision.current)Focusable(onClick,modifier.fillMaxWidth().semantics {this.selected=selected},radius=14,container=fill,
        focusedContainer=scheme.primary,focusedContent=scheme.onPrimary,outline=false,focusedScale=1f,content=body)
    else Surface(onClick,modifier.fillMaxWidth().semantics {this.selected=selected},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),color=fill,
        contentColor=if(selected)scheme.onPrimaryContainer else scheme.onSurface,content=body)
}

@Composable
internal fun PlayerSettingsBrowser(close:()->Unit,content:@Composable (String)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf<String?>(null)}
    var anchor by rememberSaveable {mutableStateOf<String?>(null)}
    var returnId by rememberSaveable {mutableStateOf<String?>(null)}
    val overview=rememberLazyListState()
    var returnResult by rememberSaveable {mutableStateOf<String?>(null)}
    val rootFocus=remember {FocusRequester()}
    val searchFocus=remember {FocusRequester()}
    val television=LocalPlayerTelevision.current
    val current=playerSettingsCategories.firstOrNull {it.id==category}
    val results=remember(query){PlayerSettingsIndex.search(query)}
    fun back(){if(category!=null){category=null;anchor=null}else if(query.isNotEmpty())query="" else close()}
    BackHandler(enabled=category!=null||query.isNotEmpty()){back()}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal=if(LocalPlayerTelevision.current)32.dp else 16.dp)) {
        PlayerHeading(current?.label ?: "Settings",back=::back)
        if(current==null) {
            PlayerSearchField(query,{query=it.take(120)},"Search settings",searchFocus)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f,false).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).testTag("settingsCategories"),state=overview,contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(query.isBlank())items(playerSettingsCategories,key={it.id}) {item->
                    val request=item.id==(returnId ?: playerSettingsCategories.first().id)
                    PlayerNavigationRow(item.label,item.description,item.icon,if(request)Modifier.focusRequester(rootFocus) else Modifier) {returnId=item.id;category=item.id;anchor=null}
                } else {
                    if(results.isEmpty())item {Text("No settings match “$query”.",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    items(results,key={it.id}) {entry->
                        val group=playerSettingsCategories.first {it.id==entry.category}
                        PlayerNavigationRow(entry.title,group.label,group.icon,if(entry.id==(returnResult?.takeIf {id->results.any {it.id==id}} ?: results.firstOrNull()?.id))Modifier.focusRequester(rootFocus) else Modifier) {returnId=entry.category;returnResult=entry.id;category=entry.category;anchor=entry.id}
                    }
                }
            }
            LaunchedEffect(category){if(television){withFrameNanos {};runCatching {rootFocus.requestFocus()}}}
        } else BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wide=maxWidth>=840.dp
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                if(wide)LazyColumn(Modifier.width(224.dp).testTag("settingsCategories"),verticalArrangement=Arrangement.spacedBy(4.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                    items(playerSettingsCategories,key={it.id}) {item->PlayerNavigationRow(item.label,"",item.icon,selected=item.id==category){returnId=item.id;category=item.id;anchor=null}}
                }
                key(category) {
                    val sections=remember(category){PlayerSettingsIndex.sections.filter {it.category==category}}
                    val list=rememberLazyListState()
                    val headingFocus=remember {FocusRequester()}
                    LazyColumn(Modifier.weight(1f).testTag("settingsContent"),state=list,verticalArrangement=Arrangement.spacedBy(24.dp),contentPadding=PaddingValues(bottom=32.dp)) {
                        items(sections,key={it.id}) {section->
                            val target=section.id==(anchor ?: sections.first().id)
                            Box(Modifier.fillMaxWidth().then(if(target)Modifier.focusRequester(headingFocus) else Modifier)){content(section.id)}
                        }
                    }
                    LaunchedEffect(anchor){val index=sections.indexOfFirst {it.id==anchor}.coerceAtLeast(0);list.scrollToItem(index);if(television){withFrameNanos {};runCatching {headingFocus.requestFocus()}}}
                }
            }
        }
    }
}
