package com.tamilscripture.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.Psalm
import com.tamilscripture.core.model.PsalmCardData
import com.tamilscripture.core.model.PsalmSelection
import com.tamilscripture.core.model.PsalmsIndex
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.model.toggled
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.firstOrNull

/**
 * சங்கீதங்களை ஆராயுங்கள் · Psalms explorer (Claude Design handoff, Psalms Mobile Screen in
 * app mode; website docs/feature_psalms_explorer.md §5, §7). Compact windows: theme tiles and
 * the number grid, filters and the psalm in bottom sheets. Wide windows: filters, grid and
 * psalm side by side.
 */
@Composable
fun PsalmsScreen(links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val state by produceState<Pair<Boolean, PsalmsIndex?>>(true to null) { value = false to graph.study.psalmsIndex() }
    PsalmsExplorer(state.second, state.first, links.back) { index, psalm, pick -> PsalmDetail(index, psalm, links, pick) }
}

/** The explorer without data loading: [detail] draws the open psalm (screenshot tests pass fixtures). */
@Composable
fun PsalmsExplorer(
    index: PsalmsIndex?,
    loading: Boolean,
    onBack: () -> Unit,
    initialPsalm: Int = 23,
    detail: @Composable (PsalmsIndex, Psalm, (Int) -> Unit) -> Unit,
) {
    var sel by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(PsalmSelection()) }
    var open by rememberSaveable { mutableStateOf(initialPsalm) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val c = Ts.colors

    fun set(next: PsalmSelection) {
        sel = next
        val idx = index ?: return
        val ok = next.matcher(idx)
        if (!ok(idx.psalms[open - 1])) idx.psalms.firstOrNull(ok)?.let { open = it.n }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(tr("சங்கீதங்கள்", "Psalms"), style = Ts.type.barTitle, color = c.ink, maxLines = 1)
                    Text(tr("150 பாடல்கள் · கருப்பொருள் வாரியாக", "150 songs · by theme"), style = Ts.type.caption, color = c.muted, maxLines = 1)
                }
                if (!wide && index != null) FilterButton(sel.panelCount) { sheet = "filters" }
            }
            HDivider(thickness = 1.5.dp)
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.TopCenter) { CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(32.dp)) }
                index == null -> Text(
                    tr("இதைத் திறக்க இணைய இணைப்பு அல்லது சங்கீதப் பொதி தேவை.", "This needs a connection, or the Psalms pack in Downloads."),
                    style = Ts.type.body, color = c.muted, modifier = Modifier.padding(28.dp),
                )
                wide -> Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Filters(index, sel, ::set)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Browse(index, sel, open, ::set, onPick = { open = it })
                    }
                    Column(Modifier.width(400.dp).fillMaxHeight().background(c.surface).verticalScroll(rememberScrollState())) {
                        detail(index, index.psalms[open - 1]) { open = it }
                    }
                }
                else -> Browse(index, sel, open, ::set, onPick = { open = it; sheet = "psalm" })
            }
        }
    }

    if (index != null && sheet != null) {
        PsalmsSheet(onDismiss = { sheet = null }) {
            if (sheet == "filters") {
                val count = index.psalms.count(sel.matcher(index))
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("வடிகட்டு", "Filters"), style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.weight(1f))
                        if (sel.panelCount > 0) Text(
                            tr("அனைத்தையும் நீக்கு", "Clear all"), style = Ts.type.label, color = c.accent,
                            modifier = Modifier.clickable { set(PsalmSelection(themes = sel.themes)) }.padding(8.dp),
                        )
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Filters(index, sel, ::set)
                    }
                    Box(Modifier.padding(16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.accent).clickable { sheet = null }, Alignment.Center) {
                        Text(tr("$count சங்கீதங்களைக் காட்டு", "Show $count psalm${if (count == 1) "" else "s"}"), style = Ts.type.rowTitle, color = c.onAccent)
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    detail(index, index.psalms[open - 1]) { open = it }
                }
            }
        }
    }
}

private val SelectionSaver = androidx.compose.runtime.saveable.listSaver<PsalmSelection, String>(
    save = { listOf(it.themes, it.genres, it.authors).map { l -> l.joinToString(",") } },
    restore = { l ->
        fun part(i: Int) = l[i].split(',').filter { it.isNotEmpty() }
        PsalmSelection(part(0), part(1), part(2))
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PsalmsSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Ts.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        contentColor = c.ink,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.navigationBarsPadding()) { content() }
    }
}

@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    val c = Ts.colors
    Box(Modifier.size(48.dp), Alignment.Center) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).border(1.5.dp, c.line2, RoundedCornerShape(11.dp)).clickable(onClick = onClick)
                .semantics { contentDescription = "Filters" },
            Alignment.Center,
        ) { Icon(TsIcons.Filter, null, Modifier.size(20.dp), tint = c.ink2) }
        if (count > 0) Box(
            Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color(0xFFEC3013)), Alignment.Center,
        ) { Text("$count", style = Ts.type.captionSmall.copy(fontWeight = FontWeight.Bold), color = Color.White) }
    }
}

