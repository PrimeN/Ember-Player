package app.ember.player

import android.app.Application
import android.content.Context
import app.ember.tv.player.*
import app.ember.player.storage.*
import app.ember.tv.data.*
import app.ember.tv.player.Kind
import app.ember.tv.player.PlaybackItem
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PrivateMediaTest {
    @Test @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun protectingAQueuedFileHidesItFromControllersAndProtectingTheCurrentFileStopsAudio() {
        val first=QueueMedia("https://media.example/first.mp3","First")
        val second=QueueMedia("https://media.example/second.mp3","Second")
        val queue=MediaQueues.put(listOf(first,second))
        val original=BackgroundAudioService.factory
        val engine=BackgroundAudioServiceTest.FakeEngine()
        BackgroundAudioService.factory={_,_->engine}
        val request=BackgroundAudioRequest(EngineSource(listOf(first.uri),first.title,first.key),PlaybackConfig(backgroundAudio=true),android.content.Intent(),queue,"main","shared",0,1f,1f)
        val id=BackgroundAudioService.stage(context,request)
        val controller=org.robolectric.Robolectric.buildService(BackgroundAudioService::class.java).create()
        try {
            controller.get().onStartCommand(android.content.Intent(context,BackgroundAudioService::class.java).putExtra(BackgroundAudioService.EXTRA_ID,id),0,1)
            assertTrue(BackgroundAudioService.commit(id,request))
            val player=controller.get().sessions.single().player
            assertEquals(2,player.mediaItemCount)
            PrivateMedia.configure("123456");PrivateMedia.add(PrivateItem(second.uri,second.title))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1,player.mediaItemCount);assertEquals(0,engine.closed)
            PrivateMedia.add(PrivateItem(first.uri,first.title))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1,engine.closed)
        }finally {controller.destroy();BackgroundAudioService.factory=original;MediaQueues.release(queue)}
    }
    @Test fun oldPinRecordsUpgradeOnlyAfterSuccessfulAuthentication() {
        val salt=ByteArray(16){it.toByte()}
        val spec=javax.crypto.spec.PBEKeySpec("123456".toCharArray(),salt,120000,256)
        val hash=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val legacy=java.util.Base64.getEncoder().encodeToString(salt)+":"+java.util.Base64.getEncoder().encodeToString(hash)
        assertTrue(TokenStore(context).putEncrypted(PrivateMedia.KEY,kotlinx.serialization.json.Json.encodeToString(PrivacyData.serializer(),PrivacyData(pin=legacy))))
        PrivateMedia.reload(context)
        assertFalse(PrivateMedia.authenticate("000000"));assertEquals(legacy,PrivateMedia.state.value.pin)
        assertTrue(PrivateMedia.authenticate("123456"));assertTrue(PrivateMedia.state.value.pin.startsWith("v2:"))
        PrivateMedia.reload(context)
        assertTrue(PrivateMedia.authenticate("123456"))
        assertFalse(PrivatePin.validRecord("v99:"+legacy))
        hash.fill(0)
    }
    @Test fun externalPublicFilesOpenWithoutUnlockingButProtectedFilesStillRequireThePin() {
        PrivateMedia.configure("123456")
        PrivateMedia.add(PrivateItem("content://files.example/private","Private"))
        PrivateMedia.lock()
        fun open(uri:String):android.content.Intent? {
            val intent=android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(uri))
            val activity=org.robolectric.Robolectric.buildActivity(OpenMediaActivity::class.java,intent).create().get()
            return org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        }
        assertNotNull(open("content://files.example/public"))
        assertNull(open("content://files.example/private"))
        assertFalse(PrivateMedia.unlocked.value)
        assertTrue(PrivateMedia.authenticate("123456"))
        assertNotNull(open("content://files.example/private"))
    }
    private val context:Context get()=RuntimeEnvironment.getApplication()
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
    }
    @After fun cleanup(){
        context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("playerSecurity",Context.MODE_PRIVATE).edit().clear().commit()
        PrivateMedia.reload(context)
        TokenStore::class.java.getDeclaredField("instance").apply {isAccessible=true;set(null,null)}
    }
    @Test fun pinUsesFreshSaltAndRejectsWrongOrMalformedInputs() {
        val a=PrivatePin.create("123456");val b=PrivatePin.create("123456")
        assertNotEquals(a,b);assertFalse(a.contains("123456"))
        assertTrue(PrivatePin.verify("123456",a));assertFalse(PrivatePin.verify("654321",a))
        assertFalse(PrivatePin.verify("123456","broken"));assertFalse(PrivatePin.valid("12345"));assertFalse(PrivatePin.valid("１２３４５６"))
    }
    @Test fun foldersMatchDescendantsAndDocumentAliasesWithoutSiblingPrefixes() {
        val folder=PrivateItem("content://docs/tree/drive%3AFilms","Films",true)
        assertTrue(PrivateMediaPolicy.contains(folder,"content://docs/document/drive%3AFilms%2Fmovie.mkv"))
        assertFalse(PrivateMediaPolicy.contains(folder,"content://docs/document/drive%3AFilms2%2Fmovie.mkv"))
        assertFalse(PrivateMediaPolicy.contains(folder,"content://other/document/drive%3AFilms%2Fmovie.mkv"))
        val removable=PrivateItem("content://com.android.externalstorage.documents/tree/ABCD-1234%3AFilms","Films",true)
        assertTrue(PrivateMediaPolicy.contains(removable,"content://com.android.externalstorage.documents/document/abcd-1234%3AFilms%2Fmovie.mkv"))
        val item=PrivateItem("content://docs/document/video","Video",aliases=listOf("content://media/drive/video/media/42"))
        assertTrue(PrivateMediaPolicy.contains(item,"content://media/drive/file/42"))
        assertFalse(PrivateMediaPolicy.contains(item,"content://media/other/file/42"))
    }
    @Test fun privateEntriesStayEncryptedAndHistoryIsPurgedWithoutRemovingOtherMedia() {
        val public=QueueMedia("content://docs/public","Public movie");val private=QueueMedia("content://docs/private","Private movie")
        assertTrue(MediaHistory(context).add(listOf(public,private)))
        PrivateMedia.configure("123456")
        PrivateMedia.add(PrivateItem(private.uri,private.title))
        assertEquals(listOf("Public movie"),MediaHistory(context).read().map {it.title})
        assertTrue(PrivateMedia.hidden(private.uri));assertFalse(PrivateMedia.hidden(public.uri))
        assertFalse(context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).all.values.any {it.toString().contains("Private movie")})
        PrivateMedia.lock();assertFalse(PrivateMedia.unlocked.value)
        assertTrue(PrivateMedia.authenticate("123456"));assertEquals("Private movie",PrivateMedia.state.value.items.single().name)
        PrivateMedia.remove(private.uri);assertFalse(PrivateMedia.hidden(private.uri))
    }
    @Test fun failedAttemptsPersistAndThrottleBeforeUnlock() {
        PrivateMedia.configure("123456");PrivateMedia.lock()
        repeat(5){assertFalse(PrivateMedia.authenticate("000000"))}
        PrivateMedia.reload(context)
        try {PrivateMedia.authenticate("123456");fail("Expected cooldown")}catch(_:IllegalStateException){}
        assertFalse(PrivateMedia.unlocked.value)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(60001))
        assertTrue(PrivateMedia.authenticate("123456"));assertEquals(0,PrivateMedia.state.value.failures)
    }
    @Test fun inaccessibleEncryptedStateFailsClosed() {
        PrivateMedia.configure("123456")
        context.getSharedPreferences(SecretVault.FILE,Context.MODE_PRIVATE).edit().clear().commit()
        PrivateMedia.reload(context)
        assertTrue(PrivateMedia.enabled);assertTrue(PrivateMedia.hidden("content://docs/anything"))
        try {PrivateMedia.authenticate("123456");fail("Expected locked storage")}catch(_:IllegalStateException){}
        assertFalse(PrivateMedia.unlocked.value)
    }
    @Test fun lockingPreventsPrivateLaunchAndReleasesPreparedResources() {
        PrivateMedia.configure("123456");PrivateMedia.add(PrivateItem("content://docs/private","Private"));PrivateMedia.lock()
        var closes=0
        try {MediaFiles.launch(context,listOf(QueueMedia("content://docs/private","Private")),listOf(java.io.Closeable {closes++}));fail("Expected private gate")}catch(_:IllegalStateException){}
        assertEquals(1,closes)
        try {PlayerBackups.snapshot(context);fail("Expected backup gate")}catch(_:IllegalStateException){}
    }
    @Test fun historyOffClearsActivityAndStopsNewResumeWritesButKeepsBookmarks() {
        MediaHistory(context).add(listOf(QueueMedia("content://docs/movie","Movie")))
        PositionStore(context).put("movie",100,1000)
        val bookmarks=PlaybackBookmarks(context,app.ember.tv.data.LocalActivity.SHARED)
        assertTrue(bookmarks.add("movie",10,"Scene"))
        PrivateMedia.recordHistory(false)
        assertTrue(MediaHistory(context).read().isEmpty());assertNull(PositionStore(context).get("movie"))
        PositionStore(context).put("other",100,1000);assertNull(PositionStore(context).get("other"))
        assertEquals("Scene",bookmarks.read("movie").single().name)
    }
    @Test fun privateTreeGrantsProtectChildrenWithOpaqueDocumentIds() {
        val folder=PrivateItem("content://docs/tree/opaque-parent","Private folder",true)
        val child="content://docs/tree/opaque-parent/document/unrelated-child-id"
        val other="content://docs/tree/other-parent/document/unrelated-child-id"
        assertTrue(PrivateMediaPolicy.contains(folder,child));assertFalse(PrivateMediaPolicy.contains(folder,other))
        val index=PrivateMediaIndex(listOf(folder))
        assertTrue(index.hidden(child));assertFalse(index.hidden(other))
    }
    @Test fun cachedIdentityIndexKeepsFolderAndAliasRules() {
        val items=listOf(PrivateItem("content://docs/tree/root%3ASecret","Secret",true),PrivateItem("content://docs/document/video","Video",aliases=listOf("content://media/drive/video/media/42")))
        val index=PrivateMediaIndex(items)
        for(uri in listOf("content://docs/document/root%3ASecret%2Fmovie.mkv","content://docs/document/root%3ASecret2%2Fmovie.mkv","content://media/drive/file/42","content://media/drive/file/43","content://other/document/video"))
            assertEquals(items.any {PrivateMediaPolicy.contains(it,uri)},index.hidden(uri))
    }
    @Test fun privateImportsCommitAsOneValidatedBatch(){
        PrivateMedia.configure("123456")
        val valid=PrivateItem("content://docs/valid","Valid")
        try {PrivateMedia.addAll(listOf(valid,PrivateItem("file:///invalid","Invalid")));fail("Expected rejected batch")}catch(_:IllegalArgumentException){}
        assertTrue(PrivateMedia.state.value.items.isEmpty())
        PrivateMedia.addAll(listOf(valid,valid));assertEquals(listOf(valid.uri),PrivateMedia.state.value.items.map {it.uri})
    }
    @Test fun latePinVerificationCannotUnlockAfterTheAppWasLocked(){
        val session=PrivateSession();val token=session.token();session.lock()
        assertFalse(session.unlock(token));assertFalse(session.unlocked.value)
        assertTrue(session.unlock(session.token()));assertTrue(session.unlocked.value)
    }
    @Test fun oldPrivacySettingsDefaultSafelyAndRemovingProtectionRequiresUnlock() {
        PrivateMedia.configure("123456");PrivateMedia.lock()
        try {PrivateMedia.removeProtection();fail("Expected PIN gate")}catch(_:IllegalStateException){}
        assertTrue(PrivateMedia.authenticate("123456"));PrivateMedia.removeProtection();assertFalse(PrivateMedia.enabled)
        assertTrue(kotlinx.serialization.json.Json.decodeFromString<PrivacyData>("{}").history)
    }
}
