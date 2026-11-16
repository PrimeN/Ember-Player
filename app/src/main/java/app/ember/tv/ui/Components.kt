package app.ember.tv.ui

import androidx.compose.runtime.*

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    radius: Int = TvTokens.Shape.Card,
    container: Color = Panel,
    focusedContainer: Color = container,
    focusedContent: Color = Fg,
    outline: Boolean = true,
    focusedScale: Float = TvTokens.Focus.Card,
    enabled: Boolean = true,
    card: Boolean = false,
    content: @Composable () -> Unit,
) {
    if(app.ember.player.LocalPlayerDesign.current && !app.ember.player.LocalPlayerTelevision.current) {
        app.ember.player.PlayerTouchSurface(onClick,onLongClick,modifier,role,radius,container,focusedContainer,Fg,focusedContent,enabled,content)
        return
    }
    val navigation = LocalNavigationFocus.current
    val scene = LocalFocusScene.current.takeUnless { androidx.compose.ui.platform.LocalView.current.parent is androidx.compose.ui.window.DialogWindowProvider }
    @Suppress("DEPRECATION")
    val itemId = androidx.compose.runtime.currentCompositeKeyHash.toString()
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.DisposableEffect(navigation, scene, itemId) {
        if (scene != null) navigation?.register(scene, itemId, requester)
        onDispose { if (scene != null) navigation?.unregister(scene, itemId, requester) }
    }
    val focusModifier = if (navigation != null && scene != null) {
        modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) navigation.focused(scene, itemId) }
    } else modifier
    val ring = Ring
    val render=LocalRenderOptions.current
    val reduceFeedback=LocalReduceMotion.current || RuntimeBudgets.pressured
    val reduced=reduceFeedback || !render.animationsEnabled
    val style=LocalCardFocusStyle.current
    var cardFocused by remember {mutableStateOf(false)}
    val motion=card && app.ember.tv.data.CardFocusStyle.animated(style,render.lowPower,reduced)
    val pulse=cardPulse(card && outline && app.ember.tv.data.CardFocusStyle.pulse(style,render.lowPower,reduced,cardFocused,LocalSceneInteractive.current))
    val cardScale=render.cardFocusScale(focusedScale,style,reduceFeedback)
    val content = Fg
    val shape = RoundedCornerShape((if(isEmberX && radius==TvTokens.Shape.Card)EmberXLayout.Radius else radius).dp)
    Surface(
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = focusModifier.onFocusChanged {cardFocused=it.isFocused}.semantics(mergeDescendants = true) {
            this.role = role
            if (onLongClick != null) onLongClick("Options") { onLongClick(); true }
        },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = focusedContainer,
            focusedContentColor = focusedContent,
            pressedContainerColor = focusedContainer,
            pressedContentColor = focusedContent,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if(card)cardScale else render.focusScale(focusedScale,reduced),
            pressedScale=if(card && cardScale>1f){if(render.lowPower || render.automaticEffectsReduced).99f else .98f} else 1f),
        border = ClickableSurfaceDefaults.border(
            // Owned by Surface so the border and image share shape, scale and pressed geometry.
            focusedBorder = if(card || outline) Border(
                border=BorderStroke(2.dp,if(card)ring.copy(alpha=1f-pulse.value*.55f) else ControlStateCue.edge(focusedContainer)),
                shape=shape,
            ) else Border.None,
        ),
        // An explicit Glow style gets one soft accent halo. Other styles never add a dark ring.
        glow = if(card && outline && motion && style==app.ember.tv.data.CardFocusStyle.GLOW) {
            ClickableSurfaceDefaults.glow(focusedGlow=androidx.tv.material3.Glow(elevationColor=ring.copy(alpha=.22f),elevation=4.dp))
        } else ClickableSurfaceDefaults.glow(),
    ) {
        if(app.ember.player.LocalPlayerDesign.current)CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides androidx.tv.material3.LocalContentColor.current
        ) {content()} else content()
    }
}

