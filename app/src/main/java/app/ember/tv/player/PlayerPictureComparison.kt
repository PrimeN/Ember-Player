package app.ember.tv.player

import app.ember.tv.R

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
    playerControlDialog(resources.getString(R.string.ui_compare_picture)) {dialog->
        var original by remember {mutableStateOf(false)}
        DisposableEffect(owner,visit){onDispose {if(player===owner && playbackVisit==visit)endPictureComparison()}}
        LaunchedEffect(visit){while(dialog.isShowing){if(player!==owner || playbackVisit!=visit){dialog.dismiss();break};delay(150)}}
        Text(if(original)resources.getString(R.string.ui_original_output) else resources.getString(R.string.ui_saved_picture_processing),style=androidx.tv.material3.MaterialTheme.typography.titleMedium)
        Text(resources.getString(R.string.ui_framing_and_playback_position_stay_the_same_closing_restores),color=Mute,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        ButtonRow {
            Chip(resources.getString(R.string.ui_original)){if(player===owner&&playbackVisit==visit){original=true;compareOriginal=true;applyEffectivePicture()}}
            Chip(resources.getString(R.string.ui_processed)){if(player===owner&&playbackVisit==visit){original=false;endPictureComparison()}}
        }
        app.ember.player.PlayerAction(resources.getString(R.string.ui_done),primary=true){dialog.dismiss()}
    }
}
