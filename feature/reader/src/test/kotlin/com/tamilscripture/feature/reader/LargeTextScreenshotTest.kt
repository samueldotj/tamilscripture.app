package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M2-12: the verse action card at 200% text size, in Tamil (the longer labels), on a small phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xhdpi", fontScale = 2.0f)
class LargeTextScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun verseCardAt200Percent() {
        compose.setContent {
            TsTheme(ThemeMode.Light) {
                CompositionLocalProvider(LocalUiLang provides UiLang.Tamil) {
                    Box(Modifier.background(Ts.colors.bg).padding(12.dp)) {
                        VerseActionCard(
                            "1 கொரிந்தியர் 13:4–7", hasAudio = true, onClose = {}, onPlayHere = {}, onCommentary = {}, onCrossRefs = {},
                            onBookmark = {}, onCopy = {}, onShare = {}, onNote = {}, onHighlight = {}, onOriginal = {}, onPeople = {}, onShareImage = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/verse_card_font200_ta.png")
    }
}
