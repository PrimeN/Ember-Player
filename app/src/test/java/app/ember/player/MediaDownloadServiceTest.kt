package app.ember.player

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.Handler
import app.ember.player.storage.SecretCipher
import app.ember.player.storage.SecretVault
import app.ember.player.storage.TokenStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import java.io.Closeable
import java.net.*
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MediaDownloadServiceTest {
    private val context:Application get()=RuntimeEnvironment.getApplication()
    private val controllers=mutableListOf<ServiceController<MediaDownloadService>>()
    private val destroyed=mutableSetOf<ServiceController<MediaDownloadService>>()
    @Before fun setup() {
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        val provider=context.packageManager.resolveContentProvider(context.packageName+".downloads",PackageManager.GET_META_DATA)!!
        FileProvider().attachInfo(context,provider)
        val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
        val cipher=object:SecretCipher {
            override fun encrypt(name:String,value:ByteArray):ByteArray {
                val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD(name.toByteArray());return c.iv+c.doFinal(value)
            }
            override fun decrypt(name:String,blob:ByteArray):ByteArray {
                val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,blob,0,12));c.updateAAD(name.toByteArray());return c.doFinal(blob,12,blob.size-12)
            }
        }
        val vault=SecretVault(context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE),cipher)
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,vault)}
        PrivateMedia.initialize(context);PrivateMedia.reload(context)
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        org.robolectric.shadows.ShadowStatFs.registerStats(context.filesDir,4096,4096,4096)
    }
    @After fun cleanup() {
        try {
            controllers.forEach(::destroy)
            context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).edit().clear().commit()
            PrivateMedia.reload(context)
        }finally {
            TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,null)}
            Dispatchers.resetMain()
        }
    }
    private fun waitFor(condition:()->Boolean):Boolean {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<until) {
            shadowOf(Looper.getMainLooper()).idle()
            if(condition())return true
            Thread.sleep(10)
        }
        return false
    }
    private fun create()=Robolectric.buildService(MediaDownloadService::class.java).create().also {controllers+=it}
    private fun destroy(controller:ServiceController<MediaDownloadService>) {
        if(!destroyed.add(controller))return
        val field=MediaDownloadService::class.java.getDeclaredField("scope").apply {isAccessible=true}
        val job=(field.get(controller.get()) as CoroutineScope).coroutineContext[Job]!!
        controller.destroy()
        assertTrue("Service cleanup should finish before the next test",waitFor {job.isCompleted})
    }
    private fun start(service:MediaDownloadService,id:String,startId:Int)=service.onStartCommand(Intent(context,MediaDownloadService::class.java).putExtra("id",id),0,startId)
    private fun startAndWait(service:MediaDownloadService,item:MediaDownload,startId:Int,origin:Origin) {
        start(service,item.id,startId)
        val started=waitFor {DownloadStore(context).read().any {it.id==item.id&&it.state=="downloading"}}
        assertTrue("First download should start; states=${DownloadStore(context).read().map {it.state}}, requests=${origin.requests.size}, private=${PrivateMedia.hidden(item.url)}",started)
    }
    @Test fun anotherRequestWaitsAndDuplicateStartsDoNotInterruptTheActiveTransfer() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=create();val service=controller.get()
            try {
                startAndWait(service,first,1,origin)
                start(service,second.id,2);start(service,first.id,3)
                assertTrue("Second download should wait",waitFor {store.read().any {it.id==second.id&&it.state=="queued"}})
                assertEquals(listOf("/first.mp4"),origin.requests.toList())
                origin.release.countDown()
                assertTrue("Both downloads should complete",waitFor {store.read().all {it.state=="complete"}})
                assertTrue("Both completions should be announced",waitFor {shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.count {it.extras.getString(Notification.EXTRA_TEXT)=="Download complete"}==2})
                assertEquals(listOf("/first.mp4","/second.mp4"),origin.requests.toList())
                assertEquals("0123456789",DownloadStore.file(context,first).readText())
                assertEquals("0123456789",DownloadStore.file(context,second).readText())
            }finally {origin.release.countDown();destroy(controller)}
        }
    }
    @Test fun pausingStopsTheActiveTransferAndClearsWaitingRequests() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=create();val service=controller.get()
            try {
                startAndWait(service,first,1,origin)
                start(service,second.id,2);assertTrue("Second download should wait",waitFor {store.read().any {it.state=="queued"}})
                service.onStartCommand(Intent(context,MediaDownloadService::class.java).setAction("pause"),0,3)
                assertTrue("Pause should clear the queue",waitFor {store.read().all {it.state=="paused"}})
                assertEquals(listOf("/first.mp4"),origin.requests.toList())
                assertFalse(DownloadStore.file(context,first).exists())
            }finally {origin.release.countDown();destroy(controller)}
        }
    }
    @Test fun removingAnActiveDownloadWaitsForItsWriterThenAdvancesTheQueue() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=create();val service=controller.get()
            try {
                startAndWait(service,first,1,origin)
                start(service,second.id,2);assertTrue("Second download should wait",waitFor {store.read().any {it.state=="queued"}})
                service.onStartCommand(Intent(context,MediaDownloadService::class.java).setAction("remove").putExtra("id",first.id),0,3)
                assertTrue("Removing the active file should advance the queue",waitFor {store.read().singleOrNull()?.let {it.id==second.id&&it.state=="complete"}==true})
                assertFalse(DownloadStore.folder(context,first.id).exists())
                assertEquals("0123456789",DownloadStore.file(context,second).readText())
            }finally {origin.release.countDown();destroy(controller)}
        }
    }
    @Test fun interruptedProcessStatesRemainResumable() {
        val store=DownloadStore(context);val item=store.add("https://media.example/video.mp4","video.mp4")
        for(state in listOf("queued","downloading")) {
            store.change(item.id){it.copy(state=state,session="previous process")}
            assertEquals("paused",store.read().single().state)
        }
    }
    @Test fun destroyingTheServiceLeavesWaitingDownloadsResumableInTheSameProcess() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=create();val service=controller.get()
            startAndWait(service,first,1,origin)
            start(service,second.id,2);assertTrue("Second download should wait",waitFor {store.read().any {it.state=="queued"}})
            destroy(controller)
            assertTrue("Stopping the service should pause the queue",waitFor {store.read().all {it.state=="paused"}})
            val resumed=create()
            try {
                start(resumed.get(),second.id,3)
                assertTrue("Queued file should remain resumable",waitFor {store.read().any {it.id==second.id&&it.state=="complete"}})
                assertEquals("paused",store.read().first {it.id==first.id}.state)
            }finally {origin.release.countDown();destroy(resumed)}
        }
    }
    private class Origin:Closeable {
        private val socket=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        private val workers=Executors.newCachedThreadPool {r->Thread(r,"Download fixture").apply {isDaemon=true}}
        val release=CountDownLatch(1)
        val requests:MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile private var closed=false
        val url="http://127.0.0.1:${socket.localPort}"
        init {workers.execute {while(!closed)try {val client=socket.accept();workers.execute {runCatching {serve(client)}}}catch(_:Exception){}}}
        private fun serve(client:Socket)=client.use {
            val input=it.getInputStream().bufferedReader();val start=input.readLine().split(' ')
            while(!input.readLine().isNullOrEmpty()){}
            requests+=start[1]
            val output=it.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: 10\r\nETag: fixture\r\nConnection: close\r\n\r\n01".toByteArray());output.flush()
            if(start[1]=="/first.mp4")release.await(10,TimeUnit.SECONDS)
            output.write("23456789".toByteArray());output.flush()
        }
        override fun close(){closed=true;release.countDown();socket.close();workers.shutdownNow()}
    }
}
