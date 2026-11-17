package app.ember.player

import app.ember.tv.R

internal data class PlayerSettingSection(val id:String,val category:String,@androidx.annotation.StringRes val title:Int,@androidx.annotation.StringRes val keywords:Int) {
    fun matches(query:String,resources:android.content.res.Resources):Boolean {val locale=resources.configuration.locales[0];val words=query.trim().lowercase(locale).split(Regex("\\s+")).filter(String::isNotBlank);val text=(resources.getString(title)+" "+resources.getString(keywords)).lowercase(locale);return words.all {it in text}}
}
internal object PlayerSettingsIndex {
    val sections=listOf(
        PlayerSettingSection("playback","playback",PlayerUiLabels.PLAYBACK,R.string.ui_player_interface_glass_ember_basic_streaming_controls_engine_resume),
        PlayerSettingSection("network","network",R.string.ui_network_and_buffering,R.string.ui_connect_connection_timeout_read_forward_rewind_memory_custom_buffering),
        PlayerSettingSection("sleep","playback",R.string.ui_sleep_timer,R.string.ui_sleep_timer_stop_after_end_item),
        PlayerSettingSection("enhancement","picture",R.string.ui_picture_enhancement,R.string.ui_gpu_upscaling_fast_shader_brightness_contrast_saturation_gamma_hue),
        PlayerSettingSection("profiles","picture",R.string.ui_saved_picture_shader_profiles,R.string.ui_saved_picture_shader_profile_name_save_delete),
        PlayerSettingSection("hdr","picture",R.string.ui_hdr_output,R.string.ui_hdr_sdr_tone_mapping_gamut_peak_scene_analysis_renderer),
        PlayerSettingSection("decoding","picture",R.string.ui_video_decoding,R.string.ui_software_hardware_video_decoder_hi10p_frame_rate_resolution_dolby),
        PlayerSettingSection("touch","playback",R.string.ui_touch_and_rotation,R.string.ui_gestures_rotation_double_tap_brightness_volume_pinch_zoom_temporary),
        PlayerSettingSection("audio","audio",PlayerUiLabels.AUDIO,R.string.ui_sound_audio_night_listening_compression_hdmi_passthrough_decoding_track),
        PlayerSettingSection("subtitles","subtitles",PlayerUiLabels.SUBTITLES,R.string.ui_captions_subtitles_language_size_background_authored_system_translate_translation),
        PlayerSettingSection("appearance","appearance",R.string.ui_appearance_and_performance,R.string.ui_appearance_performance_colors_colours_display_mode_ember_charcoal_android),
        PlayerSettingSection("privacy","privacy",PlayerUiLabels.PRIVATE_MEDIA,R.string.ui_privacy_pin_create_change_remove_protection_private_media_hidden),
        PlayerSettingSection("backups","privacy",R.string.ui_backups,R.string.ui_backup_export_import_restore_password_encrypted_settings),
        PlayerSettingSection("about","about",R.string.ui_about,R.string.ui_about_version_licenses_legal_terms_source_credits),
        PlayerSettingSection("cache","network",R.string.ui_cache,R.string.ui_cache_management_disposable_clear_image_artwork_thumbnails_temporary_data),
    )
    fun search(query:String,resources:android.content.res.Resources)=sections.filter {it.matches(query,resources)}
}
