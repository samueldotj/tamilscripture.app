package com.tamilscripture.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentaryParagraph
import com.tamilscripture.core.model.CommentarySource
import com.tamilscripture.core.model.CommentaryUnit
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr

/*
 * Spurgeon on the open psalm, in the explorer's reading pane (the website's
 * PsalmCommentary.svelte): The Treasury of David's overview of the psalm, its exposition
 * verse by verse, its Explanatory Notes and Quaint Sayings (quotations from other writers)
 * and its Hints to the Village Preacher, one tab each. The notes and hints are folded by
 * verse and drawn only when opened: Psalm 119 alone has 170,000 words of them.
 */

private enum class SpurgeonTab(val ta: String, val en: String, val section: String) {
    Overview("முன்னுரை", "Overview", "overview"),
    Exposition("விளக்கவுரை", "Exposition", "exposition"),
    Notes("குறிப்புகள்", "Notes and sayings", "notes"),
    Hints("பிரசங்கக் குறிப்புகள்", "Hints to preachers", "hints"),
}

/** One verse's paragraphs of one section, without the section's own heading. */
private class SpurgeonGroup(val id: String, val label: String, val count: Int, val paragraphs: List<CommentaryParagraph>)

/** "Verse 3", "Verses 1–2", or the whole psalm for the chapter's introduction. */
private fun unitLabel(u: CommentaryUnit, lang: UiLang): String {
    val r = u.verseRange ?: return if (lang == UiLang.Tamil) "முழுச் சங்கீதம்" else "The whole psalm"
    return when {
        r.last > r.first && r.last != Int.MAX_VALUE -> if (lang == UiLang.Tamil) "வசனங்கள் ${r.first}–${r.last}" else "Verses ${r.first}–${r.last}"
        else -> if (lang == UiLang.Tamil) "வசனம் ${r.first}" else "Verse ${r.first}"
    }
}

private fun groups(chapter: CommentaryChapter, tab: SpurgeonTab, lang: UiLang): List<SpurgeonGroup> = chapter.units.mapNotNull { u ->
    val r = u.verseRange
    val single = r == null || r.first == r.last
    val paragraphs = u.paragraphs
        .filter { it.section == tab.section && !it.heading }
        // The group's heading names the verse, so the paragraph need not, unless the group
        // covers several and the paragraph is on one of them.
        .map { if (tab == SpurgeonTab.Exposition || single) it.copy(verse = null, verseEnd = null) else it }
    if (paragraphs.isEmpty()) return@mapNotNull null
    val count = if (tab == SpurgeonTab.Notes) paragraphs.map { it.quote ?: it.id }.distinct().size else paragraphs.size
    SpurgeonGroup(u.id, unitLabel(u, lang), count, paragraphs)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpurgeonSection(chapter: CommentaryChapter, source: CommentarySource?, n: Int, modifier: Modifier = Modifier) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val tamil = lang == UiLang.Tamil
    var tab by rememberSaveable(n) { mutableStateOf(SpurgeonTab.Overview) }
    var more by rememberSaveable(n) { mutableStateOf(false) }

    // The overview: the psalm's introduction up to its notes; Title and Division (under their
    // headings) only once opened in full.
    val overview = remember(chapter) { chapter.units.firstOrNull { it.kind == "chapter" }?.paragraphs.orEmpty().filter { it.section == "overview" } }
    val lead = remember(overview) {
        // From the first paragraph (Psalm 119 opens with its Title heading) to the next heading.
        val from = overview.drop(overview.indexOfFirst { !it.heading }.coerceAtLeast(0))
        val k = from.indexOfFirst { it.heading }
        (if (k < 0) from else from.take(k)).take(2)
    }
    val all = remember(chapter, lang) { SpurgeonTab.entries.filter { it != SpurgeonTab.Overview }.associateWith { groups(chapter, it, lang) } }
    fun has(t: SpurgeonTab) = if (t == SpurgeonTab.Overview) overview.isNotEmpty() else all[t].orEmpty().isNotEmpty()
    // A psalm without an overview opens on its exposition.
    val current = if (has(tab)) tab else SpurgeonTab.entries.firstOrNull(::has) ?: SpurgeonTab.Exposition
    val shown = all[current].orEmpty()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Kicker("ஸ்பர்ஜன் · C. H. Spurgeon, The Treasury of David", color = c.amber)
        // A segmented control on a sunken track.
        FlowRow(
            Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SpurgeonTab.entries.filter(::has).forEach { t ->
                val on = t == current
                Text(
                    if (tamil) t.ta else t.en,
                    style = Ts.type.label, color = if (on) c.ink else c.ink2,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) c.surface else c.surface2)
                        .clickable(role = Role.Tab) { tab = t }.semantics { selected = on }
                        .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        when (current) {
            SpurgeonTab.Overview -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Paragraphs(if (more) overview else lead, tamil)
                if (overview.size > lead.size) Text(
                    if (more) tr("சுருக்கு", "Show less") else tr("முழு முன்னுரை", "Read the whole overview"),
                    style = Ts.type.label, color = c.accent,
                    modifier = Modifier.clickable { more = !more }.padding(vertical = 6.dp),
                )
            }
            SpurgeonTab.Exposition -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                shown.forEach { g ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(g.label, style = Ts.type.label.copy(fontWeight = FontWeight.Bold), color = c.accent)
                        Paragraphs(g.paragraphs, tamil)
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { g -> Folded(g, current, tamil) }
            }
        }

        SpurgeonFoot(source, if (current == SpurgeonTab.Overview) overview else shown.flatMap { it.paragraphs }, tamil)
    }
}

