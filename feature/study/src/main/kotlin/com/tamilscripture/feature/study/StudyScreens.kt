package com.tamilscripture.feature.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.SchematicMap
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.ArticleIndexEntry
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.EmbeddedArticle
import com.tamilscripture.core.model.MapDrawing
import com.tamilscripture.core.model.MapSvg
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.model.label
import com.tamilscripture.core.model.verseIdOfKey
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Where study screens lead (roadmap M8-2, M8-3); the app supplies the navigation. */
class StudyLinks(
    val back: () -> Unit,
    val read: (Passage) -> Unit,
    val strongs: (String) -> Unit,
    val person: (String) -> Unit,
    val place: (String) -> Unit,
    val article: (String) -> Unit,
    /** The atlas, centred on a place when one is given. */
    val atlas: (String?) -> Unit,
)

// ---- shared pieces ---------------------------------------------------------------------

@Composable
private fun StudyPage(title: String, subtitle: String?, links: StudyLinks, loading: Boolean, missing: Boolean, body: LazyListScope.() -> Unit) {
    val c = Ts.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), links.back)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = Ts.type.barTitle, color = c.ink, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = Ts.type.caption, color = c.muted, maxLines = 1)
            }
        }
        HDivider(thickness = 1.5.dp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when {
                loading -> CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(32.dp))
                missing -> Text(
                    tr("இதைத் திறக்க இணைய இணைப்பு அல்லது ஆய்வுப் பொதி தேவை.", "This needs a connection, or the study pack in Downloads."),
                    style = Ts.type.body, color = c.muted, modifier = Modifier.padding(28.dp),
                )
                else -> LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = body,
                )
            }
        }
    }
}

private fun LazyListScope.paragraph(text: String?, big: Boolean = false) {
    if (text.isNullOrBlank()) return
    item { Text(text, style = if (big) Ts.type.body.copy(fontSize = 17.sp, lineHeight = 27.sp) else Ts.type.body, color = Ts.colors.ink2) }
}

private fun LazyListScope.section(title: String) {
    item { Kicker(title, Modifier.padding(top = 12.dp)) }
}

/** Verse references as chips that open the reader (the first 60, then a count). */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.verseChips(ids: List<VerseId>, manifest: ContentManifest?, version: String, lang: UiLang, read: (Passage) -> Unit) {
    if (ids.isEmpty()) return
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ids.take(60).forEach { v ->
                val label = manifest?.book(v.book)?.label(lang, v.chapter, v.verse) ?: v.toString()
                TsChip(label, false, { read(Passage(version, v.book, v.chapter, v.verse)) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp))
            }
            if (ids.size > 60) Text(tr("+ ${ids.size - 60} மேலும்", "+ ${ids.size - 60} more"), style = Ts.type.caption, color = Ts.colors.muted, modifier = Modifier.padding(6.dp))
        }
    }
}

private fun LazyListScope.articleLinks(articles: List<EmbeddedArticle>, lang: UiLang, open: (String) -> Unit) {
    if (articles.isEmpty()) return
    section(lang.t("அகராதிக் கட்டுரைகள்", "Dictionary articles"))
    items(articles, key = { "a-" + it.id }) { a ->
        LinkRow(a.title, sourceName(a.source)) { open(a.id) }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, style = Ts.type.rowTitle, color = c.ink)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = Ts.type.caption, color = c.muted, maxLines = 2)
    }
}

/** tr() for list builders, which are not composable. */
private fun UiLang.t(ta: String, en: String) = if (this == UiLang.Tamil) ta else en

private fun sourceName(source: String) = when (source) {
    "eastons" -> "Easton's Bible Dictionary"
    "smiths" -> "Smith's Bible Dictionary"
    "aquifer" -> "BibleAquifer"
    else -> source
}

private fun parseIds(ids: List<String>): List<VerseId> = ids.mapNotNull(VerseId::parse)

// ---- Strong's ----------------------------------------------------------------------------

