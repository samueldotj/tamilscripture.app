package com.tamilscripture.core.model

/**
 * Romanised Tamil ("anbu", "kirubai", "thevan") to Tamil script for search (A-4.3), a
 * port of the website's `lib/search/romanised.ts`. It follows the way people type Tamil
 * in Latin letters rather than a formal scheme; the search's own folding absorbs most of
 * the guesswork, and capitals pick the marked letters (N ண, L ள, R ற, E ஏ, O ஓ).
 */
object Romanised {
    // latin, independent letter, sign after a consonant
    private val VOWELS = listOf(
        Triple("aa", "ஆ", "ா"), Triple("ai", "ஐ", "ை"), Triple("au", "ஔ", "ௌ"),
        Triple("ii", "ஈ", "ீ"), Triple("ee", "ஈ", "ீ"), Triple("uu", "ஊ", "ூ"), Triple("oo", "ஊ", "ூ"),
        Triple("A", "ஆ", "ா"), Triple("I", "ஈ", "ீ"), Triple("U", "ஊ", "ூ"), Triple("E", "ஏ", "ே"), Triple("O", "ஓ", "ோ"),
        Triple("a", "அ", ""), Triple("i", "இ", "ி"), Triple("u", "உ", "ு"), Triple("e", "எ", "ெ"), Triple("o", "ஒ", "ொ"),
    )

    private val CONSONANTS = listOf(
        "ksh" to "க்ஷ",
        "ng" to "ங", "nj" to "ஞ", "gn" to "ஞ", "ch" to "ச", "sh" to "ஷ", "zh" to "ழ", "th" to "த", "dh" to "த",
        "k" to "க", "g" to "க", "c" to "ச", "s" to "ச", "j" to "ஜ", "h" to "ஹ",
        "t" to "ட", "T" to "ட", "d" to "த", "D" to "ட", "N" to "ண", "n" to "ன",
        "p" to "ப", "b" to "ப", "f" to "ப", "m" to "ம", "y" to "ய", "r" to "ர", "R" to "ற",
        "l" to "ல", "L" to "ள", "z" to "ழ", "v" to "வ", "w" to "வ", "q" to "க", "x" to "க்ஸ",
    )

    private const val PULLI = "்"

    private fun word(w: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < w.length) {
            val c = CONSONANTS.firstOrNull { w.startsWith(it.first, i) }
            if (c != null) {
                i += c.first.length
                // Word-initial n is ந, as in நான், நீர்; the search folds ந/ன anyway.
                val letter = if (c.second == "ன" && out.isEmpty()) "ந" else c.second
                val v = VOWELS.firstOrNull { w.startsWith(it.first, i) }
                if (v != null) {
                    i += v.first.length
                    out.append(letter).append(v.third)
                } else {
                    out.append(letter).append(PULLI)
                }
                continue
            }
            val v = VOWELS.firstOrNull { w.startsWith(it.first, i) }
            if (v != null) {
                i += v.first.length
                out.append(v.second)
                continue
            }
            i++ // anything else (an apostrophe, a stray digit) is dropped
        }
        return out.toString()
    }

    private val LATIN_ONLY = Regex("^[A-Za-z\\s'’-]+$")

    /** Latin letters only (with spaces, hyphens, apostrophes): a candidate for transliteration. */
    fun isRomanised(q: String): Boolean = q.any { it in 'A'..'Z' || it in 'a'..'z' } && LATIN_ONLY.matches(q.trim())

    /** "anbu kirubai" → "அன்பு கிருபை". Lower-case input gives the unmarked letters. */
    fun toTamil(q: String): String =
        q.trim().split(Regex("\\s+")).map { word(it.replace(Regex("['’-]"), "")) }.filter { it.isNotEmpty() }.joinToString(" ")
}
