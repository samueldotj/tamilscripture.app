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

    /**
     * A paragraph in the Reader and Standard formats: verses flowing on, as on the website.
     * Consecutive poetry lines are one paragraph with line breaks.
     */
    data class Para(override val key: String, val runs: List<Run>, val poetry: Boolean) : ReaderItem

    /** Dual view (A-3.3): one verse in both versions; a null side lacks the verse (A-3.4). */
    data class Dual(
        override val key: String,
        val verse: Int,
        val label: String,
        val a: Verse?,
        val b: Verse?,
    ) : ReaderItem
}

/**
 * A stretch of one verse inside a paragraph: [label] where the verse starts, null on a
 * continuation; [verse] null on text outside verses. A run of "\n" breaks a poetry line.
 */
data class Run(val verse: Int?, val label: String?, val text: String, val notes: List<Note> = emptyList())

/** The verse an item shows (a paragraph: its first), for scrolling, selection and read tracking. */
val ReaderItem.verseNumber: Int?
    get() = when (this) {
        is ReaderItem.Verse -> verse
        is ReaderItem.Dual -> verse
        is ReaderItem.Para -> runs.firstNotNullOfOrNull { it.verse }
        else -> null
    }

/** Every verse an item shows. */
val ReaderItem.verses: List<Int>
    get() = when (this) {
        is ReaderItem.Para -> runs.mapNotNull { it.verse }.distinct()
        else -> listOfNotNull(verseNumber)
    }

/** The index of the item showing [verse], or -1. */
fun List<ReaderItem>.indexOfVerse(verse: Int): Int = indexOfFirst { it is ReaderItem.Para && verse in it.verses || it.verseNumber == verse }

/**
 * The chapter as paragraphs (Reader and Standard formats): headings, psalm titles, and each
 * paragraph's verses flowing together; consecutive poetry lines join with line breaks.
 */
fun Chapter.toParagraphs(showHeadings: Boolean): List<ReaderItem> {
    val out = ArrayList<ReaderItem>()
    var poetry: MutableList<Run>? = null
    var poetryKey = ""
    fun flushPoetry() {
        poetry?.let { if (it.isNotEmpty()) out += ReaderItem.Para(poetryKey, it.toList(), poetry = true) }
        poetry = null
    }
    blocks.forEachIndexed { bi, block ->
        if (block.isHeading) {
            flushPoetry()
            if (showHeadings && !block.text.isNullOrBlank()) out += ReaderItem.Heading("h$bi", block.text!!)
            return@forEachIndexed
        }
        if (block.segments.none { it.id != null }) {
            flushPoetry()
            block.segments.forEachIndexed { si, seg -> out += ReaderItem.Descriptive("d$bi.$si", seg.text) }
            return@forEachIndexed
        }
        val runs = block.segments.map { seg ->
            val v = seg.id?.let(VerseId::parse)?.verse
            Run(v, seg.n, seg.text, seg.notes)
        }
        if (block.isPoetry) {
            val indent = if (block.style == "q2" || block.style == "q3") "\u2003" else ""
            val p = poetry ?: mutableListOf<Run>().also { poetry = it; poetryKey = "q$bi" }
            if (p.isNotEmpty()) p += Run(null, null, "\n")
            if (indent.isNotEmpty()) p += Run(null, null, indent)
            p += runs
        } else {
            flushPoetry()
            out += ReaderItem.Para("p$bi", runs, poetry = false)
        }
    }
    flushPoetry()
    return out
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
