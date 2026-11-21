package app.ember.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.Focusable
import app.ember.tv.ui.OnPillColor
import app.ember.tv.ui.PillColor
import app.ember.tv.ui.ToolbarHint
import app.ember.tv.ui.TvTokens

@Composable
internal fun TvLibraryHeading(title: String, tools: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerBrandMark(36.dp)
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        tools()
    }
}

@Composable
internal fun LibraryToolbarAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (!LocalPlayerTelevision.current) {
        PlayerIconAction(label, icon, modifier, enabled, onClick)
        return
    }
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    Box {
        Focusable(
            onClick,
            modifier.size(48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
            enabled = enabled,
            radius = TvTokens.Shape.Pill,
            container = scheme.surfaceVariant,
            contentColor = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = .55f),
            focusedContainer = PillColor,
            focusedContent = OnPillColor,
            outline = false,
            focusedScale = 1f,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp))
            }
        }
        ToolbarHint(label, focused)
    }
}
