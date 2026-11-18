package app.ember.player

import android.app.Application
import app.ember.tv.data.LegacyCrashLog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class LegacyCrashLogTest {
    @Test fun clearingHistoryDeletesTheLegacyLogAndInterruptedWritesButKeepsOtherFiles() {
        val app:Application=RuntimeEnvironment.getApplication()
        PlayerUiStoreFixture(app).use {
            val log=app.filesDir.resolve("crashes.txt").apply {writeText("/storage/emulated/0/Private/movie.mkv")}
            val partial=app.filesDir.resolve(".crashes.txt-123.tmp").apply {writeText("private file")}
            val unrelated=app.filesDir.resolve("keep.txt").apply {writeText("keep")}
            PrivateMedia.clearHistory()
            assertFalse(log.exists());assertFalse(partial.exists());assertEquals("keep",unrelated.readText())
            assertTrue(LegacyCrashLog.clear(app))
        }
    }
}
