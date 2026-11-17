package app.ember.player

import app.ember.tv.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ember.tv.ui.Focusable

internal val LocalPlayerDesign=staticCompositionLocalOf {false}
internal val LocalPlayerTelevision=staticCompositionLocalOf {true}

/** Touch controls use Material 3's state layers; TV keeps its remote-oriented focus treatment. */
@Composable
internal fun PlayerTouchSurface(onClick:()->Unit,onLongClick:(()->Unit)?,modifier:Modifier,role:Role,radius:Int,
    container:Color,focusedContainer:Color,contentColor:Color,focusedContent:Color,enabled:Boolean,content:@Composable ()->Unit) {
    var focused by remember {mutableStateOf(false)}
    Surface(modifier.onFocusChanged {focused=it.hasFocus}.heightIn(min=48.dp)
        .combinedClickable(enabled=enabled,role=role,onClick=onClick,onLongClick=onLongClick),
        shape=RoundedCornerShape(radius.dp),color=if(focused)focusedContainer else container,
        contentColor=if(focused)focusedContent else contentColor,
        border=if(focused)BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) {
        CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides if(focused)focusedContent else contentColor){content()}
    }
}

@Composable
internal fun PlayerAction(label:String,icon:ImageVector?=null,modifier:Modifier=Modifier,primary:Boolean=false,enabled:Boolean=true,destructive:Boolean=false,onClick:()->Unit) {
    val scheme=androidx.tv.material3.MaterialTheme.colorScheme
    if(LocalPlayerTelevision.current) {
        Focusable(onClick,modifier.heightIn(min=48.dp),enabled=enabled,
            container=when {destructive->scheme.errorContainer;primary->scheme.primaryContainer;else->Color.Transparent},
            contentColor=when {destructive->scheme.onErrorContainer;primary->scheme.onPrimaryContainer;else->scheme.onSurface},
            focusedContainer=if(destructive)scheme.error else scheme.primary,focusedContent=if(destructive)scheme.onError else scheme.onPrimary,outline=false,focusedScale=1.02f) {
            CompositionLocalProvider(LocalContentColor provides androidx.tv.material3.LocalContentColor.current) {
                Row(Modifier.padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    icon?.let {Icon(it,null,Modifier.size(20.dp))};Text(label,style=androidx.tv.material3.MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        val body:@Composable RowScope.()->Unit={icon?.let {Icon(it,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))};Text(label,style=androidx.tv.material3.MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)}
        if(primary||destructive)FilledTonalButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,
            colors=if(destructive)ButtonDefaults.filledTonalButtonColors(containerColor=scheme.errorContainer,contentColor=scheme.onErrorContainer) else ButtonDefaults.filledTonalButtonColors(containerColor=scheme.primaryContainer,contentColor=scheme.onPrimaryContainer),shape=RoundedCornerShape(14.dp),content=body)
        else TextButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,colors=ButtonDefaults.textButtonColors(contentColor=scheme.primary),shape=RoundedCornerShape(14.dp),content=body)
    }
}

@Composable
internal fun PlayerIconAction(label:String,icon:ImageVector,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit) {
    if(LocalPlayerTelevision.current) {
        Focusable(onClick,modifier.size(48.dp).semantics {contentDescription=label},enabled=enabled,radius=50,
            container=Color.Transparent,focusedContainer=MaterialTheme.colorScheme.primary,focusedContent=MaterialTheme.colorScheme.onPrimary,outline=false,focusedScale=1.04f) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(icon,label,Modifier.size(24.dp))}
        }
    } else IconButton(onClick,modifier.size(48.dp),enabled=enabled){Icon(icon,label)}
}

@Composable
internal fun PlayerHeading(title:String,modifier:Modifier=Modifier,back:(()->Unit)?=null,actions:@Composable RowScope.()->Unit={}) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    Row(modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(back!=null)PlayerIconAction(uiResources.getString(R.string.ui_back),Icons.AutoMirrored.Outlined.ArrowBack,onClick=back) else PlayerBrandMark(36.dp)
        Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
internal fun PlayerSection(title:String,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PlayerSectionTitle(title)
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}

@Composable
internal fun PlayerSectionTitle(title:String) {
    Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.semantics {heading()})
}

@Composable
internal fun PlayerSearchField(value:String,change:(String)->Unit,placeholder:String,focus:androidx.compose.ui.focus.FocusRequester?=null) {
    if(LocalPlayerTelevision.current)app.ember.tv.ui.TvTextField(value,change,Modifier.fillMaxWidth(),placeholder=placeholder,
        imeAction=androidx.compose.ui.text.input.ImeAction.Search,focusRequester=focus)
    else {
        val manager=androidx.compose.ui.platform.LocalFocusManager.current
        TextField(value,change,Modifier.fillMaxWidth().then(if(focus!=null)Modifier.focusRequester(focus) else Modifier),
            placeholder={Text(placeholder)},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,
            keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=KeyboardActions(onSearch={manager.clearFocus()}),
            shape=RoundedCornerShape(28.dp),colors=TextFieldDefaults.colors(
                focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
    }
}
