package com.tamilscripture.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.icon.TsIcons
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentarySource
import com.tamilscripture.core.model.CommentaryUnit
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.AppServices
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentaryState(
    val passage: Passage,
    val manifest: ContentManifest? = null,
    val chapter: Chapter? = null,
    val sources: List<CommentarySource> = emptyList(),
    val source: String = "henry",
    val commentary: CommentaryChapter? = null,
    val loading: Boolean = true,
)

class CommentaryViewModel(private val services: AppServices, initial: Passage) : ViewModel() {
    private val graph = services.graph
    private val mutable = MutableStateFlow(CommentaryState(initial))
    val state: StateFlow<CommentaryState> = mutable

    init {
        viewModelScope.launch {
            val s = graph.settings.settings.firstOrNull()
            mutable.update { it.copy(source = s?.commentarySource ?: "henry", manifest = graph.content.manifest.value) }
            load()
        }
    }

    fun select(source: String) {
        mutable.update { it.copy(source = source) }
        viewModelScope.launch { graph.settings.update { it.copy(commentarySource = source) } }
        load()
    }

    fun move(delta: Int) {
        val p = state.value.passage
        val book = state.value.manifest?.book(p.book) ?: return
        val ch = p.chapter + delta
        if (ch in 1..book.chapters) {
            mutable.update { it.copy(passage = p.copy(chapter = ch, verse = null), chapter = null, commentary = null) }
            load()
        }
    }

    private fun load() {
        val s = state.value
        mutable.update { it.copy(loading = true) }
        viewModelScope.launch {
            val idx = graph.commentary.index()
            mutable.update { it.copy(sources = idx?.sources.orEmpty(), manifest = graph.content.manifest.value) }
            val c = graph.commentary.chapter(s.source, s.passage.book, s.passage.chapter)
            mutable.update { it.copy(commentary = c, loading = false) }
            graph.stats.record("commentary", book = s.passage.book, chapter = s.passage.chapter, action = s.source)
        }
        viewModelScope.launch {
            graph.content.chapter(s.passage.version, s.passage.book, s.passage.chapter)
                .catch { }
                .collect { l -> mutable.update { it.copy(chapter = l.value) } }
        }
    }
}

