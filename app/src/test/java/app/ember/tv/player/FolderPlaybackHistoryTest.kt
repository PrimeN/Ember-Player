package app.ember.tv.player

import android.net.Uri
import app.ember.tv.data.LocalActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35])
class FolderPlaybackHistoryTest {
    @Test fun savedActivityScopesRemainReadableWithoutTheOldProfileIntegrations() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ember",android.content.Context.MODE_PRIVATE).edit()
            .putString("profiles","""[{"id":"saved","name":"Saved","kids":true,"personal":false,"activityMode":"local"}]""")
            .putString("currentProfile","saved").commit()
        val scope=LocalActivity.scope(context)
        assertEquals("local:saved",scope)
        PositionStore(context,scope).put("media",1234,9000)
        assertEquals(1234L to 9000L,PositionStore(context,LocalActivity.scope(context)).get("media"))
        assertNull(PositionStore(context,LocalActivity.SHARED).get("media"))
    }
    @Test fun foldersRememberTheirOwnLastPlayedItemAndMoveItToTheDestination() {
        var raw:String?=null
        val history=FolderPlaybackHistory({raw},{raw=it;true})
        val first=Uri.parse("content://files/tree/a/document/a")
        val second=Uri.parse("content://files/tree/b/document/b")
        val file=DeviceFile(Uri.parse("content://files/tree/a/document/a%2Fmovie.mkv"),first,"movie.mkv","video/x-matroska",100,0,0)
        assertTrue(history.remember(first,RecentMedia(file.uri.toString(),file.name)))
        assertNull(history.get(second))
        val destination=Uri.parse("content://files/tree/b/document/b%2Frenamed.mkv")
        assertTrue(history.moved(file,destination,second,"renamed.mkv"))
        assertNull(history.get(first))
        assertEquals(destination.toString(),history.get(second)?.uri)
        assertEquals("renamed.mkv",history.get(second)?.title)
    }
    @Test fun changingAFileKeyKeepsResumeTracksAndBookmarks() {
        val context=RuntimeEnvironment.getApplication()
        val scope=LocalActivity.SHARED
        val old="file:old";val new="file:new"
        val positions=PositionStore(context,scope)
        val tracks=TrackMemory(context,scope)
        val bookmarks=PlaybackBookmarks(context,scope)
        positions.put(old,30000,120000)
        tracks.putAudio(old,"fr");tracks.putSubtitles(old,"en")
        assertTrue(bookmarks.add(old,15000,"First scene"))
        positions.moveKey(old,new);tracks.moveKey(old,new);assertTrue(bookmarks.moveKey(old,new))
        assertNull(positions.get(old));assertNull(tracks.get(old));assertTrue(bookmarks.read(old).isEmpty())
        assertEquals(30000L to 120000L,positions.get(new))
        assertEquals(TrackMemory.Choice("fr","en"),tracks.get(new))
        assertEquals("First scene",bookmarks.read(new).single().name)
    }
}