/** The theme colours are the design's; on a dark ground they are lifted towards white. */
@Composable
private fun themeColor(hex: String): Color {
    val base = Color(android.graphics.Color.parseColor(hex))
    return if (Ts.colors.isDark) lerp(base, Color.White, 0.3f) else base
}

/** [primary, secondary] in the interface language's order. */
@Composable
private fun two(ta: String, en: String): Pair<String, String> = if (LocalUiLang.current == UiLang.Tamil) ta to en else en to ta

@Composable
private fun Heading(ta: String, en: String) {
    val (a, b) = two(ta, en)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(a, style = Ts.type.sectionHeading.copy(fontSize = 16.sp), color = Ts.colors.amber)
        Text(b, style = Ts.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Ts.colors.muted)
    }
}

/** A two-line toggle chip: the label in both languages. */
@Composable
private fun Chip(ta: String, en: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val (a, b) = two(ta, en)
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier.clip(shape).background(if (on) c.accent else c.surface).border(1.5.dp, if (on) c.accent else c.line2, shape)
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(a, style = Ts.type.rowTitle, color = if (on) c.onAccent else c.ink)
        Text(b, style = Ts.type.captionSmall, color = if (on) c.onAccent.copy(alpha = 0.8f) else c.muted)
    }
}

/** Genre (with the collections) and author: the side pane or the filter sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(index: PsalmsIndex, sel: PsalmSelection, set: (PsalmSelection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("வகை", "Genre")
        index.genres.forEach { g -> Chip(g.ta, g.en, g.id in sel.genres, Modifier.fillMaxWidth()) { set(sel.copy(genres = sel.genres.toggled(g.id))) } }
        HDivider(Modifier.padding(vertical = 4.dp))
        index.collections.forEach { k -> Chip(k.ta, k.en, k.id in sel.genres, Modifier.fillMaxWidth()) { set(sel.copy(genres = sel.genres.toggled(k.id))) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading("ஆசிரியர்", "Author")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            index.authors.forEach { a -> Chip(a.ta, a.en, a.id in sel.authors) { set(sel.copy(authors = sel.authors.toggled(a.id))) } }
            val anon = PsalmSelection.ANONYMOUS
            Chip("அநாமதேய", "Anonymous", anon in sel.authors) { set(sel.copy(authors = sel.authors.toggled(anon))) }
        }
    }
}

/** Theme tiles, then the grid of numbers, in one lazy grid so the cells can animate. */
@Composable
private fun Browse(index: PsalmsIndex, sel: PsalmSelection, open: Int, set: (PsalmSelection) -> Unit, onPick: (Int) -> Unit) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val shown = remember(index, sel) { if (sel.isEmpty) index.psalms else index.psalms.filter(sel.matcher(index)) }
    LazyVerticalGrid(
        GridCells.Adaptive(48.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "themes") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Heading("கருப்பொருள்கள்", "Themes")
                index.themes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            val on = t.id in sel.themes
                            ThemeTile(t.ta, t.en, index.psalms.count { it.theme == t.id }, themeColor(t.color), on, sel.themes.isNotEmpty() && !on, Modifier.weight(1f)) {
                                set(sel.copy(themes = sel.themes.toggled(t.id)))
                            }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "grid-title") {
            val names = sel.genres.mapNotNull { id -> index.genres.firstOrNull { it.id == id }?.let { it.ta to it.en } ?: index.collections.firstOrNull { it.id == id }?.let { it.ta to it.en } } +
                sel.authors.map { id -> index.authors.firstOrNull { it.id == id }?.let { it.ta to it.en } ?: ("அநாமதேய" to "Anonymous") } +
                sel.themes.mapNotNull { id -> index.theme(id)?.let { it.ta to it.en } }
            val ta = if (names.isEmpty()) "எல்லா சங்கீதங்களும்" else names.joinToString(" · ") { it.first }
            val en = if (names.isEmpty()) "All 150 psalms" else names.joinToString(" · ") { it.second }
            Column(Modifier.padding(bottom = 4.dp)) {
                Heading(ta, en)
                Text(
                    if (sel.isEmpty) tr("ஒவ்வொரு எண்ணின் கீழுள்ள கோடு அதன் கருப்பொருள்", "The bar beneath each number is its theme")
                    else tr("150-இல் ${shown.size} · எண்ணைத் தொட்டுத் திறக்கவும்", "${shown.size} of 150 · tap a number to open it"),
                    style = Ts.type.caption, color = c.muted,
                )
            }
        }
        items(shown, key = { it.n }) { p ->
            val col = themeColor(index.theme(p.theme)?.color ?: "#999999")
            val on = p.n == open
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier.animateItem().aspectRatio(1f).clip(shape).background(if (on) c.accentSoft else c.surface)
                    .border(1.5.dp, if (on) c.accent else c.line, shape)
                    .drawBehind { drawRect(col, Offset(0f, size.height - 4.dp.toPx()), Size(size.width, 4.dp.toPx())) }
                    .clickable { onPick(p.n) }
                    .semantics { contentDescription = "${lang.pick("சங்கீதம்", "Psalm")} ${p.n}"; selected = on },
                Alignment.Center,
            ) { Text("${p.n}", style = Ts.type.rowTitle.copy(fontSize = 15.sp), color = c.ink) }
        }
    }
}

