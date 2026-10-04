package com.tamilscripture.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.content.References
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.Pill
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.SearchHit
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** தேடல் · Search across verses (design 1H). Dictionary and places arrive with their packs (M8). */
@Composable
fun SearchScreen(onOpen: (Passage) -> Unit, autoFocus: Boolean = false, initialQuery: String? = null) {
    val services = LocalAppServices.current
    val graph = services.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf("") }
    var romanOffer by remember { mutableStateOf<String?>(null) }
    // M3-5: "OT", "NT" or a book code narrows the search; a new query starts over.
    var scope by rememberSaveable(query) { mutableStateOf<String?>(null) }
    var perBook by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    // M3-5: every version at once, merged in canonical order.
    var allVersions by rememberSaveable { mutableStateOf(false) }
    val scopeRange = remember(scope, manifest) {
        val books = manifest?.books.orEmpty()
        when (val sc = scope) {
            null -> null
            "OT", "NT" -> books.filter { it.testament == sc }.map { it.order }.takeIf { it.isNotEmpty() }?.let { it.min()..it.max() }
            else -> books.firstOrNull { it.code == sc }?.let { it.order..it.order }
        }
    }
    val ref = remember(query, manifest) { References.parse(query, manifest) }
    // A-4.5: book names in both scripts while the reader is still typing one.
    val suggestions = remember(query, manifest) {
        if (ref != null || query.isBlank() || query.any { it.isDigit() }) emptyList() else References.suggest(query, manifest)
    }
    val coroutines = rememberCoroutineScope()
    // A-4.5: recent searches (this device only) and what people search for most.
    val saveRecent: (String) -> Unit = { q -> coroutines.launch { graph.settings.addRecentSearch(q) } }
    val common by produceState(emptyList<String>(), lang) { value = graph.search.commonSearches(if (lang == UiLang.Tamil) "ta" else "en") }
    val recentMatches = remember(query, settings.recentSearches) {
        val t = query.trim().lowercase()
        if (t.isEmpty()) emptyList() else settings.recentSearches.filter { it.lowercase().contains(t) && it.lowercase() != t }.take(4)
    }
    val openHit: (Passage) -> Unit = { p -> saveRecent(query); onOpen(p) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }

    LaunchedEffect(query, settings.version, ref, scopeRange, allVersions) {
        failed = false
        if (query.trim().length < 2 || ref != null) {
            hits = emptyList(); total = 0; loading = false; romanOffer = null; perBook = emptyList()
            return@LaunchedEffect
        }
        if (scopeRange == null) delay(350)
        loading = true
        try {
            val versions = manifest?.versions.orEmpty().sortedBy { it.order }.map { it.code }
            val r = if (allVersions && versions.size > 1) {
                // The reader's version first, so its hits lead within each verse.
                graph.search.searchVersions(query.trim(), listOf(settings.version) + (versions - settings.version), { v -> manifest?.version(v)?.lang == "ta" }, scopeRange)
            } else {
                graph.search.search(query.trim(), settings.version, tamil = manifest?.version(settings.version)?.lang == "ta", books = scopeRange)
            }
            hits = r.response.hits; total = r.response.total; offline = r.offline; shown = r.shown; romanOffer = r.romanOffer
            perBook = r.perBook
            if (scopeRange == null) graph.stats.record("search", query = query.trim(), amount = r.response.total.toLong(), version = settings.version, offline = r.offline)
        } catch (e: Exception) {
            failed = true
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().height(52.dp).clip(Pill).background(c.surface)
                    .border(1.5.dp, if (query.isNotEmpty()) c.accent else c.line2, Pill).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(TsIcons.Search, null, Modifier.size(18.dp), tint = c.muted)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("வசனம், சொல், இடம்", "Verse, word, place"), style = Ts.type.scripture(18.sp, 1.3f), color = c.muted)
                    BasicTextField(
                        query, { query = it },
                        textStyle = Ts.type.scripture(18.sp, 1.3f).copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) saveRecent(query)
                            ref?.let { r -> onOpen(Passage(settings.version, r.book.code, r.chapter, r.verse)) }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Icon(TsIcons.Close, tr("அழி", "Clear"), Modifier.size(20.dp).clip(Pill).clickable { query = "" }, tint = c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("எல்லாம்", "All"), tr("வசனங்கள்", "Verses"), tr("அகராதி", "Dictionary"), tr("இடங்கள்", "Places")).forEachIndexed { i, label ->
                    TsChip(label, filter == i, { filter = i }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isBlank()) {
                if (settings.recentSearches.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Kicker(tr("சமீபத்திய தேடல்கள்", "Recent searches"), Modifier.weight(1f))
                        TsPillButton(tr("அழி", "Clear"), { coroutines.launch { graph.settings.clearRecentSearches() } }, style = PillStyle.Ghost, height = 32.dp)
                    }
                    ChipFlow(settings.recentSearches) { query = it }
                }
                if (common.isNotEmpty()) item {
                    Kicker(tr("அதிகம் தேடப்பட்டவை", "Common searches"), Modifier.padding(start = 20.dp, top = 16.dp))
                    ChipFlow(common) { query = it }
                }
            }
            if (recentMatches.isNotEmpty()) item { ChipFlow(recentMatches) { query = it } }
            if (suggestions.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { b ->
                            TsChip(b.name(lang), false, { query = b.name(lang) + " " }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            if (ref != null) {
                item {
                    Kicker(tr("வசனக் குறிப்பு", "Reference"), Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { openHit(Passage(settings.version, ref.book.code, ref.chapter, ref.verse)) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ref.book.label(lang, ref.chapter, ref.verse, ref.verseEnd), style = Ts.type.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
                        Icon(TsIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.accent)
                    }
                }
            }
            if (filter == 2 || filter == 3) {
                item {
                    Text(
                        tr("அகராதியும் இடங்களும் ஆய்வுப் பொதிகளுடன் விரைவில் வரும்.", "Dictionary and places arrive soon with the study packs."),
                        style = Ts.type.body, color = c.muted, modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
                if (failed) item {
                    Text(tr("தேடலுக்கு இணைய இணைப்பு தேவை.", "Search needs a connection for now."), style = Ts.type.body, color = c.muted, modifier = Modifier.padding(20.dp))
                }
                if (!loading && !failed && hits.isEmpty() && ref == null && query.trim().length >= 2) item {
                    Text(
                        tr("“${query.trim()}” — முடிவுகள் இல்லை. தமிழில் தட்டச்சு செய்து பாருங்கள்.", "No results for “${query.trim()}”."),
                        style = Ts.type.body, color = c.muted, modifier = Modifier.padding(20.dp),
                    )
                }
                if (hits.isNotEmpty() || scope != null) {
                    item {
                        ScopeRow(scope, perBook, manifest?.books.orEmpty(), lang, allVersions, { allVersions = !allVersions }) { scope = it }
                    }
                }
                if (hits.isEmpty() && allVersions && scope == null && !loading && query.trim().length >= 2 && ref == null) item {
                    ScopeRow(null, emptyList(), manifest?.books.orEmpty(), lang, true, { allVersions = false }) { }
                }
                if (hits.isNotEmpty()) {
                    if (shown != query.trim()) item {
                        Text(
                            tr("“$shown” என்பதற்கான முடிவுகள்", "Showing results for “$shown”"),
                            style = Ts.type.body, color = c.ink2, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                        )
                    }
                    romanOffer?.let { offer ->
                        item {
                            TsChip(
                                tr("“$offer” எனத் தேடு", "Search “$offer” instead"), false, { query = offer },
                                Modifier.padding(start = 20.dp, top = 12.dp),
                            )
                        }
                    }
                    item {
                        Kicker(
                            tr("வசனங்கள்", "Verses") + " · $total" + if (offline) tr(" · சாதனத்தில்", " · on device") else tr(" · இணையத்தில்", " · online"),
                            Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                    items(hits, key = { it.verseId + it.version }) { h ->
                        val vid = VerseId.parse(h.verseId) ?: return@items
                        val book = manifest?.book(vid.book)
                        Column(
                            Modifier.fillMaxWidth().clickable { openHit(Passage(h.version, vid.book, vid.chapter, vid.verse)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                (book?.label(lang, vid.chapter, vid.verse) ?: h.verseId) +
                                    if (allVersions) " · " + (manifest?.version(h.version)?.short ?: h.version) else "",
                                style = Ts.type.labelSmall, color = c.accent,
                            )
                            val ranges = remember(h.text, shown) { PackRepository.matchRanges(h.text, shown) }
                            Text(highlight(h.text, ranges, c.accentSoft), style = Ts.type.scripture(16.sp, 1.7f), color = c.ink)
                        }
                        HDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

/** Marks the words the search matched, stems included (M3-4). */
private fun highlight(text: String, ranges: List<IntRange>, bg: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    append(text)
    ranges.forEach { r -> addStyle(SpanStyle(background = bg), r.first, r.last + 1) }
}

/**
 * Narrow the results (M3-5, R-5.5, R-5.8): whole Bible, a testament, or one book. With a
 * downloaded Bible each book shows how many verses matched; online, only the testaments.
 */
@Composable
private fun ScopeRow(
    scope: String?,
    perBook: List<Pair<Int, Int>>,
    books: List<Book>,
    lang: UiLang,
    allVersions: Boolean,
    onToggleVersions: () -> Unit,
    onScope: (String?) -> Unit,
) {
    val byOrder = remember(books) { books.associateBy { it.order } }
    fun count(t: String) = perBook.filter { (o, _) -> byOrder[o]?.testament == t }.sumOf { it.second }
    val pad = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TsChip(tr("எல்லா மொழிபெயர்ப்புகளும்", "All versions"), allVersions, onToggleVersions, contentPadding = pad)
        TsChip(tr("முழு வேதம்", "Whole Bible"), scope == null, { onScope(null) }, contentPadding = pad)
        listOf("OT" to tr("பழைய ஏற்பாடு", "Old Testament"), "NT" to tr("புதிய ஏற்பாடு", "New Testament")).forEach { (t, label) ->
            val n = count(t)
            if (perBook.isEmpty() || n > 0) TsChip(if (n > 0) "$label · $n" else label, scope == t, { onScope(t) }, contentPadding = pad)
        }
        perBook.forEach { (order, n) ->
            val b = byOrder[order] ?: return@forEach
            TsChip("${b.name(lang)} · $n", scope == b.code, { onScope(b.code) }, contentPadding = pad)
        }
    }
}

/** Searches to tap, wrapping onto as many lines as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { q -> TsChip(q, false, { onPick(q) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) }
    }
}
