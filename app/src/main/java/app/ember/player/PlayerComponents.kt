package app.ember.player

import app.ember.tv.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.ember.tv.player.BrowserPreview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.*

internal data class PlayerFeedback(val text:String,val error:Boolean=true)

@Composable
internal fun PlayerFeedbackText(message:PlayerFeedback) {
    if(message.error)PlayerErrorText(message.text) else PlayerStatusText(message.text)
}

@Composable
internal fun PlayerErrorText(message:String,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants=true){liveRegion=LiveRegionMode.Polite;error(message)},
        horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline,null,Modifier.size(20.dp),tint=androidx.tv.material3.MaterialTheme.colorScheme.error)
        Text(message,style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=androidx.tv.material3.MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun PlayerStatusText(message:String) {
    Text(message,style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier=Modifier.semantics {liveRegion=LiveRegionMode.Polite})
}

@Composable
internal fun PlayerConfirmationDialog(title:String,message:String,confirmLabel:String,close:()->Unit,busy:Boolean=false,content:@Composable ()->Unit={},confirm:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val cancelFocus=remember {FocusRequester()}
    val television=LocalPlayerTelevision.current
    TvDialog({if(!busy)close()},width=520.dp,resizeForKeyboard=true,scrollContent=true) {
        DialogHeading(title)
        Text(message,style=androidx.tv.material3.MaterialTheme.typography.bodyMedium)
        content()
        ButtonRow {
            PlayerAction(if(busy)uiResources.getString(R.string.ui_working) else confirmLabel,destructive=true,enabled=!busy,onClick=confirm)
            PlayerAction(uiResources.getString(R.string.ui_cancel),modifier=Modifier.focusRequester(cancelFocus),enabled=!busy,onClick=close)
        }
    }
    LaunchedEffect(television){if(television){withFrameNanos {};runCatching {cancelFocus.requestFocus()}}}
}

@Composable
internal fun PlayerFileRow(name:String,directory:Boolean,modifier:Modifier=Modifier,selected:Boolean=false,preview:BrowserPreview=BrowserPreview(),audio:Boolean=false,click:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scheme=MaterialTheme.colorScheme
    Focusable(click,modifier.fillMaxWidth().heightIn(min=48.dp).semantics {
        this.selected=selected;contentDescription=if(directory)uiResources.getString(R.string.ui_folder ,name) else name
    },container=if(selected)scheme.primaryContainer else scheme.surfaceVariant,focusedContainer=scheme.primary,
        contentColor=if(selected)scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        focusedContent=scheme.onPrimary,outline=false,focusedScale=1f) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(!directory&&preview.bitmap!=null)Image(preview.bitmap.asImageBitmap(),null,Modifier.size(80.dp,45.dp),contentScale=ContentScale.Crop)
            else Icon(if(directory)Icons.Outlined.Folder else if(audio)Icons.Outlined.AudioFile else Icons.Outlined.Movie,null,Modifier.size(if(directory)24.dp else 40.dp))
            Column(Modifier.weight(1f)) {
                Text(name,style=androidx.tv.material3.MaterialTheme.typography.bodyLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(!directory&&preview.duration>0)Text(playerMediaTime(preview.duration),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
            }
            if(selected)Icon(Icons.Outlined.Check,uiResources.getString(R.string.ui_selected),Modifier.size(20.dp))
        }
    }
}
