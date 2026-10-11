package com.tamilscripture.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.PsalmCardData
import com.tamilscripture.core.model.PsalmsIndex
import com.tamilscripture.core.model.PsalterDrawing
import com.tamilscripture.core.model.TuneEvents
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Psalms explorer from the website's psalms/ files (copied from content build a9aa63f0e1). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xxhdpi")
class PsalmsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun res(path: String) = javaClass.getResource("/entities/psalms/$path")!!.readText()
    private val index = PsalmsIndex.parse(res("index.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val cards = listOf(23, 121).associateWith { json.decodeFromString<PsalmCardData>(res("$it.json")) }
    private val verses = mapOf(
        23 to ("யெகோவா என் மேய்ப்பராக இருக்கிறார்; நான் தாழ்ச்சி அடையமாட்டேன்." to "The LORD is my shepherd; I shall not want."),
        121 to ("மலைகளுக்கு நேராக என் கண்களை ஏறெடுக்கிறேன்; எனக்கு உதவி எங்கேயிருந்து வரும்?" to "I will lift up mine eyes unto the hills, from whence cometh my help."),
    )

    private fun tuneRes(f: String) = javaClass.getResource("/entities/psalter/crimond/$f")!!.readText()
    private val crimond = TuneData(PsalterDrawing.parse(tuneRes("incipit.draw.json")), PsalterDrawing.parse(tuneRes("full.draw.json")), TuneEvents.parse(tuneRes("events.json")))

    /** The card alone, with Crimond [atMs] into playing (0: stopped). */
    private fun cardShot(file: String, mode: ThemeMode, lang: UiLang, atMs: Long, full: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalUiLang provides lang) {
                TsTheme(mode) {
                    Box(Modifier.size(411.dp, 1900.dp).background(Ts.colors.surface)) {
                        val p = index.psalms[22]
                        PsalmCardView(index, p, cards[23], verses[23], {}, onRead = {}, onShare = {}, sing = {
                            PsalmSing(cards[23]!!) { t, n, words, _ -> PsalmTuneView(t, crimond, playing = atMs > 0, positionMs = atMs, full = full, stanzas = n, lyrics = words, onToggle = {}, onFull = {}) }
                        })
                    }
                }
            }
        }
        compose.onNodeWithText(if (full) "First line" else if (lang == UiLang.Tamil) "முழுப் பண்" else "Full tune").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    /** Psalm 100's two versions in the 1650 psalter: L.M. with its traditional tune, C.M. with tunes that fit the metre. */
    @Test fun metricalVersions() {
        val p100 = json.decodeFromString<PsalmCardData>(res("100.json"))
        org.junit.Assert.assertEquals(listOf("L.M.", "C.M."), p100.metrical.map { it.label })
        org.junit.Assert.assertEquals("old-hundredth", p100.metrical[0].tunes.first())
        org.junit.Assert.assertFalse(p100.metrical[0].fallback)
        org.junit.Assert.assertTrue(p100.metrical[1].fallback)
        org.junit.Assert.assertTrue(p100.metrical[1].tunes.all { p100.tune(it) != null })
        org.junit.Assert.assertTrue(p100.metrical[0].stanzas[0][0].startsWith("¹All people that on earth do dwell"))
    }

    /** Full screen for singing: the whole tune with stanza 2's words, 9.5 s in (its second line). */
    private fun bigShot(file: String, w: Dp, h: Dp) {
        val words = cards[23]!!.metrical[0].lyrics
        compose.setContent {
            CompositionLocalProvider(LocalUiLang provides UiLang.English) {
                TsTheme(ThemeMode.Light) {
                    Box(Modifier.size(w, h)) {
                        PsalmTuneView(
                            cards[23]!!.tunes[0], crimond, playing = true, positionMs = 9500, full = true,
                            stanza = 1, stanzas = 5, lyrics = words, onToggle = {}, onFull = {}, big = true, onBig = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun fullScreenPortrait() = bigShot("psalms_fullscreen_portrait", 411.dp, 900.dp)

    @Config(sdk = [35], qualifiers = "w900dp-h411dp-xxhdpi")
    @Test fun fullScreenLandscape() = bigShot("psalms_fullscreen_landscape", 900.dp, 411.dp)

    /** The third note (B-flat, 2.2 s in) is lit while it sounds. */
    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xxhdpi")
    @Test fun tunePlayingTamil() = cardShot("psalms_tune_playing_light_ta", ThemeMode.Light, UiLang.Tamil, atMs = 2200)

    @Config(sdk = [35], qualifiers = "w411dp-h1900dp-xxhdpi")
    @Test fun tuneFullDark() = cardShot("psalms_tune_full_dark_en", ThemeMode.Dark, UiLang.English, atMs = 0, full = true)

    private fun shot(file: String, mode: ThemeMode, lang: UiLang, w: Dp, h: Dp, psalm: Int = 23, tap: String? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalUiLang provides lang) {
                TsTheme(mode) {
                    Box(Modifier.size(w, h)) {
                        PsalmsExplorer(index, loading = false, onBack = {}, initialPsalm = psalm) { idx, p, pick ->
                            PsalmCardView(idx, p, cards[p.n], verses[p.n], pick, onRead = {}, onShare = {}, sing = cards[p.n]?.takeIf { p.n == 23 }?.let { cd ->
                                { PsalmSing(cd) { t, n, words, _ -> PsalmTuneView(t, crimond, playing = false, positionMs = 0, full = false, stanzas = n, lyrics = words, onToggle = {}, onFull = {}) } }
                            })
                        }
                    }
                }
            }
        }
        if (tap != null) {
            compose.onNodeWithText(tap).performClick()
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun compactTamil() = shot("psalms_compact_light_ta", ThemeMode.Light, UiLang.Tamil, 411.dp, 900.dp)

    /** A theme tile narrows the grid to its psalms. */
    @Test fun compactLamentEnglish() = shot("psalms_compact_lament_dark_en", ThemeMode.Dark, UiLang.English, 411.dp, 900.dp, tap = "Lament")

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
    @Test fun wideEnglish() = shot("psalms_wide_light_en", ThemeMode.Light, UiLang.English, 1280.dp, 800.dp, psalm = 121)

    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
    @Test fun wideTamilDark() = shot("psalms_wide_dark_ta", ThemeMode.Dark, UiLang.Tamil, 1280.dp, 800.dp)
}
