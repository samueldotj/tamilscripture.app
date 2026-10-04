package com.tamilscripture.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The website's study files (copied from content build a9aa63f0e1) decode as the app expects. */
class StudyTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private fun fixture(name: String) = javaClass.getResource("/study/$name")!!.readText()

    @Test
    fun strongsFileNamesAndVerseKeys() {
        assertEquals("H1121_a", StrongsEntry.fileName("H1121a"))
        assertEquals("G0002", StrongsEntry.fileName("G0002"))
        val e = json.decodeFromString<StrongsEntry>(fixture("G0002.json"))
        assertEquals("Aarōn", e.translit)
        assertEquals(e.count, e.verseKeys.size)
        assertTrue(e.verseKeys.zipWithNext().all { (a, b) -> b > a })
        assertEquals(42, (e.verseKeys.first() / 1_000_000).toInt()) // Luke
    }

    @Test
    fun originalWordsOfAVerse() {
        val ch = json.decodeFromString<OriginalChapter>(fixture("original-JHN-1.json"))
        val words = ch.words(1)
        assertEquals("el", ch.lang)
        assertTrue(words.any { it.strongs == "G3056" })
    }

    @Test
    fun chapterMentionsAndPerson() {
        val m = json.decodeFromString<ChapterMentions>(fixture("mentions-JHN-1.json"))
        assertTrue(m.people.isNotEmpty() && m.verses.isNotEmpty())
        val p = json.decodeFromString<Person>(fixture("aaron.json"))
        assertEquals("ஆரோன்", p.tamilName?.label)
        assertTrue(p.verses.isNotEmpty())
    }

    @Test
    fun placeAndArticle() {
        val p = json.decodeFromString<Place>(fixture("jerusalem.json"))
        assertTrue(p.geo?.lat != null && p.verses.isNotEmpty())
        val a = json.decodeFromString<Article>(fixture("article.json"))
        assertEquals("eastons", a.source)
        assertTrue(a.paragraphs.isNotEmpty())
    }

    @Test
    fun chapterAndJourneyMaps() {
        val m = MapSvg.parse(fixture("map-JHN-1.svg"))!!
        assertEquals(360f, m.width)
        assertTrue(m.paths.any { it.first == "land" } && m.paths.any { it.first == "river" })
        val jerusalem = m.places.first { it.id == "jerusalem" }
        assertEquals("எருசலேம்", jerusalem.ta?.text)
        assertEquals("start", jerusalem.en?.anchor)
        val j = MapSvg.parse(fixture("map-paul-1.svg"))!!
        assertTrue(j.paths.any { it.first == "route" })
        assertTrue(j.places.all { it.em } && j.places.first().number == "1")
    }

    @Test
    fun atlasLayers() {
        val rivers = GeoJson.parse(fixture("rivers.geojson"))
        assertTrue(rivers.isNotEmpty() && rivers.all { it.parts.isNotEmpty() && it.parts[0].size >= 4 })
        val places = GeoJson.parse(fixture("places-5.geojson"))
        assertEquals(5, places.size)
        assertTrue(places.all { it.point != null && it.props["id"] != null })
        val journeys = json.decodeFromString<List<Journey>>(fixture("journeys-2.json"))
        assertTrue(journeys.first().stops.isNotEmpty())
        assertEquals(0.0, Mercator.y(0.0), 1e-9)
    }
}
