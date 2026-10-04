package com.tamilscripture.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsListRow
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.TsSegmented
import com.tamilscripture.core.designsystem.component.TsToggle
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import com.tamilscripture.feature.reader.highlight
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * என்னுடையவை · My highlights, notes, bookmarks and history (M6-5, M6-7). Newest first;
 * highlights filter by colour, notes by what they say. A row opens its passage.
 */
@Composable
fun MineScreen(start: Int, onBack: () -> Unit, onRead: (Passage) -> Unit) {
    val graph = LocalAppServices.current.graph
    val c = Ts.colors
    val lang = LocalUiLang.current
    val scope = rememberCoroutineScope()
    val data by graph.userData.data.collectAsStateWithLifecycle()
    val manifest by graph.content.manifest.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(Settings())
    var tab by rememberSaveable { mutableStateOf(start) }
    var color by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    fun ref(book: String, chapter: Int, start: Int?, end: Int?): String =
        manifest?.book(book)?.label(lang, chapter, start, end?.takeIf { it != start }) ?: "$book $chapter${start?.let { ":$it" } ?: ""}"
    fun open(book: String, chapter: Int, verse: Int?, version: String? = null) =
        onRead(Passage(version?.takeIf { v -> manifest?.version(v) != null } ?: settings.version, book, chapter, verse))

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            Text(tr("என்னுடையவை", "Mine"), style = Ts.type.barTitle, color = c.ink)
        }
        HDivider(thickness = 1.5.dp)
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                TsSegmented(
                    listOf(tr("முனைப்பு", "Highlights"), tr("குறிப்பு", "Notes"), tr("குறி", "Bookmarks"), tr("வரலாறு", "History")),
                    tab, { tab = it }, fill = true, height = 40.dp, textStyle = Ts.type.labelSmall,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                )
                when (tab) {
                    0 -> {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TsChip(tr("அனைத்தும்", "All"), color == null, { color = null })
                            HighlightColor.entries.forEach { h ->
                                Box(
                                    Modifier.size(36.dp).background(c.highlight(h), CircleShape)
                                        .border(if (color == h.key) 3.dp else 1.dp, if (color == h.key) c.accent else c.line2, CircleShape)
                                        .clickable { color = if (color == h.key) null else h.key },
                                )
                            }
                        }
                        val rows = data.highlights.filter { color == null || it.color == color }.sortedByDescending { it.updatedAt }
                        Listing(rows.isEmpty(), tr("முனைப்புகள் இல்லை. வசனத்தைத் தொட்டு ‘முனைப்பு’ தேர்ந்தெடுங்கள்.", "No highlights yet. Tap a verse and choose Highlight.")) {
                            items(rows, key = { it.id }) { h ->
                                MarkRow(
                                    ref(h.book, h.chapter, h.verseStart, h.verseEnd), h.quote.orEmpty(), c.highlight(HighlightColor.of(h.color)),
                                    onClick = { open(h.book, h.chapter, h.verseStart, h.version) },
                                    onDelete = { scope.launch { graph.userData.removeHighlight(h.book, h.chapter, (h.verseStart..h.verseEnd).toList()) } },
                                )
                            }
                        }
                    }
                    1 -> {
                        Box(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp))
                                .border(1.dp, c.line2, RoundedCornerShape(14.dp)).padding(12.dp),
                        ) {
                            if (query.isEmpty()) Text(tr("குறிப்புகளில் தேடு", "Search notes"), style = Ts.type.body, color = c.faint)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = Ts.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                        }
                        val rows = data.notes.filter { query.isBlank() || it.body.contains(query.trim(), ignoreCase = true) }.sortedByDescending { it.updatedAt }
                        Listing(rows.isEmpty(), if (query.isBlank()) tr("குறிப்புகள் இல்லை.", "No notes yet.") else tr("பொருந்தும் குறிப்பு இல்லை.", "No note matches.")) {
                            items(rows, key = { it.id }) { n ->
                                MarkRow(
                                    ref(n.book, n.chapter, n.verseStart, n.verseEnd), n.body, null,
                                    onClick = { open(n.book, n.chapter, n.verseStart, n.version) },
                                    onDelete = { scope.launch { graph.userData.deleteNote(n.id) } },
                                )
                            }
                        }
                    }
                    2 -> {
                        val rows = data.bookmarks.sortedByDescending { it.createdAt }
                        Listing(rows.isEmpty(), tr("குறிகள் இல்லை. வசனத்தைத் தொட்டு ‘குறி’ தேர்ந்தெடுங்கள்.", "No bookmarks yet. Tap a verse and choose Bookmark.")) {
                            items(rows, key = { it.id }) { b ->
                                MarkRow(
                                    ref(b.book, b.chapter, b.verse, null), manifest?.version(b.version)?.short ?: b.version, null,
                                    onClick = { open(b.book, b.chapter, b.verse, b.version) },
                                    onDelete = { scope.launch { graph.userData.deleteBookmark(b.id) } },
                                )
                            }
                        }
                    }
                    else -> {
                        TsListRow(
                            tr("வரலாற்றை நிறுத்து", "Pause history"),
                            subtitle = tr("நிறுத்தியிருக்கும்போது திறக்கும் அதிகாரங்கள் பதியப்படாது", "Chapters you open are not recorded while paused"),
                            trailing = { TsToggle(data.historyPaused, { v -> scope.launch { graph.userData.setHistoryPausedOnAccount(v) } }) },
                        )
                        val rows = graph.userData.visits(data)
                        if (rows.isNotEmpty()) {
                            TsPillButton(tr("வரலாற்றை அழி", "Clear history"), { scope.launch { graph.userData.clearHistory() } },
                                style = PillStyle.Outlined, height = 36.dp, modifier = Modifier.padding(horizontal = 22.dp))
                        }
                        Listing(rows.isEmpty(), tr("வரலாறு இல்லை.", "No history yet.")) {
                            items(rows, key = { it.visitedAt + it.book + it.chapter }) { v ->
                                MarkRow(
                                    ref(v.book, v.chapter, v.verseStart, null), when_(v.visitedAt, lang) + " · " + (manifest?.version(v.version)?.short ?: v.version), null,
                                    onClick = { open(v.book, v.chapter, v.verseStart, v.version) }, onDelete = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val dayTime = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private fun when_(iso: String, lang: UiLang): String =
    runCatching { dayTime.withLocale(if (lang == UiLang.Tamil) java.util.Locale.forLanguageTag("ta") else java.util.Locale.ENGLISH).format(Instant.parse(iso).atZone(ZoneId.systemDefault())) }
        .getOrDefault(iso.take(10))

@Composable
private fun Listing(empty: Boolean, emptyText: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = Ts.colors
    if (empty) {
        Text(emptyText, style = Ts.type.body, color = c.muted, modifier = Modifier.padding(22.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 32.dp)) { content() }
}

@Composable
private fun MarkRow(title: String, subtitle: String, swatch: androidx.compose.ui.graphics.Color?, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val c = Ts.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (swatch != null) Box(Modifier.size(14.dp).background(swatch, CircleShape).border(1.dp, c.line2, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ts.type.label, color = c.ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = Ts.type.caption, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (onDelete != null) IconBox(TsIcons.Close, tr("நீக்கு", "Remove"), onDelete, tint = c.muted, iconSize = 18.dp)
    }
    HDivider(Modifier.padding(start = 22.dp))
}
