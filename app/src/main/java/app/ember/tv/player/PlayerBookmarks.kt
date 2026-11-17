package app.ember.tv.player

import app.ember.tv.R

import androidx.lifecycle.lifecycleScope
import app.ember.tv.ui.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import android.widget.FrameLayout
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.showBookmarks() {
    if(privatePlayback){android.widget.Toast.makeText(this,resources.getString(R.string.ui_bookmarks_are_not_saved_for_private_playback),android.widget.Toast.LENGTH_SHORT).show();return}
    val key=resumeKey;val visit=playbackVisit
    val store=PlaybackBookmarks(this,localActivityScope)
    lifecycleScope.launch {
        val list=withContext(Dispatchers.IO){store.read(key)}
        if(visit!=playbackVisit || resumeKey!=key)return@launch
        showPlayerActions(resources.getString(R.string.ui_bookmarks),listOf(PlayerDialogAction(resources.getString(R.string.ui_save_current_position)) {
            if(visit==playbackVisit && resumeKey==key)player?.currentPosition?.let {at->bookmarkName(resources.getString(R.string.ui_bookmark_name),clock(at)) {label->
                lifecycleScope.launch {val saved=withContext(Dispatchers.IO){store.add(key,at,label)};if(!saved)android.widget.Toast.makeText(this@showBookmarks,resources.getString(R.string.ui_could_not_save_bookmark_limit_50_per_title_512),android.widget.Toast.LENGTH_LONG).show();if(visit==playbackVisit)showBookmarks()}
            }}
        })+list.map {bookmark->PlayerDialogAction("${clock(bookmark.positionMs)} · ${bookmark.name}") {
            showPlayerActions(bookmark.name,listOf(
                PlayerDialogAction(resources.getString(R.string.ui_play_from ,clock(bookmark.positionMs))) {if(visit==playbackVisit)player?.seekTo(bookmark.positionMs)},
                PlayerDialogAction(resources.getString(R.string.ui_rename)) {bookmarkName(resources.getString(R.string.ui_rename_bookmark),bookmark.name) {label->lifecycleScope.launch {val saved=withContext(Dispatchers.IO){store.rename(key,bookmark.id,label)};if(!saved)bookmarkWriteFailed();if(visit==playbackVisit)showBookmarks()}}},
                PlayerDialogAction(resources.getString(R.string.ui_remove),destructive=true) {lifecycleScope.launch {val saved=withContext(Dispatchers.IO){store.remove(key,bookmark.id)};if(!saved)bookmarkWriteFailed();if(visit==playbackVisit)showBookmarks()}},
            ))
        }},message=if(list.isEmpty())resources.getString(R.string.ui_save_a_scene_to_return_to_it_later) else null)
    }
}

/** Reuses the existing TV text field and dialog; the main player layout stays unchanged. */
internal fun PlayerActivity.bookmarkName(title:String,initial:String,saved:(String)->Unit) {
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
        var text by remember {mutableStateOf(initial)}
        TvDialog({dialog.dismiss()},width=400.dp,resizeForKeyboard=true) {
            val view=androidx.compose.ui.platform.LocalView.current
            DisposableEffect(view) {dialog.target=view;onDispose {dialog.target=null}}
            DialogHeading(title)
            TvTextField(text,{text=it.take(80)},Modifier.fillMaxWidth(),placeholder=resources.getString(R.string.ui_name))
            ButtonRow {app.ember.player.PlayerAction(resources.getString(R.string.ui_save),primary=true) {val value=PlaybackBookmarks.label(text);if(value.isNotEmpty()){dialog.dismiss();saved(value)}};app.ember.player.PlayerAction(resources.getString(R.string.ui_cancel)) {dialog.dismiss()}}
        }
    }}}
}

private fun PlayerActivity.bookmarkWriteFailed() {
    android.widget.Toast.makeText(this,resources.getString(R.string.ui_could_not_update_bookmark_please_try_again),android.widget.Toast.LENGTH_LONG).show()
}
