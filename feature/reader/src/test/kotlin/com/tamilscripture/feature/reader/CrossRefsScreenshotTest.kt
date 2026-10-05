package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.CrossRef
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Study Bible: references right after the verse, tight lines, the open one marked. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h400dp-xxhdpi")
class CrossRefsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val refs = listOf("EZK.36.26", "EPH.4.22", "ISA.43.18", "JHN.3.3", "PSA.51.10", "EZK.11.19", "ROM.6.4", "GAL.6.15")
        .map { CrossRef(it) }

    @Test fun openReference() {
        compose.setContent {
            TsTheme(ThemeMode.Dark) {
                CompositionLocalProvider(LocalUiLang provides UiLang.English) {
                    val c = Ts.colors
                    Column(Modifier.background(c.bg).padding(16.dp)) {
                        TextWithTrailer(
                            main = buildAnnotatedString { append("17 Therefore if anyone is in Christ, he is a new creation; the old has passed away, behold, the new has come.") },
                            mainStyle = Ts.type.scripture(21.sp, 1.8f),
                            color = c.ink,
                            trailer = buildAnnotatedString {
                                appendCrossRefs(refs, all = false, openKey = refs[3].key(), manifest = null, c = c, lang = UiLang.English, onRef = {}, onMore = {})
                            },
                            trailerStyle = Ts.type.caption.copy(fontSize = 12.sp, lineHeight = 22.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/crossrefs_open.png")
    }

    @Test fun openedAfterwards() {
        val open = androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent {
            TsTheme(ThemeMode.Dark) {
                val c = Ts.colors
                Column(Modifier.background(c.bg).padding(16.dp)) {
                    TextWithTrailer(
                        main = buildAnnotatedString { append("17 Therefore if anyone is in Christ, he is a new creation.") },
                        mainStyle = Ts.type.scripture(21.sp, 1.8f),
                        color = c.ink,
                        trailer = buildAnnotatedString {
                            appendCrossRefs(refs, all = false, openKey = open.value, manifest = null, c = c, lang = UiLang.English, onRef = {}, onMore = {})
                        },
                        trailerStyle = Ts.type.caption.copy(fontSize = 12.sp, lineHeight = 22.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { open.value = refs[3].key() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/crossrefs_opened_later.png")
    }
}
