package app.ember.player

import app.ember.tv.player.QueueMedia
import org.junit.Assert.*
import org.junit.Test

class PlayerLibraryChoiceTest {
    @Test fun resumeShelfUsesThePlayerThresholdAndPlaybackRecencyRegardlessOfFileSort() {
        val media=QueueMedia("content://media/video/1","One")
        for(position in listOf(0L,1000L,15_000L,30_000L,95_000L,100_000L))assertFalse(PlayerLibraryMedia(media,position,100_000).continuing)
        assertTrue(PlayerLibraryMedia(media,30_001,100_000).continuing)
        val older=PlayerLibraryMedia(media,60_000,100_000,modified=900,lastPlayed=10)
        val newer=older.copy(media=QueueMedia("content://media/video/2","Zebra"),modified=1,lastPlayed=20)
        val snapshot=PlayerLibrarySnapshot(listOf(older,newer))
        assertEquals(listOf(newer,older),snapshot.continuing)
        for(sort in PlayerLibrarySort.entries)assertEquals(listOf(newer,older),snapshot.page(0,sort,PlayerLibraryFilter.CONTINUING).entries)
    }
    @Test fun historyDuplicatesAndWatchedFiltersAreResolvedBeforePageBoundaries() {
        val values=(0 until 231).map {i->PlayerLibraryMedia(QueueMedia("content://media/video/$i","Clip %03d".format(i)),if(i<120)100_000 else 0,100_000,modified=i.toLong())}
        val snapshot=PlayerLibrarySnapshot(listOf(values.last())+values+values.take(20))
        val all=mutableListOf<PlayerLibraryMedia>();var offset=0
        do {
            val page=snapshot.page(offset,PlayerLibrarySort.NAME,PlayerLibraryFilter.ALL)
            all+=page.entries;offset=page.next ?: -1
        }while(offset>=0)
        assertEquals(231,all.size);assertEquals(231,all.map {it.media.key}.distinct().size)
        val unwatched=snapshot.page(0,PlayerLibrarySort.NAME,PlayerLibraryFilter.UNWATCHED)
        assertEquals("Clip 120",unwatched.entries.first().media.title);assertEquals(100,unwatched.entries.size)
        val last=snapshot.page(unwatched.next!!,PlayerLibrarySort.NAME,PlayerLibraryFilter.UNWATCHED)
        assertEquals(11,last.entries.size);assertNull(last.next)
        val watched=snapshot.page(0,PlayerLibrarySort.MODIFIED,PlayerLibraryFilter.WATCHED)
        assertEquals("Clip 119",watched.entries.first().media.title);assertEquals(100,watched.entries.size)
    }
    @Test fun finishedMediaLeavesContinueWatchingAndMovesIntoWatched() {
        val media=QueueMedia("content://media/video/1","One")
        val partial=PlayerLibraryMedia(media,60_000,300_000)
        val finished=partial.copy(position=285_000)
        assertTrue(PlayerLibraryFilter.CONTINUING.accepts(partial))
        assertFalse(PlayerLibraryFilter.WATCHED.accepts(partial))
        assertFalse(PlayerLibraryFilter.CONTINUING.accepts(finished))
        assertTrue(PlayerLibraryFilter.WATCHED.accepts(finished))
        assertFalse(PlayerLibraryFilter.UNWATCHED.accepts(finished))
        assertTrue(PlayerLibraryFilter.UNWATCHED.accepts(PlayerLibraryMedia(media)))
    }
    @Test fun oldOrCorruptBrowseChoicesHaveSafeDefaults() {
        assertEquals(PlayerLibraryFilter.ALL,PlayerLibraryFilter.from("removed-option"))
        assertEquals(PlayerLibrarySort.MODIFIED,PlayerLibrarySort.from(""))
    }
}
