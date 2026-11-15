package app.ember.tv.player

import android.os.Bundle
import androidx.media3.common.*
import app.ember.tv.data.Subtitles

/** Match the selected soundtrack across backend changes without persisting decoder objects. */
internal data class BackgroundAudioTrack(val language:String?,val mime:String?,val channels:Int,val label:String?) {
    fun selection(tracks:Tracks):TrackSelectionOverride? {
        val choices=tracks.groups.filter {it.type==C.TRACK_TYPE_AUDIO}.flatMap {group->
            (0 until group.length).filter {index->group.isTrackSupported(index,true)}.map {group to it}
        }
        return choices.firstOrNull {(group,index)->
            val format=group.getTrackFormat(index)
            (language==null || Subtitles.normalise(format.language)==Subtitles.normalise(language)) &&
                (mime==null || format.sampleMimeType==mime) && (channels<=0 || format.channelCount==channels) &&
                (label.isNullOrBlank() || format.label==label)
        }?.let {(group,index)->TrackSelectionOverride(group.mediaTrackGroup,index)}
    }
    fun bundle()=Bundle().apply {putString("language",language);putString("mime",mime);putInt("channels",channels);putString("label",label)}
    companion object {
        fun from(format:Format?)=format?.let {BackgroundAudioTrack(it.language,it.sampleMimeType,it.channelCount,it.label)}
        fun selected(tracks:Tracks):BackgroundAudioTrack?=tracks.groups.firstOrNull {it.type==C.TRACK_TYPE_AUDIO && it.isSelected}?.let {group->
            (0 until group.length).firstOrNull {group.isTrackSelected(it)}?.let {from(group.getTrackFormat(it))}
        }
        fun from(bundle:Bundle?)=bundle?.let {BackgroundAudioTrack(it.getString("language"),it.getString("mime"),it.getInt("channels",-1),it.getString("label"))}
    }
}
