package app.ember.player

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.ember.tv.data.Prefs
import app.ember.tv.player.PlayerTouchControls
import app.ember.tv.ui.*

internal val LocalNetworkMediaPreviews=staticCompositionLocalOf {true}

/** Both component families use one palette, so dialogs and playback controls remain cohesive. */
@Composable
internal fun PlayerTheme(prefs:Prefs,content:@Composable ()->Unit) {
    val app=LocalContext.current.applicationContext
    val preferences=remember(app){app.getSharedPreferences("ember",android.content.Context.MODE_PRIVATE)}
    var preferenceRevision by remember {mutableIntStateOf(0)}
    DisposableEffect(preferences) {
        val keys=setOf("networkMediaPreviews","accent","theme","oled","contentDensity","navigationGlass","artworkQuality","devicePerformance","customPerformance","navigationGlassMethod")
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->if(key==null||key in keys)preferenceRevision++}
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val previews=remember(prefs,preferenceRevision){prefs.networkMediaPreviews}
    val render=rememberDeviceRenderOptions(prefs.contentDensity,prefs.navigationGlass,prefs.artworkQuality,
        prefs.devicePerformance,prefs.customPerformance).copy(glassMethod=prefs.navigationGlassMethod)
    val television=PlayerTouchControls.isTelevision(LocalContext.current)
    val appearance=remember(prefs,preferenceRevision){Triple(prefs.accent,prefs.theme,prefs.oled)}
    val day=PlayerAppearance.light(appearance.second,androidx.compose.foundation.isSystemInDarkTheme())
    val context=LocalContext.current
    SideEffect {
        Ui.day=day;Ui.oled=appearance.third
        (context as? android.app.Activity)?.let {activity->
            androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
                isAppearanceLightStatusBars=day;isAppearanceLightNavigationBars=day
            }
        }
    }
    CompositionLocalProvider(LocalNetworkMediaPreviews provides previews,LocalRenderOptions provides render,LocalNight provides !day,
        LocalReduceMotion provides rememberReduceMotion(),LocalPlayerDesign provides true,LocalPlayerTelevision provides television) {
        EmberTheme(appearance.first,"modern") {
            val tv=androidx.tv.material3.MaterialTheme.colorScheme
            val make=if(day)androidx.compose.material3.lightColorScheme() else androidx.compose.material3.darkColorScheme()
            val touch=make.copy(primary=tv.primary,onPrimary=tv.onPrimary,primaryContainer=tv.primaryContainer,onPrimaryContainer=tv.onPrimaryContainer,
                secondary=tv.secondary,onSecondary=tv.onSecondary,secondaryContainer=tv.secondaryContainer,onSecondaryContainer=tv.onSecondaryContainer,
                tertiary=tv.tertiary,onTertiary=tv.onTertiary,tertiaryContainer=tv.tertiaryContainer,onTertiaryContainer=tv.onTertiaryContainer,
                background=tv.background,onBackground=tv.onBackground,surface=tv.surface,onSurface=tv.onSurface,surfaceVariant=tv.surfaceVariant,onSurfaceVariant=tv.onSurfaceVariant,
                surfaceContainer=tv.surface,surfaceContainerLow=tv.background,surfaceContainerHigh=tv.surfaceVariant,
                outline=tv.border,outlineVariant=tv.borderVariant,error=tv.error,onError=tv.onError,errorContainer=tv.errorContainer,onErrorContainer=tv.onErrorContainer)
            androidx.compose.material3.MaterialTheme(colorScheme=touch,typography=playerMaterialTypography(television)) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides touch.onSurface,
                    androidx.tv.material3.LocalContentColor provides tv.onSurface,content=content)
            }
        }
    }
}
