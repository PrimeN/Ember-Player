package app.ember.tv.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import android.widget.Toast

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.playFile(media:QueueMedia) {
    if(!queueItemAllowed(media)){Toast.makeText(this,"Open protected files from Private media.",Toast.LENGTH_LONG).show();return}
    savePosition();resumeKey=media.key;item=item.copy(key=resumeKey,title=media.title)
    resumed=false;ended=false;startOver=false;resumeFraction=-1f;audioUrl=null
    closeMenu();controls.sheet=null;controls.details=null
    controls.localUri=media.uri.takeIf {it.startsWith("content://")}
    if(subsInitialized)subs.config=subConfigFor(prefs.subtitleConfig)
    start(media.uri,media.title,media.title);refreshControlsTitle()
    platformSession?.nextAvailable(fileQueue?.hasNext(::queueItemAllowed)==true)
    fileQueue?.let {q->q.playlistId?.let {PlaylistProgress.save(applicationContext,watchProfileId,it,media.key)}}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.showFileQueue(page:Int=0) {
    val queue=fileQueue?:return
    val start=page.coerceAtLeast(0)*100
    val actions=queue.items.drop(start).take(100).mapIndexedNotNull { offset, media ->
        val index=start+offset
        if(!queueItemAllowed(media))return@mapIndexedNotNull null
        PlayerDialogAction(media.title, Icons.Outlined.PlayArrow, selected = index == queue.index) {
            val currentIndex = queue.items.indexOf(media)
            if (currentIndex >= 0) showPlayerActions(media.title, listOf(
                PlayerDialogAction("Play", Icons.Outlined.PlayArrow) { queue.select(queue.items.indexOf(media))?.let { playFile(it) } },
                PlayerDialogAction("Move earlier", Icons.Outlined.ArrowUpward) { val at = queue.items.indexOf(media); queue.move(at, at - 1); changed() },
                PlayerDialogAction("Move later", Icons.Outlined.ArrowDownward) { val at = queue.items.indexOf(media); queue.move(at, at + 1); changed() },
                PlayerDialogAction("Remove", Icons.Outlined.DeleteOutline, destructive = true) { queue.remove(queue.items.indexOf(media)); changed() },
            ))
        }
    }.toMutableList()
    if(start>0)actions.add(0,PlayerDialogAction("Previous page"){showFileQueue(page-1)})
    if(start+100<queue.items.size)actions.add(PlayerDialogAction("Next page"){showFileQueue(page+1)})
    showPlayerActions("Queue · ${start+1}–${minOf(start+100,queue.items.size)} of ${queue.items.size}",actions)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.onEpisodeEnded() {
    if(app.ember.tv.data.SleepTimer.itemEnded())return
    val next=fileQueue?.next(ended=true,accept=::queueItemAllowed)
    if(next!=null)playFile(next) else finish()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.playNext() {fileQueue?.next(accept=::queueItemAllowed)?.let(::playFile)}
