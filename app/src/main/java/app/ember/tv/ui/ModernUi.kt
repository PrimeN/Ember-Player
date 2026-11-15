package app.ember.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.Dp

@Composable
internal fun MaterialGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
    androidx.compose.foundation.Image(
        icon,
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
        modifier = Modifier.size(size),
    )
}


/**
 * Full-screen artwork at a size that stays sharp on a 4K screen: Cinemeta/metahub backdrops are
 * asked for in "large" (1920 px wide) rather than "medium", and TMDB images in "original".
 */
fun sharpArt(url: String): String = url
    .replace("/background/medium/", "/background/large/")
    .replace("/background/small/", "/background/large/")
    .replace(tmdbSized, "image.tmdb.org/t/p/original/")

private val tmdbSized = Regex("""image\.tmdb\.org/t/p/w\d+/""")


/** Home's default entry point from the menu: the hero's Watch now button (#66). */
internal const val HERO_ENTRY = "hero-watch-now"


object Art {
    /** The TV shows a 4K picture (so the Ultra profile is offered). */
    private val shows4K = androidx.compose.runtime.mutableStateOf(false)
    val display4K: Boolean get() = shows4K.value
    /** The Ultra profile is in effect (set from the active performance profile). */
    @Volatile var ultra = false
    /** 4K card artwork: a 4K display with the Ultra profile. */
    val uhd: Boolean get() = display4K && ultra

    /** Reads the current display mode (called when Ember comes to the front). */
    fun update(activity: android.app.Activity) {
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) activity.display else @Suppress("DEPRECATION") activity.windowManager.defaultDisplay
        shows4K.value = (display?.mode?.physicalWidth ?: 0) >= 3000
    }
}
