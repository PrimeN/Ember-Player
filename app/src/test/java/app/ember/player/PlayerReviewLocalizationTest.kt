package app.ember.player

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.media3.common.PlaybackException
import app.ember.tv.R
import app.ember.tv.data.SubtitleConfig
import app.ember.tv.player.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerReviewLocalizationTest {
    private val resources get()=RuntimeEnvironment.getApplication().resources
    @Test fun blankAndFullPresetsProvideDistinctTranslatableReasonsAndAllowReplacement() {
        fun failure(expected:Int,action:()->Unit) {
            try {action();fail("Expected validation failure")}
            catch(error:PresetValidationException){assertEquals(expected,error.messageId);assertTrue(resources.getString(error.messageId).isNotBlank())}
        }
        failure(R.string.ui_profile_name_required){PictureProfiles.save(PlaybackConfig()," \n ",PictureState())}
        failure(R.string.ui_style_name_required){SubtitlePresets.save(emptyList(),"\t",SubtitleConfig())}
        val pictures=PlaybackConfig(pictureProfiles=(0 until 16).map {PictureProfile("p$it","Profile $it",PictureState())})
        failure(R.string.ui_profile_limit_reached){PictureProfiles.save(pictures,"New",PictureState())}
        assertEquals(16,PictureProfiles.save(pictures,"Profile 0",PictureState()).pictureProfiles.size)
        val styles=(0 until 12).map {SubtitleStylePreset("Style $it",SubtitleStyle())}
        failure(R.string.ui_style_limit_reached){SubtitlePresets.save(styles,"New",SubtitleConfig())}
        assertEquals(12,SubtitlePresets.save(styles,"Style 0",SubtitleConfig()).size)
        assertEquals(R.string.ui_preset_save_failed,PresetValidationException.resource(IllegalStateException("raw diagnostic")))
    }
    @Test fun resultAndSecondCountsUseSingularPluralAndLocaleFormatting() {
        assertEquals("1 subtitle found",resources.getQuantityString(R.plurals.ui_found,1,1))
        assertEquals("2 subtitles found",resources.getQuantityString(R.plurals.ui_found,2,2))
        assertTrue(resources.getQuantityString(R.plurals.ui_found_made_for_this_exact_file,1,1).startsWith("1 subtitle found"))
        assertEquals("+1 second",resources.getQuantityString(R.plurals.ui_seconds,1,"+",1L))
        assertEquals("−2 seconds",resources.getQuantityString(R.plurals.ui_seconds,2,"−",2L))
        val arabic=java.util.Locale.forLanguageTag("ar")
        val localized=RuntimeEnvironment.getApplication().createConfigurationContext(Configuration(resources.configuration).apply {setLocales(LocaleList(arabic))}).resources
        assertTrue(localized.getQuantityString(R.plurals.ui_found,2,2).contains(java.text.NumberFormat.getIntegerInstance(arabic).format(2)))
    }
    @Test fun playbackReasonsAreLocalizedResourcesWithUsefulCategoriesAndASafeFallback() {
        assertEquals(R.string.ui_playback_network_timeout,PlaybackErrors.message(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(R.string.ui_playback_file_missing,PlaybackErrors.message(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(R.string.ui_playback_permission_failure,PlaybackErrors.message(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        assertEquals(R.string.ui_playback_decoding_failure,PlaybackErrors.message(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertEquals(R.string.ui_playback_protection_failure,PlaybackErrors.message(PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED))
        assertEquals(R.string.ui_playback_failure,PlaybackErrors.message(-1))
        val pseudo=RuntimeEnvironment.getApplication().createConfigurationContext(Configuration(resources.configuration).apply {setLocales(LocaleList.forLanguageTags("en-XA"))}).resources
        assertNotEquals(resources.getString(R.string.ui_profile_name_required),pseudo.getString(R.string.ui_profile_name_required))
        assertNotEquals(resources.getString(PlaybackErrors.message(-1)),pseudo.getString(PlaybackErrors.message(-1)))
    }
}
