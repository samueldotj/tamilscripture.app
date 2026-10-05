package com.tamilscripture.feature.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performFirstLinkClick
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M2-12: in a paragraph each verse is a link, so a tap or TalkBack selects that verse. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ParagraphLinksTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachVerseIsALink() {
        val tapped = mutableListOf<Int>()
        val para = ReaderItem.Para(
            "p1",
            listOf(Run(16, "16", "For God so loved the world,"), Run(17, "17", "For God did not send his Son into the world to condemn the world.")),
            poetry = false,
        )
        compose.setContent {
            TsTheme(ThemeMode.Light) {
                CompositionLocalProvider(LocalUiLang provides UiLang.English) {
                    ParagraphText(
                        para, numbers = true, selection = emptyList(), playingVerse = null, marks = emptyMap(), heat = emptyMap(),
                        fontSize = 18, lineHeightEm = 1.7f, showNotes = false, textLocale = null, notesInMargin = false,
                        onTapVerse = { tapped += it }, onOpenNote = {},
                    )
                }
            }
        }
        val node = compose.onNodeWithText("For God so loved", substring = true)
        node.performFirstLinkClick { it.item is androidx.compose.ui.text.LinkAnnotation.Clickable && (it.item as androidx.compose.ui.text.LinkAnnotation.Clickable).tag.startsWith("v17") }
        node.performFirstLinkClick { it.item is androidx.compose.ui.text.LinkAnnotation.Clickable && (it.item as androidx.compose.ui.text.LinkAnnotation.Clickable).tag.startsWith("v16") }
        assertEquals(listOf(17, 16), tapped)
    }
}
