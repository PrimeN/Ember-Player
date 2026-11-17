package app.ember.tv.player

import app.ember.tv.data.InterfaceStyles
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlayerInterfacesCompatibilityTest {
    @Test fun savedInterfaceIdsStillChooseTheirOriginalLayouts() {
        for((id,label) in listOf("basic" to app.ember.tv.R.string.ui_ember,"modern" to app.ember.tv.R.string.ui_glass,"ember_x" to app.ember.tv.R.string.ui_basic,"streaming" to app.ember.tv.R.string.ui_streaming)) {
            val config=Json.decodeFromString<PlaybackConfig>("{\"playerInterface\":\"$id\"}")
            val resolved=PlayerInterfaces.resolve(config,InterfaceStyles.MODERN)
            assertEquals(id,resolved)
            assertEquals(label,PlayerInterfaces.options.single {it.first==resolved}.second)
        }
        assertEquals(InterfaceStyles.MODERN,PlayerInterfaces.layoutStyle("basic"))
        assertEquals(InterfaceStyles.EMBER_X,PlayerInterfaces.layoutStyle("ember_x"))
    }
    @Test fun legacyLookFallbacksAndExplicitOverridesKeepTheirPreviousBehavior() {
        assertEquals(PlayerInterfaces.EMBER,PlayerInterfaces.resolve(PlaybackConfig(playerLook="simple"),InterfaceStyles.MODERN))
        assertEquals(PlayerInterfaces.GLASS,PlayerInterfaces.resolve(PlaybackConfig(playerLook="advanced"),InterfaceStyles.MODERN))
        assertEquals(PlayerInterfaces.BASIC,PlayerInterfaces.resolve(PlaybackConfig(),InterfaceStyles.EMBER_X))
        assertEquals(PlayerInterfaces.EMBER,PlayerInterfaces.resolve(PlaybackConfig(playerInterface="basic"),InterfaceStyles.EMBER_X))
    }
}
