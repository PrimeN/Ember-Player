package app.ember.player

import java.util.Locale

internal object SubtitleLanguages {
    private val supported=(Locale.getISOLanguages().toSet()+Locale.getISOLanguages().mapNotNull {runCatching {Locale.forLanguageTag(it).isO3Language}.getOrNull()}).toSet()
    private val regions=Locale.getISOCountries().toSet()
    private val scripts=Locale.getAvailableLocales().map {it.script}.filter(String::isNotBlank).toSet()
    private val tag=Regex("([A-Za-z]{2,3})(?:-([A-Za-z]{4}))?(?:-([A-Za-z]{2}))?")
    fun parse(text:String):List<String>? {
        if(text.isBlank())return emptyList()
        val codes=text.split(',').map(String::trim)
        if(codes.size>8)return null
        val values=codes.map {code->
            val match=tag.matchEntire(code) ?: return null
            val language=match.groupValues[1].lowercase(Locale.ROOT)
            val script=match.groupValues[2].lowercase(Locale.ROOT).replaceFirstChar {it.titlecase(Locale.ROOT)}
            val region=match.groupValues[3].uppercase(Locale.ROOT)
            if(language !in supported || script.isNotEmpty()&&script !in scripts || region.isNotEmpty()&&region !in regions)return null
            listOf(language,script,region).filter(String::isNotEmpty).joinToString("-")
        }
        return values.distinct()
    }
}
