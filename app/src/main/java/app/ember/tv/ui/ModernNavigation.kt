package app.ember.tv.ui

import androidx.compose.runtime.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.key.*

@Composable
internal fun RoundIcon(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Focusable(
        onClick = onClick,
        modifier = modifier.size(42.dp).onFocusChanged { focused = it.isFocused || it.hasFocus },
        radius = TvTokens.Shape.Pill,
        container = if (selected) WashHi else Wash,
        focusedContainer = PillColor,
        focusedContent = OnPillColor,
        outline = false,
        focusedScale = TvTokens.Focus.Round,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { glyph(if (focused) OnPillColor else Fg) }
    }
}
