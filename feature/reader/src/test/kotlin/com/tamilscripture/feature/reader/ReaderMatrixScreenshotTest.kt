package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.data.settings.ReadingFormat
import com.tamilscripture.core.designsystem.theme.ThemeMode
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsTheme
import com.tamilscripture.core.model.CrossRef
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M2-13: the reader's frame (top bar, Study Bible text with cross-references, chapter
 * buttons) at the four width classes, light and dark, Tamil and English: 16 images in
 * src/test/screenshots/matrix/. Catches what only shows at one width, such as a title cut
 * to "2 …" on a phone.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReaderMatrixScreenshotTest(private val width: String, private val dark: Boolean, private val tamil: Boolean) {
    @get:Rule val compose = createComposeRule()

    companion object {
        /** Compact phone, medium (foldable, small tablet), expanded tablet, large (ALOS window). */
        private val widths = mapOf("compact" to "w411dp-h891dp", "medium" to "w700dp-h1000dp", "expanded" to "w1000dp-h800dp", "large" to "w1400dp-h900dp")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark{1}-ta{2}")
        fun cases() = widths.keys.flatMap { w -> listOf(false, true).flatMap { d -> listOf(true, false).map { t -> arrayOf<Any>(w, d, t) } } }
    }

    private val ta = listOf(
        "இப்படியிருக்க, ஒருவன் கிறிஸ்துவிற்குள் இருந்தால் புதுப்படைப்பாக இருக்கிறான்; பழையவைகள் எல்லாம் ஒழிந்துபோனது, எல்லாம் புதிதானது.",
        "இவைகளெல்லாம் தேவனாலே உண்டாயிருக்கிறது; அவர் இயேசுகிறிஸ்துவைக்கொண்டு நம்மை அவரோடு ஒப்புரவாக்கினார்.",
    )
    private val en = listOf(
        "Therefore if anyone is in Christ, he is a new creation; the old has passed away, behold, the new has come.",
        "All this is from God, who through Christ reconciled us to himself and gave us the ministry of reconciliation.",
    )

    @Test fun reader() {
        RuntimeEnvironment.setQualifiers("+${widths.getValue(width)}-xhdpi")
        val texts = if (tamil) ta else en
        val items = texts.mapIndexed { i, t -> ReaderItem.Verse("v${17 + i}", 17 + i, "${17 + i}", t, emptyList(), emptyList(), false) }
        val refs = mapOf(17 to listOf("EZK.36.26", "EPH.4.22", "ISA.43.18", "JHN.3.3", "PSA.51.10", "EZK.11.19", "ROM.6.4").map { CrossRef(it) })
        compose.setContent {
            TsTheme(if (dark) ThemeMode.Dark else ThemeMode.Light) {
                CompositionLocalProvider(LocalUiLang provides if (tamil) UiLang.Tamil else UiLang.English) {
                    Column(Modifier.fillMaxSize().background(Ts.colors.bg)) {
                        ReaderTopBar(
                            if (tamil) "2 கொரிந்தியர் 5" else "2 Corinthians 5", "IRV", hasAudio = true, commentaryOn = false,
                            onBack = {}, onPicker = {}, onPlay = {}, onSettings = {}, comparing = false, onCompare = {},
                            shortTitle = if (tamil) "2 கொரி 5" else "2 Cor 5",
                        )
                        ReaderTextList(
                            items, selection = emptyList(), playingVerse = null, fontSize = 21, lineHeightEm = 1.8f, showNotes = false,
                            commentary = null, commentaryName = null, listState = rememberLazyListState(), chapterKey = "2CO.5",
                            contentPadding = PaddingValues(18.dp), onTapVerse = {}, onVerseRead = { _, _ -> }, onOpenCommentary = {},
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            format = ReadingFormat.Study, xrefs = refs,
                        )
                        ChapterNavBar(
                            if (tamil) "2 கொரிந்தியர் 4" else "2 Corinthians 4", if (tamil) "2 கொரிந்தியர் 6" else "2 Corinthians 6", "5 / 13", {}, {},
                            prevShort = if (tamil) "2 கொரி 4" else "2 Cor 4", nextShort = if (tamil) "2 கொரி 6" else "2 Cor 6",
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/matrix/reader_${width}_${if (dark) "dark" else "light"}_${if (tamil) "ta" else "en"}.png")
    }
}
