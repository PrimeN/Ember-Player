package app.ember.player

import app.ember.tv.R
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun PlaylistEditor(context:Context,initial:SavedPlaylist,store:SavedPlaylists?=null,close:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current
    val saved=remember(context,store){store ?: SavedPlaylists(context)}
    val scope=rememberCoroutineScope()
    var playlist by remember(initial.id){mutableStateOf(initial)}
    // Occurrences have separate identities, including duplicate playlist entries.
    var ids by remember(initial.id){mutableStateOf(initial.entries.indices.toList())}
    val targets=remember(initial.id){mutableMapOf<Pair<Int,String>,FocusRequester>()}
    var focused by remember {mutableStateOf<Pair<Int,String>?>(null)}
    var pendingFocus by remember {mutableStateOf<Pair<Int,String>?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var page by remember {mutableIntStateOf(0)}
    var removing by remember {mutableStateOf<Int?>(null)}
    val listState=rememberLazyListState()
    LaunchedEffect(pendingFocus) {
        pendingFocus?.let {target->
            val index=ids.indexOf(target.first)
            if(index>=0){listState.scrollToItem(index%100);withFrameNanos {};runCatching {targets[target]?.requestFocus()}}
            pendingFocus=null
        }
    }
    fun actionModifier(id:Int,action:String):Modifier {
        val target=id to action
        return Modifier.focusRequester(targets.getOrPut(target){FocusRequester()}).onFocusChanged {
            if(it.isFocused)focused=target else if(focused==target)focused=null
        }
    }
    fun move(from:Int,to:Int) {
        if(busy||to !in playlist.entries.indices)return
        val expected=playlist
        val target=ids[from] to if(to<from)"earlier" else "later"
        busy=true
        scope.launch {
            try {
                val updated=withContext(Dispatchers.IO){if(saved.move(expected.id,from,to,expected.entries))saved.readForDisplay().firstOrNull {it.id==expected.id} else null}
                if(updated==null)error=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage)
                else {
                    val nextIds=ids.toMutableList().apply {add(to,removeAt(from))}
                    // A concurrent editor may have saved after this move; reopen rather than guessing identities.
                    check(updated.entries==expected.entries.toMutableList().apply {add(to,removeAt(from))})
                    ids=nextIds;playlist=updated;page=to/100;error=null
                    if(focused==target)pendingFocus=target.first to when {
                        to==0&&target.second=="earlier"->"later"
                        to==updated.entries.lastIndex&&target.second=="later"->"earlier"
                        else->target.second
                    }
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage)}
            finally {busy=false}
        }
    }
    TvDialog({if(!busy)close()},width=620.dp) {
        DialogHeading(playlist.name)
        PlayerStatusText(uiResources.getString(R.string.ui_playlist_editor_help))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            itemsIndexed(playlist.entries.drop(page*100).take(100),key={offset,_->ids[page*100+offset]}) {offset,entry->
                val index=page*100+offset;val id=ids[index]
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiResources.getString(R.string.ui_numbered_item,index+1,entry.title),Modifier.weight(1f),style=androidx.tv.material3.MaterialTheme.typography.bodyLarge,maxLines=2)
                    PlayerIconAction(uiResources.getString(R.string.ui_move_earlier,entry.title),Icons.Outlined.ArrowUpward,actionModifier(id,"earlier"),enabled=index>0){move(index,index-1)}
                    PlayerIconAction(uiResources.getString(R.string.ui_move_later,entry.title),Icons.Outlined.ArrowDownward,actionModifier(id,"later"),enabled=index<playlist.entries.lastIndex){move(index,index+1)}
                    PlayerIconAction(uiResources.getString(R.string.ui_remove_playlist_entry,entry.title),Icons.Outlined.RemoveCircleOutline,actionModifier(id,"remove")){if(!busy)removing=index}
                }
            }
        }
        error?.let {PlayerErrorText(it)}
        ButtonRow {
            if(page>0)PlayerAction(uiResources.getString(R.string.ui_previous_page),enabled=!busy){page--}
            if((page+1)*100<playlist.entries.size)PlayerAction(uiResources.getString(R.string.ui_next_page),enabled=!busy){page++}
            PlayerAction(uiResources.getString(R.string.ui_done),primary=true,enabled=!busy,onClick=close)
        }
    }
    removing?.let {index->
        PlayerConfirmationDialog(uiResources.getString(R.string.ui_remove_playlist_entry_title),
            if(playlist.entries.size==1)uiResources.getString(R.string.ui_remove_last_playlist_entry) else uiResources.getString(R.string.ui_remove_playlist_entry_message,playlist.entries[index].title),
            uiResources.getString(R.string.ui_remove),{removing=null;pendingFocus=ids[index] to "remove"},busy) {
            if(!busy){
                val expected=playlist;busy=true
                scope.launch {
                    try {
                        val result=withContext(Dispatchers.IO) {
                            check(saved.removeEntry(expected.id,index,expected.entries))
                            saved.readForDisplay().firstOrNull {it.id==expected.id}
                        }
                        if(expected.entries.size==1){removing=null;close()}
                        else {
                            check(result!=null&&result.entries==expected.entries.filterIndexed {at,_->at!=index})
                            ids=ids.filterIndexed {at,_->at!=index};playlist=result
                            val next=index.coerceAtMost(result.entries.lastIndex)
                            page=next/100;removing=null;error=null;pendingFocus=ids[next] to "remove"
                        }
                    } catch(e:CancellationException){throw e}
                      catch(_:Exception){removing=null;error=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage)}
                    finally {busy=false}
                }
            }
        }
    }
}
