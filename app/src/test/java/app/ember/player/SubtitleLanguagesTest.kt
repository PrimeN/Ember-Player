package app.ember.player

import org.junit.Assert.*
import org.junit.Test

class SubtitleLanguagesTest {
    @Test fun validCodesAreNormalizedWithoutDroppingEntries() {
        assertEquals(listOf("en","fr","pt-BR","eng"),SubtitleLanguages.parse("EN, fr, pt-br, eng"))
        assertEquals(emptyList<String>(),SubtitleLanguages.parse(" "))
        assertEquals(listOf("en"),SubtitleLanguages.parse("en, EN"))
    }
    @Test fun invalidOrIncompleteEditsCannotReplaceTheSavedSetting() {
        for(value in listOf("en,", "en, zz", "english", "en-", "en,fr,de,it,ja,ko,zh,es,pt"))assertNull(value,SubtitleLanguages.parse(value))
    }
}
