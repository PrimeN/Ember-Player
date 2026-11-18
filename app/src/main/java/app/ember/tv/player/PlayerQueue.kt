package app.ember.tv.player

import app.ember.tv.R

import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import android.widget.Toast

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.playFile(media:QueueMedia) {
    if(!queueItemAllowed(media)){Toast.makeText(this,resources.getString(R.string.ui_open_protected_files_from_private_media),Toast.LENGTH_LONG).show();return}
    savePosition();resumeKey=media.key;item=item.copy(key=resumeKey,title=media.title)
    resumed=false;ended=false;startOver=false;resumeFraction=-1f;audioUrl=null
    closeMenu();controls.sheet=null;controls.details=null
    controls.localUri=media.uri.takeIf {it.startsWith("content://")}
    if(subsInitialized)subs.config=subConfigFor(prefs.subtitleConfig)
    start(media.uri,media.title,media.title);refreshControlsTitle()
    platformSession?.nextAvailable(fileQueue?.hasNext(::queueItemAllowed)==true)
    fileQueue?.let {q->q.playlistId?.let {id->q.originalIndex()?.let {at->PlaylistProgress.save(applicationContext,watchProfileId,id,media.key,at)}}}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.showFileQueue(page:Int=0) {
    val queue=fileQueue?:return
    val start=page.coerceAtLeast(0)*100
    val actions=queue.items.drop(start).take(100).mapIndexedNotNull { offset, media ->
        val index=start+offset
        if(!queueItemAllowed(media))return@mapIndexedNotNull null
        val occurrence=queue.occurrence(index) ?: return@mapIndexedNotNull null
        PlayerDialogAction(media.title, Icons.Outlined.PlayArrow, selected = index == queue.index) {
            val currentIndex = queue.indexOfOccurrence(occurrence)
            if (currentIndex >= 0) showPlayerActions(media.title, listOf(
                PlayerDialogAction(resources.getString(R.string.ui_play), Icons.Outlined.PlayArrow) { queue.select(queue.indexOfOccurrence(occurrence))?.let { playFile(it) } },
                PlayerDialogAction(resources.getString(R.string.ui_move_earlier_07dd6f), Icons.Outlined.ArrowUpward) { val at = queue.indexOfOccurrence(occurrence); queue.move(at, at - 1); changed() },
                PlayerDialogAction(resources.getString(R.string.ui_move_later_bb4c84), Icons.Outlined.ArrowDownward) { val at = queue.indexOfOccurrence(occurrence); queue.move(at, at + 1); changed() },
                PlayerDialogAction(resources.getString(R.string.ui_remove), Icons.Outlined.DeleteOutline, destructive = true) { queue.remove(queue.indexOfOccurrence(occurrence)); changed() },
            ))
        }
    }.toMutableList()
    if(queue.items.isNotEmpty()&&queue.items.all {queueItemAllowed(it)&&it.identity?.startsWith(DirectUsbIdentity.PREFIX)!=true})actions.add(0,PlayerDialogAction(resources.getString(R.string.ui_save_queue_to_playlist),Icons.AutoMirrored.Outlined.PlaylistAdd) {
        val snapshot=queue.items.toList();val index=queue.index;val repeat=queue.repeat;val shuffle=queue.shuffle
        playerControlDialog(resources.getString(R.string.ui_add_queue_to_playlist)) {dialog->
            app.ember.player.PlaylistPickerContent(this,snapshot,index,repeat,shuffle){dialog.dismiss()}
        }
    })
    if(start>0)actions.add(0,PlayerDialogAction(resources.getString(R.string.ui_previous_page)){showFileQueue(page-1)})
    if(start+100<queue.items.size)actions.add(PlayerDialogAction(resources.getString(R.string.ui_next_page)){showFileQueue(page+1)})
    showPlayerActions(resources.getString(R.string.ui_queue_of ,(start+1).toString(),(minOf(start+100,queue.items.size)).toString(),queue.items.size.toString()),actions)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.onEpisodeEnded() {
    if(app.ember.tv.data.SleepTimer.itemEnded())return
    val next=fileQueue?.next(ended=true,accept=::queueItemAllowed)
    if(next!=null)playFile(next) else finish()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.playNext() {fileQueue?.next(accept=::queueItemAllowed)?.let(::playFile)}
