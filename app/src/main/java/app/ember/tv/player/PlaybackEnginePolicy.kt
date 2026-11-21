package app.ember.tv.player

/** A second engine can recover format failures, not an expired account or network outage. */
object PlaybackEnginePolicy {
    fun compatibilityError(code: Int): Boolean = PlaybackFailureClassifier.classify(code).changesCore
    fun nativeFirst(uri: String, filename: String? = null): Boolean = MediaFileFormats.nativeFirst(uri, filename)
    /** Keep a working Standard decoder when an automatic Compatibility switch could lose its audio. */
    fun preservesAudio(mime:String?, protected:Boolean=false):Boolean = !protected && mime?.lowercase() !in setOf("audio/ac4","audio/mhm1","audio/mha1","audio/iamf")
}
internal data class EngineSource(
    val urls: List<String>, val title: String, val key: String,
    val durations: List<Long?> = urls.map { null }, val durationMs: Long? = null,
    val audio: String? = null, val headers: Map<String,String> = emptyMap(), val live: Boolean = false,
    val audioLanguages: List<String> = emptyList(), val subtitleLanguages: List<String> = emptyList(),
    val filename: String? = null,
    val edition:Int?=null,
)
