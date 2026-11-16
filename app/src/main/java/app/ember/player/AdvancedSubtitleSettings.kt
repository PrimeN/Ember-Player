package app.ember.player

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
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var expanded by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    val fontPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null)scope.launch {
        try {
            val family=withContext(Dispatchers.IO) {
                val bytes=context.contentResolver.openInputStream(uri)?.use {SubtitleFiles.read(it,SubtitleFonts.MAX_BYTES.toInt())} ?: error("Font unavailable.")
                SubtitleFonts.install(context,bytes)
            }
            save(config.copy(font=SubtitleFonts.CUSTOM,customFontName=family));notice="Font installed"
        } catch(e:CancellationException){throw e}
        catch(_:Exception){notice="Could not install this font. Choose a valid TTF or OTF file, up to 12 MB."}
    }}
    Chip(if(expanded)"Hide subtitle details" else "More subtitle controls") {expanded=!expanded}
    if(!expanded)return
    val fonts=SubtitleFonts.options.filter {it.first!=SubtitleFonts.CUSTOM || SubtitleFonts.hasCustom(context)}
    PlayerChoice("Font",fonts,config.font){save(config.copy(font=it))}
    Chip("Load a font file") {runCatching {fontPicker.launch(arrayOf("font/*","application/octet-stream","*/*"))}.onFailure {notice="No document picker is installed on this device."}}
    PlayerChoice("Text colour",listOf("white" to "White","yellow" to "Yellow","green" to "Green","cyan" to "Cyan"),config.color){save(config.copy(color=it,layout=config.layout.copy(foreground="")))}
    PlayerChoice("Text scale",(50..200 step 5).map {it.toString() to "$it%"},config.layout.scalePercent.toString()){save(config.copy(layout=config.layout.copy(scalePercent=it.toInt())))}
    PlayerChoice("Alignment",listOf("auto" to "From subtitle file","left" to "Left","center" to "Centre","right" to "Right"),config.layout.alignment){save(config.copy(layout=config.layout.copy(alignment=it)))}
    PlayerChoice("Outline width",listOf(0f,1f,2.5f,4f,6f).map {it.toString() to "$it px"},config.layout.outlineWidth.toString()){save(config.copy(layout=config.layout.copy(outlineWidth=it.toFloat())))}
    PlayerChoice("Letter spacing",listOf(-2f,0f,1f,2f,4f,6f,10f).map {it.toString() to "$it px"},config.layout.letterSpacing.toString()){save(config.copy(layout=config.layout.copy(letterSpacing=it.toFloat())))}
    PlayerChoice("Line spacing",listOf(-5f,0f,2f,4f,8f,12f,20f).map {it.toString() to "$it px"},config.layout.lineSpacing.toString()){save(config.copy(layout=config.layout.copy(lineSpacing=it.toFloat())))}
    PlayerChoice("Bottom margin",listOf("auto" to "From position setting")+(0..35 step 5).map {it.toString() to "$it%"},config.layout.bottomMarginPercent?.toString() ?: "auto") {save(config.copy(layout=config.layout.copy(bottomMarginPercent=it.toIntOrNull())))}
    PlayerChoice("Character encoding",SubtitleEncodings.options,config.encoding){save(config.copy(encoding=it))}
    Text("Default timing · ${config.defaultDelayMs} ms",fontSize=13.sp)
    ButtonRow {
        Chip("−100 ms"){save(config.copy(defaultDelayMs=(config.defaultDelayMs-100).coerceAtLeast(-600000)))}
        Chip("+100 ms"){save(config.copy(defaultDelayMs=(config.defaultDelayMs+100).coerceAtMost(600000)))}
        Chip("Reset timing"){save(config.copy(defaultDelayMs=0))}
    }
    var foreground by remember(config.layout.foreground) {mutableStateOf(config.layout.foreground)}
    var background by remember(config.layout.background) {mutableStateOf(config.layout.background)}
    var edge by remember(config.layout.edgeColor) {mutableStateOf(config.layout.edgeColor)}
    TvTextField(foreground,{foreground=it.take(9)},Modifier.fillMaxWidth(),placeholder="Text colour · #RRGGBB or #AARRGGBB")
    TvTextField(background,{background=it.take(9)},Modifier.fillMaxWidth(),placeholder="Box colour · #RRGGBB or #AARRGGBB")
    TvTextField(edge,{edge=it.take(9)},Modifier.fillMaxWidth(),placeholder="Outline colour · #RRGGBB or #AARRGGBB")
    Chip("Apply custom colours") {
        if(listOf(foreground,background,edge).all {it.isBlank() || SubtitleLayout.validColor(it)})
            save(config.copy(layout=config.layout.copy(foreground=foreground,background=background,edgeColor=edge)))
        else notice="Use #RRGGBB or #AARRGGBB colours."
    }
    SubtitleStylePreview(config)
    Text(if(config.systemStyle)"System caption colours, font and edge style take priority." else if(config.authoredStyles)"Preview of your defaults. A subtitle file’s authored styling takes priority during playback." else "Preview of your selected subtitle style.",fontSize=12.sp,color=Mute)
    Text("Alignment, spacing and custom outline width use Compatibility playback. System caption settings take priority. Turn off authored styling to override a subtitle file’s fonts and colours.",fontSize=12.sp,color=Mute)
    notice?.let {Text(it,fontSize=12.sp,color=Mute)}
}
