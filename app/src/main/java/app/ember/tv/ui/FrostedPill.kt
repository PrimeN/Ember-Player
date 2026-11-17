package app.ember.tv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalGlassBitmap = compositionLocalOf<Bitmap?> { null }
/** How long glass keeps its last sample while no artwork is published (#40). */
internal const val GLASS_SAMPLE_GRACE_MS = 1_000L
val LocalGlassPreblurred = compositionLocalOf { true }
internal val LocalGlassOrigin = compositionLocalOf { Offset.Zero }
/** Round transport controls retain their own shape within either interface. */
internal fun navigationGlassShape(emberX: Boolean, shape: Shape? = null): Shape =
    shape ?: if (emberX) androidx.compose.foundation.shape.RoundedCornerShape(EmberXLayout.Radius.dp) else CircleShape

@Composable
internal fun Modifier.navigationGlass(shape: Shape? = null, focused: Boolean = false): Modifier = glassSurface(navigationGlassShape(isEmberX, shape), focused = focused)

/** The material belongs to the control, so backgrounds are never painted twice. */
@Composable
fun FrostedPill(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(navigationGlassShape(isEmberX)), content = content)
}
