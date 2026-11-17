package app.ember.player

import android.app.Application
import android.content.Context
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PlaybackStateRetentionTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    @Before fun setup() {
        for(file in listOf("positions","trackMemory","bookmarks","playbackStateRecency"))context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear().commit()
        PrivateMedia.initialize(context);PrivateMedia.reload(context)
    }
    @Test fun recentResumeAndCompletedPositionsSurviveEvictionAndReopen() {
        val preferences=context.getSharedPreferences("positions",Context.MODE_PRIVATE)
        val edit=preferences.edit()
        repeat(PlaybackStateRetention.MAX_POSITIONS){edit.putString("video-${it.toString().padStart(5,'0')}","100,1000")};edit.commit()
        val store=PositionStore(context)
        store.finished("video-00000",1000)
        PositionStore(context).put("new",200,1000)
        assertEquals(PlaybackStateRetention.MAX_POSITIONS,preferences.all.size)
        assertEquals(1000L to 1000L,store.get("video-00000"));assertNull(store.resume("video-00000"))
        assertNull(store.get("video-00001"));assertEquals(200L,store.resume("new")!!.first)
        store.moveKey("new","renamed")
        assertNull(store.get("new"));assertEquals(200L,store.resume("renamed")!!.first)
    }
    @Test fun oversizedLegacyStateCanExportAgainWithoutDeletingBookmarks() {
        val edit=context.getSharedPreferences("positions",Context.MODE_PRIVATE).edit()
        repeat(50_001){edit.putString("video-${it.toString().padStart(5,'0')}","100,1000")};edit.commit()
        val bookmarks=PlaybackBookmarks(context,"shared")
        assertTrue(bookmarks.add("favorite",100,"Keep this scene"))
        val password="a sufficiently long password".toCharArray()
        val restored=PlayerBackups.decode(PlayerBackups.export(context,password),password)
        assertEquals(PlaybackStateRetention.MAX_POSITIONS,restored.preferences.getValue("positions").size)
        assertEquals(1,restored.preferences.getValue("bookmarks").size)
        assertEquals("Keep this scene",bookmarks.read("favorite").single().name)
    }
    @Test fun trackMemoryIsBoundedAcrossProfilesAndClearingKeepsOtherProfiles() {
        val edit=context.getSharedPreferences("trackMemory",Context.MODE_PRIVATE).edit()
        repeat(PlaybackStateRetention.MAX_TRACK_ENTRIES){edit.putString("a:old-${it.toString().padStart(5,'0')}","en")};edit.commit()
        val other=TrackMemory(context,"local:other")
        other.putAudio("recent","fr");other.putSubtitles("recent","off")
        assertEquals(PlaybackStateRetention.MAX_TRACK_ENTRIES,context.getSharedPreferences("trackMemory",Context.MODE_PRIVATE).all.size)
        assertEquals(TrackMemory.Choice("fr","off"),TrackMemory(context,"local:other").get("recent"))
        TrackMemory(context).clear()
        assertEquals(TrackMemory.Choice("fr","off"),other.get("recent"))
        assertEquals(2,context.getSharedPreferences("trackMemory",Context.MODE_PRIVATE).all.size)
    }
}
