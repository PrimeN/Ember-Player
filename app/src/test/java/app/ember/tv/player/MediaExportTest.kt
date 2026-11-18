package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class MediaExportTest {
    @Test fun screenshotSizesStayWithinFourKWithoutUpscalingOrChangingAspect() {
        assertEquals(3840 to 2160,PlayerExports.boundedSize(7680,4320))
        assertEquals(1000 to 2000,PlayerExports.boundedSize(1000,2000))
        assertEquals(640 to 480,PlayerExports.boundedSize(640,480))
        assertEquals(0 to 0,PlayerExports.boundedSize(0,1080))
    }
    @Test fun clipRangesRejectInvalidOrOverflowingValues() {
        assertFalse(PlayerClipExport.validRange(null,1000));assertFalse(PlayerClipExport.validRange(-1,1000))
        assertFalse(PlayerClipExport.validRange(1000,1999));assertFalse(PlayerClipExport.validRange(2000,1000))
        assertTrue(PlayerClipExport.validRange(1000,2000))
    }
    @Test fun largerQueuesAndPlaylistsKeepAllEntriesAndRejectOverflow() {
        val files=List(600){QueueMedia("https://example.test/$it.mp4","Video $it")}
        val queue=MediaQueue(files);assertEquals(600,queue.items.size)
        assertEquals(files.last(),queue.select(599))
        var saved:String?=null;val playlists=SavedPlaylists({saved},{saved=it;true})
        assertTrue(playlists.save("Many files",files));assertEquals(600,playlists.read().single().entries.size)
        try {queue.add(List(MediaQueue.MAX_ITEMS){files.first()});fail("Should reject overflow")}catch(_:IllegalArgumentException){}
        assertEquals(600,queue.items.size)
    }
}
