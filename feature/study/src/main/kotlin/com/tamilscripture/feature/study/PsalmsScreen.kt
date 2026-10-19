package com.tamilscripture.feature.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentarySource
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.Psalm
import com.tamilscripture.core.model.PsalmAuthor
import com.tamilscripture.core.model.PsalmCardData
import com.tamilscripture.core.model.PsalmSelection
import com.tamilscripture.core.model.PsalmsIndex
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.model.steps
import com.tamilscripture.core.model.toggled
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * சங்கீதங்களை ஆராயுங்கள் · Psalms explorer (Claude Design "Psalms Explorer Redesign", Oct 2026;
 * website docs/feature_psalms_explorer.md §5, §7): a map of the 150 psalms coloured by theme,
 * filtered by theme, genre, collection and author, beside the open psalm's reading pane. The
 * psalms keep their places on the map; filters fade the ones that do not match. Compact
 * windows put the psalm first, with a bar at the foot that steps through the psalms and opens
 * the map as a sheet. Wide windows give the map a column of its own beside the psalm.
 */
@Composable
fun PsalmsScreen(links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val state by produceState<Pair<Boolean, PsalmsIndex?>>(true to null) { value = false to graph.study.psalmsIndex() }
    PsalmsExplorer(state.second, state.first, links.back) { index, psalm, steps, pick -> PsalmDetail(index, psalm, steps, links, pick) }
}

/**
 * The explorer without data loading: [detail] draws the open psalm, with the psalms before and
 * after it when the pane shows them itself (screenshot tests pass fixtures).
 */
@Composable
fun PsalmsExplorer(
    index: PsalmsIndex?,
    loading: Boolean,
    onBack: () -> Unit,
    initialPsalm: Int = 23,
    detail: @Composable (PsalmsIndex, Psalm, Pair<Int, Int>?, (Int) -> Unit) -> Unit,
) {
    var sel by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(PsalmSelection()) }
    var open by rememberSaveable { mutableStateOf(initialPsalm) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf<String?>(null) }
    val c = Ts.colors

    /** Change one facet; when the open psalm no longer matches, open the first that does. */
    fun set(next: PsalmSelection) {
        sel = next
        val idx = index ?: return
        val ok = next.matcher(idx)
        if (!ok(idx.psalms[open - 1])) idx.psalms.firstOrNull(ok)?.let { open = it.n }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        val wide = maxWidth >= 840.dp
        val roomy = maxWidth >= 1100.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(tr("சங்கீதங்கள்", "Psalms"), style = Ts.type.barTitle, color = c.ink, maxLines = 1)
                    Text(tr("150 பாடல்கள் · கருப்பொருள் வாரியாக", "150 songs · by theme"), style = Ts.type.caption, color = c.muted, maxLines = 1)
                }
            }
            HDivider(thickness = 1.5.dp)
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.TopCenter) { CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(32.dp)) }
                index == null -> Text(
                    tr("இதைத் திறக்க இணைய இணைப்பு அல்லது சங்கீதப் பொதி தேவை.", "This needs a connection, or the Psalms pack in Downloads."),
                    style = Ts.type.body, color = c.muted, modifier = Modifier.padding(28.dp),
                )
                wide -> Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.width(if (roomy) 400.dp else 340.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        PsalmMap(index, sel, open, menu, inSheet = false, ::set, onMenu = { menu = it }, onPick = { open = it })
                    }
                    Box(Modifier.width(1.5.dp).fillMaxHeight().background(c.line))
                    // The pane starts at the top of each psalm it opens.
                    key(open) {
                        Column(Modifier.weight(1f).fillMaxHeight().background(c.surface).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                            Column(Modifier.widthIn(max = 760.dp).navigationBarsPadding()) {
                                detail(index, index.psalms[open - 1], index.steps(sel, open)) { open = it }
                            }
                        }
                    }
                }
                else -> {
                    val (prev, next) = index.steps(sel, open)
                    key(open) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            detail(index, index.psalms[open - 1], null) { open = it }
                        }
                    }
                    Dock(index, prev, next, sel.count, onStep = { open = it }, onMap = { menu = null; sheet = true })
                }
            }
        }
    }

    if (index != null && sheet) {
        PsalmsSheet(onDismiss = { sheet = false }) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("150 சங்கீதங்கள்", "All 150 psalms"), style = Ts.type.sheetTitle, color = c.ink, modifier = Modifier.weight(1f))
                    IconBox(TsIcons.Close, tr("மூடு", "Close"), { sheet = false })
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PsalmMap(index, sel, open, menu, inSheet = true, ::set, onMenu = { menu = it }, onPick = { open = it; sheet = false })
                }
                HDivider()
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(c.accent).clickable { sheet = false }, Alignment.Center) {
                    Text(tr("சங்கீதம் $open-ஐத் திற", "Open Psalm $open"), style = Ts.type.rowTitle, color = c.onAccent)
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
        containerColor = c.surface2,
        contentColor = c.ink,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.navigationBarsPadding()) { content() }
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
private fun Heading(ta: String, en: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ta, style = Ts.type.sectionHeading.copy(fontSize = 16.sp), color = Ts.colors.amber)
        Text(en, style = Ts.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Ts.colors.muted)
    }
}

