package app.ember.player

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.ember.tv.R
import app.ember.tv.data.Prefs
import app.ember.tv.data.Subtitles
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h800dp-port-mdpi")
class PlayerLocaleUiTest {
    @get:Rule val compose=createComposeRule()
    private val app get()=RuntimeEnvironment.getApplication()
    private fun localized(tag:String)=app.createConfigurationContext(Configuration(app.resources.configuration).apply {setLocales(LocaleList.forLanguageTags(tag))})
    @Test fun labelsRefreshWithTheLocaleAndChoicesKeepTheirSavedIds() {
        val prefs=Prefs(app).apply {theme="night"}
        var language by mutableStateOf("en")
        var revision by mutableIntStateOf(0)
        compose.setContent {
            val context=remember(language){localized(language)}
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration) {
                PlayerTheme(prefs) {
                    val resources=LocalResources.current
                    revision
                    PlayerChoice(resources.getString(R.string.ui_theme),listOf("night" to R.string.ui_dark,"day" to R.string.ui_light),prefs.theme){prefs.theme=it;revision++}
                }
            }
        }
        compose.onNodeWithText("Theme · Dark").assertExists()
        compose.runOnIdle {language="en-XA"}
        val resources=localized("en-XA").resources
        val title=resources.getString(R.string.ui_theme);val dark=resources.getString(R.string.ui_dark)
        assertNotEquals("Pseudo-locales must expose untranslated UI", "Theme",title)
        compose.onNodeWithText(resources.getString(R.string.ui_label_value,title,dark)).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.onNodeWithContentDescription(resources.getString(R.string.ui_light)).performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertEquals("day",prefs.theme)}
        compose.onNodeWithText(resources.getString(R.string.ui_label_value,title,resources.getString(R.string.ui_light))).assertExists()
    }
    @Test fun rtlFolderRowsKeepTheCompleteFilenameAndMoveTheIconBeforeTheText() {
        val context=localized("ar-XB")
        val name="Episode 2.mkv"
        var opened=false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration,LocalLayoutDirection provides LayoutDirection.Rtl) {
                PlayerTheme(Prefs(app)){Column(Modifier.width(300.dp)){PlayerFileRow(name,true){opened=true}}}
            }
        }
        val label=context.getString(R.string.ui_folder,name)
        assertTrue(label.contains(name))
        assertNotEquals("Folder: $name",label)
        val row=compose.onNodeWithContentDescription(label)
        val text=compose.onNodeWithText(name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.right<row.fetchSemanticsNode().boundsInRoot.right-24f)
        row.performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.runOnIdle {assertTrue(opened)}
    }
    @Test fun quantitiesAndSettingsSearchUseLocalizedResources() {
        val resources=localized("en").resources
        assertEquals("Delete 1 file?",resources.getQuantityString(R.plurals.ui_delete_files,1,1))
        assertEquals("Delete 2 files?",resources.getQuantityString(R.plurals.ui_delete_files,2,2))
        val expanded=localized("en-XA").resources
        val section=PlayerSettingsIndex.sections.first {it.id=="network"}
        assertNotEquals(resources.getString(section.title),expanded.getString(section.title))
        assertTrue(PlayerSettingsIndex.search(expanded.getString(section.title),expanded).any {it.id=="network"})
        assertEquals("Anglais",Subtitles.languageName("en",Locale.FRENCH).replaceFirstChar {it.uppercaseChar()})
        assertEquals("zh-TW",Subtitles.normalise("Chinese (traditional)"))
    }
    @Test fun translatedFormatStringsKeepValidPositionalArguments() {
        val pattern=Regex("%(\\d+)\\$[-+#0 ]*\\d*(?:\\.\\d+)?([sdf])")
        var validated=0
        for(language in listOf("en","en-XA","ar-XB")) {
            val resources=localized(language).resources
            for(field in R.string::class.java.fields.filter {it.name.startsWith("ui_")}) {
                val id=field.getInt(null);val value=resources.getString(id)
                val placeholders=pattern.findAll(value).toList()
                if(placeholders.isEmpty())continue
                validated++
                val args=Array<Any>(placeholders.maxOf {it.groupValues[1].toInt()}) {"Sample"}
                placeholders.forEach {match->args[match.groupValues[1].toInt()-1]=when(match.groupValues[2]){"d"->7;"f"->1.25;else->"Sample"}}
                assertTrue(field.name,resources.getString(id,*args).isNotBlank())
            }
        }
        assertTrue("Formatted resource coverage",validated>50)
    }

}
