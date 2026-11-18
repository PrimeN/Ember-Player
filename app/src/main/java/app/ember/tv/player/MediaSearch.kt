package app.ember.tv.player

import java.text.Normalizer
import java.util.Locale

internal object MediaSearch {
    private val words=Regex("[\\p{L}\\p{N}]+")
    private val cjkScripts=setOf(Character.UnicodeScript.HAN,Character.UnicodeScript.HIRAGANA,Character.UnicodeScript.KATAKANA,Character.UnicodeScript.HANGUL)
    private fun cjk(code:Int)=Character.UnicodeScript.of(code) in cjkScripts
    private fun cjk(value:String)=value.codePoints().anyMatch(::cjk)
    private fun grams(value:String):List<String> {
        val points=value.codePoints().toArray()
        return if(points.size<=1)listOf(value) else (0 until points.lastIndex).map {String(points,it,2)}
    }
    fun tokens(value:CharSequence?,query:Boolean=false):List<String> {
        val normalized=Normalizer.normalize(value?.toString().orEmpty().take(if(query)120 else 16384),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
        val terms=words.findAll(normalized).flatMap {word->
            val points=word.value.codePoints().toArray();val runs=mutableListOf<String>();var start=0
            for(i in 1 until points.size)if(cjk(points[i])!=cjk(points[i-1])) {runs+=String(points,start,i-start);start=i}
            if(points.isNotEmpty())runs+=String(points,start,points.size-start)
            runs.asSequence()
        }
        return (if(query)terms.take(12) else terms).toList()
    }
    fun matches(title:CharSequence?,query:String?):Boolean {
        val terms=tokens(query,true);if(terms.isEmpty())return true
        val names=tokens(title)
        return terms.all {term->names.any {if(cjk(term))it.contains(term) else it.startsWith(term)}}
    }
    fun indexText(title:String)=tokens(title).flatMap {term->
        if(cjk(term))listOf(term)+grams(term)+term.codePoints().toArray().map {String(Character.toChars(it))} else listOf(term)
    }.distinct().joinToString(" ")
    fun fts(query:String)=tokens(query,true).flatMap {term->if(cjk(term))grams(term).map {"\"$it\""} else listOf("\"$term\"*")}.distinct().joinToString(" ")
}
