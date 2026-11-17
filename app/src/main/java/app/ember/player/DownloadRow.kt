package app.ember.player

import app.ember.tv.R

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.ButtonRow

internal fun downloadStateLabel(state:String)=when(state) {
    "queued"->R.string.ui_waiting
    "downloading"->R.string.ui_downloading_9b4592
    "paused"->R.string.ui_paused
    "complete"->R.string.ui_downloaded
    "failed"->R.string.ui_download_failed
    else->R.string.ui_unavailable
}
internal fun downloadProgress(item:MediaDownload):Float?=when {
    item.state=="complete"->1f
    item.total>0->(item.bytes.toDouble()/item.total).coerceIn(0.0,1.0).toFloat()
    else->null
}

@Composable
internal fun DownloadRow(item:MediaDownload,play:()->Unit,resume:()->Unit,remove:()->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val context=LocalContext.current
    val transferred=Formatter.formatShortFileSize(context,item.bytes.coerceAtLeast(0))
    val size=if(item.total>=0)uiResources.getString(R.string.ui_of ,transferred,Formatter.formatShortFileSize(context,item.total)) else transferred
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(item.name,style=MaterialTheme.typography.titleSmall,maxLines=2)
        if(item.state=="failed")PlayerErrorText(uiResources.getString(R.string.ui_label_value ,uiResources.getString(downloadStateLabel(item.state)),size))
        else PlayerStatusText(uiResources.getString(R.string.ui_label_value ,uiResources.getString(downloadStateLabel(item.state)),size))
        val progress=downloadProgress(item)
        when {
            progress!=null->LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
            item.state=="downloading"->LinearProgressIndicator(Modifier.fillMaxWidth())
            item.state=="queued"->LinearProgressIndicator(progress={0f},modifier=Modifier.fillMaxWidth())
        }
        ButtonRow {
            if(item.state=="complete")PlayerAction(uiResources.getString(R.string.ui_play),primary=true,onClick=play)
            else if(item.state !in setOf("queued","downloading"))PlayerAction(uiResources.getString(R.string.ui_resume),primary=true,onClick=resume)
            PlayerAction(uiResources.getString(R.string.ui_remove),destructive=true,onClick=remove)
        }
    }
}
