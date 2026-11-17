package app.ember.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ember.tv.R

@Composable
internal fun PlayerBrandMark(size:Dp,modifier:Modifier=Modifier) {
    Image(painterResource(R.drawable.ic_player_mark),null,modifier.size(size))
}

@Composable
internal fun PlayerBrandLockup(modifier:Modifier=Modifier) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color(0xFF211C1A)),contentAlignment=Alignment.Center) {PlayerBrandMark(64.dp)}
        Column {Text(uiResources.getString(R.string.ui_ember_player),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text(app.ember.tv.BuildConfig.VERSION_NAME,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
