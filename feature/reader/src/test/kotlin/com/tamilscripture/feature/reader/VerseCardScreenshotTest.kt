package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The compact verse card: icon buttons, the highlight dot and its colours, א or α, map and people. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w402dp-h874dp-xxhdpi")
class VerseCardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun card(mode: ThemeMode, lang: UiLang, ref: String, ot: Boolean, color: HighlightColor?, bookmarked: Boolean, places: Boolean, people: Boolean) =
        compose.setContent {
            TsTheme(mode) {
                CompositionLocalProvider(LocalUiLang provides lang) {
                    Box(Modifier.size(402.dp, 360.dp).background(Ts.colors.bg).padding(12.dp), contentAlignment = Alignment.BottomCenter) {
                        VerseActionCard(
                            ref, hasAudio = true, onClose = {}, onPlayHere = {}, onCommentary = {}, onCrossRefs = {},
                            onBookmark = {}, onCopy = {}, onShare = {}, onNote = {}, onHighlight = {},
                            currentColor = color, onColor = {}, onOriginal = {},
                            onPeople = if (people) ({}) else null, onMap = if (places) ({}) else null,
                            bookmarked = bookmarked, oldTestament = ot,
                        )
                    }
                }
            }
        }

    @Test fun exodusTamil() {
        card(ThemeMode.Light, UiLang.Tamil, "யாத்திராகமம் 1:5", ot = true, color = null, bookmarked = false, places = true, people = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/verse_card_icons_ta.png")
    }

    @Test fun johnDarkEnglishHighlighted() {
        card(ThemeMode.Dark, UiLang.English, "John 3:16", ot = false, color = HighlightColor.Blue, bookmarked = true, places = false, people = false)
        compose.onRoot().captureRoboImage("src/test/screenshots/verse_card_icons_dark_en.png")
    }

    @Test fun highlightOpensColours() {
        card(ThemeMode.Light, UiLang.English, "Exodus 1:5", ot = true, color = null, bookmarked = false, places = true, people = true)
        compose.onNodeWithContentDescription("Highlight").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pink", useUnmergedTree = true).assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/verse_card_colours_en.png")
    }
}
