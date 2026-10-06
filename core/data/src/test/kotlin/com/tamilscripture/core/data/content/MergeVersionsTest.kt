package com.tamilscripture.core.data.content

import com.tamilscripture.core.model.SearchHit
import com.tamilscripture.core.model.SearchResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M3-5: "All versions" applies the romanised-Tamil rule across the versions, not to each. */
class MergeVersionsTest {
    private fun result(query: String, shown: String, vararg hits: SearchHit) =
        SearchRepository.Result(SearchResponse(query = shown, hits = hits.toList(), total = hits.size), offline = false, shown = shown)

    private val bsb = SearchHit("JHN.3.16", "BSB", "For God so loved the world", bookOrder = 43)
    private val lot = SearchHit("GEN.13.11", "TCV", "எனவே லோத்து …", bookOrder = 1)
    private val anbu = SearchHit("1JN.4.8", "TCV", "தேவன் அன்பாகவே இருக்கிறார்", bookOrder = 62)

    @Test fun anEnglishWordFoundInOneVersionIsNotReadAsTamilInAnother() {
        // "love" finds BSB verses; TCV, finding nothing as typed, fell back to லோவெ (near லோத்து).
        val r = mergeVersions("love", listOf("TCV", "BSB"), listOf(result("love", "லோவெ", lot), result("love", "love", bsb)))
        assertEquals(listOf(bsb), r.response.hits)
        assertEquals(1, r.response.total)
        assertEquals("love", r.shown)
        assertEquals("லோவெ", r.romanOffer)
    }

    @Test fun romanisedTamilFoundNowhereAsTypedIsReadAsTamil() {
        // "anbu" finds nothing as typed anywhere, so TCV's reading அன்பு stands.
        val r = mergeVersions("anbu", listOf("TCV", "BSB"), listOf(result("anbu", "அன்பு", anbu), result("anbu", "anbu")))
        assertEquals(listOf(anbu), r.response.hits)
        assertEquals("அன்பு", r.shown)
        assertNull(r.romanOffer)
    }
}
