package com.tamilscripture.core.designsystem

import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.component.VerseImage
import com.tamilscripture.core.designsystem.component.VerseImage.Icon
import com.tamilscripture.core.designsystem.component.VerseImage.Passage
import com.tamilscripture.core.designsystem.component.VerseImage.Size
import com.tamilscripture.core.designsystem.component.VerseImage.Template
import com.tamilscripture.core.designsystem.component.VerseImage.Theme
import com.tamilscripture.core.designsystem.component.VerseImage.Verse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The share-as-image renderer (M8-6): each template, both grounds, the three sizes, a long passage. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VerseImageScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val ta = Passage(
        "ta",
        listOf(Verse("16", "தேவன், தம்முடைய ஒரேபேறான குமாரனை விசுவாசிக்கிற எவனும் கெட்டுப்போகாமல் நித்தியஜீவனை அடையும்படிக்கு, அவரைத் தந்தருளி, இவ்வளவாய் உலகத்தில் அன்புகூர்ந்தார்.")),
        "யோவான் 3:16 · IRV-TA",
    )
    private val en = Passage(
        "en",
        listOf(Verse("16", "For God so loved the world that He gave His one and only Son, that everyone who believes in Him shall not perish but have eternal life.")),
        "John 3:16 · BSB",
    )

    private fun render(name: String, template: Template, size: Size = Size.Square, theme: Theme = Theme.Light, passages: List<Passage> = listOf(ta, en), many: Boolean = false, cv: String = "3:16") =
        VerseImage.render(context, VerseImage.Options(size.w / 2, size.h / 2, template, Icon.Cross, theme, passages, cv, many))
            .captureRoboImage("src/test/screenshots/verse_image_$name.png")

    @Test fun plate() = render("plate", Template.Plate)
    @Test fun margin() = render("margin", Template.Margin)
    @Test fun rules() = render("rules", Template.Rules)
    @Test fun numeral() = render("numeral", Template.Numeral)
    @Test fun initial() = render("initial", Template.Initial)
    @Test fun corner() = render("corner", Template.Corner)
    @Test fun storyDark() = render("story_dark", Template.Plate, Size.Story, Theme.Dark)
    @Test fun landscapeTamil() = render("landscape_ta", Template.Numeral, Size.Landscape, passages = listOf(ta))
    @Test fun englishInitial() = render("initial_en", Template.Initial, passages = listOf(en))

    @Test fun longPassage() = render(
        "long", Template.Rules,
        passages = listOf(Passage("ta", (4..7).map { Verse("$it", "அன்பு நீடிய சாந்தமும் தயவுமுள்ளது; அன்புக்குப் பொறாமையில்லை; அன்பு தன்னைப் புகழாது, இறுமாப்பாயிராது,") }, "1 கொரிந்தியர் 13:4–7 · IRV-TA")),
        many = true, cv = "13:4–7",
    )
}
