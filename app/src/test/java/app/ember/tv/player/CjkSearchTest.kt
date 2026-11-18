package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class CjkSearchTest {
    @Test fun cjkNamesAcceptSubstringsWhileLatinQueriesKeepTheirWordBoundaries() {
        assertTrue(MediaSearch.matches("千と千尋の神隠し","神隠し"))
        assertTrue(MediaSearch.matches("流浪地球2","地球2"))
        assertTrue(MediaSearch.matches("進撃の巨人","巨人"))
        assertTrue(MediaSearch.matches("漢字𠮷野家","𠮷野"))
        assertFalse(MediaSearch.matches("Episode 10","isode"))
        assertFalse(MediaSearch.matches("神隠〇隠し","神隠し"))
    }
}
