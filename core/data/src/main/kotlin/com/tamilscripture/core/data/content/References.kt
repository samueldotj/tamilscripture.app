package com.tamilscripture.core.data.content

import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.ContentManifest
import uniffi.ts_mobile.parseReference
import uniffi.ts_mobile.suggestBooks

/**
 * Reference parsing and book suggestions through the website's own Rust `bible-ref`
 * (via ts-mobile), so `jn3.16`, `யோவான் 3:16` and `சங் ௨௩` resolve exactly as on the site
 * (A-2.2, A-4.5).
 */
object References {
    data class Ref(val book: Book, val chapter: Int, val verse: Int?, val verseEnd: Int?)

    fun parse(input: String, manifest: ContentManifest?): Ref? {
        if (manifest == null || input.isBlank()) return null
        val r = parseReference(input.trim()) ?: return null
        val book = manifest.book(r.book) ?: return null
        val ch = r.chapter.toInt()
        if (ch !in 1..book.chapters) return null
        return Ref(book, ch, r.verse?.toInt(), r.verseEnd?.toInt())
    }

    fun suggest(prefix: String, manifest: ContentManifest?, limit: Int = 6): List<Book> {
        if (manifest == null || prefix.isBlank()) return emptyList()
        return suggestBooks(prefix.trim(), limit.toUInt()).mapNotNull(manifest::book)
    }
}