@Composable
private fun Folded(g: SpurgeonGroup, tab: SpurgeonTab, tamil: Boolean) {
    val c = Ts.colors
    val shape = RoundedCornerShape(12.dp)
    var open by rememberSaveable(tab, g.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.5.dp, c.line, shape)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(g.label, style = Ts.type.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
            val count = if (tab == SpurgeonTab.Notes) {
                tr("${g.count} மேற்கோள்கள்", "${g.count} ${if (g.count == 1) "quotation" else "quotations"}")
            } else {
                tr("${g.count} குறிப்புகள்", "${g.count} ${if (g.count == 1) "hint" else "hints"}")
            }
            Text(count, style = Ts.type.captionSmall, color = c.muted)
            Icon(if (open) TsIcons.ChevronUp else TsIcons.ChevronDown, null, Modifier.size(16.dp), tint = c.muted)
        }
        if (open) {
            Column(Modifier.drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx()) }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Paragraphs(g.paragraphs, tamil)
            }
        }
    }
}

/**
 * The commentary's paragraphs: the verse and the words explained in bold before the text;
 * a quotation with its author above and its work below, ruled at the left.
 */
@Composable
private fun Paragraphs(paragraphs: List<CommentaryParagraph>, tamil: Boolean) {
    val c = Ts.colors
    paragraphs.forEachIndexed { i, p ->
        val prev = paragraphs.getOrNull(i - 1)
        val next = paragraphs.getOrNull(i + 1)
        val quoted = p.author != null || p.quote != null
        val author = p.author?.takeIf { prev == null || prev.quote != p.quote }
        val work = p.work?.takeIf { next == null || next.quote != p.quote }
        val rule = c.accentSoft
        Column(
            if (quoted) Modifier.drawBehind { drawLine(rule, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx()) }.padding(start = 12.dp) else Modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (author != null) Text(
                listOfNotNull(author, p.via).joinToString(" · "),
                style = Ts.type.labelSmall.copy(fontFamily = com.tamilscripture.core.designsystem.theme.NotoSans), color = c.ink2,
            )
            if (p.heading) {
                Text(p.text(tamil), style = Ts.type.rowTitle, color = c.ink)
            } else {
                val anchor = p.anchor(tamil)
                Text(
                    buildAnnotatedString {
                        p.verse?.let { v ->
                            withStyle(SpanStyle(color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)) {
                                append(if (p.verseEnd != null && p.verseEnd!! > v) "$v–${p.verseEnd}" else "$v")
                            }
                            append(" ")
                        }
                        if (!anchor.isNullOrBlank()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append(anchor) }
                            withStyle(SpanStyle(color = c.muted)) { append(" — ") }
                        }
                        append(p.text(tamil))
                    },
                    style = Ts.type.body.copy(fontSize = 16.sp, lineHeight = 27.sp), color = c.ink,
                )
            }
            if (work != null) Text(
                "— $work",
                style = Ts.type.captionSmall.copy(fontStyle = FontStyle.Italic, fontFamily = com.tamilscripture.core.designsystem.theme.NotoSans), color = c.muted,
            )
        }
    }
}

/** Whose text this is and on what terms, and whether the Tamil shown is a draft or still to come. */
@Composable
private fun SpurgeonFoot(source: CommentarySource?, paragraphs: List<CommentaryParagraph>, tamil: Boolean) {
    val c = Ts.colors
    val text = paragraphs.filter { !it.heading }
    val state = when {
        !tamil -> null
        text.isNotEmpty() && text.all { !it.ta.isNullOrBlank() } -> "தமிழ் வரைவு · Tamil draft · திருத்தங்கள் வரவேற்கப்படுகின்றன"
        text.any { !it.ta.isNullOrBlank() } -> "பகுதி தமிழ் வரைவு · Part in a Tamil draft"
        else -> "ஆங்கில மூலம் · English original · தமிழ் விரைவில்"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (source != null) Text(
            listOf(source.attribution.ifBlank { listOf(source.name, source.year).filter { it.isNotBlank() }.joinToString(", ") }, source.licence)
                .filter { it.isNotBlank() }.joinToString(" · "),
            style = Ts.type.captionSmall, color = c.muted,
        )
        if (state != null) Text(state, style = Ts.type.captionSmall, color = c.muted)
    }
}
