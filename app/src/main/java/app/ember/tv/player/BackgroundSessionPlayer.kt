package app.ember.tv.player

import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Controllers see opaque queue IDs and metadata, never transport URLs or request headers. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class BackgroundSessionPlayer(private val backend:()->Player?,private val source:()->EngineSource?,
    private val sessionId:()->String,private val queue:()->MediaQueue?,private val select:(Int)->Unit,
    private val step:(Int)->Unit,private val playing:(Boolean)->Unit,private val stop:()->Unit,private val artwork:()->ByteArray?={null}):SimpleBasePlayer(Looper.getMainLooper()) {
    private fun entries():List<Pair<Int,QueueMedia>> {
        val current=source() ?: return emptyList()
        if(current.urls.any {app.ember.player.PrivateMedia.hidden(it)})return emptyList()
        return (queue()?.items?.mapIndexed {index,item->index to item}
            ?: listOf(0 to QueueMedia(current.urls.first(),current.title,current.key)))
            .filterNot {app.ember.player.PrivateMedia.hidden(it.second.uri)}
    }
    private fun item(index:Int,media:QueueMedia):MediaItem {
        val current=source()?.key==media.key
        val metadata=(if(current)backend()?.mediaMetadata else null) ?: MediaMetadata.EMPTY
        val display=metadata.buildUpon().setTitle(media.title.filterNot(Char::isISOControl).take(180)).setIsBrowsable(false).setIsPlayable(true)
        if(current&&metadata.artworkData==null)artwork()?.let {display.setArtworkData(it,MediaMetadata.PICTURE_TYPE_FRONT_COVER)}
        return MediaItem.Builder().setMediaId("${sessionId()}:$index").setMediaMetadata(display.build()).build()
    }
    fun libraryItems()=entries().map {(index,media)->item(index,media)}
    fun refresh(){invalidateState()}
    override fun getState():State {
        val p=backend();val entries=entries()
        val commands=Player.Commands.Builder().addAll(Player.COMMAND_GET_CURRENT_MEDIA_ITEM,Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,Player.COMMAND_GET_VOLUME,Player.COMMAND_GET_AUDIO_ATTRIBUTES)
        if(p==null||entries.isEmpty())return State.Builder().setAvailableCommands(commands.build()).build()
        commands.addAll(Player.COMMAND_PLAY_PAUSE,Player.COMMAND_PREPARE,Player.COMMAND_STOP,Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_SET_SPEED_AND_PITCH,Player.COMMAND_SET_VOLUME,Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        val seekable=p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if(seekable)commands.addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,Player.COMMAND_SEEK_BACK,Player.COMMAND_SEEK_FORWARD,Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
        if(queue()?.hasNext { !app.ember.player.PrivateMedia.hidden(it.uri) }==true)commands.addAll(Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        if(seekable||entries.size>1)commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        if(queue()!=null)commands.addAll(Player.COMMAND_SET_REPEAT_MODE,Player.COMMAND_SET_SHUFFLE_MODE)
        val selected=entries.indexOfFirst {it.first==(queue()?.index ?: 0)}.coerceAtLeast(0)
        val playlist=entries.map {(index,media)->MediaItemData.Builder("${sessionId()}:$index").setMediaItem(item(index,media))
            .setIsSeekable(seekable).setDurationUs(if(index==entries[selected].first&&p.duration>0)p.duration*1000 else C.TIME_UNSET).build()}
        return State.Builder().setAvailableCommands(commands.build()).setPlaylist(playlist).setCurrentMediaItemIndex(selected)
            .setPlaybackState(p.playbackState).setPlayWhenReady(p.playWhenReady,Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackSuppressionReason(p.playbackSuppressionReason).setIsLoading(p.isLoading)
            .setContentPositionMs {p.currentPosition.coerceAtLeast(0)}.setContentBufferedPositionMs {p.bufferedPosition.coerceAtLeast(0)}
            .setPlaybackParameters(p.playbackParameters).setVolume(p.volume).setAudioAttributes(p.audioAttributes)
            .setRepeatMode(when(queue()?.repeat){"all"->Player.REPEAT_MODE_ALL;"one"->Player.REPEAT_MODE_ONE;else->Player.REPEAT_MODE_OFF})
            .setShuffleModeEnabled(queue()?.shuffle==true).build()
    }
    override fun handleSetPlayWhenReady(playWhenReady:Boolean):ListenableFuture<*> {playing(playWhenReady);return Futures.immediateVoidFuture()}
    override fun handlePrepare():ListenableFuture<*> {backend()?.prepare();return Futures.immediateVoidFuture()}
    override fun handleStop():ListenableFuture<*> {stop();return Futures.immediateVoidFuture()}
    override fun handleSetPlaybackParameters(playbackParameters:PlaybackParameters):ListenableFuture<*> {backend()?.playbackParameters=playbackParameters;return Futures.immediateVoidFuture()}
    override fun handleSetVolume(volume:Float):ListenableFuture<*> {backend()?.volume=volume;return Futures.immediateVoidFuture()}
    override fun handleSetRepeatMode(repeatMode:Int):ListenableFuture<*> {queue()?.repeat=when(repeatMode){Player.REPEAT_MODE_ALL->"all";Player.REPEAT_MODE_ONE->"one";else->"off"};return Futures.immediateVoidFuture()}
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled:Boolean):ListenableFuture<*> {queue()?.shuffle=shuffleModeEnabled;return Futures.immediateVoidFuture()}
    override fun handleSeek(mediaItemIndex:Int,positionMs:Long,seekCommand:Int):ListenableFuture<*> {
        when(seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM->step(1)
            Player.COMMAND_SEEK_TO_PREVIOUS,Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM->step(-1)
            else->{entries().getOrNull(mediaItemIndex)?.let {if(it.first!=(queue()?.index ?: 0))select(it.first)};backend()?.seekTo(positionMs.takeIf {it>=0} ?: 0)}
        }
        return Futures.immediateVoidFuture()
    }
    override fun handleSetMediaItems(mediaItems:List<MediaItem>,startIndex:Int,startPositionMs:Long):ListenableFuture<*> {
        val available=libraryItems().map {it.mediaId}.toSet()
        if(mediaItems.isEmpty()||mediaItems.any {it.mediaId !in available})return Futures.immediateFailedFuture<Void>(IllegalArgumentException("Choose an item from the active queue."))
        val requested=mediaItems.getOrNull(startIndex.takeIf {it>=0} ?: 0) ?: return Futures.immediateFailedFuture<Void>(IllegalArgumentException("Invalid queue position."))
        val index=requested.mediaId.substringAfterLast(':').toInt()
        if(queue()!=null)select(index)
        if(startPositionMs>=0)backend()?.seekTo(startPositionMs)
        return Futures.immediateVoidFuture()
    }
    override fun handleRelease():ListenableFuture<*> = Futures.immediateVoidFuture()
}
