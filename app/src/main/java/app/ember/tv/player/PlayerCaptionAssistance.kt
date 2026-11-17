package app.ember.tv.player

import app.ember.tv.R

import android.widget.FrameLayout

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.updateCaptionAssistance(text:String,playing:Boolean) {
    if(!captionForeground){closeCaptionAssistance();return}
    val captions=captionConfig ?: prefs.subtitleConfig.also {captionConfig=it}
    val options=captions.assistance
    if(!options.translate&&!options.readAloud){closeCaptionAssistance();return}
    if(captionOptions!=options || captionAssistant==null) {
        closeCaptionAssistance()
        captionOptions=options
        val overlay=androidx.media3.ui.SubtitleView(this).also {
            it.isFocusable=false;it.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            SubtitleEngine.style(it,captions)
            it.setBottomPaddingFraction((captions.layout.position(.08f)+.10f).coerceAtMost(.45f))
            view.overlayFrameLayout?.addView(it,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
        }
        captionOverlay=overlay
        captionAssistant=CaptionAssistant(options,OfflineCaptions.translator(applicationContext,options),
            if(options.readAloud)OfflineCaptionSpeaker(applicationContext,options){voices->if(voices.isEmpty())android.widget.Toast.makeText(this,resources.getString(R.string.ui_no_installed_offline_speech_voice_is_available),android.widget.Toast.LENGTH_LONG).show()} else null,
            unavailable={android.widget.Toast.makeText(this,resources.getString(R.string.ui_offline_translation_is_unavailable_original_subtitles_remain_visible),android.widget.Toast.LENGTH_LONG).show()}) {translated->
            overlay.setCues(if(translated.isBlank())emptyList() else listOf(androidx.media3.common.text.Cue.Builder().setText(translated).build()))
        }
    }
    captionAssistant?.update(text,playing)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.closeCaptionAssistance() {
    captionAssistant?.close();captionAssistant=null;captionOptions=null
    captionOverlay?.let {(it.parent as? android.view.ViewGroup)?.removeView(it)};captionOverlay=null
}
