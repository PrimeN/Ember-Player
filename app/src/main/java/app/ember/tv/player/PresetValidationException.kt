package app.ember.tv.player

import app.ember.tv.R

internal class PresetValidationException(@androidx.annotation.StringRes val messageId:Int):IllegalArgumentException() {
    companion object {
        @androidx.annotation.StringRes fun resource(error:Throwable)=
            (error as? PresetValidationException)?.messageId ?: R.string.ui_preset_save_failed
    }
}
