package com.tamilscripture.feature.study

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.geometry.Offset
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

    private val kingdoms = runBlocking { loadKingdoms { path -> javaClass.getResource("/entities/$path")?.readText() } }!!
    private val church = runBlocking { loadChurch { path -> javaClass.getResource("/entities/$path")?.readText() } }

    private fun shot(
        file: String, mode: ThemeMode, tamil: Boolean, journey: String? = null, focus: String? = null,
        year: Int? = null, withChurch: Boolean = false, zoomOut: Boolean = false, fit: List<String> = emptyList(),
    ) {
        compose.setContent {
            TsTheme(mode) {
                Box(Modifier.size(411.dp, 600.dp)) {
                    AtlasCanvas(
                        data, tamil, journey, focus, focus,
                        kingdoms = year?.let { y -> kingdoms.shapes.filter { y in it.from..it.to } }.orEmpty(),
                        church = if (withChurch) church else emptyList(),
                        fitPlaces = fit,
                    ) {}
                }
            }
        }
        if (zoomOut) repeat(4) {
            compose.onRoot().performTouchInput {
                pinch(Offset(center.x - 300f, center.y), Offset(center.x - 40f, center.y), Offset(center.x + 300f, center.y), Offset(center.x + 40f, center.y))
            }
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$file.png")
    }

    @Test fun overviewTamil() = shot("atlas_overview_light_ta", ThemeMode.Light, tamil = true)

    @Test fun overviewDarkEnglish() = shot("atlas_overview_dark_en", ThemeMode.Dark, tamil = false)

    @Test fun journeyTamil() = shot("atlas_journey_paul1_ta", ThemeMode.Light, tamil = true, journey = "paul-1")

    @Test fun focusedPlace() = shot("atlas_focus_jerusalem_en", ThemeMode.Light, tamil = false, focus = "jerusalem")

    @Test fun kingdoms1000BC() = shot("atlas_kingdoms_1000bce_ta", ThemeMode.Light, tamil = true, year = -1000)

    @Test fun kingdomsAD30Dark() = shot("atlas_kingdoms_ad30_dark_en", ThemeMode.Dark, tamil = false, year = 30)

    /** Zoomed all the way out, the world fills in around the detailed box (no bare sea at its edges). */
    @Test fun zoomedOut() = shot("atlas_zoomed_out_en", ThemeMode.Light, tamil = false, zoomOut = true)

    /** M8-5e: opened from Acts 13's map, the atlas fits the chapter's places. */
    @Test fun chapterPlaces() = shot(
        "atlas_fit_acts13_en", ThemeMode.Light, tamil = false,
        fit = listOf("salamis", "paphos", "perga", "seleucia", "antioch", "cyprus"),
    )

    @Test fun earlyChurch() = shot("atlas_church_en", ThemeMode.Light, tamil = false, withChurch = true)

    /** M8-5b: the pane beside the map on wide windows, a journey with its stops. */
    @Test fun journeyPane() {
        compose.setContent {
            TsTheme(ThemeMode.Light) {
                Box(Modifier.size(340.dp, 600.dp)) {
                    DetailsPane(null, data.journeys.first { it.id == "paul-1" }, tamil = true, onOpen = {}, onSelect = {}, onClose = {}, refLabel = { it.replace("ACT.", "அப் ").replace(".", ":") })
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/atlas_pane_paul1_ta.png")
    }

    /** M8-5d: a right-click opens a menu for the spot. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun rightClickMenu() {
        compose.setContent {
            TsTheme(ThemeMode.Light) {
                Box(Modifier.size(411.dp, 600.dp)) { AtlasCanvas(data, false, null, null, null) {} }
            }
        }
        compose.onRoot().performMouseInput { rightClick(center) }
        compose.waitForIdle()
        compose.onNodeWithText("Centre here").assertExists()
        compose.onNodeWithText("Zoom in here").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/atlas_menu_en.png")
    }
}
