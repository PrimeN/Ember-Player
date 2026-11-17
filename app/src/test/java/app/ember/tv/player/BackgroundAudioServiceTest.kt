package app.ember.tv.player

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.app.Notification
import android.media.session.MediaSession
import androidx.media3.common.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35,36],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BackgroundAudioServiceTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    private val created=mutableListOf<FakeEngine>()
    private val defaultFactory=BackgroundAudioService.factory
    @Before fun setup(){BackgroundAudioService.factory={_,_->FakeEngine().also(created::add)}}
    @After fun cleanup(){BackgroundAudioService.stopCurrent();BackgroundAudioService.factory=defaultFactory}
    private fun request(queueId:String?=null)=BackgroundAudioRequest(EngineSource(listOf("https://example.test/movie.mp4"),"Movie","file:key"),
        PlaybackConfig(backgroundAudio=true),Intent(),queueId,"main","shared",20_000,1.25f,.8f)
    private fun service(id:String)=Robolectric.buildService(BackgroundAudioService::class.java).create().also {
        it.get().onStartCommand(Intent(context,BackgroundAudioService::class.java).putExtra(BackgroundAudioService.EXTRA_ID,id),0,1)
    }
    @Test fun stagingDoesNotCreateAnEngineAndCommitStartsItOnce() {
        val request=request();val id=BackgroundAudioService.stage(context,request)
        val controller=service(id)
        val session=controller.get().sessions.single()
        controller.get().onStartCommand(Intent(context,BackgroundAudioService::class.java).putExtra(BackgroundAudioService.EXTRA_ID,id),0,2)
        assertSame(session,controller.get().sessions.single())
        assertTrue(created.isEmpty())
        assertTrue(BackgroundAudioService.commit(id,request))
        assertFalse(BackgroundAudioService.commit(id,request))
        assertEquals(1,created.size);assertEquals(20_000L,created.single().position)
        controller.destroy()
        assertEquals(1,created.single().closed)
    }
    @Test fun cancellingStagingDoesNotReleaseTheForegroundQueue() {
        var closed=0
        val queue=MediaQueues.put(listOf(QueueMedia("https://example.test/a.mp4","A")),listOf(java.io.Closeable {closed++}))
        val request=request(queue);val id=BackgroundAudioService.stage(context,request)
        val controller=service(id)
        BackgroundAudioService.cancelStage(id)
        controller.destroy()
        assertEquals(0,closed);assertNotNull(MediaQueues.get(queue));assertTrue(created.isEmpty())
        assertFalse(BackgroundAudioService.commit(id,request))
        MediaQueues.release(queue);assertEquals(1,closed)
    }
    @Test fun stoppingCommittedPlaybackReleasesItsEngineAndQueueExactlyOnce() {
        var closed=0
        val queue=MediaQueues.put(listOf(QueueMedia("https://example.test/a.mp4","A")),listOf(java.io.Closeable {closed++}))
        val request=request(queue);val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        BackgroundAudioService.commit(id,request)
        shadowOf(Looper.getMainLooper()).idle()
        BackgroundAudioService.stopCurrent();controller.destroy()
        assertEquals(1,created.single().closed);assertEquals(1,closed);assertNull(MediaQueues.get(queue))
    }
    @Test fun notificationControlsPauseAndResumeAndReturningKeepsQueueOwnership() {
        var closed=0
        val queue=MediaQueues.put(listOf(QueueMedia("https://example.test/a.mp4","A")),listOf(java.io.Closeable {closed++}))
        val request=request(queue);val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        BackgroundAudioService.commit(id,request)
        shadowOf(Looper.getMainLooper()).idle()
        val engine=created.single();engine.position=23456
        val notification=shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.last()
        assertNotNull(notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION))
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)
        val pause=shadowOf(notification.actions[1].actionIntent).savedIntent
        controller.get().onStartCommand(pause,0,2)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(engine.wanted)
        val after=shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.last()
        controller.get().onStartCommand(shadowOf(after.actions[1].actionIntent).savedIntent,0,3)
        assertTrue(engine.wanted)
        val resumed=BackgroundAudioService.resume(id)
        assertEquals(23456L,resumed?.position)
        controller.destroy()
        assertEquals(1,engine.closed);assertEquals(0,closed);assertNotNull(MediaQueues.get(queue))
        MediaQueues.release(queue);assertEquals(1,closed)
    }
    @Test fun aStaleNotificationCannotStopAnotherSession() {
        val request=request();val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        BackgroundAudioService.commit(id,request)
        controller.get().onStartCommand(Intent(context,BackgroundAudioService::class.java).setAction("app.ember.player.STOP_AUDIO")
            .putExtra(BackgroundAudioService.EXTRA_ID,"stale"),0,2)
        assertEquals(0,created.single().closed)
        controller.destroy()
    }
    @Test fun unknownAndMissingStartIdsCannotStopActivePlayback() {
        val request=request();val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val engine=created.single()
            for(invalid in listOf("unknown","")) {
                controller.get().onStartCommand(Intent(context,BackgroundAudioService::class.java)
                    .putExtra(BackgroundAudioService.EXTRA_ID,invalid),0,2)
                assertEquals(0,engine.closed);assertEquals(id,BackgroundAudioService.nowPlaying.value?.id)
            }
            controller.get().onStartCommand(null,0,3)
            assertEquals(0,engine.closed);assertEquals(id,BackgroundAudioService.nowPlaying.value?.id)
        }finally {controller.destroy()}
    }
    @Test fun unrelatedControllersCannotReadTheQueueButTrustedControllersCanConnect() {
        val request=request();val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val session=controller.get().sessions.single()
            val library=BackgroundLibrary(context) {emptyList()}
            fun info(uid:Int,trusted:Boolean)=androidx.media3.session.MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                "controller.example",123,uid,1,1,trusted,android.os.Bundle.EMPTY,true)
            assertFalse(library.onConnectAsync(session,info(android.os.Process.myUid()+100,false)).get().isAccepted)
            assertTrue(library.onConnectAsync(session,info(android.os.Process.myUid()+100,true)).get().isAccepted)
            assertTrue(library.onConnectAsync(session,info(android.os.Process.myUid(),false)).get().isAccepted)
        }finally {controller.destroy()}
    }
    @Test fun verifiedCarControllersGetPlaybackAndLibraryCommandsWithoutNotificationAccess() {
        val request=request();val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val session=controller.get().sessions.single();val library=BackgroundLibrary(context) {emptyList()}
            val packages=listOf("com.google.android.projection.gearhead","com.android.car.media","com.android.car.carlauncher")
            for(name in packages) {
                val info=carController(name)
                val result=library.onConnectAsync(session,info).get()
                assertTrue(name,result.isAccepted)
                assertTrue(name,result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
                assertTrue(name,result.availablePlayerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
                assertTrue(name,result.availableSessionCommands.contains(androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
                assertFalse(name,library.onConnectAsync(session,carController(name,verified=false)).get().isAccepted)
            }
        }finally {controller.destroy()}
    }
    @Test fun legacyAutoControllersMustMatchTheirInstalledPackageUid() {
        val request=request();val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val session=controller.get().sessions.single();val library=BackgroundLibrary(context) {emptyList()}
            val uid=android.os.Process.myUid()+100
            val name="com.google.android.projection.gearhead"
            val info=android.content.pm.PackageInfo().apply {
                packageName=name
                applicationInfo=android.content.pm.ApplicationInfo().apply {packageName=name;this.uid=uid}
            }
            shadowOf(context.packageManager).installPackage(info)
            assertTrue(library.onConnectAsync(session,carController(name,verified=false,uid=uid)).get().isAccepted)
            assertFalse(library.onConnectAsync(session,carController(name,verified=false,uid=uid+1)).get().isAccepted)
            assertFalse(library.onConnectAsync(session,carController("controller.example",uid=uid)).get().isAccepted)
        }finally {controller.destroy()}
    }
    @Test fun anUntrustedAutoControllerCanResolveAVoiceSearchAndPlayOnlyAnExistingQueueItem() {
        val first=QueueMedia("https://example.test/first.mp3","First song")
        val second=QueueMedia("https://example.test/second.mp3","Second song")
        val queue=MediaQueues.put(listOf(first,second))
        val request=request(queue).copy(source=EngineSource(listOf(first.uri),first.title,first.key))
        val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val session=controller.get().sessions.single();val player=session.player
            val library=BackgroundLibrary(context) {(0 until player.mediaItemCount).map(player::getMediaItemAt)}
            val auto=carController("com.google.android.projection.gearhead")
            assertTrue(library.onConnectAsync(session,auto).get().isAccepted)
            val voice=MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("SECOND").build()).build()
            val resolved=library.onAddMediaItems(session,auto,listOf(voice)).get()
            player.setMediaItems(resolved,0,C.TIME_UNSET);player.play()
            assertEquals(1,MediaQueues.get(queue)!!.index);assertEquals("Second song",player.mediaMetadata.title)
            assertTrue(created.single().wanted)
            assertNull(resolved.single().localConfiguration)
            try {
                library.onAddMediaItems(session,auto,listOf(MediaItem.fromUri("https://unrelated.example/media"))).get()
                fail("Car controllers must stay inside the authorized queue")
            }catch(_:java.util.concurrent.ExecutionException){}
        }finally {controller.destroy()}
    }
    private fun carController(name:String,verified:Boolean=true,uid:Int=android.os.Process.myUid()+100)=
        androidx.media3.session.MediaSession.ControllerInfo.createTestOnlyControllerInfo(name,123,uid,1,1,false,android.os.Bundle.EMPTY,verified)
    @Test fun replacingTheNotificationDoesNotRetargetItsOldPendingIntents() {
        val first=request();val firstId=BackgroundAudioService.stage(context,first);val firstController=service(firstId)
        BackgroundAudioService.commit(firstId,first)
        shadowOf(Looper.getMainLooper()).idle()
        val oldStop=shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.last().deleteIntent
        BackgroundAudioService.stopCurrent();firstController.destroy()
        val next=request();val nextId=BackgroundAudioService.stage(context,next);val nextController=service(nextId)
        BackgroundAudioService.commit(nextId,next)
        nextController.get().onStartCommand(shadowOf(oldStop).savedIntent,0,2)
        assertEquals(0,created.last().closed)
        assertEquals(nextId,BackgroundAudioService.nowPlaying.value?.id)
        nextController.destroy()
    }
    @Test fun handoffCannotBeCommittedAfterCancellation() {
        val handoff=BackgroundHandoff("initial")
        assertFalse(handoff.close());assertFalse(handoff.commit("changed"));assertFalse(handoff.close())
        assertEquals(BackgroundHandoff.State.CLOSED,handoff.state)
        val owned=BackgroundHandoff("first")
        assertTrue(owned.commit("second"));assertEquals("second",owned.value);assertTrue(owned.close());assertFalse(owned.close())
    }
    @Test fun controllersSeeOpaqueQueueIdsAndCanControlExistingPlayback() {
        val queue=MediaQueues.put(listOf(QueueMedia("https://example.test/a.mp4?secret=first","First"),QueueMedia("https://example.test/b.mp4?secret=second","Second")))
        val request=request(queue).copy(source=EngineSource(listOf("https://example.test/a.mp4?secret=first"),"First",MediaQueues.get(queue)!!.current!!.key),artwork=byteArrayOf(1,2,3))
        val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val player=controller.get().sessions.single().player
            assertEquals(2,player.mediaItemCount)
            assertFalse(player.currentMediaItem!!.mediaId.contains("secret"))
            assertNull(player.currentMediaItem!!.localConfiguration)
            assertArrayEquals(byteArrayOf(1,2,3),player.mediaMetadata.artworkData)
            player.pause();assertFalse(created.single().wanted)
            player.play();assertTrue(created.single().wanted)
            player.seekToNextMediaItem();assertEquals("Second",player.mediaMetadata.title)
            assertEquals(1,MediaQueues.get(queue)!!.index)
            assertFalse(player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        }finally {controller.destroy()}
    }
    @Test fun libraryResolutionRejectsUnknownIdsAndStripsUntrustedTransportData() {
        val allowed=MediaItem.Builder().setMediaId("opaque:0").setMediaMetadata(MediaMetadata.Builder().setTitle("Allowed").build()).build()
        val library=BackgroundLibrary(context) {listOf(allowed)}
        val request=MediaItem.Builder().setMediaId("opaque:0").setUri("https://untrusted.example/media").build()
        assertSame(allowed,library.resolve(listOf(request)).single())
        try {library.resolve(listOf(request.buildUpon().setMediaId("unknown").build()));fail("Unknown sources must be rejected")}catch(_:IllegalArgumentException){}
    }
    @Test fun voiceSearchResolvesOnlyMatchingItemsFromTheActiveQueue() {
        val first=MediaItem.Builder().setMediaId("opaque:0").setMediaMetadata(MediaMetadata.Builder().setTitle("First song").build()).build()
        val second=first.buildUpon().setMediaId("opaque:1").setMediaMetadata(MediaMetadata.Builder().setTitle("Second song").build()).build()
        val library=BackgroundLibrary(context) {listOf(first,second)}
        fun request(query:String)=MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).build()).build()
        assertSame(second,library.resolve(listOf(request("SECOND"))).single())
        assertSame(second,library.resolve(listOf(request("")),second.mediaId).single())
        assertEquals(listOf(first,second),library.search("song"))
        try {library.resolve(listOf(request("https://untrusted.example/media")));fail("Voice requests must stay in the authorized queue")}catch(_:IllegalArgumentException){}
    }
    @Test fun savingProgressDoesNotRepostAnUnchangedMediaNotification() {
        val request=request();val id=BackgroundAudioService.stage(context,request);val controller=service(id)
        try {
            BackgroundAudioService.commit(id,request)
            val looper=shadowOf(Looper.getMainLooper());looper.idleFor(1,java.util.concurrent.TimeUnit.SECONDS)
            val notifications=shadowOf(context.getSystemService(NotificationManager::class.java))
            val before=notifications.getNotification(4104)
            assertNotNull(before)
            looper.idleFor(6,java.util.concurrent.TimeUnit.SECONDS)
            assertSame(before,notifications.getNotification(4104))
        }finally {controller.destroy()}
    }
    internal class FakeEngine:BackgroundAudioEngine {
        var position=0L;var wanted=true;var closed=0
        private val listeners=mutableListOf<Player.Listener>()
        override val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) {proxy,method,args->
            when(method.name) {
                "equals"->proxy===args?.firstOrNull();"hashCode"->System.identityHashCode(proxy);"toString"->"Background test player"
                "getCurrentPosition"->position;"getDuration"->120_000L;"getPlaybackState"->Player.STATE_READY
                "getBufferedPosition"->position
                "getPlaybackSuppressionReason"->Player.PLAYBACK_SUPPRESSION_REASON_NONE
                "isLoading"->false
                "getAudioAttributes"->AudioAttributes.DEFAULT
                "getMediaMetadata"->MediaMetadata.Builder().setTitle("Movie").build()
                "isPlaying","getPlayWhenReady"->wanted
                "getPlaybackParameters"->PlaybackParameters(1.25f)
                "getVolume"->.8f
                "getCurrentTracks"->Tracks.EMPTY
                "addListener"->{listeners+=args!![0] as Player.Listener;null}
                "removeListener"->{listeners-=args!![0] as Player.Listener;null}
                "setPlayWhenReady"->{wanted=args!![0] as Boolean;null}
                "play"->{wanted=true;null};"pause"->{wanted=false;null}
                "isCommandAvailable"->true
                "seekTo"->{position=args!!.last() as Long;null}
                else->null
            }
        } as Player
        override fun load(source:EngineSource,position:Long){this.position=position}
        override fun recover(error:PlaybackException)=false
        override fun close(){closed++}
    }
}
