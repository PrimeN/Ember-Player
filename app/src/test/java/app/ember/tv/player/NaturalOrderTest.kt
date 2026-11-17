package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class NaturalOrderTest {
    @Test fun episodeNumbersAndHugeNumbersSortByMagnitude() {
        val names=listOf("Episode 10.mkv","Episode 2.mkv","Episode 1.mkv","Episode 1000000000000000000000.mkv")
        assertEquals(listOf(names[2],names[1],names[0],names[3]),names.sortedWith(NaturalOrder))
        assertEquals(listOf("a1b2","a1b10","a2b1"),listOf("a2b1","a1b10","a1b2").sortedWith(NaturalOrder))
    }
    @Test fun tiesAreStableAndSortingDoesNotDependOnTheDefaultLocale() {
        val before=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"))
            val names=listOf("ITEM 2","item 02","item 2","item 10")
            val sorted=names.sortedWith(NaturalOrder)
            assertEquals(sorted,names.reversed().sortedWith(NaturalOrder))
            assertEquals("item 10",sorted.last())
            for(a in names)for(b in names)assertEquals(Integer.signum(NaturalOrder.compare(a,b)),-Integer.signum(NaturalOrder.compare(b,a)))
        }finally {java.util.Locale.setDefault(before)}
    }
}
