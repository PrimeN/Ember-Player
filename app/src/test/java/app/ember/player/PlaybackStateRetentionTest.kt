package app.ember.player

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import app.ember.tv.player.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        PlaybackStateRetention.prune(context)
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
        PlaybackStateRetention.prune(context)
        assertEquals(PlaybackStateRetention.MAX_TRACK_ENTRIES,context.getSharedPreferences("trackMemory",Context.MODE_PRIVATE).all.size)
        assertEquals(TrackMemory.Choice("fr","off"),TrackMemory(context,"local:other").get("recent"))
        TrackMemory(context).clear()
        assertEquals(TrackMemory.Choice("fr","off"),other.get("recent"))
        assertEquals(2,context.getSharedPreferences("trackMemory",Context.MODE_PRIVATE).all.size)
    }
    @Test fun positionSavesDoNotReadTheWholeStoreOrOpenARecencyFile() {
        val actual=context.getSharedPreferences("positions",Context.MODE_PRIVATE)
        var reads=0;var writes=0;val opened=mutableListOf<String>()
        val measured=object:SharedPreferences by actual {
            override fun getAll():Map<String,*> {reads++;return actual.all}
            override fun edit():SharedPreferences.Editor {writes++;return actual.edit()}
        }
        val scoped=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
                opened+=name;return if(name=="positions")measured else super.getSharedPreferences(name,mode)
            }
        }
        val store=PositionStore(scoped)
        store.put("video",100,1000);store.put("video",200,1000)
        assertEquals(0,reads);assertEquals(2,writes);assertEquals(listOf("positions"),opened)
        assertEquals(200L to 1000L,store.get("video"))
        assertTrue(actual.getString("video",null)!!.substringAfterLast(',').toLong()>0)
    }
    @Test fun migrationKeepsRecordedRecencyAndDeletesTheLegacyIndex() {
        val edit=context.getSharedPreferences("positions",Context.MODE_PRIVATE).edit()
        repeat(PlaybackStateRetention.MAX_POSITIONS+1){edit.putString("video-${it.toString().padStart(5,'0')}","100,1000")};edit.commit()
        val legacy=context.getSharedPreferences("playbackStateRecency",Context.MODE_PRIVATE)
        legacy.edit().putString("positions",Json.encodeToString(listOf("video-00000"))).commit()
        PlaybackStateRetention.prune(context)
        assertNotNull(PositionStore(context).get("video-00000"))
        assertNull(PositionStore(context).get("video-00001"))
        assertTrue(context.getSharedPreferences("playbackStateRecency",Context.MODE_PRIVATE).all.isEmpty())
        val before=PlayerBackups.snapshot(context)
        assertEquals(2,before.version)
        PlayerBackups.restore(context,before)
        assertEquals(before,PlayerBackups.snapshot(context))
        assertNotNull(PositionStore(context).get("video-00000"))
    }
    @Test fun restoreAndLaterPruningKeepNewestTimestampsRegardlessOfMapOrder() {
        val positions=(PlaybackStateRetention.MAX_POSITIONS downTo 0).associate {i->
            "video-$i" to BackupValue("string","100,1000,${i+1L}")
        }
        val backup=PlayerBackupData(version=2,preferences=PlayerBackups.files.associateWith {
            if(it=="positions")positions else emptyMap()
        },secrets=emptyMap())
        PlayerBackups.restore(context,backup)
        assertNull(PositionStore(context).get("video-0"))
        assertNotNull(PositionStore(context).get("video-${PlaybackStateRetention.MAX_POSITIONS}"))
        val before=PlayerBackups.snapshot(context)
        PlaybackStateRetention.prune(context)
        assertEquals(before,PlayerBackups.snapshot(context))
        assertEquals("100,1000,2001",before.preferences.getValue("positions").getValue("video-2000").text)
    }
    @Test fun legacyBackupPositionsRemainReadableAndUpgradeOnRestore() {
        val backup=PlayerBackupData(preferences=PlayerBackups.files.associateWith {
            if(it=="positions")mapOf("legacy" to BackupValue("string","100,1000")) else emptyMap()
        },secrets=emptyMap())
        PlayerBackups.restore(context,backup)
        assertEquals(100L to 1000L,PositionStore(context).get("legacy"))
        assertEquals("100,1000,0",PlayerBackups.snapshot(context).preferences.getValue("positions").getValue("legacy").text)
    }
    @Test fun failedMigrationLeavesTheLegacyOrderAvailableForRetry() {
        val actual=context.getSharedPreferences("positions",Context.MODE_PRIVATE)
        actual.edit().putString("legacy","100,1000").commit()
        val legacy=context.getSharedPreferences("playbackStateRecency",Context.MODE_PRIVATE)
        legacy.edit().putString("positions",Json.encodeToString(listOf("legacy"))).commit()
        val failing=object:SharedPreferences by actual {
            override fun edit():SharedPreferences.Editor {
                val editor=actual.edit()
                return object:SharedPreferences.Editor by editor {
                    override fun putString(key:String,value:String?):SharedPreferences.Editor {editor.putString(key,value);return this}
                    override fun commit()=false
                }
            }
        }
        val scoped=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences =
                if(name=="positions")failing else super.getSharedPreferences(name,mode)
        }
        assertFalse(PlaybackStateRetention(scoped,"positions").prune())
        assertTrue(legacy.contains("positions"));assertEquals("100,1000",actual.getString("legacy",null))
        assertTrue(PlaybackStateRetention.prune(context))
        assertFalse(legacy.contains("positions"));assertEquals("100,1000,1",actual.getString("legacy",null))
    }
}