/** A two-line toggle chip: the label in both languages. */
@Composable
private fun Chip(ta: String, en: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val (a, b) = two(ta, en)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).background(if (on) c.accent else c.surface).border(1.5.dp, if (on) c.accent else c.line2, shape)
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on }
            .heightIn(min = 44.dp).padding(horizontal = 13.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(a, style = Ts.type.rowTitle.copy(fontSize = 14.sp), color = if (on) c.onAccent else c.ink)
        Text(b, style = Ts.type.captionSmall, color = if (on) c.onAccent.copy(alpha = 0.8f) else c.muted)
    }
}

private fun UiLang.pick(ta: String, en: String) = if (this == UiLang.Tamil) ta else en

private const val GENRE = "genre"
private const val COLLECTION = "collection"
private const val AUTHOR = "author"

/**
 * The map: the themes, Genre, Collection and Author as three buttons that open a panel of
 * chips, and all 150 psalms in a fixed grid (ten columns beside the psalm, seven in the
 * sheet) that fades the ones the filters do not keep.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PsalmMap(
    index: PsalmsIndex,
    sel: PsalmSelection,
    open: Int,
    menu: String?,
    inSheet: Boolean,
    set: (PsalmSelection) -> Unit,
    onMenu: (String?) -> Unit,
    onPick: (Int) -> Unit,
) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val anonymous = PsalmAuthor(PsalmSelection.ANONYMOUS, "Anonymous", "அநாமதேய")
    val matches = remember(index, sel) { sel.matcher(index) }
    val shownCount = remember(index, sel) { if (sel.isEmpty) 150 else index.psalms.count(matches) }

    // Themes
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Heading("கருப்பொருள்", "Theme", Modifier.weight(1f))
            if (!sel.isEmpty) Text(
                tr("எல்லாம் நீக்கு", "Clear all"), style = Ts.type.label, color = c.accent,
                modifier = Modifier.clickable { set(PsalmSelection()) }.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        if (inSheet) {
            // In the sheet the themes are one row that scrolls sideways, leaving room for the map.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                index.themes.forEach { t ->
                    val on = t.id in sel.themes
                    ThemePill(lang.pick(t.ta, t.en), index.psalms.count { it.theme == t.id }, themeColor(t.color), on, sel.themes.isNotEmpty() && !on) {
                        set(sel.copy(themes = sel.themes.toggled(t.id)))
                    }
                }
            }
        } else {
            index.themes.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { t ->
                        val on = t.id in sel.themes
                        ThemeButton(t.ta, t.en, index.psalms.count { it.theme == t.id }, themeColor(t.color), on, sel.themes.isNotEmpty() && !on, Modifier.weight(1f)) {
                            set(sel.copy(themes = sel.themes.toggled(t.id)))
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }

    // Genre, Collection, Author
    val collectionIds = remember(index) { index.collections.map { it.id }.toSet() }
    fun chosen(m: String) = if (m == AUTHOR) sel.authors.size else sel.genres.count { (it in collectionIds) == (m == COLLECTION) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple(GENRE, "வகை", "Genre"), Triple(COLLECTION, "தொகுப்பு", "Collection"), Triple(AUTHOR, "ஆசிரியர்", "Author")).forEach { (id, ta, en) ->
                MenuButton(ta, en, chosen(id), menu == id) { onMenu(if (menu == id) null else id) }
            }
        }
        if (menu != null) {
            val items: List<Triple<String, String, String>> = when (menu) {
                GENRE -> index.genres.map { Triple(it.id, it.ta, it.en) }
                COLLECTION -> index.collections.map { Triple(it.id, it.ta, it.en) }
                else -> (index.authors + anonymous).map { Triple(it.id, it.ta, it.en) }
            }
            FlowRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (inSheet) c.surface else c.surface2)
                    .border(1.5.dp, c.line, RoundedCornerShape(12.dp)).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { (id, ta, en) ->
                    val on = if (menu == AUTHOR) id in sel.authors else id in sel.genres
                    Chip(ta, en, on) {
                        set(if (menu == AUTHOR) sel.copy(authors = sel.authors.toggled(id)) else sel.copy(genres = sel.genres.toggled(id)))
                    }
                }
            }
        }
    }

    // The 150 psalms
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val names = sel.genres.mapNotNull { id -> index.genres.firstOrNull { it.id == id }?.let { it.ta to it.en } ?: index.collections.firstOrNull { it.id == id }?.let { it.ta to it.en } } +
            sel.authors.map { id -> index.authors.firstOrNull { it.id == id }?.let { it.ta to it.en } ?: (anonymous.ta to anonymous.en) } +
            sel.themes.mapNotNull { id -> index.theme(id)?.let { it.ta to it.en } }
        Text(
            if (sel.isEmpty) tr("150 சங்கீதங்கள் · நிறம் ஒவ்வொன்றின் கருப்பொருள்", "All 150 psalms · the colour is each psalm’s theme")
            else tr("150-இல் $shownCount · ${names.joinToString(", ") { it.first }}", "$shownCount of 150 · ${names.joinToString(", ") { it.second }}"),
            style = Ts.type.caption, color = c.ink2,
        )
        val cols = if (inSheet) 7 else 10
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            index.psalms.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { p ->
                        Cell(p.n, themeColor(index.theme(p.theme)?.color ?: "#999999"), p.n == open, sel.isEmpty || matches(p), lang, inSheet, Modifier.weight(1f)) { onPick(p.n) }
                    }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        if (!inSheet) Text(
            tr("சங்கீதங்கள் தங்கள் இடத்திலேயே இருக்கும்; பொருந்தாதவை மங்கும்.", "Psalms stay in their places; filters fade the ones that don’t match."),
            style = Ts.type.captionSmall, color = c.muted,
        )
    }
}

/** One psalm on the map: tinted with its theme, a bar of it at the foot; faded when filtered out. */
@Composable
private fun Cell(n: Int, tc: Color, on: Boolean, kept: Boolean, lang: UiLang, inSheet: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val alpha by animateFloatAsState(if (kept || on) 1f else 0.2f, label = "fade")
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier.aspectRatio(1f).heightIn(min = if (inSheet) 44.dp else 30.dp).alpha(alpha)
            .then(if (on) Modifier.border(2.dp, tc, RoundedCornerShape(9.dp)).padding(3.dp) else Modifier)
            .clip(shape).background(if (on) tc else lerp(c.surface, tc, 0.14f))
            .drawBehind { if (!on) drawRect(tc, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${lang.pick("சங்கீதம்", "Psalm")} $n"; selected = on },
        Alignment.Center,
    ) {
        Text("$n", style = Ts.type.label.copy(fontSize = if (inSheet) 14.sp else 13.sp, fontWeight = FontWeight.SemiBold), color = if (on) c.bg else c.ink, maxLines = 1)
    }
}

@Composable
private fun ThemeButton(ta: String, en: String, count: Int, tc: Color, on: Boolean, dim: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ts.colors
    val (a, b) = two(ta, en)
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier.heightIn(min = 52.dp).alpha(if (dim) 0.6f else 1f).clip(shape).background(if (on) lerp(c.surface, tc, 0.16f) else c.surface)
            .border(1.5.dp, if (on) tc else c.line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(tc))
        Column(Modifier.weight(1f)) {
            Text(a, style = Ts.type.rowTitle.copy(fontSize = 14.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b, style = Ts.type.captionSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$count", style = Ts.type.captionSmall, color = c.muted)
    }
}

@Composable
private fun ThemePill(label: String, count: Int, tc: Color, on: Boolean, dim: Boolean, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(44.dp).alpha(if (dim) 0.6f else 1f).clip(shape).background(if (on) lerp(c.surface, tc, 0.16f) else c.surface)
            .border(1.5.dp, if (on) tc else c.line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tc))
        Text(label, style = Ts.type.rowTitle.copy(fontSize = 14.sp), color = c.ink, maxLines = 1)
        Text("$count", style = Ts.type.captionSmall, color = c.muted)
    }
}

@Composable
private fun MenuButton(ta: String, en: String, count: Int, expanded: Boolean, onClick: () -> Unit) {
    val c = Ts.colors
    // The interface language's name alone, so the three fit on one line; the chips have both.
    val a = two(ta, en).first
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.height(44.dp).clip(shape).background(if (count > 0) c.accentSoft else c.surface)
            .border(1.5.dp, if (expanded) c.accent else c.line2, shape)
            .clickable(onClick = onClick).semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(a, style = Ts.type.rowTitle.copy(fontSize = 14.sp), color = c.ink)
        if (count > 0) Badge(count)
        Icon(if (expanded) TsIcons.ChevronUp else TsIcons.ChevronDown, null, Modifier.size(14.dp), tint = c.ink2)
    }
}

