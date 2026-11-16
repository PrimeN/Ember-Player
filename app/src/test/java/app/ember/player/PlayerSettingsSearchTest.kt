package app.ember.player

import org.junit.Assert.*
import org.junit.Test

class PlayerSettingsSearchTest {
    @Test fun commonControlNamesFindTheirActualSettingsSections() {
        for((query,section) in listOf(" Bluetooth " to "audio","DISPLAY MODE" to "appearance","pinch zoom" to "touch",
            "Dolby Vision" to "decoding","cache management" to "cache","backup password" to "backups","subtitle language" to "subtitles")) {
            assertTrue("No destination for $query",PlayerSettingsIndex.search(query).any {it.id==section})
        }
        assertTrue(PlayerSettingsIndex.search("not-an-existing-option").isEmpty())
    }
}
