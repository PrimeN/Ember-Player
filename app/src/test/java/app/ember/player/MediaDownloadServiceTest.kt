package app.ember.player

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import app.ember.tv.tracker.SecretCipher
import app.ember.tv.tracker.SecretVault
import app.ember.tv.tracker.TokenStore
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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
class MediaDownloadServiceTest {
    private val context:Application get()=RuntimeEnvironment.getApplication()
    @Before fun setup() {
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
        context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).edit().clear().commit()
        PrivateMedia.reload(context)
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,null)}
    }
    private fun waitFor(condition:()->Boolean) {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<until) {
            shadowOf(Looper.getMainLooper()).idle()
            if(condition())return
            Thread.sleep(10)
        }
        fail("Download did not reach the expected state")
    }
    private fun start(service:MediaDownloadService,id:String,startId:Int)=service.onStartCommand(Intent(context,MediaDownloadService::class.java).putExtra("id",id),0,startId)
    @Test fun anotherRequestWaitsAndDuplicateStartsDoNotInterruptTheActiveTransfer() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=Robolectric.buildService(MediaDownloadService::class.java).create();val service=controller.get()
            try {
                start(service,first.id,1);waitFor {store.read().any {it.id==first.id&&it.state=="downloading"}}
                start(service,second.id,2);start(service,first.id,3)
                waitFor {store.read().any {it.id==second.id&&it.state=="queued"}}
                assertEquals(listOf("/first.mp4"),origin.requests.toList())
                origin.release.countDown()
                waitFor {store.read().all {it.state=="complete"}}
                waitFor {shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.count {it.extras.getString(Notification.EXTRA_TEXT)=="Download complete"}==2}
                assertEquals(listOf("/first.mp4","/second.mp4"),origin.requests.toList())
                assertEquals("0123456789",DownloadStore.file(context,first).readText())
                assertEquals("0123456789",DownloadStore.file(context,second).readText())
            }finally {origin.release.countDown();controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        }
    }
    @Test fun pausingStopsTheActiveTransferAndClearsWaitingRequests() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=Robolectric.buildService(MediaDownloadService::class.java).create();val service=controller.get()
            try {
                start(service,first.id,1);waitFor {store.read().any {it.id==first.id&&it.state=="downloading"}}
                start(service,second.id,2);waitFor {store.read().any {it.state=="queued"}}
                service.onStartCommand(Intent(context,MediaDownloadService::class.java).setAction("pause"),0,3)
                waitFor {store.read().all {it.state=="paused"}}
                assertEquals(listOf("/first.mp4"),origin.requests.toList())
                assertFalse(DownloadStore.file(context,first).exists())
            }finally {origin.release.countDown();controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        }
    }
    @Test fun removingAnActiveDownloadWaitsForItsWriterThenAdvancesTheQueue() {
        Origin().use {origin->
            val store=DownloadStore(context);val first=store.add("${origin.url}/first.mp4","first.mp4");val second=store.add("${origin.url}/second.mp4","second.mp4")
            val controller=Robolectric.buildService(MediaDownloadService::class.java).create();val service=controller.get()
            try {
                start(service,first.id,1);waitFor {store.read().any {it.id==first.id&&it.state=="downloading"}}
                start(service,second.id,2);waitFor {store.read().any {it.state=="queued"}}
                service.onStartCommand(Intent(context,MediaDownloadService::class.java).setAction("remove").putExtra("id",first.id),0,3)
                waitFor {store.read().singleOrNull()?.let {it.id==second.id&&it.state=="complete"}==true}
                assertFalse(DownloadStore.folder(context,first.id).exists())
                assertEquals("0123456789",DownloadStore.file(context,second).readText())
            }finally {origin.release.countDown();controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        }
    }
    @Test fun interruptedProcessStatesRemainResumable() {
        val store=DownloadStore(context);val item=store.add("https://media.example/video.mp4","video.mp4")
        for(state in listOf("queued","downloading")) {
            store.change(item.id){it.copy(state=state,session="previous process")}
            assertEquals("paused",store.read().single().state)
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