@Composable
private fun Badge(count: Int, modifier: Modifier = Modifier) {
    val c = Ts.colors
    Box(modifier.heightIn(min = 20.dp).widthIn(min = 20.dp).clip(CircleShape).background(c.accent).padding(horizontal = 5.dp), Alignment.Center) {
        Text("$count", style = Ts.type.captionSmall.copy(fontWeight = FontWeight.Bold), color = c.onAccent)
    }
}

/** Compact windows: the bar at the foot steps through the psalms and opens the map. */
@Composable
private fun Dock(index: PsalmsIndex, prev: Int, next: Int, count: Int, onStep: (Int) -> Unit, onMap: () -> Unit) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().background(c.surface2)) {
        HDivider(thickness = 1.5.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun step(n: Int, back: Boolean) = Row(
                Modifier.size(width = 60.dp, height = 52.dp).clip(shape).border(1.5.dp, c.line2, shape).clickable { onStep(n) }
                    .semantics { contentDescription = "${lang.pick("சங்கீதம்", "Psalm")} $n" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (back) Icon(TsIcons.ChevronLeft, null, Modifier.size(14.dp), tint = c.ink2)
                Text("$n", style = Ts.type.label, color = c.ink2)
                if (!back) Icon(TsIcons.ChevronRight, null, Modifier.size(14.dp), tint = c.ink2)
            }
            step(prev, back = true)
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(shape).background(c.surface).border(1.5.dp, c.line2, shape).clickable(onClick = onMap)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // A swatch of the first ten psalms' colours.
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        index.psalms.take(10).chunked(5).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { p -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.dp)).background(themeColor(index.theme(p.theme)?.color ?: "#999999"))) }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr("150 சங்கீதங்கள்", "All 150 psalms"), style = Ts.type.rowTitle.copy(fontSize = 15.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr("வரைபடம் · வடிகட்டு", "Map and filters"), style = Ts.type.captionSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (count > 0) Badge(count, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
            }
            step(next, back = false)
        }
    }
}

