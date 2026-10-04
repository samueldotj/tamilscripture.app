package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamilscripture.core.data.user.UserData
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.designsystem.theme.TsColors
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.UserNote
import com.tamilscripture.core.services.tr

/** What the reader has put on one verse (M6): a highlight, notes, a bookmark. */
data class VerseMarks(
    val color: HighlightColor? = null,
    val notes: List<UserNote> = emptyList(),
    val bookmarked: Boolean = false,
)

/** The marks of one chapter, by verse. */
fun UserData.marksFor(book: String, chapter: Int): Map<Int, VerseMarks> {
    val out = HashMap<Int, VerseMarks>()
    fun at(v: Int) = out.getOrPut(v) { VerseMarks() }
    for (h in highlights) if (h.book == book && h.chapter == chapter) for (v in h.verseStart..h.verseEnd) out[v] = at(v).copy(color = HighlightColor.of(h.color))
    // A note shows under the last verse it covers.
    for (n in notes.sortedBy { it.updatedAt }) if (n.book == book && n.chapter == chapter) out[n.verseEnd] = at(n.verseEnd).let { it.copy(notes = it.notes + n) }
    for (b in bookmarks) if (b.book == book && b.chapter == chapter) out[b.verse] = at(b.verse).copy(bookmarked = true)
    return out
}

fun TsColors.highlight(color: HighlightColor): Color = when (color) {
    HighlightColor.Yellow -> hlYellow
    HighlightColor.Green -> hlGreen
    HighlightColor.Blue -> hlBlue
    HighlightColor.Pink -> hlPink
}

private fun colorName(color: HighlightColor, tamil: Boolean) = when (color) {
    HighlightColor.Yellow -> if (tamil) "மஞ்சள்" else "Yellow"
    HighlightColor.Green -> if (tamil) "பச்சை" else "Green"
    HighlightColor.Blue -> if (tamil) "நீலம்" else "Blue"
    HighlightColor.Pink -> if (tamil) "இளஞ்சிவப்பு" else "Pink"
}

/** The four highlight colours, and removing it (the website's palette). */
@Composable
fun HighlightSheet(reference: String, current: HighlightColor?, tamil: Boolean, onPick: (HighlightColor?) -> Unit, onDismiss: () -> Unit) {
    val c = Ts.colors
    TsSheet(onDismiss) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Kicker(tr("முனைப்பு", "Highlight") + " · " + reference)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                HighlightColor.entries.forEach { color ->
                    val selected = color == current
                    Box(
                        Modifier.size(48.dp).background(c.highlight(color), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) c.accent else c.line2, CircleShape)
                            .clickable(role = Role.RadioButton) { onPick(color) }
                            .semantics { contentDescription = colorName(color, tamil) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(TsIcons.Check, null, Modifier.size(20.dp), tint = c.ink)
                    }
                }
            }
            if (current != null) TsPillButton(tr("முனைப்பை நீக்கு", "Remove highlight"), { onPick(null) }, style = PillStyle.Outlined)
        }
    }
}

/** Writing or changing a note on the selected verses. */
@Composable
fun NoteSheet(reference: String, note: UserNote?, onSave: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Ts.colors
    var text by rememberSaveable(note?.id) { mutableStateOf(note?.body.orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TsSheet(onDismiss) {
        Column(Modifier.imePadding().padding(start = 22.dp, end = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Kicker(tr("குறிப்பு", "Note") + " · " + reference)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 120.dp).background(c.surface2, RoundedCornerShape(14.dp))
                    .border(1.dp, c.line2, RoundedCornerShape(14.dp)).padding(14.dp),
            ) {
                if (text.isEmpty()) Text(tr("உங்கள் எண்ணங்கள்…", "Your thoughts…"), style = Ts.type.body, color = c.faint)
                BasicTextField(
                    text, { if (it.length <= 5000) text = it },
                    textStyle = Ts.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TsPillButton(tr("சேமி", "Save"), { if (text.isNotBlank()) onSave(text.trim()) }, style = PillStyle.Filled)
                if (onDelete != null) TsPillButton(tr("நீக்கு", "Delete"), onDelete, style = PillStyle.Outlined)
            }
        }
    }
}

/** A note under its verse in the text; tapping it opens it. */
@Composable
fun NoteChip(note: UserNote, onOpen: () -> Unit) {
    val c = Ts.colors
    Row(
        Modifier.padding(start = 22.dp, end = 10.dp, top = 2.dp, bottom = 4.dp).fillMaxWidth()
            .background(c.surface2, RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(TsIcons.Note, null, Modifier.size(16.dp).padding(top = 2.dp), tint = c.accent)
        Text(note.body, style = Ts.type.caption, color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