/** விளக்கவுரை · Commentary reader (design 2A). */
@Composable
fun CommentaryScreen(passage: Passage, onBack: () -> Unit, onReadWithVerses: (Passage) -> Unit) {
    val services = LocalAppServices.current
    val vm: CommentaryViewModel = viewModel(key = "commentary-${passage.chapterKey()}") { CommentaryViewModel(services, passage) }
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = LocalUiLang.current
    val c = Ts.colors
    val book = state.manifest?.book(state.passage.book)
    val tamil = lang == UiLang.Tamil
    val units = state.commentary?.units.orEmpty()
    val intro = units.firstOrNull { it.kind == "chapter" }
    val passages = units.filter { it.kind == "passage" }
    var open by rememberSaveable(state.passage.chapterKey(), state.source) {
        mutableStateOf(passages.firstOrNull { u -> passage.verse != null && u.verseRange?.contains(passage.verse!!) == true }?.id ?: passages.firstOrNull()?.id)
    }
    LaunchedEffect(passages) {
        if (open == null) open = passages.firstOrNull { u -> passage.verse != null && u.verseRange?.contains(passage.verse!!) == true }?.id ?: passages.firstOrNull()?.id
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(TsIcons.ChevronLeft, tr("பின்", "Back"), onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    tr("விளக்கவுரை", "Commentary") + " · " + (book?.label(lang, state.passage.chapter) ?: ""),
                    style = Ts.type.barTitle.copy(fontSize = 17.sp), color = c.ink, maxLines = 1,
                )
                Text("${state.sources.size} ${tr("ஆதாரங்கள்", "sources")} · ${if (tamil) "தமிழ்" else "English"}", style = Ts.type.captionSmall, color = c.muted)
            }
            IconBox(TsIcons.Book, tr("வசனங்களுடன் படிக்க", "Read with verses"), { onReadWithVerses(state.passage) })
        }
        HDivider(thickness = 1.5.dp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.sources.forEach { s ->
                TsChip(s.short, s.id == state.source, { vm.select(s.id) }, square = true, textStyle = Ts.type.label,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (state.loading && state.commentary == null) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(32.dp))
            } else if (state.commentary == null) {
                Text(tr("இந்த அதிகாரத்துக்கு இந்த விளக்கவுரை இல்லை.", "This commentary has nothing on this chapter."),
                    style = Ts.type.body, color = c.muted, modifier = Modifier.padding(32.dp))
            } else {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (intro != null) item(key = "intro") { IntroCard(intro, tamil) }
                    passages.forEachIndexed { i, u ->
                        item(key = u.id) {
                            UnitSection(
                                u, state.chapter, open == u.id, tamil, first = i == 0,
                                onToggle = { open = if (open == u.id) null else u.id },
                            )
                        }
                    }
                    // M4-6: whose text this is and on what terms, under every chapter.
                    state.sources.firstOrNull { it.id == state.source }?.let { src ->
                        item(key = "attribution") {
                            Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HDivider()
                                Text(
                                    listOf(src.attribution.ifBlank { src.name }, src.licence).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                                )
                                if (tamil) {
                                    Text(
                                        "தமிழ்: வரைவு மொழிபெயர்ப்பு; திருத்தங்கள் வரவேற்கப்படுகின்றன.",
                                        style = Ts.type.captionSmall, color = c.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.background(c.surface2)) {
            HDivider(thickness = 1.5.dp)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val last = book?.chapters ?: Int.MAX_VALUE
                if (state.passage.chapter > 1) TsPillButton("‹ ${tr("அதி", "Ch")} ${state.passage.chapter - 1}", { vm.move(-1) }) else Box(Modifier.size(1.dp))
                TsPillButton(tr("வசனங்களுடன் படிக்க", "Read with verses"), { onReadWithVerses(state.passage) }, style = PillStyle.Filled)
                if (state.passage.chapter < last) TsPillButton("${tr("அதி", "Ch")} ${state.passage.chapter + 1} ›", { vm.move(+1) }) else Box(Modifier.size(1.dp))
            }
        }
    }
}

@Composable
private fun IntroCard(u: CommentaryUnit, tamil: Boolean) {
    val c = Ts.colors
    val shape = RoundedCornerShape(16.dp)
    var expanded by rememberSaveable(u.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(shape).background(c.surface2).border(1.5.dp, c.line, shape)
            .clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(tr("அதிகார அறிமுகம்", "Chapter introduction"), Modifier.weight(1f), color = c.amber)
            Icon(if (expanded) TsIcons.ChevronUp else TsIcons.ChevronDown, null, Modifier.size(16.dp), tint = c.muted)
        }
        Text(
            u.paragraphs.filter { !it.footnote }.joinToString("\n\n") { it.text(tamil) },
            style = Ts.type.body, color = c.ink2,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnitSection(u: CommentaryUnit, chapter: Chapter?, open: Boolean, tamil: Boolean, first: Boolean, onToggle: () -> Unit) {
    val c = Ts.colors
    val r = u.verseRange
    val label = when {
        r == null -> tr("பகுதி", "Section")
        r.first == r.last -> tr("வசனம்", "Verse") + " ${r.first}"
        r.last == Int.MAX_VALUE -> tr("வசனங்கள்", "Verses") + " ${r.first}–"
        else -> tr("வசனங்கள்", "Verses") + " ${r.first}–${r.last}"
    }
    Column {
        if (!first) HDivider()
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Ts.type.cardTitle.copy(fontSize = 16.sp), color = c.accent, modifier = Modifier.weight(1f))
            Icon(if (open) TsIcons.ChevronUp else TsIcons.ChevronDown, null, Modifier.size(16.dp), tint = c.muted)
        }
        if (open) {
            val quote = remember(chapter, r) {
                if (chapter == null || r == null) null
                else chapter.verseNumbers.filter { it in r }.take(2).joinToString(" ") { v -> "$v " + chapter.verseText(v) }
            }
            if (!quote.isNullOrBlank()) {
                val leftLine = c.line2
                Text(
                    buildAnnotatedString {
                        val firstNum = quote.substringBefore(' ')
                        withStyle(SpanStyle(color = c.accent, fontSize = 10.sp)) { append(firstNum) }
                        append(" ")
                        append(quote.substringAfter(' ').take(240) + if (quote.length > 240) "…" else "")
                    },
                    style = Ts.type.scripture(15.sp, 1.7f), color = c.muted,
                    modifier = Modifier.fillMaxWidth()
                        .drawBehind { drawLine(leftLine, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx()) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            u.paragraphs.filter { !it.footnote }.forEach { p ->
                val anchor = p.anchor(tamil)
                Text(
                    buildAnnotatedString {
                        if (!anchor.isNullOrBlank()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.ink)) { append(anchor) }
                            append(" — ")
                        }
                        append(p.text(tamil))
                    },
                    style = Ts.type.body.copy(lineHeight = 27.sp), color = c.ink,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                )
            }
            val draft = u.paragraphs.any { it.taSource == "draft" } && tamil
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (draft) tr("தமிழ் வரைவு · சமூகத் திருத்தம் வரவேற்கப்படுகிறது", "Tamil draft · corrections welcome") else tr("சமூகத் திருத்தம்", "Community corrections"),
                    style = Ts.type.captionSmall, color = c.muted, modifier = Modifier.weight(1f),
                )
                Text(tr("திருத்தம் பரிந்துரை", "Suggest a correction"), style = Ts.type.labelSmall, color = c.accent)
            }
        }
    }
}
