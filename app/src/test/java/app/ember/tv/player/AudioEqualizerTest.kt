package app.ember.tv.player

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioEqualizerTest {
    @Test fun editedBandsAreBoundedAndSwitchToCustomWithoutLosingOtherBands() {
        val preset = AudioEffects("dialogue")
        val edited = preset.band(3, 100)
        assertEquals("custom", edited.preset)
        assertEquals(12, edited.gains[3])
        assertEquals(preset.gains[4], edited.gains[4])
        assertEquals(edited, edited.band(999, 10))
        assertEquals(List(10) { 0 }, AudioEffects("custom", bands = emptyList()).gains)
    }

    @Test fun decodingOldSettingsKeepsFlatAudioAndCustomValuesRoundTrip() {
        assertFalse(Json.decodeFromString<AudioEffects>("{}").active)
        val value = AudioEffects("custom", 3, "swap", List(10) { it - 5 }, 2, 150)
        assertEquals(value, Json.decodeFromString<AudioEffects>(Json.encodeToString(value)))
    }

    @Test fun allTenBandsReachTheNativeFilterAndHeadroomStaysFinite() {
        val value = AudioEffects("custom", 6, bands = List(10) { 12 }, treble = 6, stereoWidth = 200)
        val filter = value.nativeFilters(6)
        assertTrue(value.headroomDb(6).isFinite())
        assertTrue(value.headroomDb(6) >= 24)
        AudioEffects.frequencies.forEach { assertTrue(filter.contains("equalizer=f=$it:")) }
        assertTrue(filter.contains("|c2=c2|c3=c3|c4=c4|c5=c5"))
        assertTrue(filter.startsWith("lavfi=[volume=-"))
        assertFalse(filter.contains("NaN"))
    }

    @Test fun flatDspDoesNotChangeSamplesAndWidthDoesNotTouchSurroundChannels() {
        val input = floatArrayOf(.25f, -.25f, .3f, -.2f, .1f, -.1f)
        val flat = input.clone()
        AudioDsp(48000, 6, AudioEffects()).frame(flat)
        assertArrayEquals(input, flat, .00001f)
        val monoFront = input.clone()
        AudioDsp(48000, 6, AudioEffects(stereoWidth = 0)).frame(monoFront)
        assertEquals(0f, monoFront[0], .00001f)
        assertEquals(0f, monoFront[1], .00001f)
        for (i in 2..5) assertEquals(input[i], monoFront[i], .00001f)
    }

    @Test fun manualEqChangesTheIntendedFrequencyWithoutClipping() {
        val effects = AudioEffects().band(5, 12)
        fun energy(frequency: Int): Double {
            val dsp = AudioDsp(48000, 2, effects)
            var sum = 0.0
            val frame = FloatArray(2)
            for (i in 0 until 48000) {
                frame.fill((.1 * sin(2 * PI * frequency * i / 48000)).toFloat())
                dsp.frame(frame)
                assertTrue(frame.all { it.isFinite() && abs(it) <= 1f })
                if (i > 24000) sum += frame[0] * frame[0]
            }
            return sum
        }
        assertTrue(energy(1000) > energy(100) * 4)
    }

    @Test fun savedPresetsReplaceNamesAndRespectTheLimit() {
        val first = AudioPresets.save(emptyList(), "  My sound\n", AudioEffects("warm"))!!
        assertEquals("My sound", first.single().name)
        assertEquals(1, AudioPresets.save(first, "my SOUND", AudioEffects("bright"))!!.size)
        assertNull(AudioPresets.save(first, "\n", AudioEffects()))
        val full = (1..12).map { SavedAudioPreset("Preset $it", AudioEffects()) }
        assertNull(AudioPresets.save(full, "Another", AudioEffects()))
        assertEquals(12, AudioPresets.save(full, "Preset 1", AudioEffects("dialogue"))!!.size)
    }
}
