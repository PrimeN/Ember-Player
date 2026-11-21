package app.ember.player

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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
    val preferDark = television || !isPlayerTablet()
    val appearance=remember(prefs,preferenceRevision,preferDark){Triple(prefs.accent,prefs.themeForDevice(preferDark),prefs.oled)}
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
            val base=androidx.tv.material3.MaterialTheme.colorScheme
            val tv=if (preferDark && !day) base.copy(
                background = if (appearance.third) Color.Black else Color(0xFF111215),
                onBackground = Color(0xFFF5F4F3),
                surface = if (appearance.third) Color(0xFF151518) else Color(0xFF1F2024),
                onSurface = Color(0xFFF5F4F3),
                surfaceVariant = Color(0xFF303136),
                onSurfaceVariant = Color(0xFFC7C6CB),
            ) else if(appearance.first==0)base.copy(
                background=if(day)Color(0xFFFFF8F3) else if(appearance.third)Color.Black else Color(0xFF171517),
                onBackground=if(day)Color(0xFF241A15) else Color(0xFFF3E7DF),
                surface=if(day)Color(0xFFFFF8F3) else Color(0xFF211D1B),onSurface=if(day)Color(0xFF241A15) else Color(0xFFF3E7DF),
                surfaceVariant=if(day)Color(0xFFF0E2D8) else Color(0xFF382F2B),onSurfaceVariant=if(day)Color(0xFF55443A) else Color(0xFFD7C4B8)
            ) else base
            val make=if(day)androidx.compose.material3.lightColorScheme() else androidx.compose.material3.darkColorScheme()
            val touch=make.copy(primary=tv.primary,onPrimary=tv.onPrimary,primaryContainer=tv.primaryContainer,onPrimaryContainer=tv.onPrimaryContainer,
                secondary=tv.secondary,onSecondary=tv.onSecondary,secondaryContainer=tv.secondaryContainer,onSecondaryContainer=tv.onSecondaryContainer,
                background=tv.background,onBackground=tv.onBackground,surface=tv.surface,onSurface=tv.onSurface,surfaceVariant=tv.surfaceVariant,onSurfaceVariant=tv.onSurfaceVariant,
                surfaceContainer=tv.surface,surfaceContainerLow=tv.background,surfaceContainerHigh=tv.surfaceVariant,
                outline=tv.onSurfaceVariant.copy(alpha=.55f),outlineVariant=tv.onSurfaceVariant.copy(alpha=.18f),error=tv.error,onError=tv.onError,errorContainer=tv.errorContainer,onErrorContainer=tv.onErrorContainer)
            androidx.tv.material3.MaterialTheme(colorScheme=tv,typography=playerTvTypography(television)) {
                androidx.compose.material3.MaterialTheme(colorScheme=touch,typography=playerMaterialTypography(television)) {
                    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides touch.onSurface,
                        androidx.tv.material3.LocalContentColor provides tv.onSurface,content=content)
                }
            }
        }
    }
}
