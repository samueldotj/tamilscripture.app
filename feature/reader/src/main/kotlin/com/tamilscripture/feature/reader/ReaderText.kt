package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentaryUnit
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import com.tamilscripture.core.model.UiLang
import kotlinx.coroutines.delay

/** A verse's text with its superscript number, in the design's 1B style. */
@Composable
fun verseAnnotated(item: ReaderItem.Verse, numberSize: Int = 12, showNotes: Boolean): AnnotatedString {
    val c = Ts.colors
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = numberSize.sp, color = c.accent, fontWeight = FontWeight.Bold, baselineShift = BaselineShift.Superscript)) {
            append(item.label)
        }
        append(' ')
        append(item.text)
        if (showNotes) {
            item.notes.forEachIndexed { i, _ ->
                withStyle(SpanStyle(fontSize = 11.sp, color = c.muted, baselineShift = BaselineShift.Superscript)) {
                    append(" " + ('a' + i))
                }
            }
        }
    }
}

/**
 * The scrolling chapter text. Inline commentary cards (1C) appear after the last verse of
 * the unit they explain. [onVerseRead] fires once per verse per chapter visit when the
 * verse has been at least 60% visible for 2 s (requirements §5.1).
 */
@Composable
fun ReaderTextList(
    items: List<ReaderItem>,
    selection: List<Int>,
    playingVerse: Int?,
    fontSize: Int,
    lineHeightEm: Float,
    showNotes: Boolean,
    commentary: CommentaryChapter?,
    commentaryName: String?,
    listState: LazyListState,
    chapterKey: String,
    contentPadding: PaddingValues,
    onTapVerse: (Int) -> Unit,
    onVerseRead: (Int, Long) -> Unit,
    onOpenCommentary: () -> Unit,
    modifier: Modifier = Modifier,
    sectionHeadingSize: Int = 15,
) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val unitsAfter = remember(commentary, items) { commentaryPlacement(items, commentary) }
    LazyColumn(modifier, state = listState, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is ReaderItem.Heading -> Text(
                    item.text,
                    style = Ts.type.sectionHeading.copy(fontSize = sectionHeadingSize.sp, fontFamily = Ts.type.scripture),
                    color = c.amber,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
                )
                is ReaderItem.Descriptive -> Text(
                    item.text,
                    style = Ts.type.scripture((fontSize - 3).sp, lineHeightEm).copy(fontWeight = FontWeight.SemiBold),
                    color = c.muted,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
                is ReaderItem.Verse -> Column {
                    val selected = item.verse in selection
                    val playing = item.verse == playingVerse
                    Text(
                        verseAnnotated(item, numberSize = 12, showNotes = showNotes),
                        style = Ts.type.scripture(fontSize.sp, lineHeightEm),
                        color = c.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    selected -> c.verseSelected
                                    playing -> c.accentWash
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .then(if (playing && !selected) Modifier.border(1.dp, c.accentSoft, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { onTapVerse(item.verse) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics { contentDescription = "${item.label}. ${item.text}" },
                    )
                    if (showNotes && item.notes.isNotEmpty()) {
                        Column(Modifier.padding(start = 22.dp, end = 10.dp, bottom = 4.dp)) {
                            item.notes.forEachIndexed { i, n ->
                                Text("${'a' + i}  ${n.text}", style = Ts.type.caption, color = c.muted)
                            }
                        }
                    }
                    unitsAfter[item.verse]?.forEach { unit ->
                        InlineCommentaryCard(unit, commentaryName ?: "", preferTamil = lang == UiLang.Tamil, onOpen = onOpenCommentary)
                    }
                }
            }
        }
    }

    // Verse-read tracking (requirements §5.1, design §6.6).
    val readNow by rememberUpdatedState(onVerseRead)
    val visibleSince = remember(chapterKey) { mutableStateMapOf<Int, Long>() }
    val reported = remember(chapterKey) { HashSet<Int>() }
    LaunchedEffect(chapterKey, items) {
        while (true) {
            delay(250)
            val info = listState.layoutInfo
            val top = info.viewportStartOffset
            val bottom = info.viewportEndOffset
            val now = System.currentTimeMillis()
            val seen = HashSet<Int>()
            for (vi in info.visibleItemsInfo) {
                val item = items.getOrNull(vi.index) as? ReaderItem.Verse ?: continue
                val visible = (minOf(bottom, vi.offset + vi.size) - maxOf(top, vi.offset)).coerceAtLeast(0)
                if (vi.size > 0 && visible.toFloat() / vi.size >= 0.6f) {
                    seen += item.verse
                    val since = visibleSince.getOrPut(item.verse) { now }
                    if (now - since >= 2_000 && reported.add(item.verse)) readNow(item.verse, now - since)
                }
            }
            visibleSince.keys.retainAll(seen)
        }
    }
}

/** Maps a verse to the commentary units whose last verse it is. */
private fun commentaryPlacement(items: List<ReaderItem>, commentary: CommentaryChapter?): Map<Int, List<CommentaryUnit>> {
    if (commentary == null) return emptyMap()
    val verses = items.filterIsInstance<ReaderItem.Verse>().map { it.verse }
    if (verses.isEmpty()) return emptyMap()
    val last = verses.last()
    val out = HashMap<Int, MutableList<CommentaryUnit>>()
    for (u in commentary.units) {
        val r = u.verseRange ?: continue
        val end = if (r.last > last) last else verses.lastOrNull { it <= r.last } ?: continue
        out.getOrPut(end) { mutableListOf() } += u
    }
    return out
}

/** Inline commentary card (design 1C): kicker, collapsible text, community-correction footer. */
@Composable
fun InlineCommentaryCard(unit: CommentaryUnit, sourceName: String, preferTamil: Boolean, onOpen: () -> Unit) {
    val c = Ts.colors
    var open by rememberSaveable(unit.id) { mutableStateOf(false) }
    val r = unit.verseRange
    val rangeLabel = when {
        r == null -> ""
        r.first == r.last -> "${unit.range.split('.').getOrNull(1)}:${r.first}"
        r.last == Int.MAX_VALUE -> "${unit.range.split('.').getOrNull(1)}:${r.first}–"
        else -> "${unit.range.split('.').getOrNull(1)}:${r.first}–${r.last}"
    }
    val text = unit.paragraphs.filter { !it.footnote }.joinToString("\n\n") { it.text(preferTamil) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(top = 6.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface2)
            .border(1.5.dp, c.line, shape)
            .drawBehind { drawRect(c.accent, size = Size(3.dp.toPx(), size.height)) },
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${tr("விளக்கவுரை", "Commentary")} · $rangeLabel · $sourceName".uppercase(),
                style = Ts.type.kicker, color = c.accent, modifier = Modifier.weight(1f),
            )
            Icon(if (open) TsIcons.ChevronUp else TsIcons.ChevronDown, null, Modifier.size(16.dp), tint = c.muted)
        }
        Text(
            text,
            style = Ts.type.body,
            color = c.ink2,
            maxLines = if (open) Int.MAX_VALUE else 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().background(c.pane).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("முழு விளக்கவுரை", "Full commentary"), style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.weight(1f))
            TsPillButton(tr("திற →", "Open →"), onClick = onOpen, style = PillStyle.Ghost, height = 32.dp)
        }
    }
}
