package app.ember.tv.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class BackgroundLibrary(private val items:()->List<MediaItem>):MediaLibrarySession.Callback {
    private val root=MediaItem.Builder().setMediaId(ROOT).setMediaMetadata(MediaMetadata.Builder()
        .setTitle("Current playback").setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()
    override fun onGetLibraryRoot(session:MediaLibrarySession,browser:MediaSession.ControllerInfo,params:LibraryParams?):ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(root,params))
    override fun onGetItem(session:MediaLibrarySession,browser:MediaSession.ControllerInfo,mediaId:String):ListenableFuture<LibraryResult<MediaItem>> {
        val item=if(mediaId==ROOT)root else items().firstOrNull {it.mediaId==mediaId}
        return Futures.immediateFuture(if(item==null)LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(item,null))
    }
    override fun onGetChildren(session:MediaLibrarySession,browser:MediaSession.ControllerInfo,parentId:String,page:Int,pageSize:Int,params:LibraryParams?):ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if(parentId!=ROOT||page<0||pageSize<1)return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        val values=items();val start=(page.toLong()*pageSize).coerceAtMost(values.size.toLong()).toInt()
        val end=(start.toLong()+pageSize).coerceAtMost(values.size.toLong()).toInt()
        return Futures.immediateFuture(LibraryResult.ofItemList(values.subList(start,end),params))
    }
    override fun onAddMediaItems(session:MediaSession,controller:MediaSession.ControllerInfo,mediaItems:List<MediaItem>):ListenableFuture<List<MediaItem>> {
        return try {Futures.immediateFuture(resolve(mediaItems))}catch(e:IllegalArgumentException){Futures.immediateFailedFuture(e)}
    }
    internal fun resolve(mediaItems:List<MediaItem>):List<MediaItem> {
        val allowed=items().associateBy {it.mediaId}
        require(mediaItems.isNotEmpty()&&mediaItems.all {it.mediaId in allowed}){"Choose an item from the active queue."}
        return mediaItems.map {allowed.getValue(it.mediaId)}
    }
    companion object {const val ROOT="current-playback"}
}
