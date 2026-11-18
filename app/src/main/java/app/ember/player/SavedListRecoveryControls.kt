package app.ember.player

import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalResources
import app.ember.tv.R
import app.ember.tv.ui.ButtonRow
import kotlinx.coroutines.*

/** Recovery is an explicit action, separate from normal guarded collection writes. */
@Composable
internal fun SavedListRecoveryControls(@StringRes label:Int,@StringRes warning:Int,reset:()->Boolean,reload:()->Unit) {
    val resources=LocalResources.current
    val scope=rememberCoroutineScope()
    var confirming by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    ButtonRow {
        PlayerAction(resources.getString(R.string.ui_refresh),enabled=!busy,onClick=reload)
        PlayerAction(resources.getString(label),destructive=true,enabled=!busy){confirming=true}
    }
    if(failed)PlayerErrorText(resources.getString(R.string.ui_saved_list_reset_failed))
    if(confirming)PlayerConfirmationDialog(resources.getString(label),resources.getString(warning),resources.getString(R.string.ui_reset_list),{confirming=false},busy) {
        if(!busy){
            busy=true;failed=false
            scope.launch {
                try {failed=!withContext(Dispatchers.IO){reset()}}
                catch(e:CancellationException){throw e}
                catch(_:Exception){failed=true}
                finally {busy=false}
                confirming=false;reload()
            }
        }
    }
}