/** The open psalm: loads the card, the key verse in the reader's versions, and Spurgeon. */
@Composable
private fun PsalmDetail(index: PsalmsIndex, psalm: Psalm, steps: Pair<Int, Int>?, links: StudyLinks, onPick: (Int) -> Unit) {
    val graph = LocalAppServices.current.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val card by produceState<PsalmCardData?>(null, psalm.n) { value = graph.study.psalmCard(psalm.n) }
    val (taV, enV) = versionsFor(manifest, settings.version)
    val ch = psalm.key.substringBefore(':').toIntOrNull() ?: psalm.n
    val chapters by produceState<Pair<Chapter?, Chapter?>?>(null, psalm.n, taV, enV) {
        suspend fun get(v: String) = runCatching { graph.content.chapter(v, "PSA", ch).firstOrNull()?.value }.getOrNull()
        value = get(taV) to get(enV)
    }
    val verse = chapters?.let { (t, e) -> (t?.let { keyText(it, psalm.keyVerses) }.orEmpty()) to (e?.let { keyText(it, psalm.keyVerses) }.orEmpty()) }
    val verseCount = chapters?.let { (t, e) -> (t ?: e)?.verseNumbers?.maxOrNull() } ?: 0
    val spurgeon by produceState<SpurgeonState>(SpurgeonState.Loading, psalm.n) {
        val idx = graph.commentary.index()
        val chapter = graph.commentary.chapter(SPURGEON, "PSA", psalm.n)
        value = when {
            chapter != null -> SpurgeonState.Ready(chapter, idx?.sources?.firstOrNull { it.id == SPURGEON })
            // Not yet published: nothing to say. Published but unreachable: say so.
            idx?.covers(SPURGEON, "PSA", psalm.n) == true -> SpurgeonState.Failed
            else -> SpurgeonState.None
        }
    }
    PsalmCardView(
        index, psalm, card, verse, onPick,
        onRead = { links.read(Passage(settings.version, "PSA", psalm.n)) },
        onShare = { links.read(Passage(settings.version, "PSA", psalm.n, psalm.keyVerses.first)) },
        steps = steps,
        verseCount = verseCount,
        commentary = when (val s = spurgeon) {
            is SpurgeonState.Ready -> ({ SpurgeonSection(s.chapter, s.source, psalm.n) })
            SpurgeonState.Loading -> ({ Text("…", style = Ts.type.body, color = Ts.colors.muted) })
            SpurgeonState.Failed -> ({ Text(tr("விளக்கத்தை ஏற்ற முடியவில்லை.", "Could not load the commentary."), style = Ts.type.caption, color = Ts.colors.muted) })
            SpurgeonState.None -> null
        },
        sing = card?.takeIf { it.metrical.isNotEmpty() }?.let { cd -> { PsalmSing(cd) { t, n, words, onPlace -> PsalmTuneBlock(t, n, words, onPlace) } } },
    )
}

