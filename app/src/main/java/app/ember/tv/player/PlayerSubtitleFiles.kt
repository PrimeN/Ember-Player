package app.ember.tv.player

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

/** Serialized storage survives an Activity closing without retaining that Activity. */
internal object SubtitleChoiceStorage {
    val dispatcher=Dispatchers.IO.limitedParallelism(1)
    val scope=CoroutineScope(SupervisorJob()+dispatcher)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.subtitleSelectionChanged() {
    manualSubtitleJob?.cancel();manualSubtitleJob=null
    subsTouched=true
    val context=applicationContext;val profile=watchProfileId;val key=resumeKey
    SubtitleChoiceStorage.scope.launch {runCatching {ManualSubtitleChoices(context,profile).remove(key)}}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.loadPickedSubtitles(uris:List<Uri>) {
    subtitleSelectionChanged()
    val visit=playbackVisit
    manualSubtitleJob=lifecycleScope.launch {
        try {
            val choice=withContext(Dispatchers.IO){NearbySubtitles.localChoice(applicationContext,uris)}
            if(visit==playbackVisit)loadManualSubtitle(choice,remember=true)
        } catch(e:CancellationException){throw e}
        catch(_:Exception){subtitleFileError()}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal suspend fun PlayerActivity.loadManualSubtitle(choice:ManualSubtitleFile,remember:Boolean) {
    val context=applicationContext;val profile=watchProfileId;val key=resumeKey
    val visit=playbackVisit;val captionConfig=subs.config
    var temporary:Pair<java.io.File,List<java.io.File>>?=null
    try {
        subs.reserveManualSelection()
        val text=withContext(Dispatchers.IO) {
            if(choice.network!=null)NearbySubtitles.networkText(context,profile,choice,captionConfig)
            else {
                val uris=choice.uris.map(Uri::parse)
                temporary=MediaFiles.bitmap(context,uris)
                if(temporary==null)MediaFiles.subtitle(context,uris.first()) else null
            }
        }
        currentCoroutineContext().ensureActive()
        if(visit!=playbackVisit)return
        val bitmap=temporary
        if(bitmap!=null) {
            val native=playbackEngine?.compatibility ?: if(playbackEngine?.switch()==true)playbackEngine?.compatibility else null
            checkNotNull(native){"Compatibility playback unavailable"}
            native.externalFile(bitmap.first,bitmap.second)
            temporary=null // The compatibility engine owns and removes these files.
            subs.selectManualNative()
        } else {
            checkNotNull(text)
            check(subs.loadManual(text.first,text.second)){"Unreadable subtitle"}
        }
        if(remember)SubtitleChoiceStorage.scope.launch {
            val saved=runCatching {ManualSubtitleChoices(context,profile).put(key,choice)}.getOrDefault(false)
            if(!saved)withContext(Dispatchers.Main){
                Toast.makeText(context,"Subtitle loaded, but its choice could not be saved.",Toast.LENGTH_SHORT).show()
            }
        }
        changed()
    } finally {temporary?.let {(file,others)->(listOf(file)+others).forEach {it.delete()}}}
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.restoreManualSubtitle(notify:Boolean=false) {
    manualSubtitleJob?.cancel()
    val context=applicationContext;val profile=watchProfileId;val key=resumeKey;val visit=playbackVisit
    manualSubtitleJob=lifecycleScope.launch {
        try {
            val choice=withContext(SubtitleChoiceStorage.dispatcher){ManualSubtitleChoices(context,profile).get(key)}
            if(visit!=playbackVisit || subsTouched&&!notify)return@launch
            if(choice!=null)loadManualSubtitle(choice,remember=false)
            else if(notify)Toast.makeText(context,"No saved subtitle for this video.",Toast.LENGTH_SHORT).show()
        } catch(e:CancellationException){throw e}
        catch(_:Exception){if(notify)subtitleFileError()}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.findNearbySubtitles() {
    nearbySubtitleJob?.cancel()
    val media=fileQueue?.current ?: QueueMedia(currentUrl.orEmpty(),item.title)
    val visit=playbackVisit;val profile=watchProfileId
    Toast.makeText(this,"Looking for nearby subtitle files…",Toast.LENGTH_SHORT).show()
    nearbySubtitleJob=lifecycleScope.launch {
        try {
            val choices=withContext(Dispatchers.IO){
                val job=currentCoroutineContext()
                NearbySubtitles.find(applicationContext,profile,media){job.ensureActive()}
            }
            if(visit!=playbackVisit)return@launch
            showPlayerActions("Nearby subtitles",choices.map {choice->PlayerDialogAction(choice.name){
                if(visit!=playbackVisit)return@PlayerDialogAction
                subtitleSelectionChanged()
                manualSubtitleJob=lifecycleScope.launch {
                    try {loadManualSubtitle(choice,remember=true)}
                    catch(e:CancellationException){throw e}
                    catch(_:Exception){subtitleFileError()}
                }
            }}+PlayerDialogAction("Choose subtitle folder"){pickSubtitleFolder()},
                message=if(choices.isEmpty())"No matching files found. Choose a folder or load a subtitle file directly." else null)
        } catch(e:CancellationException){throw e}
        catch(_:Exception){subtitleFileError()}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.pickSubtitleFolder() {
    pickerWasPlaying=player?.playWhenReady==true;pickingSidecar=true;player?.pause()
    try {subtitleFolderPicker.launch(null)}
    catch(_:android.content.ActivityNotFoundException){
        pickingSidecar=false;player?.playWhenReady=pickerWasPlaying
        Toast.makeText(this,"No folder picker is installed on this device.",Toast.LENGTH_LONG).show()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.saveSubtitleFolder(uri:Uri) {
    nearbySubtitleJob?.cancel()
    val visit=playbackVisit;val profile=watchProfileId
    nearbySubtitleJob=lifecycleScope.launch {
        try {
            withContext(Dispatchers.IO){
                applicationContext.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                check(NearbySubtitles.folders(applicationContext,profile).add(DeviceFolder(uri.toString(),DeviceFiles.name(applicationContext,DeviceFiles.root(uri)))))
            }
            if(visit==playbackVisit)findNearbySubtitles()
        } catch(e:CancellationException){throw e}
        catch(_:Exception){Toast.makeText(this@saveSubtitleFolder,"Could not keep access to that subtitle folder.",Toast.LENGTH_LONG).show()}
    }
}

private fun PlayerActivity.subtitleFileError() {
    Toast.makeText(this,"Could not read this subtitle file. Check its format, encoding and folder access. For VobSub, select both IDX and SUB files.",Toast.LENGTH_LONG).show()
}
