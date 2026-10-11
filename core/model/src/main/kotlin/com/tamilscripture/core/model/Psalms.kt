package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * The Psalms explorer's data (website docs/feature_psalms_explorer.md §4), read from
 * `entities/psalms/index.json` and `entities/psalms/{n}.json` of the study.psalms pack.
 */

@Serializable data class PsalmTheme(val id: String, val en: String, val ta: String, val color: String)

@Serializable
data class PsalmGenre(
    val id: String, val en: String, val ta: String, val mood: String = "",
    @SerialName("about_en") val aboutEn: String = "", @SerialName("about_ta") val aboutTa: String = "",
)

@Serializable data class PsalmCollection(val id: String, val en: String, val ta: String, val psalms: List<Int>, val basis: String = "")

@Serializable data class PsalmAuthor(val id: String, val en: String, val ta: String)

/** One row of index.json: `[n, theme, genres, authors, title_ta, title_en, key, tune]`. */
data class Psalm(
    val n: Int,
    val theme: String,
    val genres: List<String>,
    val authors: List<String>,
    val titleTa: String?,
    val titleEn: String?,
    /** "23:1" or "121:1-2" */
    val key: String,
    val tune: String?,
) {
    val keyVerses: IntRange
        get() {
            val vs = key.substringAfter(':')
            val a = vs.substringBefore('-').toIntOrNull() ?: 1
            return a..(vs.substringAfter('-', "").toIntOrNull() ?: a)
        }
}

data class PsalmsIndex(
    val themes: List<PsalmTheme>,
    val genres: List<PsalmGenre>,
    val collections: List<PsalmCollection>,
    val authors: List<PsalmAuthor>,
    val psalms: List<Psalm>,
) {
    fun theme(id: String): PsalmTheme? = themes.firstOrNull { it.id == id }

    companion object {
        fun parse(text: String, json: Json = Json { ignoreUnknownKeys = true }): PsalmsIndex {
            val o = json.parseToJsonElement(text).jsonObject
            fun str(e: kotlinx.serialization.json.JsonElement) = if (e is JsonNull) null else e.jsonPrimitive.content
            fun strs(e: kotlinx.serialization.json.JsonElement) = e.jsonArray.map { it.jsonPrimitive.content }
            return PsalmsIndex(
                themes = json.decodeFromJsonElement(o.getValue("themes")),
                genres = json.decodeFromJsonElement(o.getValue("genres")),
                collections = json.decodeFromJsonElement(o.getValue("collections")),
                authors = json.decodeFromJsonElement(o.getValue("authors")),
                psalms = o.getValue("psalms").jsonArray.map { r ->
                    val a = r as JsonArray
                    Psalm(a[0].jsonPrimitive.int, a[1].jsonPrimitive.content, strs(a[2]), strs(a[3]), str(a[4]), str(a[5]), a[6].jsonPrimitive.content, str(a[7]))
                },
            )
        }
    }
}

@Serializable data class PsalmTune(val id: String, val name: String, val meter: String = "", val key: String = "")

@Serializable data class PsalmNt(val authors: List<String>, val ref: String)

/** psalms/{n}.json: the card's summaries, tags and tunes. */
@Serializable
data class PsalmCardData(
    val n: Int,
    @SerialName("summary_ta") val summaryTa: String? = null,
    @SerialName("summary_en") val summaryEn: String? = null,
    val tags: List<String> = emptyList(),
    val nt: PsalmNt? = null,
    val tunes: List<PsalmTune> = emptyList(),
)

/**
 * The explorer's filters: any value within a facet, every facet that has a selection.
 * Genre and collection ids share [genres]; [ANONYMOUS] is the author chip for a psalm
 * with no named author. Mirrors the website's lib/psalms/filter.ts.
 */
@Serializable
data class PsalmSelection(
    val themes: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = themes.isEmpty() && genres.isEmpty() && authors.isEmpty()

    /** Selected values outside the theme tiles: the Filters button's badge. */
    val panelCount: Int get() = genres.size + authors.size

    fun matcher(index: PsalmsIndex): (Psalm) -> Boolean {
        val inCollection = index.collections.filter { it.id in genres }.flatMap { it.psalms }.toSet()
        return { p ->
            (themes.isEmpty() || p.theme in themes) &&
                (genres.isEmpty() || p.genres.any { it in genres } || p.n in inCollection) &&
                (authors.isEmpty() || p.authors.any { it in authors } || (p.authors.isEmpty() && ANONYMOUS in authors))
        }
    }

    companion object {
        const val ANONYMOUS = "anonymous"
    }
}

/** [this] with [value] added, or removed when already there. */
fun <T> List<T>.toggled(value: T): List<T> = if (value in this) this - value else this + value
