package com.tamilscripture.feature.reader

import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.Note
import com.tamilscripture.core.model.Span
import com.tamilscripture.core.model.VerseId

/** One row of the reader. The design lays text out one verse per block (1B). */
sealed interface ReaderItem {
    val key: String

    data class Heading(override val key: String, val text: String) : ReaderItem

    /** Non-verse text such as a psalm's superscription. */
    data class Descriptive(override val key: String, val text: String) : ReaderItem

    data class Verse(
        override val key: String,
        val verse: Int,
        val label: String,
        val text: String,
        val wj: List<Span>,
        val notes: List<Note>,
        val poetry: Boolean,
    ) : ReaderItem

    /** Dual view (A-3.3): one verse in both versions; a null side lacks the verse (A-3.4). */
    data class Dual(
        override val key: String,
        val verse: Int,
        val label: String,
        val a: Verse?,
        val b: Verse?,
    ) : ReaderItem
}

/** The verse an item shows, for scrolling, selection and read tracking. */
val ReaderItem.verseNumber: Int?
    get() = when (this) {
        is ReaderItem.Verse -> verse
        is ReaderItem.Dual -> verse
        else -> null
    }

/**
 * Aligns two versions' items verse by verse, as the website's DualChapter does: rows
 * follow verse number, so a verse only one version has lands in place with a dash on
 * the other side. Headings come from the first version.
 */
fun dualItems(a: List<ReaderItem>, b: List<ReaderItem>): List<ReaderItem> {
    val second = b.filterIsInstance<ReaderItem.Verse>().associateBy { it.verse }
    val first = a.filterIsInstance<ReaderItem.Verse>().associateBy { it.verse }
    val headingsBefore = HashMap<Int, MutableList<ReaderItem>>()
    var pending = mutableListOf<ReaderItem>()
    for (item in a) {
        when (item) {
            is ReaderItem.Verse -> if (pending.isNotEmpty()) { headingsBefore[item.verse] = pending; pending = mutableListOf() }
            is ReaderItem.Heading, is ReaderItem.Descriptive -> pending += item
            else -> Unit
        }
    }
    val out = ArrayList<ReaderItem>()
    for (v in (first.keys + second.keys).sorted()) {
        headingsBefore[v]?.let { out += it }
        val x = first[v]
        val y = second[v]
        out += ReaderItem.Dual("p$v", v, x?.label ?: y?.label ?: v.toString(), x, y)
    }
    return out + pending
}

/** Flattens the chapter's blocks into headings and whole verses, joining poetry lines with line breaks. */
fun Chapter.toItems(showHeadings: Boolean): List<ReaderItem> {
    val out = ArrayList<ReaderItem>()
    var current: ReaderItem.Verse? = null
    blocks.forEachIndexed { bi, block ->
        if (block.isHeading) {
            current?.let { out += it; current = null }
            if (showHeadings && !block.text.isNullOrBlank()) out += ReaderItem.Heading("h$bi", block.text!!)
            return@forEachIndexed
        }
        block.segments.forEachIndexed { si, seg ->
            val vid = seg.id?.let(VerseId::parse)
            if (vid == null) {
                current?.let { out += it; current = null }
                out += ReaderItem.Descriptive("d$bi.$si", seg.text)
                return@forEachIndexed
            }
            val cur = current
            if (cur != null && cur.verse == vid.verse) {
                val sep = if (block.isPoetry) "\n" else " "
                val shift = cur.text.length + sep.length
                current = cur.copy(
                    text = cur.text + sep + seg.text,
                    wj = cur.wj + seg.spans.filter { it.kind == "wj" }.map { it.copy(start = it.start + shift, end = it.end + shift) },
                    notes = cur.notes + seg.notes.map { it.copy(at = it.at + shift) },
                    poetry = cur.poetry || block.isPoetry,
                )
            } else {
                cur?.let { out += it }
                current = ReaderItem.Verse(
                    key = "v${vid.verse}",
                    verse = vid.verse,
                    label = seg.n ?: vid.verse.toString(),
                    text = seg.text,
                    wj = seg.spans.filter { it.kind == "wj" },
                    notes = seg.notes,
                    poetry = block.isPoetry,
                )
            }
        }
    }
    current?.let { out += it }
    return out
}
