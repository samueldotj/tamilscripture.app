package com.tamilscripture.feature.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Present mode's slide (M8-8) on a 1080p landscape screen: a short verse and a long one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class PresentScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(file: String, text: String, ref: String) {
        compose.setContent { TsTheme(ThemeMode.Dark) { SlideView(Slide(text, ref)) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun shortVerse() = shot("present_short", "இயேசு கண்ணீர்விட்டார்.", "யோவான் 11:35")

    @Test fun longVerse() = shot(
        "present_long",
        "தேவன், தம்முடைய ஒரே பேறான குமாரனை விசுவாசிக்கிறவன் எவனோ அவன் கெட்டுப்போகாமல் நித்தியஜீவனை அடையும்படிக்கு, அவரைத் தந்தருளி, இவ்வளவாய் உலகத்தில் அன்புகூர்ந்தார்.",
        "யோவான் 3:16",
    )
}