@Composable
fun Chip(label: String, selected: Boolean = false, modifier: Modifier = Modifier, centered: Boolean = false, maxLines: Int = 2, onClick: () -> Unit) {
    if(app.ember.player.LocalPlayerDesign.current && !app.ember.player.LocalPlayerTelevision.current) {
        val scheme=androidx.compose.material3.MaterialTheme.colorScheme
        androidx.compose.material3.FilledTonalButton(onClick,modifier.semantics {this.selected=selected}.then(Modifier.sizeIn(minHeight=48.dp)),
            shape=RoundedCornerShape(12.dp),colors=androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor=if(selected)scheme.primaryContainer else scheme.surfaceVariant,
                contentColor=if(selected)scheme.onPrimaryContainer else scheme.onSurface)) {
            androidx.compose.material3.Text(label,maxLines=maxLines,softWrap=maxLines>1,overflow=TextOverflow.Ellipsis)
        }
        return
    }
    // Selection uses a persistent accent fill; focus uses the brighter navigation pill.
    Focusable(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        radius = if(isEmberX)EmberXLayout.Radius else TvTokens.Shape.Pill,
        container = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .38f) else Wash,
        focusedContainer = PillColor,
        focusedContent = OnPillColor,
        outline = false,
        focusedScale = TvTokens.Focus.Control,
    ) {
        Row((if(centered)Modifier.fillMaxWidth() else Modifier).padding(horizontal=18.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(centered)Arrangement.Center else Arrangement.Start) {
            Text(label,fontSize=TvTokens.Type.Label.sp,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Medium,
                maxLines=maxLines,softWrap=maxLines>1,overflow=TextOverflow.Ellipsis,
                textAlign=if(centered)androidx.compose.ui.text.style.TextAlign.Center else null)
        }
    }
}

@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) { content() }
}

@Composable
/**
 * [locked] (#102): shown off and dimmed because it can't be changed here; it still takes focus,
 * and [onClick] should say why.
 */
fun SwitchChip(label: String, on: Boolean, modifier: Modifier = Modifier, locked: Boolean = false, onClick: () -> Unit) {
    if(app.ember.player.LocalPlayerDesign.current && !app.ember.player.LocalPlayerTelevision.current) {
        val scheme=androidx.compose.material3.MaterialTheme.colorScheme
        Row(modifier.fillMaxWidth().sizeIn(minHeight=56.dp).semantics {
            role=Role.Switch;toggleableState=if(on&&!locked)ToggleableState.On else ToggleableState.Off
            stateDescription=if(locked)"Off, not available" else if(on)"On" else "Off"
        }.then(Modifier.clickable(role=Role.Switch,onClick=onClick)).padding(horizontal=8.dp,vertical=4.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Text(label,Modifier.weight(1f),color=scheme.onSurface,maxLines=3,overflow=TextOverflow.Ellipsis)
            androidx.compose.material3.Switch(checked=on&&!locked,onCheckedChange=null,enabled=!locked)
        }
        return
    }
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val shownOn = on && !locked
    Focusable(
        onClick = onClick,
        role = Role.Switch,
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = if (shownOn) ToggleableState.On else ToggleableState.Off
            stateDescription = if (locked) "Off, not available" else if (on) "On" else "Off"
        }.onFocusChanged { focused = it.isFocused || it.hasFocus }.then(if (locked) Modifier.alpha(0.5f) else Modifier),
        radius = TvTokens.Shape.Pill,
        container = Wash,
        focusedContainer = PillColor,
        focusedContent = OnPillColor,
        outline = false,
        focusedScale = TvTokens.Focus.Control,
    ) {
        Row(
            Modifier.padding(start = if (label.isEmpty()) 10.dp else 18.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (label.isNotEmpty()) Text(label, fontSize = TvTokens.Type.Label.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            // On: accent track (darker on the white focused pill); off: grey track.
            val track = when {
                shownOn && focused -> lerp(accent, Color.Black, 0.25f)
                shownOn -> accent
                focused -> OnPillColor.copy(alpha = 0.35f)
                else -> Fg.copy(alpha = 0.3f)
            }
            Box(
                Modifier.width(40.dp).height(22.dp).clip(RoundedCornerShape(50)).background(track),
                contentAlignment = if (shownOn) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}


@Composable
fun Section(title:String,subtitle:String?=null,id:String?=null,content:@Composable ()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,fontSize=TvTokens.Type.Section.sp,fontWeight=FontWeight.SemiBold)
        subtitle?.let {Text(it,color=Mute,fontSize=13.sp)}
        content()
    }
}
