package app.ember.tv.player

internal enum class SubtitleLayer { PRIMARY, SECONDARY }
internal enum class SubtitleOwner { OFF, EMBEDDED, EXTERNAL, NATIVE }

/** One owner per layer. Call after a replacement loads, so a failed load keeps the old owner. */
internal class SubtitleRenderCoordinator(private val clearPrevious:(SubtitleLayer,SubtitleOwner,SubtitleOwner)->Unit) {
    private val owners=mutableMapOf<SubtitleLayer,SubtitleOwner>()
    fun owner(layer:SubtitleLayer=SubtitleLayer.PRIMARY)=owners[layer] ?: SubtitleOwner.OFF
    fun select(next:SubtitleOwner,layer:SubtitleLayer=SubtitleLayer.PRIMARY) {
        val previous=owner(layer)
        if(previous==next)return
        clearPrevious(layer,previous,next)
        owners[layer]=next
    }
}

internal data class SubtitleDrawing(val embedded: Boolean = false, val overlay: Boolean = false, val native: Boolean = false)

internal fun subtitleDrawing(owner: SubtitleOwner, compatibility: Boolean): SubtitleDrawing = when (owner) {
    SubtitleOwner.OFF -> SubtitleDrawing()
    SubtitleOwner.EMBEDDED -> if (compatibility) SubtitleDrawing(native = true) else SubtitleDrawing(embedded = true)
    SubtitleOwner.EXTERNAL -> SubtitleDrawing(overlay = true)
    SubtitleOwner.NATIVE -> SubtitleDrawing(native = compatibility)
}
