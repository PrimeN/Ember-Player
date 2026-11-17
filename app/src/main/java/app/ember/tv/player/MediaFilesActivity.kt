package app.ember.tv.player

import app.ember.tv.R

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import app.ember.tv.data.Prefs
import androidx.lifecycle.lifecycleScope
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
              catch(_:Exception) {Toast.makeText(this@MediaFilesActivity,resources.getString(R.string.ui_could_not_open_the_selected_files),Toast.LENGTH_LONG).show()}
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
                        .onFailure {Toast.makeText(this,resources.getString(R.string.ui_no_file_picker_is_installed_on_this_device),Toast.LENGTH_LONG).show()}
                }
            }
        }
    }
}
