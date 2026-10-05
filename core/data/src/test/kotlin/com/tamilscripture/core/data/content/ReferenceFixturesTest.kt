package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.testing.FixturePacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ts_mobile.parseReference

/**
 * M0-8: the website's reference-parser fixtures (data/fixtures/references.tsv, copied from
 * the website repo) through the app's own binding of the Rust parser, so the app reads every
 * reference the website does, the same way.
 */
class ReferenceFixturesTest {
    @Test fun everyFixtureParsesAsOnTheWebsite() {
        val failures = ArrayList<String>()
        var n = 0
        for (line in FixturePacks.resource("references.tsv").lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val (input, expected) = line.split('\t').map { it.trim() }
            val r = parseReference(input)
            val got = r?.let { p ->
                buildString {
                    append(p.book).append('.').append(p.chapter)
                    p.verse?.let { append('.').append(it) }
                    p.verseEnd?.takeIf { it != p.verse }?.let { append('-').append(it) }
                }
            } ?: "NONE"
            if (got != expected) failures += "$input: got $got, expected $expected"
            n++
        }
        assertTrue("only $n cases", n >= 50)
        assertEquals(emptyList<String>(), failures)
    }
}
