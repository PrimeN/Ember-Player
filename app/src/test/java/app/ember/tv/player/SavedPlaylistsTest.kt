package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class SavedPlaylistsTest {
    @Test fun savedPlaylistsKeepOrderAndSettingsAndCanBeRenamed() {
        var raw:String?=null
        val store=SavedPlaylists({raw},{raw=it;true})
        val items=listOf(QueueMedia("https://example.test/a.mp4","A"),QueueMedia("content://videos/2","B"))
        assertTrue(store.save("My videos",items,1,"all",true))
        val saved=store.read().single()
        assertEquals(items.map {it.uri},saved.entries.map {it.uri})
        assertEquals(1,saved.index);assertTrue(saved.shuffle);assertEquals("all",saved.repeat)
        assertTrue(store.progress(saved.id,items.first().key));assertEquals(0,store.read().single().index)
        assertTrue(store.rename(saved.id,"New title"));assertEquals("New title",store.read().single().name)
        assertTrue(store.remove(saved.id));assertTrue(store.read().isEmpty())
    }
    @Test fun networkPlaylistsStoreStableReferencesInsteadOfExpiredRelayAddresses() {
        var raw:String?=null
        val store=SavedPlaylists({raw},{raw=it;true})
        val media=QueueMedia("http://127.0.0.1:54321/temporary","Movie","network:stable",NetworkQueueEntry("account-id","Movies/Movie.mkv",100))
        assertTrue(store.save("Network",listOf(media)))
        assertFalse(raw!!.contains("127.0.0.1"));assertFalse(raw!!.contains("54321"))
        val entry=store.read().single().entries.single()
        assertEquals(media.network,entry.network);assertEquals("network:stable",entry.identity)
    }
    @Test fun invalidImportsAndFailedEncryptedWritesDoNotReplaceSavedLists() {
        val store=SavedPlaylists({"not json"},{false})
        assertTrue(store.read().isEmpty())
        assertFalse(store.save("Bad",listOf(QueueMedia("file:///outside/private.mkv","Bad"))))
        assertFalse(store.save("Valid",listOf(QueueMedia("https://example.test/a.mp4","A"))))
        val traversing=SavedPlaylist("id","Bad",listOf(PlaylistEntry("","Bad",network=NetworkQueueEntry("id","../private",1))))
        assertFalse(SavedPlaylists.valid(traversing))
    }
    @Test fun shuffleVisitsEveryItemOnceAndPreviousFollowsItsTraversal() {
        val queue=MediaQueue((0..19).map {QueueMedia("https://example.test/$it.mp4","$it")})
        queue.select(9);queue.shuffle=true
        val visited=mutableListOf(queue.current!!)
        while(queue.hasNext())visited+=queue.next()!!
        assertEquals(20,visited.size);assertEquals(20,visited.distinct().size)
        assertNull(queue.next())
        assertEquals(visited[18],queue.previous())
        queue.repeat="all"
        assertTrue(queue.hasNext())
        queue.repeat="one"
        val current=queue.current
        assertEquals(current,queue.next(ended=true))
    }
}
