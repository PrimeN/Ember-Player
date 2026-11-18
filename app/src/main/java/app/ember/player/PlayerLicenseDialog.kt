package app.ember.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.ember.tv.R
import app.ember.tv.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A bounded text region owns remote paging; at either end focus can leave normally. */
@Composable
internal fun PlayerLicenseDialog(title:String,text:String,close:()->Unit) {
    val scroll=rememberScrollState()
    val scope=rememberCoroutineScope()
    val first=remember {FocusRequester()}
    var focused by remember {mutableStateOf(false)}
    var viewport by remember {mutableIntStateOf(0)}
    var paging by remember {mutableStateOf<Job?>(null)}
    val television=LocalPlayerTelevision.current
    TvDialog(close,width=680.dp) {
        DialogHeading(title)
        Box(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("license-content")
            .onSizeChanged {viewport=it.height}.focusCue(focused,TvTokens.Shape.Small)
            .focusRequester(first).onFocusChanged {focused=it.isFocused}
            .onPreviewKeyEvent {event->
                if(event.type!=KeyEventType.KeyDown)return@onPreviewKeyEvent false
                val direction=when(event.key){Key.DirectionDown,Key.PageDown->1;Key.DirectionUp,Key.PageUp->-1;else->0}
                if(direction==0 || direction>0&&!scroll.canScrollForward || direction<0&&!scroll.canScrollBackward)return@onPreviewKeyEvent false
                paging?.cancel()
                paging=scope.launch {scroll.scrollBy(direction*viewport*.9f)}
                true
            }.focusable().verticalScroll(scroll).padding(8.dp)) {
            Text(text,style=MaterialTheme.typography.bodySmall)
        }
        PlayerAction(androidx.compose.ui.res.stringResource(R.string.ui_close),onClick=close)
    }
    LaunchedEffect(television){if(television){withFrameNanos {};first.requestFocus()}}
}
