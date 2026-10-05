package com.tamilscripture.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M8-9: a stroke covers whole words, never spaces or punctuation at its ends, counted in code points. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PenStrokeTest {
    @Test fun englishWordsSnapOut() {
        val t = "For God so loved the world, that he gave"
        // From inside "God" to inside "loved".
        val s = wordStroke(t, 5, 12, erase = false)!!
        assertEquals("God so loved", s.quote(t))
        // Ending on the comma leaves it out.
        assertEquals("the world", wordStroke(t, 17, 26, erase = false)!!.quote(t))
    }

    @Test fun tamilWordsKeepTheirSigns() {
        val t = "தேவன், தம்முடைய ஒரேபேறான குமாரனை"
        val inside = t.indexOf("முடைய")
        val s = wordStroke(t, inside, inside + 2, erase = false)!!
        assertEquals("தம்முடைய", s.quote(t))
        assertEquals(t.codePointCount(0, t.indexOf("தம்")), s.start)
    }

    @Test fun onlySpacesIsNothing() {
        assertNull(wordStroke("a   b", 2, 2, erase = false))
    }
}
