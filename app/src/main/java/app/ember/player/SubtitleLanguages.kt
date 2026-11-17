package app.ember.player

import java.util.Locale

internal object SubtitleLanguages {
    private val supported=(Locale.getISOLanguages().toSet()+Locale.getISOLanguages().mapNotNull {runCatching {Locale.forLanguageTag(it).isO3Language}.getOrNull()}).toSet()
    private val regions=Locale.getISOCountries().toSet()
    fun parse(text:String):List<String>? {
        if(text.isBlank())return emptyList()
        val codes=text.split(',').map(String::trim)
        if(codes.size>8||codes.any {code->
            !code.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z]{2})?"))||code.substringBefore('-').lowercase(Locale.ROOT) !in supported||('-' in code&&code.substringAfter('-').uppercase(Locale.ROOT) !in regions)
        })return null
        return codes.map {code->code.substringBefore('-').lowercase(Locale.ROOT)+if('-' in code)"-"+code.substringAfter('-').uppercase(Locale.ROOT) else ""}.distinct()
    }
}
