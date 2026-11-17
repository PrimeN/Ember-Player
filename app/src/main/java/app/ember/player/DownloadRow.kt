package app.ember.player

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.ButtonRow

internal fun downloadStateLabel(state:String)=when(state) {
    "queued"->"Waiting"
    "downloading"->"Downloading"
    "paused"->"Paused"
    "complete"->"Downloaded"
    "failed"->"Download failed"
    else->"Unavailable"
}
internal fun downloadProgress(item:MediaDownload):Float?=when {
    item.state=="complete"->1f
    item.total>0->(item.bytes.toDouble()/item.total).coerceIn(0.0,1.0).toFloat()
    else->null
}

@Composable
internal fun DownloadRow(item:MediaDownload,play:()->Unit,resume:()->Unit,remove:()->Unit) {
    val context=LocalContext.current
    val transferred=Formatter.formatShortFileSize(context,item.bytes.coerceAtLeast(0))
    val size=if(item.total>=0)"$transferred of ${Formatter.formatShortFileSize(context,item.total)}" else transferred
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(item.name,style=MaterialTheme.typography.titleSmall,maxLines=2)
        if(item.state=="failed")PlayerErrorText("${downloadStateLabel(item.state)} · $size")
        else PlayerStatusText("${downloadStateLabel(item.state)} · $size")
        val progress=downloadProgress(item)
        when {
            progress!=null->LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
            item.state=="downloading"->LinearProgressIndicator(Modifier.fillMaxWidth())
            item.state=="queued"->LinearProgressIndicator(progress={0f},modifier=Modifier.fillMaxWidth())
        }
        ButtonRow {
            if(item.state=="complete")PlayerAction("Play",primary=true,onClick=play)
            else if(item.state !in setOf("queued","downloading"))PlayerAction("Resume",primary=true,onClick=resume)
            PlayerAction("Remove",destructive=true,onClick=remove)
        }
    }
}