private const val SPURGEON = "spurgeon"

private sealed interface SpurgeonState {
    data object Loading : SpurgeonState
    data object None : SpurgeonState
    data object Failed : SpurgeonState
    data class Ready(val chapter: CommentaryChapter, val source: CommentarySource?) : SpurgeonState
}

/**
 * The psalm's reading pane (the design's card): the psalms before and after when [steps] is
 * given, the number and titles, theme, genres, author and verse count, the key verse ([verse]
 * in Tamil and English, null while it loads), the summaries, Read / Sing / Share, Spurgeon
 * ([commentary]), more psalms of the theme, and the psalm to sing ([sing]).
 */
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
    steps: Pair<Int, Int>? = null,
    verseCount: Int = 0,
    commentary: (@Composable () -> Unit)? = null,
    /** "Sing this psalm" (PsalmSing): the 1650 metrical psalm and its tunes, at the foot. */
    sing: (@Composable () -> Unit)? = null,
) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val theme = index.theme(psalm.theme)
    val tc = themeColor(theme?.color ?: "#999999")
    val singAt = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (steps != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Step(steps.first, back = true, onPick)
                Step(steps.second, back = false, onPick)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${psalm.n}",
                    style = Ts.type.headline.copy(fontSize = if (steps != null) 88.sp else 68.sp, lineHeight = if (steps != null) 80.sp else 62.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp),
                    color = c.accent,
                )
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Kicker("Psalm · சங்கீதம்")
                    Text(psalm.titleTa ?: "${theme?.ta.orEmpty()} சங்கீதம்", style = Ts.type.headline.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold), color = c.ink)
                    Text(psalm.titleEn ?: "A psalm of ${theme?.en.orEmpty().lowercase()}", style = Ts.type.body, color = c.ink2)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (theme != null) Tag(theme.ta, border = tc, dot = tc, secondary = theme.en)
                psalm.genres.forEach { id -> index.genres.firstOrNull { it.id == id }?.let { Tag(lang.pick(it.ta, it.en)) } }
                card?.tags?.forEach { Tag(it) }
                Tag(authorLine(index, psalm, card), filled = true)
                if (verseCount > 0) Tag(tr("$verseCount வசனங்கள்", "$verseCount verses"), plain = true)
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Kicker("${tr("முக்கிய வசனம்", "Key verse")} · ${psalm.key.replace('-', '–')}", color = c.amber)
                if (verse == null) CircularProgressIndicator(color = c.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else {
                    val v = verse
                    if (v.first.isNotBlank()) Text(v.first, style = Ts.type.body.copy(fontSize = 19.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium), color = c.ink)
                    if (v.second.isNotBlank()) Text(v.second, style = Ts.type.body.copy(fontFamily = com.tamilscripture.core.designsystem.theme.NotoSans, fontSize = 15.sp, lineHeight = 24.sp, fontStyle = FontStyle.Italic), color = c.ink2)
                    if (v.first.isBlank() && v.second.isBlank()) Text(tr("வசனத்தை ஏற்ற முடியவில்லை.", "Could not load the verse."), style = Ts.type.caption, color = c.muted)
                }
            }

            card?.summaryTa?.let { Text(it, style = Ts.type.body.copy(fontSize = 16.sp, lineHeight = 28.sp), color = c.ink) }
            card?.summaryEn?.let { Text(it, style = Ts.type.body.copy(fontFamily = com.tamilscripture.core.designsystem.theme.NotoSans, fontSize = 14.sp, lineHeight = 22.sp), color = c.ink2) }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                val shape = RoundedCornerShape(11.dp)
                Box(
                    Modifier.weight(1f).height(46.dp).clip(shape).background(c.accent).clickable(onClick = onRead).padding(horizontal = 14.dp),
                    Alignment.Center,
                ) { Text("முழு சங்கீதம் · Read", style = Ts.type.rowTitle, color = c.onAccent, maxLines = 1) }
                if (sing != null) ActionButton(TsIcons.Play, tr("பாடுக", "Sing")) { scope.launch { singAt.bringIntoView() } }
                ActionButton(TsIcons.Share, tr("பகிர்", "Share"), onShare)
            }
        }

        if (commentary != null) {
            HDivider()
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp)) { commentary() }
        }

        val related = index.psalms.filter { it.theme == psalm.theme && it.n != psalm.n }.take(8)
        if (related.isNotEmpty() && theme != null) {
            HDivider()
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker(tr("${theme.ta} — மேலும்", "More on ${theme.en}"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    related.forEach { r ->
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(lerp(c.surface, tc, 0.14f))
                                .drawBehind { drawRect(tc, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                                .clickable { onPick(r.n) }
                                .semantics { contentDescription = "${lang.pick("சங்கீதம்", "Psalm")} ${r.n}" },
                            Alignment.Center,
                        ) { Text("${r.n}", style = Ts.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.ink) }
                    }
                }
            }
        }

        if (sing != null) {
            HDivider()
            Box(Modifier.fillMaxWidth().bringIntoViewRequester(singAt).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)) { sing() }
        }
    }
}

