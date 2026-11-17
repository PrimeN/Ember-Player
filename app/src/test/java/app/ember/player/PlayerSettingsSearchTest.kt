package app.ember.player

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35],application=android.app.Application::class)
class PlayerSettingsSearchTest {
    private val resources get()=org.robolectric.RuntimeEnvironment.getApplication().resources
    @Test fun commonControlNamesFindTheirActualSettingsSections() {
        for((query,section) in listOf(" Bluetooth " to "audio","DISPLAY MODE" to "appearance","pinch zoom" to "touch",
            "Dolby Vision" to "decoding","cache management" to "cache","backup password" to "backups","subtitle language" to "subtitles")) {
            assertTrue("No destination for $query",PlayerSettingsIndex.search(query,resources).any {it.id==section})
        }
        assertTrue(PlayerSettingsIndex.search("not-an-existing-option",resources).isEmpty())
    }
}
