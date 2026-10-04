package com.tamilscripture.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelTest {
    private fun book(code: String, order: Int, t: String, ch: Int, en: String, ta: String, abbrTa: List<String> = emptyList(), abbrEn: List<String> = emptyList()) =
        Book(code, order, t, ch, en.lowercase(), en, ta, abbrEn, abbrTa)

    private val books = listOf(
        book("GEN", 1, "OT", 50, "Genesis", "ஆதியாகமம்", listOf("ஆதி"), listOf("Gen")),
        book("PSA", 19, "OT", 150, "Psalms", "சங்கீதம்", listOf("சங்"), listOf("Ps", "Psa")),
        book("JHN", 43, "NT", 21, "John", "யோவான்", listOf("யோவா"), listOf("Jn", "Jhn")),
        book("1CO", 46, "NT", 16, "1 Corinthians", "1 கொரிந்தியர்", listOf("1 கொரி"), listOf("1Co")),
    )

    @Test fun verseIdParsesRangesToFirstVerse() {
        assertEquals(VerseId("JHN", 3, 16), VerseId.parse("JHN.3.16-18"))
        assertNull(VerseId.parse("JHN.3"))
    }

    @Test fun psalmsPlanMatchesWebsiteShape() {
        val plan = Plans.builtIn(books).first { it.id == "psalms-6m" }
        val s = Plans.schedule(plan)
        assertEquals(180, s.size)
        // 149 whole psalms + 22 stanzas of Psalm 119, every unit scheduled exactly once.
        assertEquals(171, s.sumOf { d -> d.sumOf { it.units.size } })
        assertEquals("சங்கீதம் 1", Plans.passageLabel(s[0].first().units, UiLang.Tamil))
    }

    @Test fun statsCountStreakAndMissedDays() {
        // John's 21 chapters over 10 days: every day has reading, so no rest days interfere.
        val john = books.first { it.code == "JHN" }
        val plan = ReadingPlan("t", "t", "t", "", "", 10, listOf(PlanTrack("யோ", "Jn", (1..21).map { PlanUnit(john, it) })))
        val start = LocalDate.of(2026, 10, 1)
        val sched = Plans.schedule(plan)
        val done = (0..1).flatMap { d -> sched[d].map { Plans.key(d, it.trackIndex) } }.toSet()
        val st = Plans.stats(plan, PlanProgress(start, done), now = LocalDate.of(2026, 10, 3))
        assertEquals(2, st.streak)
        assertEquals(0, st.behind)
        // A day later with day 2 still unread, it counts as missed.
        val later = Plans.stats(plan, PlanProgress(start, done), now = LocalDate.of(2026, 10, 4))
        assertEquals(listOf(2), later.missed)
    }
}

class RomanisedTest {
    @Test
    fun commonWords() {
        assertEquals("அன்பு", Romanised.toTamil("anbu"))
        assertEquals("கிருபை", Romanised.toTamil("kirubai"))
        assertEquals("தேவன்", Romanised.toTamil("thEvan"))
        assertEquals("நான் இயேசு", Romanised.toTamil("naan iyEsu"))
        assertEquals("அன்பு கிருபை", Romanised.toTamil("  anbu   kirubai "))
    }

    @Test
    fun detection() {
        assertTrue(Romanised.isRomanised("anbu"))
        assertTrue(Romanised.isRomanised("o'brien-x"))
        assertFalse(Romanised.isRomanised("அன்பு"))
        assertFalse(Romanised.isRomanised("john 3:16"))
        assertFalse(Romanised.isRomanised("  "))
    }
}

class CommunityPlanTest {
    private fun book(code: String, order: Int, ch: Int, t: String = "NT") = Book(code, order, t, ch, code.lowercase(), code, code)
    private val books = listOf(book("GEN", 1, 50, "OT"), book("MAT", 40, 28), book("MRK", 41, 16), book("LUK", 42, 24), book("JHN", 43, 21))

    @Test
    fun tracksReadTheirBookRangeInOrder() {
        val row = CommunityPlan("x", titleTa = "நற்செய்தி", days = 90, tracks = listOf(TrackSpec("Gospels", "MAT", "JHN"), TrackSpec("Bad", "JHN", "MAT")))
        val plan = Plans.fromRow(row, books)!!
        assertEquals(1, plan.tracks.size)
        assertEquals(28 + 16 + 24 + 21, plan.tracks[0].units.size)
        assertEquals("MAT", plan.tracks[0].units.first().book.code)
        assertEquals("நற்செய்தி", plan.titleEn) // no English title: the Tamil one, as on the website
        assertTrue(plan.community)
    }

    @Test
    fun aPlanWithNoUsableTrackIsDropped() {
        assertNull(Plans.fromRow(CommunityPlan("y", days = 30, tracks = listOf(TrackSpec("", "XXX", "JHN"))), books))
    }
}
