package com.tamilscripture.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.UserNote

/** Where a verse's text lies in a paragraph's string. */
private class VerseRange(val verse: Int, val start: Int, val end: Int)

/**
 * A paragraph in the Reader and Standard formats (the website's fmt-reader and
 * fmt-standard): verses flow on as in a printed Bible. Standard shows small raised verse
 * numbers and footnote letters; Reader shows neither. A tap selects the verse under it;
 * selection, the verse being read aloud, highlights and heat colour that verse's words.
 */
@Composable
fun ParagraphText(
    item: ReaderItem.Para,
    numbers: Boolean,
    selection: List<Int>,
    playingVerse: Int?,
    marks: Map<Int, VerseMarks>,
    heat: Map<Int, Int>,
    fontSize: Int,
    lineHeightEm: Float,
    showNotes: Boolean,
    textLocale: LocaleList?,
    notesInMargin: Boolean,
    onTapVerse: (Int) -> Unit,
    onOpenNote: (UserNote) -> Unit,
) {
    val c = Ts.colors
    val footnotes = ArrayList<String>()
    val ranges = ArrayList<VerseRange>()
    val text = buildAnnotatedString {
        // Code points of each verse already laid out, for word-range highlights (offsets
        // count the verse's text with its parts joined by one character, as packs join them).
        val consumed = HashMap<Int, Int>()
        for (r in item.runs) {
            val v = r.verse
            if (v != null && r.label != null && numbers) {
                val bookmarked = marks[v]?.bookmarked == true
                withStyle(
                    SpanStyle(
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, baselineShift = BaselineShift.Superscript,
                        color = if (bookmarked) c.onAccent else c.accent, background = if (bookmarked) c.accent else Color.Unspecified,
                    ),
                ) { append(r.label) }
                append(' ')
            }
            val start = length
            append(r.text)
            if (v != null) {
                ranges += VerseRange(v, start, length)
                val before = consumed[v] ?: if (r.label != null) 0 else -1
                marks[v]?.words?.takeIf { before >= 0 }?.forEach { w ->
                    val from = (w.from - before).coerceAtLeast(0)
                    val to = (w.to.toLong() - before).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val a = r.text.clusterStart(r.text.offsetByCodePointsSafe(from))
                    val b = r.text.clusterEnd(r.text.offsetByCodePointsSafe(to))
                    if (to > 0 && b > a) addStyle(SpanStyle(background = c.highlight(w.color)), start + a, start + b)
                }
                if (before >= 0) consumed[v] = before + r.text.codePointCount(0, r.text.length) + 1
                if (numbers && showNotes) r.notes.forEach { n ->
                    withStyle(SpanStyle(fontSize = 11.sp, color = c.muted, baselineShift = BaselineShift.Superscript)) {
                        append(" " + ('a' + footnotes.size))
                    }
                    footnotes += n.text
                }
            }
            if (r.text != "\n" && r.text != " " && r !== item.runs.last()) append(' ')
        }
        // Whole-verse colours under the words: selection first, then reading aloud, highlight, heat.
        for (r in ranges) {
            val bg = when {
                r.verse in selection -> c.verseSelected
                r.verse == playingVerse -> c.accentWash
                else -> marks[r.verse]?.color?.let { c.highlight(it) }
                    ?: heat[r.verse]?.takeIf { it > 0 }?.let { c.accent.copy(alpha = HEAT_TINTS[it]) }
            } ?: continue
            addStyle(SpanStyle(background = bg), r.start, r.end)
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap by rememberUpdatedState(onTapVerse)
    val hit by rememberUpdatedState(ranges)
    val notes = item.verses.flatMap { marks[it]?.notes.orEmpty() }.distinctBy { it.id }

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = Ts.type.scripture(fontSize.sp, lineHeightEm).copy(localeList = textLocale),
                color = c.ink,
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .pointerInput(item.key) {
                        detectTapGestures { pos ->
                            val at = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                            hit.firstOrNull { at >= it.start && at <= it.end }?.let { tap(it.verse) }
                        }
                    },
            )
            if (footnotes.isNotEmpty()) {
                Column(Modifier.padding(start = 22.dp, end = 10.dp, bottom = 4.dp)) {
                    footnotes.forEachIndexed { i, n -> Text("${'a' + i}  $n", style = Ts.type.caption, color = c.muted) }
                }
            }
            if (!notesInMargin) notes.forEach { n -> NoteChip(n) { onOpenNote(n) } }
        }
        if (notesInMargin) {
            Column(Modifier.width(220.dp).padding(start = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                notes.forEach { n -> MarginNote(n) { onOpenNote(n) } }
            }
        }
    }
}

private val HEAT_TINTS = floatArrayOf(0f, 0.08f, 0.16f, 0.26f, 0.38f)
