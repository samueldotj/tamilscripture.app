package com.tamilscripture.core.model

/**
 * Words under the notes (website docs/feature_psalms_explorer.md §9): a line's syllables, as
 * the website's build splits them ("shep-", "herd,"), set on the notes that start a syllable
 * in that line of the tune ([TuneEvents.slots]). Mirrors the website's lib/psalms/lyrics.ts.
 */
object Lyrics {
    /**
     * Note index → the syllable written under it. Fewer syllables than slots: the last
     * syllable keeps the line's last note and the one before holds across the gap (a
     * melisma). More: the extra syllables share the last note, as a feminine ending is sung.
     */
    fun align(syllables: List<String>, slots: List<Int>): Map<Int, String> {
        val s = syllables.size
        val n = slots.size
        if (s == 0 || n == 0) return emptyMap()
        val out = LinkedHashMap<Int, String>()
        if (s <= n) {
            for (i in 0 until s - 1) out[slots[i]] = syllables[i]
            out[slots[n - 1]] = syllables[s - 1]
        } else {
            for (i in 0 until n - 1) out[slots[i]] = syllables[i]
            out[slots[n - 1]] = join(syllables.drop(n - 1))
        }
        return out
    }

    /** Syllables run together: "ri-" + "ver," is "river,"; separate words keep a space. */
    fun join(parts: List<String>): String = parts.fold("") { acc, p ->
        when {
            acc.endsWith("-") -> acc.dropLast(1) + p
            acc.isEmpty() -> p
            else -> "$acc $p"
        }
    }

    /** A stanza's words on the tune's notes, line by line through [slots]. */
    fun stanzaOnNotes(lines: List<List<String>>, slots: List<List<Int>>): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        lines.forEachIndexed { i, line -> slots.getOrNull(i)?.let { out.putAll(align(line, it)) } }
        return out
    }
}
