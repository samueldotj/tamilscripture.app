package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The reader's own data (roadmap M6), shaped like the website's tables so a row moves
// between the phone and Supabase unchanged. Whole-verse rows leave the word-range fields
// null; rows the website made for part of a verse keep them, and the app shows them as
// the whole verse.

enum class HighlightColor(val key: String) {
    Yellow("yellow"), Green("green"), Blue("blue"), Pink("pink");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Yellow
    }
}

@Serializable
data class Highlight(
    val id: String,
    val book: String,
    val chapter: Int,
    @SerialName("verse_start") val verseStart: Int,
    @SerialName("verse_end") val verseEnd: Int,
    val color: String = "yellow",
    val version: String? = null,
    @SerialName("char_start") val charStart: Int? = null,
    @SerialName("char_end") val charEnd: Int? = null,
    val quote: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun covers(verse: Int) = verse in verseStart..verseEnd
}

@Serializable
data class UserNote(
    val id: String,
    val book: String,
    val chapter: Int,
    @SerialName("verse_start") val verseStart: Int,
    @SerialName("verse_end") val verseEnd: Int,
    val body: String,
    val version: String? = null,
    @SerialName("char_start") val charStart: Int? = null,
    @SerialName("char_end") val charEnd: Int? = null,
    val quote: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun covers(verse: Int) = verse in verseStart..verseEnd
}

/** One per verse (`public.bookmarks`); [version] is the one it was made in, empty when unknown. */
@Serializable
data class Bookmark(
    val id: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val version: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class Visit(
    val id: Long = 0,
    val book: String,
    val chapter: Int,
    @SerialName("verse_start") val verseStart: Int? = null,
    @SerialName("verse_end") val verseEnd: Int? = null,
    val version: String,
    @SerialName("visited_at") val visitedAt: String,
)

/** Whole verses [verses] as rows of contiguous runs, as the website writes them. */
fun contiguousRuns(verses: Collection<Int>): List<IntRange> {
    val sorted = verses.toSortedSet().toList()
    if (sorted.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    var start = sorted[0]
    var prev = sorted[0]
    for (v in sorted.drop(1)) {
        if (v == prev + 1) { prev = v; continue }
        out += start..prev
        start = v
        prev = v
    }
    out += start..prev
    return out
}