@Composable
private fun Step(n: Int, back: Boolean, onPick: (Int) -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.height(40.dp).clip(shape).border(1.5.dp, c.line2, shape).clickable { onPick(n) }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (back) Icon(TsIcons.ChevronLeft, null, Modifier.size(16.dp), tint = c.ink2)
        Text(tr("சங்கீதம் $n", "Psalm $n"), style = Ts.type.label, color = c.ink2)
        if (!back) Icon(TsIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.ink2)
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier.height(46.dp).clip(shape).border(1.5.dp, c.line2, shape).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.ink)
        Text(label, style = Ts.type.rowTitle, color = c.ink, maxLines = 1)
    }
}

@Composable
private fun Tag(text: String, border: Color? = null, dot: Color? = null, filled: Boolean = false, plain: Boolean = false, secondary: String? = null) {
    val c = Ts.colors
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).then(
            when {
                filled -> Modifier.background(c.surface2)
                plain -> Modifier
                else -> Modifier.border(1.5.dp, border ?: c.line2, shape)
            },
        ).padding(horizontal = if (plain) 4.dp else 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = Ts.type.labelSmall, color = if (plain) c.muted else if (border != null) c.ink else c.ink2)
        if (secondary != null) Text(secondary, style = Ts.type.labelSmall.copy(fontWeight = FontWeight.Medium), color = c.muted)
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
