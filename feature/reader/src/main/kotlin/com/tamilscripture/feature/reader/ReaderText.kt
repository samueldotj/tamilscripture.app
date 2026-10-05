package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentaryUnit
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.delay

private val MARGIN_WIDTH = 220.dp

private fun String.clusterStart(i: Int): Int {
    if (i <= 0 || i >= length) return i.coerceIn(0, length)
    val it = android.icu.text.BreakIterator.getCharacterInstance().also { b -> b.setText(this) }
    return if (it.isBoundary(i)) i else it.preceding(i)
}

private fun String.clusterEnd(i: Int): Int {
    if (i <= 0 || i >= length) return i.coerceIn(0, length)
    val it = android.icu.text.BreakIterator.getCharacterInstance().also { b -> b.setText(this) }
    return if (it.isBoundary(i)) i else it.following(i)
}

/** The UTF-16 index [n] code points in, clamped to the text. */
private fun String.offsetByCodePointsSafe(n: Int): Int =
    if (n >= codePointCount(0, length)) length else offsetByCodePoints(0, n.coerceAtLeast(0))

private val HEAT_ALPHA = floatArrayOf(0f, 0.08f, 0.16f, 0.26f, 0.38f)

/** A verse's text with its superscript number, in the design's 1B style. */
@Composable
fun verseAnnotated(item: ReaderItem.Verse, numberSize: Int = 12, showNotes: Boolean, words: List<WordMark> = emptyList()): AnnotatedString {
    val c = Ts.colors
    return buildAnnotatedString {
        withStyle(SpanStyle(fontSize = numberSize.sp, color = c.accent, fontWeight = FontWeight.Bold, baselineShift = BaselineShift.Superscript)) {
            append(item.label)
        }
        append(' ')
        val shift = length
        append(item.text)
        // Word-range highlights: offsets count code points of the verse text.
        for (w in words) {
            // Snapped out to whole letters: a background split inside a Tamil cluster breaks its shaping.
            val from = item.text.clusterStart(item.text.offsetByCodePointsSafe(w.from))
            val to = item.text.clusterEnd(item.text.offsetByCodePointsSafe(w.to))
            if (to > from) addStyle(SpanStyle(background = c.highlight(w.color)), shift + from, shift + to)
        }
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
    /** Dual view: version names over the columns; columns side by side when [dualColumns]. */
    dualLabels: kotlin.Pair<String, String>? = null,
    dualColumns: Boolean = false,
    /** Right-click menu and drag-out on large screens; null on touch-only layouts. */
    interactions: VerseInteractions? = null,
    /** Languages of the text and, in dual view, of the second version (M2-12: TalkBack reads each in its own voice). */
    textLocale: LocaleList? = null,
    secondLocale: LocaleList? = null,
    /** The reader's highlights, notes and bookmarks (M6), by verse. */
    marks: Map<Int, VerseMarks> = emptyMap(),
    /** Community heat bucket by verse (M8-7), drawn as the website does: accent at 8–38%. */
    heat: Map<Int, Int> = emptyMap(),
    /** Notes in a margin column beside the text instead of under each verse. */
    notesInMargin: Boolean = false,
    onOpenNote: (com.tamilscripture.core.model.UserNote) -> Unit = {},
) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val unitsAfter = remember(commentary, items) { commentaryPlacement(items, commentary) }
    // The version names sit above the list, not in it, so list positions match [items].
    Column(modifier) {
        if (dualLabels != null && dualColumns) {
            DualHeader(dualLabels, Modifier.padding(start = contentPadding.calculateStartPadding(LayoutDirection.Ltr), end = contentPadding.calculateEndPadding(LayoutDirection.Ltr), top = 8.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is ReaderItem.Dual -> VerseInteractionBox(item.verse, interactions) { extra ->
                        DualRow(
                            item, item.verse in selection, item.verse == playingVerse, fontSize, lineHeightEm, dualColumns,
                            dualLabels, onTap = { onTapVerse(item.verse) }, modifier = extra,
                            locales = textLocale to secondLocale,
                        )
                    }
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
                    is ReaderItem.Verse -> Row { Column(Modifier.weight(1f)) {
                        val selected = item.verse in selection
                        val playing = item.verse == playingVerse
                        val mark = marks[item.verse]
                        val bookmarkColor = c.accent
                        VerseInteractionBox(item.verse, interactions) { extra -> Text(
                            verseAnnotated(item, numberSize = 12, showNotes = showNotes, words = mark?.words.orEmpty()),
                            style = Ts.type.scripture(fontSize.sp, lineHeightEm).copy(localeList = textLocale),
                            color = c.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        selected -> c.verseSelected
                                        playing -> c.accentWash
                                        mark?.color != null -> c.highlight(mark.color)
                                        (heat[item.verse] ?: 0) > 0 -> c.accent.copy(alpha = HEAT_ALPHA[heat.getValue(item.verse)])
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    },
                                )
                                .then(if (playing && !selected) Modifier.border(1.dp, c.accentSoft, RoundedCornerShape(12.dp)) else Modifier)
                                .then(extra)
                                // A bookmarked verse carries a bar in the margin.
                                .then(if (mark?.bookmarked == true) Modifier.drawBehind { drawRect(bookmarkColor, size = Size(3.dp.toPx(), size.height)) } else Modifier)
                                .clickable { onTapVerse(item.verse) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .semantics { contentDescription = "${item.label}. ${item.text}" },
                        ) }
                        if (showNotes && item.notes.isNotEmpty()) {
                            Column(Modifier.padding(start = 22.dp, end = 10.dp, bottom = 4.dp)) {
                                item.notes.forEachIndexed { i, n ->
                                    Text("${'a' + i}  ${n.text}", style = Ts.type.caption, color = c.muted)
                                }
                            }
                        }
                        if (!notesInMargin) mark?.notes?.forEach { n -> NoteChip(n) { onOpenNote(n) } }
                        unitsAfter[item.verse]?.forEach { unit ->
                            InlineCommentaryCard(unit, commentaryName ?: "", preferTamil = lang == UiLang.Tamil, onOpen = onOpenCommentary)
                        }
                    }
                    // M6-6: on wide windows a note sits in the margin beside its verse, as on the website.
                    if (notesInMargin) {
                        Column(Modifier.width(MARGIN_WIDTH).padding(start = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            marks[item.verse]?.notes?.forEach { n -> MarginNote(n) { onOpenNote(n) } }
                        }
                    }
                    }
                }
            }
        }

    }

    // Verse-read tracking (requirements §5.1, design §6.6).
    val readNow by rememberUpdatedState(onVerseRead)
    val visibleSince = remember(chapterKey) { mutableStateMapOf<Int, Long>() }
    val reported = remember(chapterKey) { HashSet<Int>() }
    val byKey = remember(items) { items.associateBy { it.key } }
    LaunchedEffect(chapterKey, items) {
        while (true) {
            delay(250)
            val info = listState.layoutInfo
            val top = info.viewportStartOffset
            val bottom = info.viewportEndOffset
            val now = System.currentTimeMillis()
            val seen = HashSet<Int>()
            for (vi in info.visibleItemsInfo) {
                val verse = byKey[vi.key]?.verseNumber ?: continue
                val visible = (minOf(bottom, vi.offset + vi.size) - maxOf(top, vi.offset)).coerceAtLeast(0)
                if (vi.size > 0 && visible.toFloat() / vi.size >= 0.6f) {
                    seen += verse
                    val since = visibleSince.getOrPut(verse) { now }
                    if (now - since >= 2_000 && reported.add(verse)) readNow(verse, now - since)
                }
            }
            visibleSince.keys.retainAll(seen)
        }
    }
}

