package app.ember.player

import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Test

class StandaloneMediaTest {
    @Test fun externalMediaRequiresAValidMediaTransportAndBoundsItsInput() {
        listOf("https://media.example/movie.mkv","rtsp://192.0.2.1:554/live","content://documents.example/item/7","udp://239.1.1.1:1234").forEach {assertTrue(it,ExternalMedia.allowed(it))}
        listOf("file:///data/data/app.ember.player/shared_prefs/ember.xml","javascript:alert(1)","data:text/plain,one","https:///file","https://media.example:99999/file","content:/missing","https://host/path\r\nHeader:value","https://host/"+"x".repeat(16384)).forEach {assertFalse(it.take(80),ExternalMedia.allowed(it))}
    }
    @Test fun encryptedHistoryIsBoundedDeduplicatedAndCanBeReloaded() {
        var data:String?=null
        val history=MediaHistory({data},{value->data=value;true})
        repeat(25){assertTrue(history.add(listOf(QueueMedia("https://media.example/$it.mkv","Video $it"))))}
        val loaded=MediaHistory({data},{false}).read()
        assertEquals(20,loaded.size);assertEquals("Video 24",loaded.first().title)
        history.add(listOf(QueueMedia("https://media.example/12.mkv","Renamed")))
        assertEquals(20,history.read().size);assertEquals("Renamed",history.read().first().title)
        assertTrue(history.clear());assertTrue(history.read().isEmpty())
    }
    @Test fun unavailableEncryptedStorageDoesNotClaimHistoryWasSavedOrClearTheOldHistory() {
        var data:String?=null
        val existing=MediaHistory({data},{value->data=value;true})
        existing.add(listOf(QueueMedia("content://files.example/1","One")))
        val failing=MediaHistory({data},{false})
        assertFalse(failing.add(listOf(QueueMedia("content://files.example/2","Two"))))
        assertFalse(failing.clear());assertEquals("One",failing.read().single().title)
    }
    @Test fun malformedHistoryAndUnsafeStoredUrisCannotBeReopened() {
        assertTrue(MediaHistory({"broken"},{false}).read().isEmpty())
        assertTrue(MediaHistory({"x".repeat(524289)},{false}).read().isEmpty())
        assertTrue(MediaHistory({"""[{"uri":"file:///private/file","title":"Unsafe"}]"""},{false}).read().isEmpty())
    }
}
