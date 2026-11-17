package app.ember.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.Focusable
import app.ember.tv.ui.Accents
import app.ember.tv.ui.LocalNight
import app.ember.tv.ui.emberColorScheme
import app.ember.tv.data.ColorThemes

@Composable
internal fun PlayerPalettePicker(value:Int,pick:(Int)->Unit) {
    Text("Colors",style=MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(0,ColorThemes.SHADOW_EMBER,ColorThemes.MODERN_EMBER,ColorThemes.CINEMA_RED).forEach {id->
            val name=Accents[id].first
            val color=emberColorScheme(id,day=!LocalNight.current,oled=false).primary
            Focusable({pick(id)},Modifier.widthIn(min=148.dp,max=220.dp).semantics {selected=value==id},
                container=if(value==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                focusedContainer=MaterialTheme.colorScheme.primary,focusedContent=MaterialTheme.colorScheme.onPrimary,outline=false,focusedScale=1.02f) {
                Row(Modifier.heightIn(min=56.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(24.dp).background(color,CircleShape).testTag("paletteSwatch_$id"))
                    Text(name,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(value==id)Icon(Icons.Outlined.Check,"Selected",Modifier.size(18.dp))
                }
            }
        }
    }
}
