package com.tamilscripture.feature.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.PsalmCardData
import com.tamilscripture.core.model.PsalmsIndex
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

    private fun shot(file: String, mode: ThemeMode, lang: UiLang, w: Dp, h: Dp, psalm: Int = 23, tap: String? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalUiLang provides lang) {
                TsTheme(mode) {
                    Box(Modifier.size(w, h)) {
                        PsalmsExplorer(index, loading = false, onBack = {}, initialPsalm = psalm) { idx, p, pick ->
                            PsalmCardView(idx, p, cards[p.n], verses[p.n], pick, onRead = {}, onShare = {})
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
