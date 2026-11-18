package app.ember.player

import app.ember.tv.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ember.tv.data.*
import app.ember.tv.ui.*
import kotlinx.coroutines.*

@Composable
internal fun AdvancedSubtitleSettings(config:SubtitleConfig,save:(SubtitleConfig)->Unit) {
    val uiResources=androidx.compose.ui.platform.LocalResources.current

    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var expanded by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<PlayerFeedback?>(null)}
    val fontPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)scope.launch {
        try {
            val family=withContext(Dispatchers.IO) {
                val bytes=context.contentResolver.openInputStream(uri)?.use {SubtitleFiles.read(it,SubtitleFonts.MAX_BYTES.toInt())} ?: error("Font unavailable.")
                SubtitleFonts.install(context,bytes)
            }
            save(config.copy(font=SubtitleFonts.CUSTOM,customFontName=family));notice=PlayerFeedback(uiResources.getString(R.string.ui_font_installed),false)
        } catch(e:CancellationException){throw e}
        catch(_:Exception){notice=PlayerFeedback(uiResources.getString(R.string.ui_could_not_install_this_font_choose_a_valid_ttf))}
    }}
    PlayerAction(if(expanded)uiResources.getString(R.string.ui_hide_subtitle_details) else uiResources.getString(R.string.ui_more_subtitle_controls)) {expanded=!expanded}
    if(!expanded)return
    val fonts=SubtitleFonts.options.filter {it.first!=SubtitleFonts.CUSTOM || SubtitleFonts.hasCustom(context)}
    PlayerChoice(uiResources.getString(R.string.ui_font),fonts,config.font){save(config.copy(font=it))}
    PlayerAction(uiResources.getString(R.string.ui_load_a_font_file)) {runCatching {fontPicker.launch(arrayOf("font/*","application/octet-stream","*/*"))}.onFailure {notice=PlayerFeedback(uiResources.getString(R.string.ui_no_document_picker_is_installed_on_this_device))}}
    PlayerChoice(uiResources.getString(R.string.ui_text_color),listOf("white" to uiResources.getString(R.string.ui_white),"yellow" to uiResources.getString(R.string.ui_yellow),"green" to uiResources.getString(R.string.ui_green),"cyan" to uiResources.getString(R.string.ui_cyan)),config.color){save(config.copy(color=it,layout=config.layout.copy(foreground="")))}
    PlayerChoice(uiResources.getString(R.string.ui_text_scale),(50..200 step 5).map {it.toString() to "$it%"},config.layout.scalePercent.toString()){save(config.copy(layout=config.layout.copy(scalePercent=it.toInt())))}
    PlayerChoice(uiResources.getString(R.string.ui_alignment),listOf("auto" to uiResources.getString(R.string.ui_from_subtitle_file),"left" to uiResources.getString(R.string.ui_left),"center" to uiResources.getString(R.string.ui_center),"right" to uiResources.getString(R.string.ui_right)),config.layout.alignment){save(config.copy(layout=config.layout.copy(alignment=it)))}
    PlayerChoice(uiResources.getString(R.string.ui_outline_width),listOf(0f,1f,2.5f,4f,6f).map {it.toString() to uiResources.getString(R.string.ui_px ,it.toString())},config.layout.outlineWidth.toString()){save(config.copy(layout=config.layout.copy(outlineWidth=it.toFloat())))}
    PlayerChoice(uiResources.getString(R.string.ui_letter_spacing),listOf(-2f,0f,1f,2f,4f,6f,10f).map {it.toString() to uiResources.getString(R.string.ui_px ,it.toString())},config.layout.letterSpacing.toString()){save(config.copy(layout=config.layout.copy(letterSpacing=it.toFloat())))}
    PlayerChoice(uiResources.getString(R.string.ui_line_spacing),listOf(-5f,0f,2f,4f,8f,12f,20f).map {it.toString() to uiResources.getString(R.string.ui_px ,it.toString())},config.layout.lineSpacing.toString()){save(config.copy(layout=config.layout.copy(lineSpacing=it.toFloat())))}
    PlayerChoice(uiResources.getString(R.string.ui_bottom_margin),listOf("auto" to uiResources.getString(R.string.ui_from_position_setting))+(0..35 step 5).map {it.toString() to "$it%"},config.layout.bottomMarginPercent?.toString() ?: "auto") {save(config.copy(layout=config.layout.copy(bottomMarginPercent=it.toIntOrNull())))}
    PlayerChoice(uiResources.getString(R.string.ui_character_encoding),SubtitleEncodings.options,config.encoding){save(config.copy(encoding=it))}
    Text(uiResources.getString(R.string.ui_default_timing_ms ,config.defaultDelayMs.toString()),style=androidx.tv.material3.MaterialTheme.typography.bodySmall)
    ButtonRow {
        PlayerAction(uiResources.getString(R.string.ui_100_ms)){save(config.copy(defaultDelayMs=(config.defaultDelayMs-100).coerceAtLeast(-600000)))}
        PlayerAction(uiResources.getString(R.string.ui_100_ms_d618f2)){save(config.copy(defaultDelayMs=(config.defaultDelayMs+100).coerceAtMost(600000)))}
        PlayerAction(uiResources.getString(R.string.ui_reset_timing)){save(config.copy(defaultDelayMs=0))}
    }
    var foreground by remember(config.layout.foreground) {mutableStateOf(config.layout.foreground)}
    var background by remember(config.layout.background) {mutableStateOf(config.layout.background)}
    var edge by remember(config.layout.edgeColor) {mutableStateOf(config.layout.edgeColor)}
    TvTextField(foreground,{foreground=it.take(9)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_text_color_rrggbb_or_aarrggbb))
    TvTextField(background,{background=it.take(9)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_box_color_rrggbb_or_aarrggbb))
    TvTextField(edge,{edge=it.take(9)},Modifier.fillMaxWidth(),placeholder=uiResources.getString(R.string.ui_outline_color_rrggbb_or_aarrggbb))
    PlayerAction(uiResources.getString(R.string.ui_apply_custom_colors)) {
        if(listOf(foreground,background,edge).all {it.isBlank() || SubtitleLayout.validColor(it)})
            save(config.copy(layout=config.layout.copy(foreground=foreground,background=background,edgeColor=edge)))
        else notice=PlayerFeedback(uiResources.getString(R.string.ui_use_rrggbb_or_aarrggbb_colors))
    }
    SubtitleStylePreview(config)
    Text(if(config.systemStyle)uiResources.getString(R.string.ui_system_caption_colors_font_and_edge_style_take_priority) else if(config.authoredStyles)uiResources.getString(R.string.ui_preview_of_your_defaults_a_subtitle_file_s_authored) else uiResources.getString(R.string.ui_preview_of_your_selected_subtitle_style),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    Text(uiResources.getString(R.string.ui_alignment_spacing_and_custom_outline_width_use_compatibility_playback),style=androidx.tv.material3.MaterialTheme.typography.bodySmall,color=Mute)
    notice?.let {PlayerFeedbackText(it)}
}
