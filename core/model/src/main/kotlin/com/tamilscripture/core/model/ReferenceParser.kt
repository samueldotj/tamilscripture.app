package com.tamilscripture.core.model

/**
 * Interim reference parser for the search box (A-2.2, A-4.1). Recognises English and
 * Tamil book names and abbreviations from the manifest, Tamil numerals, and `:`, `.`,
 * `,` or a space between chapter and verse. To be replaced by the shared Rust
 * `bible-ref` crate through `ts-mobile` (roadmap M0-6) so web and app parse identically.
 */
class ReferenceParser(books: List<Book>) {
    data class Ref(val book: Book, val chapter: Int, val verse: Int?, val verseEnd: Int?)

    private val names: List<Pair<String, Book>> = books.flatMap { b ->
        (listOf(b.nameEn, b.nameTa, b.slug, b.code) + b.abbrEn + b.abbrTa).map { norm(it) to b }
    }.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.length }

    fun parse(input: String): Ref? {
        val s = digits(input.trim())
        if (s.isEmpty()) return null
        val m = TAIL.find(s) ?: return null
        val bookPart = norm(s.substring(0, m.range.first))
        if (bookPart.isEmpty()) return null
        val book = names.firstOrNull { it.first == bookPart }?.second
            ?: names.firstOrNull { it.first.startsWith(bookPart) && bookPart.length >= 2 }?.second
            ?: return null
        val ch = m.groupValues[1].toIntOrNull() ?: return null
        if (ch !in 1..book.chapters) return null
        val v = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val e = m.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
        return Ref(book, ch, v, e)
    }

    private companion object {
        val TAIL = Regex("""\s*(\d{1,3})(?:\s*[:.,\s]\s*(\d{1,3})(?:\s*[-–]\s*(\d{1,3}))?)?\s*$""")

        fun norm(s: String): String = s.lowercase().filter { !it.isWhitespace() && it != '.' }

        /** Tamil numerals ௦–௯ count as digits. */
        fun digits(s: String): String = buildString(s.length) {
            for (ch in s) append(if (ch in '௦'..'௯') '0' + (ch - '௦') else ch)
        }
    }
}
