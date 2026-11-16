package app.ember.tv.player

import androidx.compose.ui.res.stringResource
import app.ember.tv.R

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.ember.tv.data.Prefs
import app.ember.tv.ui.EmberTheme
import app.ember.tv.ui.Chip
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*

/** Uses document grants by default; mounted-drive browsing requests media access explicitly. */
class MediaFilesActivity:ComponentActivity() {
    private lateinit var openedProfile:String
    private var preparing by mutableStateOf(false)
    private var refresh by mutableIntStateOf(0)
    private fun openFiles(uris:List<android.net.Uri>) {
        if(preparing || uris.isEmpty())return
        preparing=true
        lifecycleScope.launch {
            try {
                val items=withContext(Dispatchers.IO) {uris.map {
                    ensureActive();MediaFiles.retain(this@MediaFilesActivity,it)
                    QueueMedia(it.toString(),MediaFiles.name(this@MediaFilesActivity,it))
                }}
                ensureActive()
                if(isFinishing || Prefs(this@MediaFilesActivity).currentProfile!=openedProfile)return@launch
                withContext(Dispatchers.IO){MediaHistory(this@MediaFilesActivity).add(items)}
                MediaFiles.launch(this@MediaFilesActivity,items)
            } catch(e:CancellationException) {throw e}
              catch(_:Exception) {Toast.makeText(this@MediaFilesActivity,"Could not open the selected files.",Toast.LENGTH_LONG).show()}
            finally {preparing=false}
        }
    }
    override fun onResume() {
        super.onResume()
        refresh++
        if(::openedProfile.isInitialized && Prefs(this).currentProfile!=openedProfile)finish()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        openedProfile=Prefs(this).currentProfile
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        setContent {
            val prefs=remember {Prefs(this)}
            app.ember.player.PlayerTheme(prefs) {
                val privacy by app.ember.player.PrivateMedia.state.collectAsState()
                SideEffect {if(app.ember.player.PrivateMedia.enabled)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
                val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->openFiles(uris)}
                app.ember.player.PlayerLibraryScreen(this,refresh+privacy.hashCode(),preparing) {
                    if(!preparing)runCatching {picker.launch(arrayOf("video/*","audio/*","application/octet-stream"))}
                        .onFailure {Toast.makeText(this,"No file picker is installed on this device.",Toast.LENGTH_LONG).show()}
                }
            }
        }
    }
}