private fun UiLang.pick(ta: String, en: String) = if (this == UiLang.Tamil) ta else en

@Composable
private fun ThemeTile(ta: String, en: String, count: Int, color: Color, on: Boolean, dim: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val (a, b) = two(ta, en)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(64.dp).clip(shape).background(if (on) c.accentSoft else c.surface)
            .border(1.5.dp, if (on) c.accent else c.line, shape)
            .drawBehind {
                val inset = 14.dp.toPx()
                drawRoundRect(color, Offset(inset, size.height - 3.dp.toPx()), Size(size.width - 2 * inset, 3.dp.toPx() * 2), CornerRadius(3.dp.toPx()))
            }
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val alpha = if (dim) 0.55f else 1f
        Text(a, style = Ts.type.rowTitle.copy(fontSize = 16.sp), color = c.ink.copy(alpha = alpha), maxLines = 1)
        Text("$b · $count", style = Ts.type.captionSmall, color = c.muted.copy(alpha = alpha), maxLines = 1)
    }
}

/** The open psalm (the design's card): loads the card and the key verse in the reader's versions. */
@Composable
private fun PsalmDetail(index: PsalmsIndex, psalm: Psalm, links: StudyLinks, onPick: (Int) -> Unit) {
    val graph = LocalAppServices.current.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val card by produceState<PsalmCardData?>(null, psalm.n) { value = graph.study.psalmCard(psalm.n) }
    val (taV, enV) = versionsFor(manifest, settings.version)
    val verse by produceState<Pair<String, String>?>(null, psalm.n, taV, enV) {
        val range = psalm.keyVerses
        val ch = psalm.key.substringBefore(':').toIntOrNull() ?: psalm.n
        suspend fun text(v: String) = runCatching { graph.content.chapter(v, "PSA", ch).firstOrNull()?.value }.getOrNull()?.let { keyText(it, range) }.orEmpty()
        value = text(taV) to text(enV)
    }
    PsalmCardView(
        index, psalm, card, verse, onPick,
        onRead = { links.read(Passage(settings.version, "PSA", psalm.n)) },
        onShare = { links.read(Passage(settings.version, "PSA", psalm.n, psalm.keyVerses.first)) },
        sing = card?.takeIf { it.metrical.isNotEmpty() }?.let { cd -> { PsalmSing(cd) { t -> PsalmTuneBlock(t) } } },
    )
}

