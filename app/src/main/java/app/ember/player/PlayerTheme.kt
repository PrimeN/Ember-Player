package app.ember.player

import androidx.compose.runtime.*
import app.ember.tv.data.Prefs
import app.ember.tv.ui.*

@Composable
internal fun PlayerTheme(prefs:Prefs,content:@Composable ()->Unit) {
    val render=rememberDeviceRenderOptions(prefs.contentDensity,prefs.navigationGlass,prefs.artworkQuality,
        prefs.devicePerformance,prefs.customPerformance).copy(glassMethod=prefs.navigationGlassMethod)
    SideEffect {Ui.day=prefs.theme=="day";Ui.oled=prefs.oled}
    CompositionLocalProvider(LocalRenderOptions provides render,LocalNight provides (prefs.theme!="day"),
        LocalReduceMotion provides rememberReduceMotion()) {EmberTheme(prefs.accent,"modern",content)}
}
