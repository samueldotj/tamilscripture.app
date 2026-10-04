package com.tamilscripture.core.model

import kotlinx.serialization.Serializable

enum class UiLang(val code: String) { Tamil("ta"), English("en") }

/** `JHN.3.16`. Personal data and stats key on this, never on a version (website ADR-5). */
@Serializable
data class VerseId(val book: String, val chapter: Int, val verse: Int) : Comparable<VerseId> {
    override fun toString() = "$book.$chapter.$verse"
    override fun compareTo(other: VerseId): Int =
        compareValuesBy(this, other, { it.book }, { it.chapter }, { it.verse })

    companion object {
        /** Parses `JHN.3.16`, and `JHN.3.16-18` to its first verse. Returns null for anything else. */
        fun parse(id: String): VerseId? {
            val parts = id.split('.')
            if (parts.size < 3) return null
            val verse = parts[2].takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            val chapter = parts[1].toIntOrNull() ?: return null
            return VerseId(parts[0], chapter, verse)
        }
    }
}

/** A place in the Bible the reader can open: a chapter, optionally scrolled to a verse. */
@Serializable
data class Passage(
    val version: String,
    val book: String,
    val chapter: Int,
    val verse: Int? = null,
) {
    fun chapterKey() = "$version/$book/$chapter"
}

/** "யோவான் 3:16", "John 3:16–18". */
fun Book.label(lang: UiLang, chapter: Int? = null, verse: Int? = null, verseEnd: Int? = null): String = buildString {
    append(name(lang))
    if (chapter != null) {
        append(' ').append(chapter)
        if (verse != null) {
            append(':').append(verse)
            if (verseEnd != null && verseEnd != verse) append('–').append(verseEnd)
        }
    }
}
