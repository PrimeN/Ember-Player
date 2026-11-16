package app.ember.tv.player

import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Text
import app.ember.tv.ui.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.goToPlaybackTime() {
    val p=player ?: return
    val visit=playbackVisit
    playbackValueDialog("Go to time",clock(p.currentPosition),"Seconds, MM:SS or HH:MM:SS · milliseconds are optional","Go") {text->
        seekToEnteredTime(text,p,visit)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.seekToEnteredTime(text:String,p:androidx.media3.exoplayer.ExoPlayer,visit:Long):String? {
    val target=PlaybackControlInput.position(text)
    return when {
        player!==p || playbackVisit!=visit -> "The media changed. Reopen this control."
        !p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) || p.duration<=0 -> "This source does not support seeking to a time."
        target==null -> "Enter a valid time, such as 90, 1:30 or 01:02:03.250."
        target>p.duration -> "Choose a time within ${clock(p.duration)}."
        else -> {if(playbackEngine!=null)playbackEngine?.navigate(target,false,coalesce=false) else PlaybackSeeking.seek(p,target,false);null}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.customPlaybackSpeed() {
    val p=player ?: return
    val visit=playbackVisit
    val initial=String.format(java.util.Locale.US,"%.3f",p.playbackParameters.speed).trimEnd('0').trimEnd('.')
    playbackValueDialog("Custom playback speed",initial,"Choose 0.25–4.0×, for example 1.1 or 1.35","Apply") {text->
        val value=PlaybackControlInput.speed(text)
        when {
            player!==p || playbackVisit!=visit -> "The media changed. Reopen this control."
            value==null -> "Enter a speed between 0.25 and 4.0."
            !p.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) -> "This source does not allow playback speed changes."
            else -> {p.setPlaybackSpeed(value);speed=value;changed();null}
        }
    }
}

/** Validation stays in the dialog. Closing or cancelling never applies a partial value. */
internal fun PlayerActivity.playbackValueDialog(title:String,initial:String,hint:String,button:String,submit:(String)->String?) {
    playerControlDialog(title) {dialog->
        var text by remember {mutableStateOf(initial)}
        var error by remember {mutableStateOf<String?>(null)}
        fun apply() {error=submit(text);if(error==null)dialog.dismiss()}
        Text(hint,color=Soft,fontSize=13.sp)
        TvTextField(text,{text=it.take(32);error=null},Modifier.fillMaxWidth(),onSubmit={apply()})
        error?.let {Text(it,fontSize=13.sp)}
        ButtonRow {Chip(button){apply()};Chip("Cancel"){dialog.dismiss()}}
    }
}

internal fun PlayerActivity.playerControlDialog(title:String,content:@Composable (PlayerActionDialog)->Unit) {
    actionDialog?.dismiss()
    val host=ComposeView(this)
    val dialog=PlayerActionDialog(host,currentFocus,{})
    actionDialog=dialog
    addContentView(host,FrameLayout.LayoutParams(1,1))
    host.setContent {
        val render=rememberDeviceRenderOptions(prefs.contentDensity,prefs.navigationGlass,prefs.artworkQuality,
            prefs.devicePerformance,prefs.customPerformance).copy(glassMethod=prefs.navigationGlassMethod)
        CompositionLocalProvider(LocalNight provides true,LocalRenderOptions provides render,
            LocalReduceMotion provides rememberReduceMotion()) {EmberTheme(prefs.accent,playerLayoutStyle) {
            TvDialog({dialog.dismiss()},width=440.dp,resizeForKeyboard=true) {
                val view=LocalView.current
                DisposableEffect(view){dialog.target=view;onDispose {dialog.target=null}}
                DialogHeading(title)
                content(dialog)
            }
        }}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.inspectFrames() {
    val engine=playbackEngine?:return
    val p=player?:return
    val wanted=p.playWhenReady
    engine.flushNavigation()
    p.playWhenReady=false
    if(engine.compatibility==null && !engine.switch()) {
        p.playWhenReady=wanted
        android.widget.Toast.makeText(this,"Frame inspection requires Compatibility playback and a supported audio track.",android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val visit=playbackVisit
    playerControlDialog("Frame inspection") {dialog->
        var at by remember {mutableLongStateOf(p.currentPosition)}
        var error by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(visit) {
            while(dialog.isShowing) {
                if(playbackVisit!=visit || player!==p){dialog.dismiss();break}
                at=p.currentPosition
                kotlinx.coroutines.delay(100)
            }
        }
        Text(String.format(java.util.Locale.US,"%s.%03d",clock(at),at.coerceAtLeast(0)%1000),fontSize=22.sp)
        Text("Playback stays paused. Backward steps may take longer while the previous keyframe is decoded.",color=Soft,fontSize=13.sp)
        fun step(forward:Boolean) {
            if(playbackVisit!=visit || player!==p){dialog.dismiss();return}
            error=if(engine.compatibility?.stepFrame(forward)==true)null else "This video does not support frame inspection."
        }
        ButtonRow {Chip("Previous frame"){step(false)};Chip("Next frame"){step(true)}}
        error?.let {Text(it,fontSize=13.sp)}
        Chip("Done"){dialog.dismiss()}
    }
}