/** A Hebrew or Greek word: lemma, meaning, and every verse it is used in (M8-2). */
@Composable
fun StrongsScreen(number: String, links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val lang = LocalUiLang.current
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val state by produceState<Pair<Boolean, com.tamilscripture.core.model.StrongsEntry?>>(true to null, number) { value = false to graph.study.strongs(number) }
    val e = state.second
    StudyPage(e?.lemma ?: number, e?.let { "$number · ${it.translit}" }, links, state.first, !state.first && e == null) {
        if (e == null) return@StudyPage
        item {
            Text(e.lemma, style = Ts.type.headline.copy(fontSize = 34.sp), color = Ts.colors.ink)
            Text(listOf(e.translit, e.pos).filter { it.isNotBlank() }.joinToString(" · "), style = Ts.type.caption, color = Ts.colors.muted)
        }
        item {
            Text(
                listOfNotNull(e.glossTa?.takeIf { lang == UiLang.Tamil && it.isNotBlank() }, e.gloss).joinToString(" · "),
                style = Ts.type.cardTitle, color = Ts.colors.accent,
            )
        }
        paragraph(e.def)
        if (e.renderings.isNotEmpty()) {
            section(lang.t("மொழிபெயர்ப்புகளில்", "Translated as"))
            paragraph(e.renderings.joinToString(", "))
        }
        if (e.names.isNotEmpty()) {
            section(lang.t("பெயர்கள்", "Names"))
            items(e.names, key = { "n-" + it.id }) { n ->
                LinkRow(if (lang == UiLang.Tamil) n.nameTa.ifBlank { n.nameEn } else n.nameEn, n.kind) {
                    if (n.kind == "place") links.place(n.id) else links.person(n.id)
                }
            }
        }
        section(lang.t("வசனங்கள் · ${e.count}", "Verses · ${e.count}"))
        val books = manifest?.books.orEmpty()
        verseChips(e.verseKeys.mapNotNull { verseIdOfKey(it, books) }, manifest, settings.version, lang, links.read)
    }
}

// ---- persons and places ------------------------------------------------------------------

@Composable
fun PersonScreen(id: String, links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val lang = LocalUiLang.current
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val state by produceState<Pair<Boolean, com.tamilscripture.core.model.Person?>>(true to null, id) { value = false to graph.study.person(id) }
    val p = state.second
    val name = p?.let { if (lang == UiLang.Tamil) it.tamilName?.label?.ifBlank { null } ?: it.nameEn else it.nameEn } ?: ""
    StudyPage(name, p?.let { if (lang == UiLang.Tamil) it.nameEn else it.tamilName?.label }, links, state.first, !state.first && p == null) {
        if (p == null) return@StudyPage
        paragraph(p.brief ?: p.description, big = true)
        val facts = listOfNotNull(p.gender, p.tribe?.let { lang.t("கோத்திரம்: $it", "Tribe: $it") })
        if (facts.isNotEmpty()) item { Text(facts.joinToString(" · "), style = Ts.type.caption, color = Ts.colors.muted) }
        paragraph(p.article ?: p.summary)
        section(lang.t("வசனங்கள் · ${p.verses.size}", "Verses · ${p.verses.size}"))
        verseChips(parseIds(p.verses), manifest, settings.version, lang, links.read)
        articleLinks(p.articles, lang, links.article)
        p.source?.let { s -> item { Text(listOf(s.name, s.licence).filter { it.isNotBlank() }.joinToString(" · "), style = Ts.type.captionSmall, color = Ts.colors.muted) } }
    }
}

@Composable
fun PlaceScreen(id: String, links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val lang = LocalUiLang.current
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    val state by produceState<Pair<Boolean, com.tamilscripture.core.model.Place?>>(true to null, id) { value = false to graph.study.place(id) }
    val map by produceState<MapDrawing?>(null, id) { value = graph.study.file("maps/place/$id.svg")?.let(MapSvg::parse) }
    val p = state.second
    val name = p?.let { if (lang == UiLang.Tamil) it.tamilName?.label?.ifBlank { null } ?: it.nameEn else it.nameEn } ?: ""
    StudyPage(name, p?.let { listOfNotNull(it.placeType, it.modern?.takeIf { m -> m != it.nameEn }).joinToString(" · ") }, links, state.first, !state.first && p == null) {
        if (p == null) return@StudyPage
        map?.let { d -> item { SchematicMap(d, lang == UiLang.Tamil, selected = id, description = name, onPlace = { other -> if (other != id) links.place(other) }) } }
        if (p.geo?.lat != null) item { TsPillButton(lang.t("வரைபடத்தில் காட்டு", "Show on the atlas"), { links.atlas(id) }, height = 36.dp) }
        paragraph(p.description?.brief, big = true)
        paragraph(p.description?.article ?: p.description?.short)
        p.geo?.lat?.let { lat ->
            item {
                Text(
                    "%.3f°, %.3f°".format(lat, p.geo?.lon ?: 0.0) + (p.geo?.precision?.let { " · $it" } ?: ""),
                    style = Ts.type.caption, color = Ts.colors.muted,
                )
            }
        }
        section(lang.t("வசனங்கள் · ${p.verses.size}", "Verses · ${p.verses.size}"))
        verseChips(parseIds(p.verses), manifest, settings.version, lang, links.read)
        if (p.nearby.isNotEmpty()) {
            section(lang.t("அருகிலுள்ள இடங்கள்", "Nearby places"))
            items(p.nearby, key = { "p-" + it.id }) { n ->
                LinkRow(if (lang == UiLang.Tamil) n.nameTa.ifBlank { n.nameEn } else n.nameEn, null) { links.place(n.id) }
            }
        }
        articleLinks(p.articles, lang, links.article)
    }
}

