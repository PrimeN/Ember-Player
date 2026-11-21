package app.ember.tv.player

import app.ember.tv.R

import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.ember.tv.data.SubtitleCandidate
import app.ember.tv.data.SubtitleConfig
import app.ember.tv.data.Subtitles
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Finds, ranks, picks and shows subtitles for the playing video:
 * the video's own tracks (via Media3) and downloaded ones (via [SubtitleEngine], which can be
 * shifted in time). Settings > Subtitles decides the providers, languages and auto-selection.
 */
@OptIn(UnstableApi::class)
internal class SubtitleController(
    private val activity: ComponentActivity,
    private val player: () -> ExoPlayer?,
    val engine: SubtitleEngine,
    /** Can change between episodes (the show's remembered subtitle choice). */
    var config: SubtitleConfig,
    /** Called when the list, status or selection changes, so the panel can redraw. */
    private val onChange: () -> Unit,
    private val nativeBackend: () -> Any? = { null },
    private val showOwner: (SubtitleOwner) -> Unit = {},
    private val nativeSubtitle: (String,String,Boolean) -> Boolean = {_,_,_->false},
) {
    sealed interface Selection {
        data object Off : Selection
        data class Manual(val label:String) : Selection
        data class Embedded(val group: Int, val track: Int) : Selection
        data class External(val candidate: SubtitleCandidate) : Selection
    }

    var candidates: List<SubtitleCandidate> = emptyList(); private set
    var status: String? = null; private set
    var selection: Selection = Selection.Off; private set
    private val renderers = SubtitleRenderCoordinator { _, _, next ->
        if (next != SubtitleOwner.EXTERNAL) engine.clear()
    }
    internal val drawsOverlay get() = renderers.owner() == SubtitleOwner.EXTERNAL
    private var loadGeneration = 0L
    private var loaded: LoadedText? = null
    private var renderedBackend: Any? = null

    private data class LoadedText(
        val text: String,
        val format: String,
        val wholeFile: Boolean,
        val fallback: SubtitleEngine.Prepared?,
    )

    var onManualSelection:(()->Unit)?=null
    fun reserveManualSelection() { autoSelection.manual(); cancelLoad() }

    private fun cancelLoad() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
    }

    private fun setOwner(owner: SubtitleOwner) {
        renderers.select(owner)
        showOwner(owner)
        if (owner == SubtitleOwner.EXTERNAL || owner == SubtitleOwner.OFF) setMedia3Text(false)
    }

    private fun applyText(value: LoadedText): Boolean {
        val native = nativeSubtitle(value.text, value.format, value.wholeFile)
        val fallback = value.fallback
        if (!native && fallback == null) return false
        if (native) {
            setOwner(SubtitleOwner.NATIVE)
        } else {
            setOwner(SubtitleOwner.EXTERNAL)
            engine.show(checkNotNull(fallback))
        }
        loaded = value
        renderedBackend = nativeBackend()
        refreshAssOverlay()
        return true
    }

    fun refreshAssOverlay() {
        val text = loaded?.takeIf {
            it.format == "ass" && drawsOverlay && nativeBackend() == null &&
                authoredAssEnabled(config.authoredStyles, config.systemStyle)
        }
        engine.useAss(text?.text)
    }

    fun close() {
        cancelLoad()
        searchJob?.cancel()
        loaded = null
        engine.clear()
    }

    fun nativeLoadFailed() {
        if (renderers.owner() != SubtitleOwner.NATIVE) return
        val fallback = loaded?.fallback
        if (fallback == null) {
            setOwner(SubtitleOwner.OFF)
            selection = Selection.Off
        } else {
            setOwner(SubtitleOwner.EXTERNAL)
            engine.show(fallback)
        }
        onChange()
    }

    fun onBackendChanged() {
        val current = nativeBackend()
        val text = loaded
        if (text != null && renderedBackend !== current) {
            if (!applyText(text)) setOwner(SubtitleOwner.OFF)
        } else if (manualBitmap && current == null) {
            manualBitmap = false
            selection = Selection.Off
            setOwner(SubtitleOwner.OFF)
            onChange()
        } else {
            showOwner(renderers.owner())
        }
    }

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var searched = false
    private var ready = false
    private val autoSelection = SubtitleAutoSelection()
    private var release: String? = null
    private var query: Subtitles.Query? = null
    private data class Choice(val language:String,val external:Boolean,val provider:String?=null,val forced:Boolean=false)
    private var choice: Choice? = null
    private var chosenOff = false
    internal val offChosenByUser get()=chosenOff
    private var manualFile:Pair<String,String>?=null
    private var manualBitmap=false
    val wholeFileTiming:Boolean get()=manualFile!=null || manualBitmap

    /** Starts looking for online subtitles for a new video. */
    fun startSearch(url: String, query: Subtitles.Query, preserve: Boolean = false, headers:Map<String,String> = emptyMap()) {
        if (!preserve) { choice=null; chosenOff=false;manualFile=null;manualBitmap=false }
        this.query = query
        searchJob?.cancel()
        cancelLoad()
        if (!preserve || !manualBitmap) {
            loaded = null
            renderedBackend = null
            setOwner(SubtitleOwner.OFF)
        }
        candidates = emptyList()
        selection = Selection.Off
        searched = false
        ready = player()?.playbackState == androidx.media3.common.Player.STATE_READY
        autoSelection.reset()
        release = query.release
        if(preserve&&(manualFile!=null||manualBitmap)) {
            autoSelection.manual();selection=Selection.Manual(activity.resources.getString(R.string.ui_manual_subtitle_file));status=activity.resources.getString(R.string.ui_manual_subtitle_file)
            manualFile?.let {(text,format)->loadJob=activity.lifecycleScope.launch {loadManual(text,format,restoring=true)}}
            onChange();return
        }
        if (chosenOff) autoSelection.manual()
        val anyProvider = config.stremioOpenSubs || config.openSubsCom || config.subdl
        status = if (anyProvider) activity.resources.getString(R.string.ui_searching_for_subtitles) else null
        onChange()
        autoSelect()
        searchJob = activity.lifecycleScope.launch {
            // Use an existing file hash when available to avoid unnecessary range reads.
            val hashed: Pair<String, Long?>? = if (!config.hashMatch || !anyProvider) null
                else query.hash?.let { it to query.size } ?: if (url.startsWith("http")) kotlinx.coroutines.withTimeoutOrNull(5000) { Subtitles.movieHash(url,headers) } else null
            val found = if (anyProvider) Subtitles.search(query.copy(hash = hashed?.first, size = hashed?.second), config) { batch ->
                candidates=Subtitles.rank((candidates+batch).distinctBy { it.url ?: it.fileId.toString() },config,release,player()?.videoFormat?.frameRate?.takeIf { it>0 })
                searched=true;onChange();autoSelect()
            } else emptyList()
            ensureActive()
            candidates = Subtitles.rank((candidates + found).distinctBy {it.url ?: it.fileId.toString()}, config, release, player()?.videoFormat?.frameRate?.takeIf { it > 0 })
            searched = true
            status = when {
                candidates.isEmpty() -> activity.resources.getString(R.string.ui_no_subtitles_found_in_your_languages)
                candidates.any { it.hashMatch } -> activity.resources.getQuantityString(R.plurals.ui_found_made_for_this_exact_file,candidates.size,candidates.size)
                else -> activity.resources.getQuantityString(R.plurals.ui_found,candidates.size,candidates.size)
            }
            onChange()
            autoSelect()
        }
    }

    /** Call when the player is ready: the audio language is known then. */
    fun onReady() {
        ready = true
        autoSelect()
    }

    /** A source program can replace embedded track IDs without changing the subtitle controller. */
    fun reconcileEmbeddedSelection(allowMissing: Boolean = false) {
        if(selection !is Selection.Embedded)return
        val groups=player()?.currentTracks?.groups ?: return
        val index=groups.indexOfFirst {it.type==C.TRACK_TYPE_TEXT&&it.isSelected}
        if (index < 0) {
            if (!allowMissing) {
                selection = Selection.Off
                renderers.select(SubtitleOwner.OFF)
                showOwner(SubtitleOwner.OFF)
                onChange()
            }
            return
        }
        val track = (0 until groups[index].length).firstOrNull { groups[index].isTrackSelected(it) } ?: return
        selection = Selection.Embedded(index, track)
        onChange()
    }

    /** Built-in text tracks of the video, as (group index, track index, label). */
    fun embedded(): List<Triple<Int, Int, String>> {
        val p = player() ?: return emptyList()
        val out = ArrayList<Triple<Int, Int, String>>()
        p.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i, true)) continue
                val f = g.getTrackFormat(i)
                val lang = Subtitles.languageName(Subtitles.normalise(f.language))
                val forced = (if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) activity.resources.getString(R.string.ui_forced_4ce31f) else "") + (if (f.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) activity.resources.getString(R.string.ui_sdh) else "")
                out += Triple(gi, i, listOfNotNull(lang + forced, f.label?.takeIf { it.isNotBlank() && it != f.language }).joinToString(" · "))
            }
        }
        return out
    }

    private fun autoSelect() {
        if (!searched || !ready || chosenOff) return
        val p=player() ?: return
        val first=choice?.language ?: Subtitles.wanted(config).firstOrNull() ?: return
        val audio=p.audioFormat?.language?.let(Subtitles::normalise)
        val want=choice!=null || config.autoSelect=="always" || config.autoSelect=="foreign" && audio!=null && audio!="und" && audio.substringBefore('-')!=first.substringBefore('-')
        if(!want)return
        val embedded=if(choice?.external==true)null else embedded().firstOrNull { (gi,ti,_) ->
            val f=p.currentTracks.groups[gi].getTrackFormat(ti)
            Subtitles.normalise(f.language).substringBefore('-')==first.substringBefore('-') &&
                (if(choice?.forced==true)f.selectionFlags and C.SELECTION_FLAG_FORCED!=0 else f.selectionFlags and C.SELECTION_FLAG_FORCED==0)
        }
        if(embedded!=null) { if(autoSelection.claim(true,true))selectEmbedded(embedded.first,embedded.second,manual=false);return }
        val ordered=candidates.filter { it.lang==first }.sortedByDescending { it.provider==choice?.provider && it.forced==choice?.forced }
        if(ordered.isNotEmpty() && autoSelection.claim(true,true))selectExternal(ordered.first(),ordered.drop(1),manual=false)
    }

    fun selectOff() {
        onManualSelection?.invoke()
        autoSelection.manual()
        cancelLoad()
        loaded = null
        setOwner(SubtitleOwner.OFF)
        selection = Selection.Off
        chosenOff=true;choice=null;manualFile=null;manualBitmap=false
        onChange()
    }

    fun selectEmbedded(group: Int, track: Int, manual: Boolean = true) {
        if(manual)onManualSelection?.invoke()
        autoSelection.manual()
        cancelLoad()
        val p = player() ?: return
        val g = p.currentTracks.groups.getOrNull(group) ?: return
        if(track !in 0 until g.length)return
        loaded = null
        setOwner(SubtitleOwner.EMBEDDED)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, track))
            .build()
        selection = Selection.Embedded(group, track)
        manualFile=null;manualBitmap=false
        if(manual) { val f=g.getTrackFormat(track);choice=Choice(Subtitles.normalise(f.language),false,forced=f.selectionFlags and C.SELECTION_FLAG_FORCED!=0);chosenOff=false }
        onChange()
    }

    /** Downloads and shows a subtitle; if it cannot be read, tries [fallbacks] in turn. */
    fun selectExternal(c: SubtitleCandidate, fallbacks: List<SubtitleCandidate> = emptyList(), manual: Boolean = true) {
        if(manual)onManualSelection?.invoke()
        autoSelection.manual()
        cancelLoad()
        val generation = loadGeneration
        status = activity.resources.getString(R.string.ui_loading_subtitles_from ,c.languageName,c.provider)
        onChange()
        loadJob = activity.lifecycleScope.launch(kotlinx.coroutines.CoroutineExceptionHandler { _, _ ->
            status = activity.resources.getString(R.string.ui_could_not_load_that_subtitle_try_another_one); runCatching { onChange() }
        }) {
            for (s in listOf(c) + fallbacks) {
                val got = Subtitles.download(s,query,config.encoding)
                ensureActive()
                if (got == null) continue
                // #256: a file the player or the drawing engine cannot take moves on to the next one instead of
                // ending playback (and the app) with it.
                val applied = try {
                    val fps=player()?.videoFormat?.frameRate?.takeIf {it>0}
                    val text=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {when(got.second) {
                        "microdvd"->app.ember.tv.data.SubtitleFiles.microDvd(got.first,fps)
                        "sami"->app.ember.tv.data.SamiSubtitles.toSrt(got.first,Subtitles.wanted(config))
                        else->got.first
                    }}
                    val format=if(got.second in setOf("microdvd","sami"))"srt" else got.second
                    val parsed = text?.let { engine.prepare(it, format, fps) }
                    ensureActive()
                    text != null && generation == loadGeneration &&
                        applyText(LoadedText(text, format, false, parsed))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { false }
                if (applied) {
                    manualFile = null
                    manualBitmap = false
                    if (manual) { choice = Choice(s.lang, true, s.provider, s.forced); chosenOff = false }
                    selection = Selection.External(s)
                    status = "${s.languageName} · ${s.provider}" + if (s.hashMatch) activity.resources.getString(R.string.ui_made_for_this_file) else ""
                    onChange()
                    return@launch
                }
            }
            status = activity.resources.getString(R.string.ui_could_not_load_that_subtitle_try_another_one)
            onChange()
        }
    }

    /** Manual files have the same selection priority as an explicit track choice. */
    suspend fun loadManual(text:String,format:String,restoring:Boolean=false):Boolean {
        if (!restoring) cancelLoad()
        autoSelection.manual()
        val generation = loadGeneration
        val fps = player()?.videoFormat?.frameRate?.takeIf { it > 0 }
        val converted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            when (format) {
                "microdvd" -> app.ember.tv.data.SubtitleFiles.microDvd(text, fps)
                "sami" -> app.ember.tv.data.SamiSubtitles.toSrt(text, Subtitles.wanted(config))
                else -> text
            }
        } ?: return false
        val resolvedFormat = if (format in setOf("microdvd", "sami")) "srt" else format
        val parsed = engine.prepare(converted, resolvedFormat, fps)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (generation != loadGeneration || !applyText(LoadedText(converted, resolvedFormat, true, parsed))) return false
        chosenOff = false
        manualBitmap = false
        manualFile = text to format
        status = activity.resources.getString(R.string.ui_manual_subtitle_file)
        selection = Selection.Manual(checkNotNull(status))
        onChange()
        return true
    }

    fun selectManualNative() {
        autoSelection.manual()
        cancelLoad()
        chosenOff = false
        manualFile = null
        manualBitmap = true
        loaded = null
        setOwner(SubtitleOwner.NATIVE)
        status = activity.resources.getString(R.string.ui_manual_bitmap_subtitles)
        selection = Selection.Manual(checkNotNull(status))
        onChange()
    }

    /** Media3's own subtitle tracks on or off; off while a downloaded subtitle is shown. */
    private fun setMedia3Text(enabled: Boolean) {
        val p = player() ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    @Suppress("unused")
    private fun Tracks.textGroups() = groups.filter { it.type == C.TRACK_TYPE_TEXT }
}
