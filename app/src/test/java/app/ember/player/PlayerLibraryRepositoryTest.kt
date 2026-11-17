package app.ember.player

import app.ember.tv.player.PositionStore
import app.ember.tv.player.QueueMedia
import org.junit.Assert.*
import org.junit.Test

class PlayerLibraryRepositoryTest {
    private val values=(0 until 231).map {i->PlayerLibraryMedia(QueueMedia("content://media/video/$i","Episode $i"),modified=i.toLong())}
    @Test fun returningRefreshesProgressAndRecentMediaWithoutReadingOrSortingTheIndexAgain() {
        var indexReads=0;var history=emptyList<QueueMedia>();var positions=emptyMap<String,PositionStore.SavedPosition>()
        val repository=PlayerLibraryRepository({"same index"},{_,_->indexReads++;PlayerLibraryCatalog(values)},{history},{positions},{emptyList()})
        val first=repository.load("",0).first
        val media=values[130].media
        positions=mapOf(media.key to PositionStore.SavedPosition(60_000,100_000,42))
        history=listOf(media,QueueMedia("https://example.test/new.mkv","Episode 232"))
        val returned=repository.load("",0).first
        assertSame(first.catalog,returned.catalog)
        assertSame(first.catalog.ordered,returned.catalog.ordered)
        assertEquals(1,indexReads)
        assertEquals(232,returned.entries.size)
        assertEquals(media,returned.continuing.single().media)
        assertEquals(42L,returned.continuing.single().lastPlayed)
        assertEquals("Episode 100",returned.page(100,PlayerLibrarySort.NAME,PlayerLibraryFilter.ALL).entries.first().media.title)
        positions=mapOf(media.key to PositionStore.SavedPosition(100_000,100_000,43))
        val completed=repository.load("",0).first
        assertTrue(completed.continuing.isEmpty())
        assertEquals(media,completed.page(0,PlayerLibrarySort.NAME,PlayerLibraryFilter.WATCHED).entries.single().media)
        assertEquals(1,indexReads)
        history=emptyList();positions=emptyMap()
        val cleared=repository.load("",0).first
        assertEquals(231,cleared.entries.size);assertTrue(cleared.continuing.isEmpty())
    }
    @Test fun realIndexChangesExplicitRefreshAndFailedIndexLoadsInvalidateTheCatalog() {
        var stamp=0;var reads=0;var unavailable=false
        val repository=PlayerLibraryRepository({stamp},{_,_->reads++;PlayerLibraryCatalog(values,unavailable)},{emptyList()},{emptyMap()},{emptyList()})
        val first=repository.load("",0).first
        stamp++
        val changed=repository.load("",0).first
        assertNotSame(first.catalog,changed.catalog);assertEquals(2,reads)
        repository.load("",1);assertEquals(3,reads)
        unavailable=true;stamp++
        assertTrue(repository.load("",1).first.indexUnavailable)
        unavailable=false
        assertFalse(repository.load("",1).first.indexUnavailable);assertEquals(5,reads)
    }
    @Test fun searchIsBoundedAndDoesNotEvictTheMainLibrary() {
        var reads=0
        val repository=PlayerLibraryRepository({0},{query,_->reads++;PlayerLibraryCatalog(values.filter {it.media.title.contains(query)})},{emptyList()},{emptyMap()},{emptyList()})
        val main=repository.load("",0).first
        for(query in listOf("1","2","3"))repository.load(query,0)
        assertNull(repository.peek("1"));assertNull(repository.peek("2"));assertNotNull(repository.peek("3"))
        assertSame(main.catalog,repository.load("",0).first.catalog);assertEquals(4,reads)
    }
    @Test fun newlyPrivateMediaIsRemovedBeforePaginationAndRecentItemsNeverRepeat() {
        val snapshot=PlayerLibrarySnapshot(PlayerLibraryCatalog(values),values.take(20).map {it.media},emptyMap())
        val hidden=values.take(120).map {it.media.uri}.toSet()
        val first=snapshot.page(0,PlayerLibrarySort.NAME,PlayerLibraryFilter.ALL,hidden::contains)
        val last=snapshot.page(first.next!!,PlayerLibrarySort.NAME,PlayerLibraryFilter.ALL,hidden::contains)
        assertEquals(100,first.entries.size);assertEquals("Episode 120",first.entries.first().media.title)
        assertEquals(11,last.entries.size);assertNull(last.next)
        assertEquals(111,(first.entries+last.entries).map {it.media.key}.distinct().size)
    }
    @Test fun cancellationKeepsThePreviousSnapshotAndRetriesTheChangedIndex() {
        var stamp=0
        val repository=PlayerLibraryRepository({stamp},{_,check->check();PlayerLibraryCatalog(values)},{emptyList()},{emptyMap()},{emptyList()})
        val first=repository.load("",0).first
        stamp++
        try {repository.load("",0){throw kotlinx.coroutines.CancellationException()};fail("Expected cancellation")}
        catch(_:kotlinx.coroutines.CancellationException) {}
        assertSame(first,repository.peek(""))
        assertNotSame(first.catalog,repository.load("",0).first.catalog)
    }
    @Test fun sourceChangesDuringLoadingAreRetriedOnTheNextRefresh() {
        var stamp=0;var reads=0
        val repository=PlayerLibraryRepository({stamp},{_,_->reads++;stamp++;PlayerLibraryCatalog(values)},{emptyList()},{emptyMap()},{emptyList()})
        repository.load("",0);repository.load("",0)
        assertEquals(2,reads)
    }

}
