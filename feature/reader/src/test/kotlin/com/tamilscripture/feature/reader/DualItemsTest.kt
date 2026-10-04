package com.tamilscripture.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DualItemsTest {
    private fun v(n: Int, text: String = "v$n") = ReaderItem.Verse("v$n", n, n.toString(), text, emptyList(), emptyList(), false)

    @Test
    fun alignsByVerseNumberWithDashesForMissingVerses() {
        val a = listOf(ReaderItem.Heading("h0", "Start"), v(1), v(2), v(4))
        val b = listOf(ReaderItem.Heading("h0", "Other"), v(1), v(3), v(4))
        val rows = dualItems(a, b)
        assertEquals(listOf("h0", "p1", "p2", "p3", "p4"), rows.map { it.key })
        assertEquals("Start", (rows[0] as ReaderItem.Heading).text)
        val r2 = rows[2] as ReaderItem.Dual
        assertEquals(2, r2.a?.verse)
        assertNull(r2.b)
        val r3 = rows[3] as ReaderItem.Dual
        assertNull(r3.a)
        assertEquals("3", r3.label)
    }

    @Test
    fun headingsStayBeforeTheirVerse() {
        val a = listOf(v(1), ReaderItem.Heading("h1", "Mid"), v(2))
        assertEquals(listOf("p1", "h1", "p2"), dualItems(a, listOf(v(1), v(2))).map { it.key })
    }
}
