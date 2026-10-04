package com.tamilscripture.feature.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The atlas drawn from the website's GeoJSON (copied from content build a9aa63f0e1). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h700dp-xxhdpi")
class AtlasScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val data = runBlocking { loadAtlas { path -> javaClass.getResource("/entities/$path")?.readText() } }

    private fun shot(file: String, mode: ThemeMode, tamil: Boolean, journey: String? = null, focus: String? = null) {
        compose.setContent {
            TsTheme(mode) {
                Box(Modifier.size(411.dp, 600.dp)) { AtlasCanvas(data, tamil, journey, focus, focus) {} }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun overviewTamil() = shot("atlas_overview_light_ta", ThemeMode.Light, tamil = true)

    @Test fun overviewDarkEnglish() = shot("atlas_overview_dark_en", ThemeMode.Dark, tamil = false)

    @Test fun journeyTamil() = shot("atlas_journey_paul1_ta", ThemeMode.Light, tamil = true, journey = "paul-1")

    @Test fun focusedPlace() = shot("atlas_focus_jerusalem_en", ThemeMode.Light, tamil = false, focus = "jerusalem")
}