/** Version names over the two columns (website 3B). */
@Composable
private fun DualHeader(labels: kotlin.Pair<String, String>, modifier: Modifier = Modifier) {
    val c = Ts.colors
    Row(modifier.fillMaxWidth().background(c.bg).padding(start = 56.dp, end = 10.dp, top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(labels.first, labels.second).forEach { l ->
            Column(Modifier.weight(1f)) {
                Text(l, style = Ts.type.labelSmall, color = c.ink)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(2.dp).background(c.ink))
            }
        }
    }
}

/**
 * One verse in both versions (A-3.3). Columns: the number in a gutter, then the two
 * texts. Compact: a card with the number on top and the versions stacked. A version
 * without the verse shows a dash (A-3.4). Tapping anywhere selects the verse in both.
 */
@Composable
private fun DualRow(
    item: ReaderItem.Dual,
    selected: Boolean,
    playing: Boolean,
    fontSize: Int,
    lineHeightEm: Float,
    columns: Boolean,
    labels: kotlin.Pair<String, String>?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    locales: kotlin.Pair<LocaleList?, LocaleList?> = null to null,
) {
    val c = Ts.colors
    val missing = tr("இந்த மொழிபெயர்ப்பில் இந்த வசனம் இல்லை", "Not in this version")
    @Composable
    fun Cell(v: ReaderItem.Verse?, size: Int, locale: LocaleList?, modifier: Modifier) {
        if (v == null) {
            Text("—", style = Ts.type.body, color = c.muted, modifier = modifier.semantics { contentDescription = missing })
        } else {
            Text(v.text, style = Ts.type.scripture(size.sp, lineHeightEm).copy(localeList = locale), color = c.ink, modifier = modifier)
        }
    }
    val shape = RoundedCornerShape(14.dp)
    if (columns) {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(if (selected) c.verseSelected else if (playing) c.accentWash else Color.Transparent)
                .then(modifier)
                .clickable(onClick = onTap)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                item.label, style = Ts.type.label, color = if (selected) c.amber else c.muted,
                modifier = Modifier.width(22.dp).padding(top = 4.dp),
            )
            Cell(item.a, fontSize, locales.first, Modifier.weight(1f))
            Cell(item.b, fontSize, locales.second, Modifier.weight(1f))
        }
        HDivider()
    } else {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
                .background(c.surface)
                .border(1.5.dp, if (selected) c.accent else if (playing) c.accentSoft else c.line, shape)
                .then(modifier)
                .clickable(onClick = onTap),
        ) {
            Row(
                Modifier.fillMaxWidth().background(c.surface2).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Text(item.label, style = Ts.type.labelSmall, color = c.onAccent, maxLines = 1)
                }
                Text("${tr("வசனம்", "Verse")} ${item.label}", style = Ts.type.kicker, color = c.muted)
            }
            listOf(item.a to labels?.first, item.b to labels?.second).forEachIndexed { i, (v, label) ->
                if (i > 0) HDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (label != null) Text(label, style = Ts.type.captionSmall, color = c.muted)
                    Cell(v, if (i == 0) fontSize else (fontSize - 2).coerceAtLeast(14), if (i == 0) locales.first else locales.second, Modifier.fillMaxWidth())
                }
            }
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
