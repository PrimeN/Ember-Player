package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class SavedPlaylistsTest {
    @Test fun everyMutationPreservesUnreadableOrPartlyInvalidSavedPlaylists() {
        var raw:String?=null;var writes=0;var unavailable=false
        val store=SavedPlaylists({check(!unavailable);raw},{raw=it;writes++;true})
        val media=QueueMedia("https://example.test/video.mkv","Video")
        assertTrue(store.save("Keep",listOf(media)))
        val good=raw;val id=store.read().single().id
        val invalid=good!!.replace("https://example.test/video.mkv","file:///private")
        for(bad in listOf("not json",good.dropLast(1),invalid," ".repeat(16*1024*1024+1))) {
            raw=bad;writes=0
            assertTrue(store.read().isEmpty())
            assertFalse(store.save("New",listOf(media)))
            assertFalse(store.rename(id,"Renamed"))
            assertFalse(store.progress(id,media.key))
            assertFalse(store.remove(id))
            assertFalse(store.append(id,listOf(media)))
            assertFalse(store.move(id,0,0))
            assertEquals(0,writes);assertEquals(bad,raw)
        }
        raw=good;unavailable=true;writes=0
        assertFalse(store.save("New",listOf(media)));assertFalse(store.remove(id));assertEquals(0,writes)
        unavailable=false
        assertTrue(store.rename(id,"Recovered"));assertEquals("Recovered",store.read().single().name)
    }
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
    @Test fun appendingAndReorderingPreserveTheResumeItemAndConnectionDetails() {
        var raw:String?=null
        val store=SavedPlaylists({raw},{raw=it;true})
        val first=QueueMedia("https://example.test/1.mkv","One")
        val second=QueueMedia("http://127.0.0.1/ephemeral","Two","network:two",NetworkQueueEntry("account","Two.mkv",100))
        val third=QueueMedia("content://videos/3","Three")
        assertTrue(store.save("Keep",listOf(first,second),1,"all",true))
        val id=store.read().single().id
        assertTrue(store.append(id,listOf(third)))
        assertTrue(store.move(id,2,0))
        var saved=store.read().single()
        assertEquals(listOf("Three","One","Two"),saved.entries.map {it.title})
        assertEquals(2,saved.index);assertTrue(saved.shuffle);assertEquals("all",saved.repeat)
        assertEquals(second.network,saved.entries.last().network);assertEquals("",saved.entries.last().uri)
        assertTrue(store.move(id,2,0));saved=store.read().single()
        assertEquals("Two",saved.entries[saved.index].title);assertEquals(0,saved.index)
        assertFalse(store.move(id,-1,0));assertFalse(store.move(id,0,3))
    }

}
