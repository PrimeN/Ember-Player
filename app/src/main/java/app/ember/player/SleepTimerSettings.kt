package app.ember.player

import app.ember.tv.R

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.data.SleepTimer
import app.ember.tv.ui.*
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun SleepTimerSettings() {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val state by SleepTimer.state.collectAsState()
    var now by remember {mutableLongStateOf(android.os.SystemClock.elapsedRealtime())}
    var custom by remember {mutableStateOf(false)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state){lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED){while(state.deadline>0){now=android.os.SystemClock.elapsedRealtime();delay(1000)}}}
    Text(when {state.endOfItem->uiResources.getString(R.string.ui_stops_after_the_current_item);state.deadline>0->uiResources.getString(R.string.ui_remaining_min ,(((state.remaining(now)+59999)/60000)).toString());else->uiResources.getString(R.string.ui_sleep_timer_is_off)},style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    ButtonRow {listOf(15,30,60,90).forEach {minutes->Chip(uiResources.getString(R.string.ui_min ,minutes.toString())){SleepTimer.after(minutes)}}}
    ButtonRow {
        Chip(uiResources.getString(R.string.ui_custom_duration)){custom=true}
        Chip(uiResources.getString(R.string.ui_end_of_item),selected=state.endOfItem){SleepTimer.atEnd()}
        Chip(uiResources.getString(R.string.ui_cancel_timer)){SleepTimer.cancel()}
    }
    if(custom) {
        var text by remember {mutableStateOf("45")}
        var notice by remember {mutableStateOf<String?>(null)}
        TvDialog({custom=false},width=420.dp,resizeForKeyboard=true,scrollContent=true){
            DialogHeading(uiResources.getString(R.string.ui_sleep_timer))
            TvTextField(text,{text=it.filter(Char::isDigit).take(4)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_minutes_1_to_1440),keyboardType=KeyboardType.Number)
            notice?.let {app.ember.player.PlayerErrorText(it)}
            ButtonRow {
                PlayerAction(uiResources.getString(R.string.ui_start),primary=true){val minutes=text.toIntOrNull();if(minutes!=null&&minutes in 1..1440){SleepTimer.after(minutes);custom=false}else notice=uiResources.getString(R.string.ui_choose_1_1440_minutes)}
                PlayerAction(uiResources.getString(R.string.ui_back)){custom=false}
            }
        }
    }
}
