package app.ember.tv.player

/** Compact panels occupy roughly one third of the previous area, with readable labels. */
internal object PlayerMenuLayout {
    const val QUICK_WIDTH=280
    const val PANEL_WIDTH=360
    const val ROW_HEIGHT=32
    val categories=listOf("playback" to app.ember.player.PlayerUiLabels.PLAYBACK,"video" to app.ember.player.PlayerUiLabels.PICTURE,
        "audio" to app.ember.player.PlayerUiLabels.AUDIO,"subtitles" to app.ember.player.PlayerUiLabels.SUBTITLES)
}
