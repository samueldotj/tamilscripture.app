package com.tamilscripture.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Psalms explorer's index (a copy of the website's built psalms/index.json) and filters. */
class PsalmsTest {
    private val index = PsalmsIndex.parse(javaClass.getResource("/psalms-index.json")!!.readText())

    private fun shown(sel: PsalmSelection) = index.psalms.filter(sel.matcher(index)).map { it.n }

    @Test fun parsesEveryPsalm() {
        assertEquals((1..150).toList(), index.psalms.map { it.n })
        assertEquals(8, index.themes.size)
        val p23 = index.psalms[22]
        assertEquals("கர்த்தர் என் மேய்ப்பர்", p23.titleTa)
        assertEquals(listOf("david"), p23.authors)
        assertEquals(1..1, p23.keyVerses)
        assertNull(index.psalms[1].titleTa)
        assertEquals(1..2, index.psalms[120].keyVerses)
    }

    @Test fun emptySelectionKeepsAll() {
        assertEquals(150, shown(PsalmSelection()).size)
    }

    @Test fun anyWithinAFacetEveryAcrossFacets() {
        assertEquals(listOf(50) + (73..83), shown(PsalmSelection(authors = listOf("asaph"))))
        val asaphLament = shown(PsalmSelection(themes = listOf("lament"), authors = listOf("asaph")))
        assertTrue(asaphLament.all { index.psalms[it - 1].theme == "lament" && it in 50..83 })
        val two = shown(PsalmSelection(authors = listOf("moses", "ethan")))
        assertEquals(listOf(89, 90), two)
    }

    @Test fun collections() {
        assertEquals((120..134).toList(), shown(PsalmSelection(genres = listOf("ascents"))))
        assertEquals(listOf(130), shown(PsalmSelection(genres = listOf("ascents"), themes = listOf("repent"))))
    }

    @Test fun anonymousMeansNoNamedAuthor() {
        val anon = shown(PsalmSelection(authors = listOf(PsalmSelection.ANONYMOUS)))
        assertTrue(1 in anon && 2 in anon && 23 !in anon)
        assertEquals(anon.size, index.psalms.count { it.authors.isEmpty() })
    }

    @Test fun stepsWrapAmongTheKeptPsalms() {
        assertEquals(22 to 24, index.steps(PsalmSelection(), 23))
        assertEquals(150 to 2, index.steps(PsalmSelection(), 1))
        val asaph = PsalmSelection(authors = listOf("asaph"))
        assertEquals(50 to 74, index.steps(asaph, 73))
        assertEquals(83 to 73, index.steps(asaph, 50))
        // The open psalm left behind by the filters steps through all 150.
        assertEquals(22 to 24, index.steps(asaph, 23))
        assertEquals(3, PsalmSelection(themes = listOf("lament"), genres = listOf("ascents"), authors = listOf("asaph")).count)
    }

    @Test fun toggling() {
        assertEquals(listOf("a", "b"), listOf("a").toggled("b"))
        assertEquals(listOf("a"), listOf("a", "b").toggled("b"))
    }
}
