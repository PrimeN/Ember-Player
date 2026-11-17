package app.ember.tv.player

import app.ember.tv.R

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
    playbackValueDialog(resources.getString(R.string.ui_go_to_time),clock(p.currentPosition),resources.getString(R.string.ui_seconds_mm_ss_or_hh_mm_ss_milliseconds_are),resources.getString(R.string.ui_go)) {text->
        seekToEnteredTime(text,p,visit)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.seekToEnteredTime(text:String,p:androidx.media3.exoplayer.ExoPlayer,visit:Long):String? {
    val target=PlaybackControlInput.position(text)
    return when {
        player!==p || playbackVisit!=visit -> resources.getString(R.string.ui_the_media_changed_reopen_this_control)
        !p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) || p.duration<=0 -> resources.getString(R.string.ui_this_source_does_not_support_seeking_to_a_time)
        target==null -> resources.getString(R.string.ui_enter_a_valid_time_such_as_90_1_30)
        target>p.duration -> resources.getString(R.string.ui_choose_a_time_within ,clock(p.duration))
        else -> {if(playbackEngine!=null)playbackEngine?.navigate(target,false,coalesce=false) else PlaybackSeeking.seek(p,target,false);null}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.customPlaybackSpeed() {
    val p=player ?: return
    val visit=playbackVisit
    val initial=String.format(java.util.Locale.US,"%.3f",p.playbackParameters.speed).trimEnd('0').trimEnd('.')
    playbackValueDialog(resources.getString(R.string.ui_custom_playback_speed),initial,resources.getString(R.string.ui_choose_0_25_4_0_for_example_1_1),resources.getString(R.string.ui_apply_cfea41)) {text->
        val value=PlaybackControlInput.speed(text)
        when {
            player!==p || playbackVisit!=visit -> resources.getString(R.string.ui_the_media_changed_reopen_this_control)
            value==null -> resources.getString(R.string.ui_enter_a_speed_between_0_25_and_4_0)
            !p.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) -> resources.getString(R.string.ui_this_source_does_not_allow_playback_speed_changes)
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
        Text(hint,color=Soft,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        TvTextField(text,{text=it.take(32);error=null},Modifier.fillMaxWidth(),onSubmit={apply()},startEditing=!PlayerTouchControls.isTelevision(this@playbackValueDialog))
        error?.let {app.ember.player.PlayerErrorText(it)}
        ButtonRow {app.ember.player.PlayerAction(button,primary=true){apply()};app.ember.player.PlayerAction(resources.getString(R.string.ui_cancel)){dialog.dismiss()}}
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
            TvDialog({dialog.dismiss()},width=440.dp,resizeForKeyboard=true,scrollContent=true) {
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
        android.widget.Toast.makeText(this,resources.getString(R.string.ui_frame_inspection_requires_compatibility_playback_and_a_supported_audio),android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val visit=playbackVisit
    playerControlDialog(resources.getString(R.string.ui_frame_inspection)) {dialog->
        var at by remember {mutableLongStateOf(p.currentPosition)}
        var error by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(visit) {
            while(dialog.isShowing) {
                if(playbackVisit!=visit || player!==p){dialog.dismiss();break}
                at=p.currentPosition
                kotlinx.coroutines.delay(100)
            }
        }
        Text(String.format(java.util.Locale.US,"%s.%03d",clock(at),at.coerceAtLeast(0)%1000),style=androidx.tv.material3.MaterialTheme.typography.headlineSmall)
        Text(resources.getString(R.string.ui_playback_stays_paused_backward_steps_may_take_longer_while),color=Soft,style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
        fun step(forward:Boolean) {
            if(playbackVisit!=visit || player!==p){dialog.dismiss();return}
            error=if(engine.compatibility?.stepFrame(forward)==true)null else resources.getString(R.string.ui_this_video_does_not_support_frame_inspection)
        }
        ButtonRow {Chip(resources.getString(R.string.ui_previous_frame)){step(false)};Chip(resources.getString(R.string.ui_next_frame)){step(true)}}
        error?.let {app.ember.player.PlayerErrorText(it)}
        app.ember.player.PlayerAction(resources.getString(R.string.ui_done),primary=true){dialog.dismiss()}
    }
}
