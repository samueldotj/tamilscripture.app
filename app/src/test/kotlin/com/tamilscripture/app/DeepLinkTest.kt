package com.tamilscripture.app

import android.net.Uri
import com.tamilscripture.core.model.BibleVersion
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Website links the app opens (A-2.10, M8-5d). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeepLinkTest {
    private val m = ContentManifest(
        build = "b1",
        versions = listOf(BibleVersion("IRVTAM"), BibleVersion("BSB", lang = "en")),
        books = listOf(Book("JHN", 43, "NT", 21, slug = "john", nameEn = "John", nameTa = "யோவான்", abbrEn = listOf("Jn"))),
    )

    private fun open(path: String) = parseDeepLink(Uri.parse("https://www.tamilscripture.com$path"), m, "IRVTAM")

    @Test fun chapters() {
        assertEquals(ReaderRoute(Passage("IRVTAM", "JHN", 3, 16)), open("/irvtam/john/3/16")?.route)
        assertEquals(ReaderRoute(Passage("BSB", "JHN", 3, null), compare = true), open("/bsb+irvtam/john/3")?.route)
        assertEquals(SearchRoute(query = "அன்பு"), open("/search?q=அன்பு")?.route)
    }

    @Test fun studyPages() {
        assertEquals(AtlasRoute(), open("/atlas")?.route)
        assertEquals(AtlasRoute(), open("/atlas/explore")?.route)
        assertEquals(AtlasRoute(journey = "paul-2"), open("/atlas/paul-2")?.route)
        assertEquals(PlaceRoute("corinth"), open("/place/corinth")?.route)
        assertEquals(PersonRoute("paul"), open("/person/paul")?.route)
        assertEquals(StrongsRoute("G3972G"), open("/strongs/G3972G")?.route)
        assertEquals(DictionaryRoute, open("/dictionary")?.route)
        assertNull(open("/place/Not_A_Slug"))
    }
}