// ---- dictionary ----------------------------------------------------------------------------

/** A dictionary article, in Tamil paragraph by paragraph where a draft exists (M8-3). */
@Composable
fun ArticleScreen(id: String, links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val lang = LocalUiLang.current
    val state by produceState<Pair<Boolean, com.tamilscripture.core.model.Article?>>(true to null, id) { value = false to graph.study.article(id) }
    val a = state.second
    val tamil = lang == UiLang.Tamil
    StudyPage(
        a?.let { if (tamil) it.titleTa?.ifBlank { null } ?: it.title else it.title } ?: "",
        a?.let { sourceName(it.source) }, links, state.first, !state.first && a == null,
    ) {
        if (a == null) return@StudyPage
        items(a.paragraphs, key = { it.id }) { p ->
            Text(if (tamil && !p.ta.isNullOrBlank()) p.ta!! else p.text, style = Ts.type.body, color = Ts.colors.ink2)
        }
        item {
            Column(Modifier.padding(top = 12.dp)) {
                HDivider()
                Text(listOf(a.attribution, a.licence).filter { it.isNotBlank() }.joinToString(" · "), style = Ts.type.captionSmall, color = Ts.colors.muted, modifier = Modifier.padding(top = 8.dp))
                if (tamil && a.paragraphs.any { !it.ta.isNullOrBlank() }) {
                    Text("தமிழ்: வரைவு மொழிபெயர்ப்பு; திருத்தங்கள் வரவேற்கப்படுகின்றன.", style = Ts.type.captionSmall, color = Ts.colors.muted)
                }
            }
        }
    }
}

/** Search and browse field shared by the dictionary and root-word lists. */
@Composable
private fun Filter(query: String, hint: String, onChange: (String) -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(shape).background(c.surface).border(1.5.dp, c.line2, shape).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) Text(hint, style = Ts.type.body, color = c.muted)
        BasicTextField(query, onChange, singleLine = true, textStyle = Ts.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun DictionaryScreen(links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    var query by rememberSaveable { mutableStateOf("") }
    val state by produceState<Pair<Boolean, List<ArticleIndexEntry>>>(true to emptyList(), Unit) { value = false to graph.study.articleIndex() }
    val shown = remember(query, state.second) {
        val q = query.trim().lowercase()
        state.second.filter { q.isEmpty() || it.title.lowercase().contains(q) }.sortedBy { !it.title.lowercase().startsWith(q) }.take(200)
    }
    StudyPage(tr("அகராதி", "Dictionary"), "Easton · Smith · Aquifer", links, state.first, !state.first && state.second.isEmpty()) {
        item { Filter(query, tr("தலைப்பைத் தேடு", "Find a title"), { query = it }) }
        items(shown, key = { it.id }) { e -> LinkRow(e.title, sourceName(e.source)) { links.article(e.id) } }
    }
}

/** Hebrew and Greek words from the Strong's index, by number, word or meaning (M8-2). */
@Composable
fun RootWordsScreen(links: StudyLinks) {
    val graph = LocalAppServices.current.graph
    val lang = LocalUiLang.current
    var query by rememberSaveable { mutableStateOf("") }
    val state by produceState<Pair<Boolean, List<JsonArray>>>(true to emptyList(), Unit) {
        val text = graph.study.file("strongs/index.json")
        value = false to (text?.let { runCatching { graph.content.json.decodeFromString<List<JsonArray>>(it) }.getOrNull() } ?: emptyList())
    }
    val shown = remember(query, state.second) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            state.second.sortedByDescending { runCatching { it[4].jsonPrimitive.int }.getOrDefault(0) }.take(100)
        } else {
            state.second.filter { row -> (0..5).any { i -> row.getOrNull(i)?.jsonPrimitive?.content?.lowercase()?.contains(q) == true } }.take(200)
        }
    }
    StudyPage(tr("மூலச்சொற்கள்", "Root words"), tr("எபிரெயம் · கிரேக்கம்", "Hebrew · Greek"), links, state.first, !state.first && state.second.isEmpty()) {
        item { Filter(query, tr("எண், சொல் அல்லது பொருள்", "Number, word or meaning"), { query = it }) }
        items(shown, key = { it[0].jsonPrimitive.content }) { row ->
            val num = row[0].jsonPrimitive.content
            val glossTa = row.getOrNull(5)?.jsonPrimitive?.content.orEmpty()
            val gloss = if (lang == UiLang.Tamil && glossTa.isNotBlank()) glossTa else row.getOrNull(3)?.jsonPrimitive?.content.orEmpty()
            LinkRow("${row[1].jsonPrimitive.content}  ·  $gloss", "$num · ${row[2].jsonPrimitive.content} · ${row.getOrNull(4)?.jsonPrimitive?.content ?: ""}×") {
                links.strongs(num)
            }
        }
    }
}
