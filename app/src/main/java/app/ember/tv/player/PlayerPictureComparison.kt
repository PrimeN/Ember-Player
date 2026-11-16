package app.ember.tv.player

import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.ui.*
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.endPictureComparison(){compareOriginal=false;applyEffectivePicture()}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.comparePicture() {
    val owner=player?:return
    val visit=playbackVisit
    playerControlDialog("Compare picture") {dialog->
        var original by remember {mutableStateOf(false)}
        DisposableEffect(owner,visit){onDispose {if(player===owner && playbackVisit==visit)endPictureComparison()}}
        LaunchedEffect(visit){while(dialog.isShowing){if(player!==owner || playbackVisit!=visit){dialog.dismiss();break};delay(150)}}
        Text(if(original)"Original output" else "Saved picture processing",fontSize=18.sp)
        Text("Framing and playback position stay the same. Closing restores your saved processing.",color=Mute,fontSize=13.sp)
        ButtonRow {
            Chip("Original"){if(player===owner&&playbackVisit==visit){original=true;compareOriginal=true;applyEffectivePicture()}}
            Chip("Processed"){if(player===owner&&playbackVisit==visit){original=false;endPictureComparison()}}
        }
        Chip("Done"){dialog.dismiss()}
    }
}
