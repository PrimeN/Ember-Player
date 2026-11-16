package app.ember.player

import app.ember.tv.player.QueueMedia
import org.junit.Assert.*
import org.junit.Test

class PlayerLibraryChoiceTest {
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
