package app.ember.tv.player

import app.ember.tv.R

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
internal fun PlayerActivity.pickFile(which:String) {
    if(privatePlayback)app.ember.player.PrivateMedia.beginPicker()
    pickerWasPlaying=player?.playWhenReady==true;pickingSidecar=true;player?.pause()
    try {when(which){"sub"->subtitleFilePicker.launch(arrayOf("*/*"));"audio"->audioFilePicker.launch(arrayOf("audio/*","application/octet-stream"));else->queueFilePicker.launch(arrayOf("video/*","audio/*","application/octet-stream"))}}
    catch(_:android.content.ActivityNotFoundException){pickingSidecar=false;player?.playWhenReady=pickerWasPlaying;Toast.makeText(this,resources.getString(R.string.ui_no_file_picker_is_installed_on_this_tv),Toast.LENGTH_LONG).show()}
}
