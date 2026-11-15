package app.ember.player

import android.app.Application
import app.ember.tv.data.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.*

class PlayerApplication:Application(),ImageLoaderFactory {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        PrivateMedia.install(this)
        CrashLog.install(this)
        app.ember.tv.remote.RemoteBridge.install(this)
        Net.init(this)
        CacheManagement.init(this)
        LocalLibrary.init(this)
        scope.launch {ApiKeys.init(this@PlayerApplication)}
    }
    override fun newImageLoader()=ImageLoader.Builder(this)
        .okHttpClient {Net.artworkClient()}.allowRgb565(true)
        .memoryCache {coil.memory.MemoryCache.Builder(this).maxSizePercent(.12).build()}
        .diskCache {coil.disk.DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(128L*1024*1024).build()}
        .components {add(app.ember.tv.mediaserver.ServerArtwork.Factory())}
        .build()
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        app.ember.tv.ui.RuntimeBudgets.trim(level)
        if(app.ember.tv.ui.RuntimeBudgets.pressureLevel(level))CacheManagement.trimMemory()
    }
}
