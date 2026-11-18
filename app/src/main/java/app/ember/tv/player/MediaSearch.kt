package app.ember.tv.player

import java.text.Normalizer
import java.util.Locale

internal object MediaSearch {
    private val words=Regex("[\\p{L}\\p{N}]+")
    fun tokens(value:CharSequence?,query:Boolean=false):List<String> {
        val normalized=Normalizer.normalize(value?.toString().orEmpty().take(if(query)120 else 16384),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
        return words.findAll(normalized).map {it.value}.let {if(query)it.take(12) else it}.toList()
    }
    fun matches(title:CharSequence?,query:String?):Boolean {
        val terms=tokens(query,true);val names=tokens(title)
        return terms.all {term->names.any {it.startsWith(term)}}
    }
    fun fts(query:String)=tokens(query,true).joinToString(" "){"\"$it\"*"}
}
