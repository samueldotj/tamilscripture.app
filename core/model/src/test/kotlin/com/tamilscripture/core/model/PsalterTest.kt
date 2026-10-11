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
        // Every note the timings name is drawn, so it can be lit.
        val ids = PsalterDrawing.parse(res("full.draw.json")).items.mapNotNull { it.note }.toSet()
        assertTrue(e.notes.all { it.fullId in ids })
    }
}