/** The card itself; [verse] is the key verse in Tamil and English, null while it loads. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PsalmCardView(
    index: PsalmsIndex,
    psalm: Psalm,
    card: PsalmCardData?,
    verse: Pair<String, String>?,
    onPick: (Int) -> Unit,
    onRead: () -> Unit,
    onShare: () -> Unit,
    /** "Sing this psalm" (PsalmSing): the 1650 metrical psalm and its tunes, below the actions. */
    sing: (@Composable () -> Unit)? = null,
) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val theme = index.theme(psalm.theme)
    val tc = themeColor(theme?.color ?: "#999999")

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Psalm · சங்கீதம்")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Text("${psalm.n}", style = Ts.type.headline.copy(fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold), color = c.accent)
            Column(Modifier.weight(1f)) {
                Text(psalm.titleTa ?: "${theme?.ta.orEmpty()} சங்கீதம்", style = Ts.type.headline.copy(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
                Text(psalm.titleEn ?: "A psalm of ${theme?.en.orEmpty().lowercase()}", style = Ts.type.body, color = c.ink2)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (theme != null) Tag(lang.pick(theme.ta, theme.en), border = tc, dot = tc)
            psalm.genres.forEach { id -> index.genres.firstOrNull { it.id == id }?.let { Tag(lang.pick(it.ta, it.en)) } }
            card?.tags?.forEach { Tag(it) }
            Tag(authorLine(index, psalm, card), filled = true)
        }
        card?.summaryTa?.let { Text(it, style = Ts.type.body.copy(fontSize = 16.sp, lineHeight = 28.sp), color = c.ink) }
        card?.summaryEn?.let { Text(it, style = Ts.type.body.copy(fontFamily = com.tamilscripture.core.designsystem.theme.NotoSans, fontSize = 14.sp, lineHeight = 22.sp), color = c.ink2) }
        if (card != null && card?.summaryEn == null && card?.summaryTa == null) {
            Text(tr("சுருக்கம் விரைவில் சேர்க்கப்படும்.", "A short summary will be added here."), style = Ts.type.caption, color = c.muted)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Kicker("${tr("முக்கிய வசனம்", "Key verse")} · ${psalm.key.replace('-', '–')}", color = c.amber)
            if (verse == null) CircularProgressIndicator(color = c.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else {
                val v = verse
                if (v.first.isNotBlank()) Text(v.first, style = Ts.type.body.copy(fontSize = 19.sp, lineHeight = 32.sp), color = c.ink, modifier = Modifier.background(c.verseSelected, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (v.second.isNotBlank()) Text(v.second, style = Ts.type.body.copy(fontFamily = com.tamilscripture.core.designsystem.theme.NotoSans, fontSize = 15.sp, lineHeight = 24.sp), color = c.ink2)
                if (v.first.isBlank() && v.second.isBlank()) Text(tr("வசனத்தை ஏற்ற முடியவில்லை.", "Could not load the verse."), style = Ts.type.caption, color = c.muted)
            }
        }

        val related = index.psalms.filter { it.theme == psalm.theme && it.n != psalm.n }.take(8)
        if (related.isNotEmpty() && theme != null) {
            Kicker(tr("${theme.ta} — மேலும்", "More on ${theme.en}"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                related.forEach { r ->
                    Box(
                        Modifier.widthIn(min = 44.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                            .border(1.5.dp, c.line2, RoundedCornerShape(10.dp)).clickable { onPick(r.n) }.padding(horizontal = 10.dp),
                        Alignment.Center,
                    ) { Text("${r.n}", style = Ts.type.label, color = c.ink) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            val shape = RoundedCornerShape(11.dp)
            Box(
                Modifier.weight(1f).height(46.dp).clip(shape).background(c.accent).clickable(onClick = onRead),
                Alignment.Center,
            ) { Text("முழு சங்கீதம் · Read", style = Ts.type.rowTitle, color = c.onAccent) }
            Row(
                Modifier.height(46.dp).clip(shape).background(c.surface).border(1.5.dp, c.line2, shape)
                    .clickable(onClick = onShare)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(TsIcons.Share, null, Modifier.size(16.dp), tint = c.ink)
                Text(tr("பகிர்", "Share"), style = Ts.type.rowTitle, color = c.ink)
            }
        }

        if (sing != null) {
            HDivider(Modifier.padding(vertical = 8.dp))
            sing()
        }
    }
}

@Composable
private fun Tag(text: String, border: Color? = null, dot: Color? = null, filled: Boolean = false) {
    val c = Ts.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).then(if (filled) Modifier.background(c.surface2) else Modifier.border(1.5.dp, border ?: c.line2, shape))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = Ts.type.labelSmall, color = if (border != null) c.ink else c.ink2)
    }
}

/** "தாவீது · David", the New Testament's attribution for an untitled psalm, or anonymous. */
private fun authorLine(index: PsalmsIndex, psalm: Psalm, card: PsalmCardData?): String {
    fun names(ids: List<String>) = ids.mapNotNull { id -> index.authors.firstOrNull { it.id == id } }
    val named = names(psalm.authors)
    if (named.isNotEmpty()) return "${named.joinToString(", ") { it.ta }} · ${named.joinToString(", ") { it.en }}"
    val nt = card?.nt ?: return "அநாமதேய · Anonymous"
    val a = names(nt.authors)
    return "${a.joinToString(", ") { it.ta }} · ${a.joinToString(", ") { it.en }} (${nt.ref})"
}

/** The reader's version for its language, and the other language's default beside it. */
internal fun versionsFor(manifest: ContentManifest?, version: String): Pair<String, String> {
    val versions = manifest?.versions.orEmpty()
    fun default(lang: String) = (versions.firstOrNull { it.lang == lang && it.default } ?: versions.firstOrNull { it.lang == lang })?.code
    val mine = manifest?.version(version)
    val ta = if (mine?.lang == "ta") version else default("ta") ?: "IRVTAM"
    val en = if (mine?.lang == "en") version else default("en") ?: "KJV"
    return ta to en
}

/** The text of verses [range] of a chapter, as one string. */
internal fun keyText(ch: Chapter, range: IntRange): String =
    ch.blocks.flatMap { it.segments }
        .filter { s -> s.id?.let(VerseId::parse)?.verse?.let { it in range } == true }
        .joinToString(" ") { it.text }
        .replace(Regex("\\s+"), " ").trim()
