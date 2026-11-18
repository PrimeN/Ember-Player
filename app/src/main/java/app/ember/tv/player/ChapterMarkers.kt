package app.ember.tv.player

import java.util.Locale

/** Explicit chapter boundaries are local metadata; credits never skip automatically. */
internal object ChapterMarkers {
    private val label=Regex("^(intro(?:duction)?|opening|op|credits|end credits|ending|ed|recap)(?:\\s*(?:[0-9]+|theme|sequence))?$")
    fun from(chapters:List<Chapter>,duration:Long):List<Marker> {
        if(duration<=0)return emptyList()
        val sorted=chapters.filter {it.startMs in 0 until duration}.distinctBy {it.startMs}.sortedBy {it.startMs}.take(2000)
        return sorted.mapIndexedNotNull {i,c->
            val name=c.name.trim().lowercase(Locale.ROOT).replace(Regex("[_.-]+")," ").trim()
            val type=label.matchEntire(name)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            val kind=when(type){"credits","end credits","ending","ed"->"credits";"recap"->"recap";else->"intro"}
            val end=sorted.getOrNull(i+1)?.startMs ?: duration
            if(end-c.startMs !in 2000..(if(kind=="credits")20*60_000L else 5*60_000L) || kind!="credits"&&end==duration)return@mapIndexedNotNull null
            Marker(kind,c.startMs,end)
        }
    }
}
