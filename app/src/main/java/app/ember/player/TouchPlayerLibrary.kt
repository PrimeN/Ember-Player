package app.ember.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.R
import app.ember.tv.ui.LocalRenderOptions

@Composable
internal fun isPlayerTablet(): Boolean {
    val config = LocalConfiguration.current
    val shortestWidth = config.smallestScreenWidthDp.takeIf { it > 0 }
        ?: minOf(config.screenWidthDp, config.screenHeightDp)
    return shortestWidth >= 600
}

@Composable
internal fun TouchLibraryHeading(title: String, tools: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        LibraryControlSurface { Row(content = tools) }
    }
}

@Composable
internal fun TouchLibraryDock(
    homeSelected: Boolean,
    filesSelected: Boolean,
    searchSelected: Boolean,
    home: () -> Unit,
    files: () -> Unit,
    settings: () -> Unit,
    search: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryControlSurface(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), floating = true) {
        Row(Modifier.padding(4.dp)) {
            val item = Modifier.weight(1f)
            TouchLibraryTab(stringResource(R.string.ui_library_home), Icons.Outlined.PlayArrow, homeSelected, item, home)
            TouchLibraryTab(stringResource(R.string.ui_library_files), Icons.Outlined.Folder, filesSelected, item, files)
            TouchLibraryTab(stringResource(R.string.ui_settings), Icons.Outlined.Settings, false, item, settings)
            TouchLibraryTab(stringResource(R.string.ui_search), Icons.Outlined.Search, searchSelected, item, search)
        }
    }
}

@Composable
private fun TouchLibraryTab(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier,
    click: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = click,
        modifier = modifier.heightIn(min = 54.dp).semantics { selected = active },
        shape = CircleShape,
        color = if (active) scheme.surfaceContainerHigh else Color.Transparent,
        contentColor = if (active) scheme.primary else scheme.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibraryControlSurface(modifier: Modifier = Modifier, floating: Boolean = false, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scheme.surface.copy(alpha = if (floating && LocalRenderOptions.current.glass) .96f else 1f),
        shadowElevation = if (floating) 4.dp else 0.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        content = content,
    )
}

@Composable
internal fun LibraryWelcome(message: String, addFiles: (() -> Unit)?, folders: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val television = LocalPlayerTelevision.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerBrandMark(if (television) 80.dp else 106.dp)
        Text(
            stringResource(R.string.ui_ember_player),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(message, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        val addLabel = stringResource(R.string.ui_add_files)
        val foldersLabel = stringResource(R.string.ui_media_folders)
        val addButton = Modifier.widthIn(min = 176.dp).heightIn(min = 48.dp)
        if (television) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PlayerAction(addLabel, Icons.Outlined.Add, primary = true, enabled = addFiles != null) { addFiles?.invoke() }
                PlayerAction(foldersLabel, Icons.Outlined.CreateNewFolder, onClick = folders)
            }
        } else {
            Button(
                onClick = { addFiles?.invoke() },
                modifier = addButton,
                enabled = addFiles != null,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(addLabel)
            }
            PlayerAction(foldersLabel, Icons.Outlined.CreateNewFolder, onClick = folders)
        }
    }
}
