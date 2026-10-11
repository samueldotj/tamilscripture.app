package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.component.VerseImage
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

/** Design 3b: the share-as-image sheet (opened from a verse's long-press menu). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w402dp-h874dp-xxhdpi")
class ShareImageScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val passages = listOf(
        VerseImage.Passage(
            "ta",
            listOf(VerseImage.Verse("16", "தேவன், தம்முடைய ஒரேபேறான குமாரனை விசுவாசிக்கிற எவனும் கெட்டுப்போகாமல் நித்தியஜீவனை அடையும்படிக்கு, அவரைத் தந்தருளி, இவ்வளவாய் உலகத்தில் அன்புகூர்ந்தார்.")),
            "யோவான் 3:16 · IRV-TA",
        ),
        VerseImage.Passage(
            "en",
            listOf(VerseImage.Verse("16", "For God so loved the world that He gave His one and only Son, that everyone who believes in Him shall not perish but have eternal life.")),
            "John 3:16 · BSB",
        ),
    )

    private fun panel(mode: ThemeMode, lang: UiLang) = compose.setContent {
        TsTheme(mode) {
            CompositionLocalProvider(LocalUiLang provides lang) {
                Box(Modifier.size(402.dp, 874.dp).background(Ts.colors.scrim)) {
                    Box(Modifier.align(Alignment.BottomCenter).background(Ts.colors.surface).padding(top = 16.dp)) {
                        ShareImagePanel(
                            if (lang == UiLang.Tamil) "யோவான் 3:16" else "John 3:16", "JHN", passages, "3:16", false,
                            "john-3-16.png", "https://www.tamilscripture.com/irvtam/john/3.16", onShared = {}, onClose = {},
                        )
                    }
                }
            }
        }
    }

    /** Waits for the preview and the six thumbnails, drawn off the main thread. */
    private fun drawn() = compose.waitUntil(10_000) {
        compose.onAllNodesWithContentDescription("முன்னோட்டம்").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithContentDescription("Preview").fetchSemanticsNodes().isNotEmpty()
    }.also { Thread.sleep(500); compose.waitForIdle() }

    @Test fun sheetTamil() {
        panel(ThemeMode.Light, UiLang.Tamil)
        drawn()
        compose.onRoot().captureRoboImage("src/test/screenshots/share_image_sheet_ta.png")
    }

    @Test fun sheetStoryDarkEnglish() {
        panel(ThemeMode.Dark, UiLang.English)
        compose.onNodeWithText("Status").performClick()
        compose.onNodeWithText("Initial").performClick()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.onNodeWithText("Status").performScrollTo()
        drawn()
        compose.onRoot().captureRoboImage("src/test/screenshots/share_image_sheet_story_dark_en.png")
    }
}
