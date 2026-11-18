package app.ember.player

import org.junit.Assert.*
import org.junit.Test

class SubtitleLanguagesTest {
    @Test fun validCodesAreNormalizedWithoutDroppingEntries() {
        assertEquals(listOf("en","fr","pt-BR","eng"),SubtitleLanguages.parse("EN, fr, pt-br, eng"))
        assertEquals(emptyList<String>(),SubtitleLanguages.parse(" "))
        assertEquals(listOf("en"),SubtitleLanguages.parse("en, EN"))
    }
    @Test fun scriptPreferencesUseCanonicalTagsAndRejectIncompleteOrUnknownCodes() {
        assertEquals(listOf("zh-Hant","zh-Hant-TW","sr-Latn-RS"),SubtitleLanguages.parse("ZH-hant, zh-HANT-tw, SR-latn-rs"))
        for(value in listOf("zh-Han", "zh-Hant-", "en-Xxxx", "zh-Hant-ZZ"))assertNull(value,SubtitleLanguages.parse(value))
    }
    @Test fun invalidOrIncompleteEditsCannotReplaceTheSavedSetting() {
        for(value in listOf("en,", "en, zz", "english", "en-", "en,fr,de,it,ja,ko,zh,es,pt"))assertNull(value,SubtitleLanguages.parse(value))
    }
}
