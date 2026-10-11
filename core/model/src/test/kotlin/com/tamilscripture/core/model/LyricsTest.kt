package com.tamilscripture.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Words on notes: the same rules as the website's lib/psalms/lyrics.ts. */
class LyricsTest {
    @Test fun oneSyllableANote() {
        assertEquals(mapOf(0 to "The", 1 to "Lord’s", 3 to "shep-"), Lyrics.align(listOf("The", "Lord’s", "shep-"), listOf(0, 1, 3)))
    }

    @Test fun fewerSyllablesHoldTheLastNote() {
        // "the qui- et" on four notes: the last syllable keeps the last note, "qui-" holds across.
        assertEquals(mapOf(0 to "the", 1 to "qui-", 5 to "et"), Lyrics.align(listOf("the", "qui-", "et"), listOf(0, 1, 3, 5)))
    }

    @Test fun moreSyllablesShareTheLastNote() {
        // A feminine ending: "ri- ver," sung on one note.
        assertEquals(mapOf(0 to "by", 1 to "a", 2 to "river,"), Lyrics.align(listOf("by", "a", "ri-", "ver,"), listOf(0, 1, 2)))
        assertEquals("his leaf", Lyrics.join(listOf("his", "leaf")))
    }

    @Test fun aStanzaLineByLine() {
        val words = Lyrics.stanzaOnNotes(listOf(listOf("He", "makes"), listOf("me")), listOf(listOf(0, 1), listOf(2, 3)))
        assertEquals(mapOf(0 to "He", 1 to "makes", 3 to "me"), words)
    }
}
