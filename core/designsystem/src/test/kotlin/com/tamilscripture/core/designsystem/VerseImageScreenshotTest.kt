package com.tamilscripture.core.designsystem

import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.tamilscripture.core.designsystem.component.VerseImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The share-as-image card (M8-6): a short verse and a long one that must shrink to fit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VerseImageScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun shortVerse() = VerseImage.render(
        context,
        "தேவன், தம்முடைய ஒரே பேறான குமாரனை விசுவாசிக்கிறவன் எவனோ அவன் கெட்டுப்போகாமல் நித்தியஜீவனை அடையும்படிக்கு, அவரைத் தந்தருளி, இவ்வளவாய் உலகத்தில் அன்புகூர்ந்தார்.",
        "யோவான் 3:16",
    ).captureRoboImage("src/test/screenshots/verse_image_jhn3_16.png")

    @Test fun longPassage() = VerseImage.render(
        context,
        List(4) { "அன்பு நீடிய சாந்தமும் தயவுமுள்ளது; அன்புக்குப் பொறாமையில்லை; அன்பு தன்னைப் புகழாது, இறுமாப்பாயிராது," }.joinToString(" "),
        "1 கொரிந்தியர் 13:4–7",
    ).captureRoboImage("src/test/screenshots/verse_image_long.png")
}
