package com.tamilscripture.feature.reader

import com.tamilscripture.core.designsystem.component.VerseImage
import org.junit.Assert.assertEquals
import org.junit.Test

/** The text sent with a shared verse image, as the website sends it. */
class ShareTextTest {
    private fun options(many: Boolean, vararg passages: VerseImage.Passage) = VerseImage.Options(
        1080, 1080, VerseImage.Template.Plate, VerseImage.Icon.Cross, VerseImage.Theme.Light, passages.toList(), "1:5", many,
    )

    @Test fun oneVerseInTwoVersions() {
        val ta = VerseImage.Passage("ta", listOf(VerseImage.Verse("5", "யாக்கோபின் கர்ப்பப்பிறப்புகள் எல்லாம் எழுபது பேர்.")), "யாத்திராகமம் 1:5 · IRV")
        val en = VerseImage.Passage("en", listOf(VerseImage.Verse("5", "The descendants of Jacob numbered seventy in all.")), "Exodus 1:5 · BSB")
        assertEquals(
            "யாக்கோபின் கர்ப்பப்பிறப்புகள் எல்லாம் எழுபது பேர்.\n— யாத்திராகமம் 1:5 · IRV\n\n" +
                "The descendants of Jacob numbered seventy in all.\n— Exodus 1:5 · BSB\n\n" +
                "https://www.tamilscripture.com/irvtam/exodus/1.5",
            shareText(options(false, ta, en), "https://www.tamilscripture.com/irvtam/exodus/1.5"),
        )
    }

    @Test fun severalVersesAreNumbered() {
        val en = VerseImage.Passage("en", listOf(VerseImage.Verse("1", "In the beginning."), VerseImage.Verse("2", "Now the earth was formless.")), "Genesis 1:1–2 · BSB")
        assertEquals(
            "1 In the beginning. 2 Now the earth was formless.\n— Genesis 1:1–2 · BSB\n\nhttps://x/1.1-2",
            shareText(options(true, en), "https://x/1.1-2"),
        )
    }
}
