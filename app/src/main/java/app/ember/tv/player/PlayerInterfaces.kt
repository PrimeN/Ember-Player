package app.ember.tv.player

import app.ember.tv.data.InterfaceStyles

/** Constants name the visible layouts; their legacy stored IDs remain compatible with saved choices. */
object PlayerInterfaces {
    const val EMBER = "basic"
    const val GLASS = "modern"
    const val BASIC = "ember_x"
    /** Streaming-app layout: transport top left, title top right, logo on the left, tracks under the bar. */
    const val STREAMING = "streaming"
    val options = listOf(EMBER to "Ember", GLASS to "Glass", BASIC to "Basic", STREAMING to "Streaming")

    fun normalize(value: String?): String = when (value) {
        EMBER, "simple" -> EMBER
        BASIC -> BASIC
        STREAMING -> STREAMING
        else -> GLASS
    }

    /** Old settings/backups retain their previous effective layout until explicitly changed. */
    fun resolve(config: PlaybackConfig, launcher: String): String = config.playerInterface?.let(::normalize)
        ?: if (InterfaceStyles.normalize(launcher) == InterfaceStyles.EMBER_X) BASIC else normalize(config.playerLook)

    fun layoutStyle(style: String): String = if (normalize(style) == BASIC) InterfaceStyles.EMBER_X else InterfaceStyles.MODERN
}
