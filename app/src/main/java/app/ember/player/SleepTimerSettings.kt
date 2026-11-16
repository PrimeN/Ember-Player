package app.ember.player

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
    val state by SleepTimer.state.collectAsState()
    var now by remember {mutableLongStateOf(android.os.SystemClock.elapsedRealtime())}
    var custom by remember {mutableStateOf(false)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state){lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED){while(state.deadline>0){now=android.os.SystemClock.elapsedRealtime();delay(1000)}}}
    Text(when {state.endOfItem->"Stops after the current item";state.deadline>0->"Remaining · ${((state.remaining(now)+59999)/60000)} min";else->"Sleep timer is off"},fontSize=13.sp,color=Mute)
    ButtonRow {listOf(15,30,60,90).forEach {minutes->Chip("$minutes min"){SleepTimer.after(minutes)}}}
    ButtonRow {
        Chip("Custom duration"){custom=true}
        Chip("End of item",selected=state.endOfItem){SleepTimer.atEnd()}
        Chip("Cancel timer"){SleepTimer.cancel()}
    }
    if(custom) {
        var text by remember {mutableStateOf("45")}
        var notice by remember {mutableStateOf<String?>(null)}
        TvDialog({custom=false},width=420.dp,resizeForKeyboard=true){
            DialogHeading("Sleep timer")
            TvTextField(text,{text=it.filter(Char::isDigit).take(4)},Modifier.fillMaxWidth(),placeholder="Minutes · 1 to 1440",keyboardType=KeyboardType.Number)
            notice?.let {Text(it,fontSize=12.sp)}
            ButtonRow {
                Chip("Start"){val minutes=text.toIntOrNull();if(minutes!=null&&minutes in 1..1440){SleepTimer.after(minutes);custom=false}else notice="Choose 1–1440 minutes."}
                Chip("Back"){custom=false}
            }
        }
    }
}
