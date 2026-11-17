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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.Focusable

@Composable
internal fun PlayerPalettePicker(value:Int,pick:(Int)->Unit) {
    Text("Colors",style=MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(Triple(0,"Ember",Color(0xFFFF9A3C)),Triple(1,"Charcoal",Color(0xFF747B86)),
            Triple(5,"Modern Ember",Color(0xFFA8C7FA)),Triple(6,"Cinema Red",Color(0xFFE50914))).forEach {(id,name,color)->
            Focusable({pick(id)},Modifier.widthIn(min=148.dp,max=220.dp).semantics {selected=value==id},
                container=if(value==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                focusedContainer=MaterialTheme.colorScheme.primary,focusedContent=MaterialTheme.colorScheme.onPrimary,outline=false,focusedScale=1.02f) {
                Row(Modifier.heightIn(min=56.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(24.dp).background(color,CircleShape))
                    Text(name,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(value==id)Icon(Icons.Outlined.Check,"Selected",Modifier.size(18.dp))
                }
            }
        }
    }
}
