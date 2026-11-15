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
import kotlinx.coroutines.*

/** Uses Android's storage grants; no broad video/storage permission is requested. */
class MediaFilesActivity:ComponentActivity() {
    private lateinit var openedProfile:String
    private var preparing by mutableStateOf(false)
    private var refresh by mutableIntStateOf(0)
    private fun openFiles(uris:List<android.net.Uri>) {
        if(preparing || uris.isEmpty())return
        preparing=true
        lifecycleScope.launch {
            try {
                val items=withContext(Dispatchers.IO) {uris.take(200).map {
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
        setContent {app.ember.player.PlayerTheme(Prefs(this)) {
            val first=remember {FocusRequester()}
            LaunchedEffect(Unit){withFrameNanos {};runCatching {first.requestFocus()}}
            val version=refresh
            val scope=rememberCoroutineScope()
            var recent by remember {mutableStateOf<List<RecentMedia>>(emptyList())}
            var urlDialog by remember {mutableStateOf(false)}
            var message by remember {mutableStateOf<String?>(null)}
            LaunchedEffect(version){recent=withContext(Dispatchers.IO){MediaHistory(this@MediaFilesActivity).read()}}
            val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {uris ->
                openFiles(uris)
            }
            CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides app.ember.tv.ui.Fg) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(if(PlayerTouchControls.isTelevision(this@MediaFilesActivity))40.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    app.ember.tv.ui.EmberMark(36.dp)
                    Text("Ember Player",fontSize=26.sp,modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Chip("Settings"){startActivity(android.content.Intent(this@MediaFilesActivity,app.ember.player.PlayerSettingsActivity::class.java))}
                }
                Text(stringResource(R.string.tx_play_files_from_this_device_a))
                Chip(if(preparing) "Preparing files…" else "Choose files",modifier=Modifier.focusRequester(first)) {
                    if(!preparing)runCatching {picker.launch(arrayOf("video/*","audio/*","application/octet-stream"))}.onFailure {Toast.makeText(this@MediaFilesActivity,"No file picker is installed on this TV.",Toast.LENGTH_LONG).show()}
                }
                Chip("Open stream URL"){urlDialog=true}
                message?.let {Text(it,fontSize=13.sp)}
                if(recent.isNotEmpty()) {
                    Text("Recent media",fontSize=20.sp)
                    recent.forEach {entry->Chip(entry.title,maxLines=1){
                        runCatching {MediaFiles.launch(this@MediaFilesActivity,listOf(QueueMedia(entry.uri,entry.title)))}
                            .onFailure {message="This media is unavailable. Select it again if its permission has expired."}
                    }}
                    Chip("Clear recent media"){scope.launch {
                        val cleared=withContext(Dispatchers.IO){MediaHistory(this@MediaFilesActivity).clear()}
                        if(cleared)recent=emptyList() else message="Could not clear encrypted history."
                    }}
                }
                PlaylistsPanel(this@MediaFilesActivity,version)
                DeviceFilesPanel(this@MediaFilesActivity,version)
                NetworkFilesPanel(this@MediaFilesActivity,version)
                Chip("Back"){finish()}
            }
            if(urlDialog) {
                var url by remember {mutableStateOf("")}
                var note by remember {mutableStateOf<String?>(null)}
                app.ember.tv.ui.TvDialog({urlDialog=false},width=560.dp,resizeForKeyboard=true) {
                    app.ember.tv.ui.DialogHeading("Open stream URL")
                    app.ember.tv.ui.TvTextField(url,{url=it.take(16384)},Modifier.fillMaxWidth(),placeholder="https:// or rtsp://")
                    note?.let {Text(it,fontSize=13.sp)}
                    app.ember.tv.ui.ButtonRow {
                        Chip("Play"){val value=url.trim()
                            if(!app.ember.player.ExternalMedia.allowed(value) || value.startsWith("content:",true))note="Enter a supported network stream URL."
                            else scope.launch {
                                val name=runCatching {java.net.URI(value).path?.substringAfterLast('/')?.takeIf {it.isNotBlank()}?.take(120)}.getOrNull() ?: "Stream"
                                val queue=listOf(QueueMedia(value,name))
                                withContext(Dispatchers.IO){MediaHistory(this@MediaFilesActivity).add(queue)}
                                runCatching {MediaFiles.launch(this@MediaFilesActivity,queue)}.onSuccess {urlDialog=false}.onFailure {note="Could not open this stream."}
                            }
                        }
                        Chip("Cancel"){urlDialog=false}
                    }
                }
            }
            }
        }}
    }
}
