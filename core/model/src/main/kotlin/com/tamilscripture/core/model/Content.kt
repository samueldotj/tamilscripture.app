package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `content/manifest.json`: the build id, versions and the 66 books. */
@Serializable
data class ContentManifest(
    val build: String,
    @SerialName("default_version") val defaultVersion: String = "IRVTAM",
    val versions: List<BibleVersion> = emptyList(),
    val books: List<Book> = emptyList(),
) {
    fun book(code: String): Book? = books.firstOrNull { it.code == code }
    fun version(code: String): BibleVersion? = versions.firstOrNull { it.code == code }
}

@Serializable
data class BibleVersion(
    val code: String,
    val lang: String = "ta",
    val name: String = code,
    @SerialName("name_native") val nameNative: String? = null,
    val short: String = code,
    val licence: String = "",
    val attribution: String = "",
    @SerialName("source_url") val sourceUrl: String? = null,
    val order: Int = 0,
    val default: Boolean = false,
    val audio: VersionAudio? = null,
)

@Serializable
data class VersionAudio(
    val recording: String,
    val licence: String = "",
    val attribution: String = "",
)

@Serializable
data class Book(
    val code: String,
    val order: Int,
    val testament: String,
    val chapters: Int,
    val slug: String = code.lowercase(),
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_ta") val nameTa: String,
    @SerialName("abbr_en") val abbrEn: List<String> = emptyList(),
    @SerialName("abbr_ta") val abbrTa: List<String> = emptyList(),
) {
    val isNewTestament: Boolean get() = testament == "NT"
    fun name(lang: UiLang): String = if (lang == UiLang.Tamil) nameTa else nameEn
}

/** `content/{build}/{VERSION}/{BOOK}/{ch}.json`. Also the body of a Bible pack's `chapter` row (ADR-4). */
@Serializable
data class Chapter(
    val version: String,
    val book: String,
    val chapter: Int,
    val build: String = "",
    val label: String = "",
    val blocks: List<Block> = emptyList(),
    val prev: ChapterRef? = null,
    val next: ChapterRef? = null,
    val audio: ChapterAudio? = null,
    val bridges: Map<String, String> = emptyMap(),
) {
    /** Verse numbers in reading order (bridges and split verses counted once). */
    val verseNumbers: List<Int> by lazy {
        blocks.flatMap { b -> b.segments.mapNotNull { it.id?.let(VerseId::parse)?.verse } }.distinct()
    }

    fun verseText(verse: Int): String = blocks.flatMap { it.segments }
        .filter { it.id?.let(VerseId::parse)?.verse == verse }
        .joinToString(" ") { it.text }
}

@Serializable
data class ChapterRef(val book: String, val chapter: Int)

@Serializable
data class ChapterAudio(
    val src: String,
    val ms: Long = 0,
    val timed: Boolean = false,
)

@Serializable
data class Block(
    /** "heading" or "para". */
    val type: String,
    /** USFM paragraph marker: p, q1, q2, d, m, pi… */
    val style: String? = null,
    /** Heading kind: s, s1, ms, mr… */
    val kind: String? = null,
    val level: Int = 1,
    val text: String? = null,
    val segments: List<Segment> = emptyList(),
) {
    val isHeading: Boolean get() = type == "heading"
    val isPoetry: Boolean get() = style?.startsWith("q") == true
    /** Psalm superscription ("தாவீதின் பாடல்."). */
    val isDescriptive: Boolean get() = style == "d"
    val indent: Int get() = style?.drop(1)?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
}

@Serializable
data class Segment(
    /** Verse id `JHN.3.16`; absent on non-verse text such as a psalm title. */
    val id: String? = null,
    /** Verse number shown, present only where the verse starts. */
    val n: String? = null,
    val text: String,
    val spans: List<Span> = emptyList(),
    val notes: List<Note> = emptyList(),
)

@Serializable
data class Span(val kind: String, val start: Int, val end: Int)

@Serializable
data class Note(
    val kind: String = "f",
    /** Character offset in the segment text where the caller sits. */
    val at: Int = 0,
    val caller: String = "+",
    val reference: String? = null,
    val text: String,
)

/** `content/{build}/xref/{BOOK}/{ch}.json`: verse id → cross-references, best first. */
@Serializable
data class CrossRef(val to: String, val end: String? = null, val votes: Int = 0)

/** `content/{build}/{VERSION}/{BOOK}/{ch}.audio.json`. */
@Serializable
data class AudioTimings(val verses: List<List<Long>> = emptyList()) {
    /** Start of [verse] in ms, or null when the chapter has no timing for it. */
    fun startOf(verse: Int): Long? = verses.firstOrNull { it.firstOrNull()?.toInt() == verse }?.getOrNull(1)

    /** The verse being read at [positionMs]. */
    fun verseAt(positionMs: Long): Int? = verses.lastOrNull { (it.getOrNull(1) ?: Long.MAX_VALUE) <= positionMs }?.firstOrNull()?.toInt()
}

/** `/api/search` response from the website. */
@Serializable
data class SearchResponse(
    val query: String = "",
    val exact: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SearchHit(
    @SerialName("verse_id") val verseId: String,
    val version: String,
    val text: String,
    val rank: Double = 0.0,
    @SerialName("book_ord") val bookOrder: Int = 0,
)
