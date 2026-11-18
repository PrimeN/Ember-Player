package app.ember.player

import app.ember.tv.R

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.player.MediaFiles
import app.ember.tv.player.QueueMedia
import app.ember.tv.ui.*
import kotlinx.coroutines.*
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun DownloadsPanel(activity:ComponentActivity,save:suspend (String,String)->MediaDownload={url,name->DownloadStore(activity).add(url,name)}) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val scope=rememberCoroutineScope()
    var pending by remember {mutableStateOf<String?>(null)}
    val notifications=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        pending?.let {id->runCatching {MediaDownloadService.start(activity,id)}};pending=null
    }
    fun start(id:String) {
        if(android.os.Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pending=id;notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
        else MediaDownloadService.start(activity,id)
    }
    var adding by remember {mutableStateOf(false)}
    var removing by remember {mutableStateOf<MediaDownload?>(null)}
    var notice by remember {mutableStateOf<String?>(null)}
    var readError by remember {mutableStateOf(false)}
    var entries by remember {mutableStateOf<List<MediaDownload>>(emptyList())}
    var version by remember {mutableIntStateOf(0)}
    app.ember.player.PlayerSection(uiResources.getString(R.string.ui_downloads)) {
        LaunchedEffect(version) {activity.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED){while(true){val loaded=withContext(Dispatchers.IO){runCatching {DownloadStore(activity).readForDisplay()}.getOrNull()};readError=loaded==null;entries=loaded.orEmpty();delay(1500)}}}
        ButtonRow {PlayerAction(uiResources.getString(R.string.ui_download_media_url)){adding=true};PlayerAction(uiResources.getString(R.string.ui_pause_downloads)){MediaDownloadService.pause(activity)}}
        Text(uiResources.getString(R.string.ui_direct_media_files_download_one_at_a_time_additional),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
        entries.filterNot {PrivateMedia.hidden(it.url)||PrivateMedia.hidden(DownloadStore.uri(activity,it).toString())}.forEach {entry->
            DownloadRow(entry,
                {runCatching {MediaFiles.launch(activity,listOf(QueueMedia(DownloadStore.uri(activity,entry).toString(),entry.name)))}.onFailure {notice=uiResources.getString(R.string.ui_the_downloaded_file_is_unavailable)}},
                {runCatching {start(entry.id)}.onFailure {notice=uiResources.getString(R.string.ui_android_could_not_start_this_download)}},
                {removing=entry})
        }
        if(readError) {
            PlayerErrorText(uiResources.getString(R.string.ui_downloads_unreadable))
            SavedListRecoveryControls(R.string.ui_reset_saved_downloads,R.string.ui_reset_saved_downloads_warning,{DownloadStore(activity).resetUnreadable()}){version++}
        }
        notice?.let {PlayerErrorText(it)}
        removing?.let {entry->PlayerConfirmationDialog(uiResources.getString(R.string.ui_delete_download),uiResources.getString(R.string.ui_and_its_downloaded_file_will_be_removed_from_this ,entry.name),uiResources.getString(R.string.ui_delete_download_d41b50),{removing=null}) {
            removing=null
            runCatching {MediaDownloadService.remove(activity,entry.id);version++}.onFailure {notice=uiResources.getString(R.string.ui_could_not_remove_this_download)}
        }}
        if(adding) {
            var url by remember {mutableStateOf("")}
            var editedName by remember {mutableStateOf<String?>(null)}
            val name=editedName ?: DownloadStore.suggestedName(url)
            var error by remember {mutableStateOf<String?>(null)}
            var submitting by remember {mutableStateOf(false)}
            TvDialog({if(!submitting)adding=false},width=560.dp,resizeForKeyboard=true,scrollContent=true) {
                DialogHeading(uiResources.getString(R.string.ui_download_media))
                TvTextField(url,{if(!submitting)url=it.take(16384)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_https_video_mp4),enabled=!submitting)
                TvTextField(name,{if(!submitting)editedName=it.take(120)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_filename),enabled=!submitting)
                error?.let {PlayerErrorText(it)}
                ButtonRow {
                    PlayerAction(uiResources.getString(if(submitting)R.string.ui_working else R.string.ui_download),primary=true,enabled=!submitting){if(!submitting){if(!DownloadStore.supported(url.trim())||PrivateMedia.hidden(url.trim()))error=uiResources.getString(R.string.ui_enter_a_direct_non_private_media_url)
                        else {
                            val address=url.trim();val filename=name;submitting=true;error=null
                            scope.launch {
                            try {
                                val item=withContext(Dispatchers.IO){save(address,filename)}
                                start(item.id);adding=false;version++
                            }catch(e:CancellationException){throw e}catch(_:Exception){error=uiResources.getString(R.string.ui_could_not_start_this_download_check_available_space_and)}
                            finally {submitting=false}
                            }
                        }
                    }}
                    app.ember.player.PlayerAction(uiResources.getString(R.string.ui_cancel),enabled=!submitting){adding=false}
                }
            }
        }
    }
}
