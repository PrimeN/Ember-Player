package app.ember.player

import app.ember.tv.R

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import app.ember.tv.player.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun PlaylistEditor(context:Context,initial:SavedPlaylist,close:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val store=remember(context){SavedPlaylists(context)}
    val scope=rememberCoroutineScope()
    var playlist by remember(initial.id){mutableStateOf(initial)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var page by remember {mutableIntStateOf(0)}
    fun move(from:Int,to:Int) {
        if(busy)return
        busy=true
        scope.launch {
            try {
                val updated=withContext(Dispatchers.IO){if(store.move(playlist.id,from,to))store.read().firstOrNull {it.id==playlist.id} else null}
                if(updated==null)error=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage) else {playlist=updated;page=to/100;error=null}
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error=uiResources.getString(R.string.ui_could_not_update_encrypted_playlist_storage)}
            finally {busy=false}
        }
    }
    TvDialog({if(!busy)close()},width=620.dp) {
        DialogHeading(playlist.name)
        PlayerStatusText(uiResources.getString(R.string.ui_move_entries_earlier_or_later_your_resume_item_stays))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            itemsIndexed(playlist.entries.drop(page*100).take(100)) {offset,entry->
                val index=page*100+offset
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiResources.getString(R.string.ui_numbered_item ,index+1,entry.title),Modifier.weight(1f),style=androidx.tv.material3.MaterialTheme.typography.bodyLarge,maxLines=2)
                    PlayerIconAction(uiResources.getString(R.string.ui_move_earlier ,entry.title),Icons.Outlined.ArrowUpward,enabled=!busy&&index>0){move(index,index-1)}
                    PlayerIconAction(uiResources.getString(R.string.ui_move_later ,entry.title),Icons.Outlined.ArrowDownward,enabled=!busy&&index<playlist.entries.lastIndex){move(index,index+1)}
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
}
