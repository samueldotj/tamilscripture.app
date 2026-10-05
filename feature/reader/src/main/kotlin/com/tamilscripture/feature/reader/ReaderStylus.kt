package com.tamilscripture.feature.reader

import android.icu.text.BreakIterator
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult

/** A stretch of a verse marked with the stylus: code points [start, end) of its text. */
data class PenStroke(val start: Int, val end: Int, val erase: Boolean)

/**
 * Stylus highlighting (M8-9): drawn across a verse, the pen marks the words it passes over
 * (snapped to whole words) and [onCommit] saves them when it lifts; the eraser end, or the
 * pen with its side button held, removes. Fingers and the mouse are left to the verse's own
 * tap and selection.
 *
 * [layout] gives the verse's text layout, [textStart] where the verse's own text begins in
 * it (after the number), [text] that text.
 */
fun Modifier.stylusMarks(
    key: Any,
    layout: () -> TextLayoutResult?,
    textStart: Int,
    text: String,
    onPreview: (PenStroke?) -> Unit,
    onCommit: (PenStroke) -> Unit,
): Modifier = pointerInput(key, textStart, text) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val type = down.type
        if (type != PointerType.Stylus && type != PointerType.Eraser) return@awaitEachGesture
        val erase = type == PointerType.Eraser || currentEvent.buttons.isSecondaryPressed
        down.consume()
        fun at(p: Offset): Int? {
            val l = layout() ?: return null
            val o = l.getOffsetForPosition(p) - textStart
            return o.coerceIn(0, text.length)
        }
        val anchor = at(down.position) ?: return@awaitEachGesture
        var last = anchor
        var stroke: PenStroke? = null
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            ch.consume()
            at(ch.position)?.let { last = it }
            stroke = wordStroke(text, minOf(anchor, last), maxOf(anchor, last), erase)
            onPreview(stroke)
        }
        onPreview(null)
        (stroke ?: wordStroke(text, anchor, anchor, erase))?.let(onCommit)
    }
}

/** UTF-16 [from, to] widened to whole words, as code points; null when it covers no letters. */
internal fun wordStroke(text: String, from: Int, to: Int, erase: Boolean): PenStroke? {
    if (text.isEmpty()) return null
    val words = BreakIterator.getWordInstance().also { it.setText(text) }
    val a = if (from >= text.length || words.isBoundary(from)) from.coerceAtMost(text.length) else words.preceding(from)
    var b = if (to >= text.length) text.length else if (words.isBoundary(to) && to > a) to else words.following(to)
    if (b == BreakIterator.DONE) b = text.length
    val start = a.coerceIn(0, text.length)
    val end = b.coerceIn(start, text.length)
    // Trailing spaces and punctuation stay out of the highlight.
    var e = end
    while (e > start && !Character.isLetterOrDigit(text.codePointBefore(e)) && Character.getType(text.codePointBefore(e)) !in MARKS) e -= Character.charCount(text.codePointBefore(e))
    var s = start
    while (s < e && Character.isWhitespace(text.codePointAt(s))) s += Character.charCount(text.codePointAt(s))
    if (e <= s) return null
    return PenStroke(text.codePointCount(0, s), text.codePointCount(0, e), erase)
}

/** Tamil vowel signs and the virama are marks, part of the letter before them. */
private val MARKS = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt())

/** The words a stroke covers, for the quote kept with the highlight. */
fun PenStroke.quote(text: String): String {
    val a = text.offsetByCodePointsSafe(start)
    val b = text.offsetByCodePointsSafe(end)
    return if (b > a) text.substring(a, b) else ""
}
