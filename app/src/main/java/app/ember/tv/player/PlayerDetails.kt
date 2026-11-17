package app.ember.tv.player

import app.ember.tv.data.Person

/** Optional title metadata displayed by the advanced player controls. */
data class PlayerDetails(
    val name: String,
    val logo: String? = null,
    val year: String? = null,
    /** e.g. "IMDb 8.3". */
    val rating: String? = null,
    val episodeTitle: String? = null,
    val synopsis: String? = null,
    val cast: List<Person> = emptyList(),
    val backdrop: String? = null,
) {
}
