package app.ember.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.R
import app.ember.tv.ui.Focusable

private val shortcutDestinations = listOf(
    PlayerLibraryDestination.FOLDERS to Color(0xFF9C390D),
    PlayerLibraryDestination.NETWORK to Color(0xFF30578C),
    PlayerLibraryDestination.PLAYLISTS to Color(0xFF684488),
    PlayerLibraryDestination.DOWNLOADS to Color(0xFF24675F),
    PlayerLibraryDestination.PRIVATE to Color(0xFF484851),
)

@Composable
internal fun LibraryShortcuts(open: (PlayerLibraryDestination) -> Unit) {
    val television = LocalPlayerTelevision.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerSectionTitle(stringResource(R.string.ui_library_shortcuts))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (television) 16.dp else 12.dp),
            contentPadding = PaddingValues(if (television) 4.dp else 0.dp),
        ) {
            items(shortcutDestinations, key = { (destination, _) -> destination.name }) { (destination, color) ->
                val label = stringResource(destination.label)
                val content: @Composable () -> Unit = {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = if (television) 78.dp else 68.dp)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Icon(destination.icon, contentDescription = null, modifier = Modifier.size(if (television) 30.dp else 24.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (television) {
                    Focusable(
                        { open(destination) }, Modifier.width(158.dp), radius = 14,
                        container = color, focusedContainer = color, contentColor = Color.White,
                        focusedContent = Color.White, card = true, focusedScale = 1.025f, content = content,
                    )
                } else {
                    Surface(
                        onClick = { open(destination) }, modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(12.dp), color = color,
                        contentColor = Color.White, content = content,
                    )
                }
            }
        }
    }
}
