package com.tamilscripture.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `commentary/latest.json`. */
@Serializable
data class CommentaryLatest(val version: String)

/** `commentary/{version}/index.json`: sources and the chapters each covers (0 = book introduction). */
@Serializable
data class CommentaryIndex(
    val sources: List<CommentarySource> = emptyList(),
    val chapters: Map<String, Map<String, List<Int>>> = emptyMap(),
) {
    fun covers(source: String, book: String, chapter: Int): Boolean =
        chapters[source]?.get(book)?.contains(chapter) == true
}

@Serializable
data class CommentarySource(
    val id: String,
    val name: String,
    val short: String = name,
    val year: String = "",
    val title: String = "",
    @SerialName("desc_ta") val descTa: String = "",
    @SerialName("desc_en") val descEn: String = "",
    val licence: String = "",
    val attribution: String = "",
)

/** `commentary/{version}/{source}/{BOOK}/{ch}.json`. */
@Serializable
data class CommentaryChapter(
    val source: String,
    val book: String,
    val chapter: Int,
    val units: List<CommentaryUnit> = emptyList(),
)

@Serializable
data class CommentaryUnit(
    val id: String,
    /** `JHN.3.1-21` (passage), `JHN.3` (chapter introduction), `JHN` (book). */
    val range: String,
    val kind: String = "passage",
    val title: String? = null,
    @SerialName("title_ta") val titleTa: String? = null,
    val paragraphs: List<CommentaryParagraph> = emptyList(),
) {
    /** First and last verse this unit covers inside its chapter; null for introductions. */
    val verseRange: IntRange? by lazy {
        val parts = range.split('.')
        if (parts.size < 3) return@lazy null
        val v = parts[2]
        val start = v.substringBefore('-').toIntOrNull() ?: return@lazy null
        val endPart = v.substringAfter('-', "")
        // "36-4.2" crosses into the next chapter: the unit runs to this chapter's end.
        val end = when {
            endPart.isEmpty() -> start
            endPart.contains('.') -> Int.MAX_VALUE
            else -> endPart.toIntOrNull() ?: start
        }
        start..end
    }

    val isIntroduction: Boolean get() = kind != "passage"
}

@Serializable
data class CommentaryParagraph(
    val id: String,
    val text: String,
    val ta: String? = null,
    @SerialName("ta_source") val taSource: String? = null,
    val heading: Boolean = false,
    val anchor: String? = null,
    @SerialName("anchor_ta") val anchorTa: String? = null,
    val verse: Int? = null,
    val label: String? = null,
    val footnote: Boolean = false,
    val refs: List<CommentaryRef> = emptyList(),
) {
    fun text(preferTamil: Boolean): String = if (preferTamil && !ta.isNullOrBlank()) ta else text
    fun anchor(preferTamil: Boolean): String? = if (preferTamil && !anchorTa.isNullOrBlank()) anchorTa else anchor
}

@Serializable
data class CommentaryRef(val text: String, val ref: String)
