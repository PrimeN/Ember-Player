package app.ember.tv.player

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class PositionStoreTest {
    @Test fun resumeThresholdAndSavedTimestampsSurviveReopening() {
        val context=RuntimeEnvironment.getApplication()
        val store=PositionStore(context)
        store.put("short",30_000,100_000);assertNull(store.resume("short"))
        store.put("resumable",30_001,100_000)
        val saved=PositionStore(context).saved("resumable")!!
        assertEquals(30_001L,saved.position);assertTrue(saved.lastPlayed>0)
        assertEquals(30_001L,store.resume("resumable")!!.first)
        context.getSharedPreferences("positions",0).edit().putString("legacy","60000,100000").commit()
        assertEquals(0L,store.saved("legacy")!!.lastPlayed)
    }
    @Test fun completedMediaKeepsItsLibraryStateButNeverResumesAtTheEnd() {
        val context=RuntimeEnvironment.getApplication()
        val store=PositionStore(context)
        store.put("video",90_000,120_000)
        assertEquals(90_000L,store.resume("video")?.first)
        store.finished("video",120_000)
        val reopened=PositionStore(context)
        assertEquals(120_000L to 120_000L,reopened.get("video"))
        assertNull(reopened.resume("video"))
        reopened.moveKey("video","renamed")
        assertNull(reopened.get("video"));assertEquals(120_000L to 120_000L,reopened.get("renamed"))
        reopened.clear("renamed");assertNull(reopened.get("renamed"))
    }
    @Test fun nearEndPositionsAndUnknownDurationsAreNotResumeTargets() {
        val store=PositionStore(RuntimeEnvironment.getApplication())
        store.put("near-end",95_000,100_000)
        assertNotNull(store.get("near-end"));assertNull(store.resume("near-end"))
        store.finished("unknown",0)
        assertNull(store.get("unknown"));assertNull(store.resume("unknown"))
    }
}
