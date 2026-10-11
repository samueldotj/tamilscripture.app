package com.tamilscripture.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crimond as the website's tools/psalter renders it (copied from content build a9aa63f0e1). */
class PsalterTest {
    private fun res(name: String) = javaClass.getResource("/psalter/crimond-$name")!!.readText()

    @Test fun drawing() {
        val d = PsalterDrawing.parse(res("incipit.draw.json"))
        assertTrue(d.width > d.height)
        assertTrue(d.items.all { (it.glyph != null) != (it.path != null) && it.matrix.size == 6 })
        assertTrue(d.items.mapNotNull { it.glyph }.all { it in d.glyphs })
    }

    @Test fun events() {
        val e = TuneEvents.parse(res("events.json"))
        assertEquals(34, e.notes.size)
        assertEquals(10, e.incipitNotes)
        assertEquals(60, e.notes[0].midi)
        assertEquals(e.notes[1], e.at(e.notes[1].on))
        assertNull(e.at(e.durationMs + 1))
        assertTrue(e.notes.take(e.incipitNotes).all { it.incipitId != null })
        assertTrue(e.notes.drop(e.incipitNotes).all { it.incipitId == null })
        // Crimond's four lines (C.M.), and the line sung at a moment of the stanza.
        assertEquals(listOf(0L, 8571L, 15000L, 21429L), e.lines)
        assertEquals(0, e.line(0))
        assertEquals(1, e.line(9000))
        assertEquals(3, e.line(e.durationMs - 1))
        assertEquals(-1, e.line(e.durationMs + 300))
        // Where the words go: a syllable slot per line, and a baseline under every note.
        assertEquals(84, e.bpm)
        assertEquals(4, e.slots.size)
        assertEquals(0, e.slots[0][0])
        assertTrue(e.notes.all { it.fullX != null && it.fullLyric != null && it.fullLyric!! > 0f })
        // Every note the timings name is drawn, so it can be lit.
        val ids = PsalterDrawing.parse(res("full.draw.json")).items.mapNotNull { it.note }.toSet()
        assertTrue(e.notes.all { it.fullId in ids })
    }

    @Test fun seeking() {
        val e = TuneEvents.parse(res("events.json"))
        // A tap on a notehead, or on the word under it, finds that note.
        val (w, h) = 20000f to 4000f
        val n = e.notes[5]
        assertEquals(5, e.nearest(n.fullX!! * w + 80, n.fullY!! * h, w, h, full = true))
        assertEquals(5, e.nearest(n.fullX!! * w + 80, n.fullLyric!! * h + 20, w, h, full = true))
        // The whole psalm as stanzas a breath apart.
        val per = e.durationMs + 700
        assertEquals(0 to 0L, e.place(0, 700, 3))
        assertEquals(1 to 500L, e.place(per + 500, 700, 3))
        assertEquals(0 to e.durationMs, e.place(e.durationMs + 300, 700, 3))
        assertEquals(2 to e.durationMs, e.place(10 * per, 700, 3))
    }
}
