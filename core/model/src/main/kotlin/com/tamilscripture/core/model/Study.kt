package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The website's study data (entities/), as the app reads it from a study pack or online
// (roadmap M8). Only the fields the app shows; the rest is ignored when decoding.

/** `original/{BOOK}/{ch}.json`: each word is [text, transliteration, gloss, strongs, morphology]. */
@Serializable
data class OriginalChapter(
    val book: String,
    val chapter: Int,
    val lang: String,
    val verses: Map<String, List<List<String>>> = emptyMap(),
) {
    fun words(verse: Int): List<OriginalWord> = verses[verse.toString()].orEmpty().map {
        OriginalWord(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }, it.getOrElse(2) { "" }, it.getOrElse(3) { "" }, it.getOrElse(4) { "" })
    }
}

data class OriginalWord(val text: String, val translit: String, val gloss: String, val strongs: String, val morph: String)

@Serializable
data class StrongsName(
    val id: String,
    val kind: String,
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("name_ta") val nameTa: String = "",
)

/** `strongs/{num}.json`. [verses] holds delta-encoded verse keys (book order × 1e6 + chapter × 1e3 + verse). */
@Serializable
data class StrongsEntry(
    @SerialName("s") val number: String,
    val script: String = "",
    val lemma: String = "",
    val translit: String = "",
    val pos: String = "",
    val gloss: String = "",
    val def: String = "",
    val count: Int = 0,
    @SerialName("v") val verses: List<Long> = emptyList(),
    @SerialName("gloss_ta") val glossTa: String? = null,
    val names: List<StrongsName> = emptyList(),
    val renderings: List<String> = emptyList(),
) {
    /** Verse keys in order, decoded from the deltas. */
    val verseKeys: List<Long> by lazy {
        var acc = 0L
        verses.map { acc += it; acc }
    }

    companion object {
        /** H1121a is stored as H1121_a.json, as the website writes it. */
        fun fileName(number: String): String =
            number.indexOfFirst { it.isLowerCase() }.let { i -> if (i < 0) number else number.substring(0, i) + "_" + number.substring(i) }
    }
}

@Serializable
data class MentionPerson(
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("name_ta") val nameTa: String = "",
    val brief: String = "",
    val mentions: Int = 0,
    val qualifier: String? = null,
)

@Serializable
data class MentionPlace(
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("name_ta") val nameTa: String = "",
    val type: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val mentions: Int = 0,
    val qualifier: String? = null,
)

@Serializable
data class VerseMentions(val verse: String, val people: List<String> = emptyList(), val places: List<String> = emptyList())

/** `mentions/{BOOK}/{ch}.json`: who and where a chapter names, and in which verses. */
@Serializable
data class ChapterMentions(
    val book: String,
    val chapter: Int,
    val map: Boolean = false,
    val people: Map<String, MentionPerson> = emptyMap(),
    val places: Map<String, MentionPlace> = emptyMap(),
    val verses: List<VerseMentions> = emptyList(),
)

@Serializable
data class TamilName(val label: String = "")

@Serializable
data class ArticleParagraph(val id: String = "", val text: String = "", val ta: String? = null)

/** A dictionary article a person or place links to; open it with its [id] (`source/slug`). */
@Serializable
data class EmbeddedArticle(val source: String = "", val id: String = "", val title: String = "", val paragraphs: Int = 0)

@Serializable
data class EntitySource(val name: String = "", val url: String = "", val licence: String = "", val attribution: String = "")

/** `person/{id}.json`. */
@Serializable
data class Person(
    val id: String,
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("tamil_name") val tamilName: TamilName? = null,
    val gender: String? = null,
    val tribe: String? = null,
    val summary: String? = null,
    val brief: String? = null,
    val short: String? = null,
    /** A longer English account of the person, when written. */
    val article: String? = null,
    val description: String? = null,
    val verses: List<String> = emptyList(),
    val articles: List<EmbeddedArticle> = emptyList(),
    val source: EntitySource? = null,
)

@Serializable
data class Geo(val lat: Double? = null, val lon: Double? = null, val precision: String? = null)

@Serializable
data class PlaceDescription(val brief: String? = null, val short: String? = null, val article: String? = null)

@Serializable
data class NearbyPlace(val id: String, @SerialName("name_en") val nameEn: String = "", @SerialName("name_ta") val nameTa: String = "")

/** `place/{id}.json`. */
@Serializable
data class Place(
    val id: String,
    @SerialName("name_en") val nameEn: String = "",
    @SerialName("tamil_name") val tamilName: TamilName? = null,
    @SerialName("place_type") val placeType: String? = null,
    val geo: Geo? = null,
    val modern: String? = null,
    val description: PlaceDescription? = null,
    val verses: List<String> = emptyList(),
    val nearby: List<NearbyPlace> = emptyList(),
    val articles: List<EmbeddedArticle> = emptyList(),
    val source: EntitySource? = null,
)

/** `articles/{source}/{slug}.json`: a dictionary article, Tamil per paragraph when drafted. */
@Serializable
data class Article(
    val source: String,
    val id: String,
    val title: String = "",
    @SerialName("title_ta") val titleTa: String? = null,
    val licence: String = "",
    val attribution: String = "",
    val paragraphs: List<ArticleParagraph> = emptyList(),
)

/** A row of `articles/index.json`. */
@Serializable
data class ArticleIndexEntry(val id: String, val source: String, val title: String)

/** Verse key (book order × 1e6 + chapter × 1e3 + verse) to a verse id, given the books. */
fun verseIdOfKey(key: Long, books: List<Book>): VerseId? {
    val order = (key / 1_000_000).toInt()
    val book = books.firstOrNull { it.order == order } ?: return null
    return VerseId(book.code, ((key / 1000) % 1000).toInt(), (key % 1000).toInt())
}
