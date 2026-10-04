package com.tamilscripture.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.Pill
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.ReferenceParser
import com.tamilscripture.core.model.SearchHit
import com.tamilscripture.core.model.VerseId
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.delay

/** தேடல் · Search across verses (design 1H). Dictionary and places arrive with their packs (M8). */
@Composable
fun SearchScreen(onOpen: (Passage) -> Unit, autoFocus: Boolean = false) {
    val services = LocalAppServices.current
    val graph = services.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    val parser = remember(manifest) { manifest?.let { ReferenceParser(it.books) } }
    val ref = remember(query, parser) { parser?.parse(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }

    LaunchedEffect(query, settings.version, ref) {
        failed = false
        if (query.trim().length < 2 || ref != null) {
            hits = emptyList(); total = 0; loading = false
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        try {
            val r = graph.search.search(query.trim(), settings.version)
            hits = r.response.hits; total = r.response.total; offline = r.offline
            graph.stats.record("search", query = query.trim(), amount = r.response.total.toLong(), version = settings.version, offline = r.offline)
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
                        keyboardActions = KeyboardActions(onSearch = { ref?.let { r -> onOpen(Passage(settings.version, r.book.code, r.chapter, r.verse)) } }),
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
            if (ref != null) {
                item {
                    Kicker(tr("வசனக் குறிப்பு", "Reference"), Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(Passage(settings.version, ref.book.code, ref.chapter, ref.verse)) }.padding(horizontal = 20.dp, vertical = 12.dp),
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
                if (hits.isNotEmpty()) {
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
                            Modifier.fillMaxWidth().clickable { onOpen(Passage(h.version, vid.book, vid.chapter, vid.verse)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(book?.label(lang, vid.chapter, vid.verse) ?: h.verseId, style = Ts.type.labelSmall, color = c.accent)
                            Text(highlight(h.text, query.trim(), c.accentSoft), style = Ts.type.scripture(16.sp, 1.7f), color = c.ink)
                        }
                        HDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

private fun highlight(text: String, q: String, bg: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    val terms = q.split(' ').filter { it.length >= 2 }
    var i = 0
    while (i < text.length) {
        val hit = terms.mapNotNull { t -> text.indexOf(t, i, ignoreCase = true).takeIf { it >= 0 }?.let { it to t } }.minByOrNull { it.first }
        if (hit == null) { append(text.substring(i)); break }
        append(text.substring(i, hit.first))
        withStyle(SpanStyle(background = bg)) { append(text.substring(hit.first, hit.first + hit.second.length)) }
        i = hit.first + hit.second.length
    }
}
