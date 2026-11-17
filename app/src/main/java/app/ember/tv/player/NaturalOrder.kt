package app.ember.tv.player

import java.util.Locale

/** Numeric runs sort by magnitude without parsing integers or limiting episode-number length. */
internal object NaturalOrder:Comparator<String> {
    fun key(value:String):String=buildString {
        val text=value.lowercase(Locale.ROOT)
        var at=0
        while(at<text.length) {
            if(text[at] in '0'..'9') {
                val start=at
                while(at<text.length&&text[at] in '0'..'9')at++
                val digits=text.substring(start,at).trimStart('0').ifEmpty {"0"}
                append('0');append(digits.length.toString().padStart(10,'0'));append(digits);append(';')
            } else append(text[at++])
        }
    }
    override fun compare(a:String,b:String)=key(a).compareTo(key(b)).takeIf {it!=0} ?: a.compareTo(b)
}
