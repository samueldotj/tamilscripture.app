package com.tamilscripture.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.component.SchematicMap
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.MapSvg
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The website's chapter and journey maps drawn by [SchematicMap], light and dark, Tamil and English. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xxhdpi")
class SchematicMapScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun svg(name: String) = javaClass.getResource("/$name")!!.readText()

    private fun shot(file: String, map: String, mode: ThemeMode, tamil: Boolean, selected: String? = null) {
        val drawing = MapSvg.parse(svg(map))!!
        compose.setContent {
            TsTheme(mode) {
                Box(Modifier.background(Ts.colors.bg).padding(12.dp).width(376.dp)) {
                    SchematicMap(drawing, tamil, selected = selected)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun chapterLightTamil() = shot("map_jhn1_light_ta", "map-JHN-1.svg", ThemeMode.Light, tamil = true, selected = "jerusalem")

    @Test fun chapterDarkEnglish() = shot("map_jhn1_dark_en", "map-JHN-1.svg", ThemeMode.Dark, tamil = false)

    @Test fun journeyLightTamil() = shot("map_paul1_light_ta", "map-paul-1.svg", ThemeMode.Light, tamil = true)
}
