package app.ember.tv.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Small memory-only thumbnails: browsing never leaves private video frames in a disk cache. */
internal object DeviceThumbnails {
    private val memory = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failed = object : LruCache<String, Boolean>(100) {}
    private val gate = Semaphore(2)
    fun clear() { memory.evictAll(); failed.evictAll() }
    suspend fun load(context: Context, file: DeviceFile, requestedWidth:Int=160): Bitmap? {
        if (file.directory || file.mime.startsWith("audio/")) return null
        val width=requestedWidth.coerceIn(160,320)
        val key = "${file.uri}|${file.modified}|${file.size}|$width"
        memory.get(key)?.let { return it }
        if (failed.get(key) == true) return null
        return gate.withPermit { withContext(Dispatchers.IO) {
            memory.get(key) ?: runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, file.uri)
                    retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, width*9/16)
                }
            }.getOrNull()?.also { memory.put(key, it) } ?: run {failed.put(key, true); null}
        } }
    }
}

@Composable
internal fun DeviceThumbnail(file: DeviceFile) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, file.uri, file.modified, file.size) {value = DeviceThumbnails.load(context, file)}
    val modifier = Modifier.size(64.dp, 36.dp)
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Image(if (file.directory) Icons.Outlined.Folder else Icons.Outlined.Movie, contentDescription = null, modifier = modifier)
}
