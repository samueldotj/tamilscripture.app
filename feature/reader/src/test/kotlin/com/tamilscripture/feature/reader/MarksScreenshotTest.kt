package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.UserNote
import com.tamilscripture.core.services.LocalUiLang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M6: highlights, a bookmark, heat and notes in the text; notes under the verse on a phone, in the margin on a wide window. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarksScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val items = listOf(
        "யூதர்களுக்குள்ளே அதிகாரியான நிக்கொதேமு என்னப்பட்ட பரிசேயன் ஒருவன் இருந்தான்.",
        "அவன் இரவு நேரத்தில் இயேசுவினிடம் வந்து: ரபீ, நீர் தேவனிடத்தில் இருந்து வந்த போதகர் என்று அறிந்திருக்கிறோம்.",
        "இயேசு அவனுக்கு மறுமொழியாக: ஒருவன் மறுபடியும் பிறக்காவிட்டால் தேவனுடைய ராஜ்யத்தைப் பார்க்கமாட்டான் என்றார்.",
        "அதற்கு நிக்கொதேமு: ஒருவன் வயதானபின்பு எப்படிப் பிறப்பான்? என்றான்.",
    ).mapIndexed { i, t -> ReaderItem.Verse("v${i + 1}", i + 1, "${i + 1}", t, emptyList(), emptyList(), false) }

    private val note = UserNote("n1", "JHN", 3, 3, 3, "மறுபடியும் பிறத்தல் — born again: from above (ἄνωθεν).", updatedAt = "2026-10-04T10:00:00Z")
    private val marks = mapOf(
        1 to VerseMarks(bookmarked = true),
        3 to VerseMarks(color = HighlightColor.Green, notes = listOf(note)),
        4 to VerseMarks(color = HighlightColor.Pink),
        // A word range made on the website (R-10.15), its ends inside letters on purpose.
        2 to VerseMarks(words = listOf(WordMark(28, 46, HighlightColor.Yellow))),
    )

    private fun shot(file: String, margin: Boolean, dark: Boolean) {
        compose.setContent {
            TsTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                CompositionLocalProvider(LocalUiLang provides UiLang.Tamil) {
                    ReaderTextList(
                        items, selection = emptyList(), playingVerse = null, fontSize = 18, lineHeightEm = 1.7f, showNotes = false,
                        commentary = null, commentaryName = null, listState = rememberLazyListState(), chapterKey = "JHN.3",
                        contentPadding = PaddingValues(16.dp), onTapVerse = {}, onVerseRead = { _, _ -> }, onOpenCommentary = {},
                        modifier = Modifier.fillMaxSize().background(Ts.colors.bg),
                        marks = marks, heat = mapOf(1 to 3), notesInMargin = margin,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneLight() = shot("marks_phone_light", margin = false, dark = false)

    @Test @Config(sdk = [35], qualifiers = "w900dp-h600dp-mdpi")
    fun wideDark() = shot("marks_wide_dark", margin = true, dark = true)
}
