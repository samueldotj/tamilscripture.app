package com.tamilscripture.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.VDivider
import com.tamilscripture.core.designsystem.theme.Ts
import com.tamilscripture.core.media.AudioController
import com.tamilscripture.core.media.AudioState
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.model.UiLang
import com.tamilscripture.core.model.label
import com.tamilscripture.core.services.LocalAppServices
import com.tamilscripture.core.services.LocalUiLang
import com.tamilscripture.core.services.tr
import kotlin.math.abs

/** Callbacks into the app's navigation; the reader never navigates by itself. */
class ReaderNav(
    val back: () -> Unit,
    val picker: (Passage) -> Unit,
    val commentary: (Passage) -> Unit,
    val search: () -> Unit,
    val home: () -> Unit,
    /** Opens the passage in another window (FF-9). */
    val newWindow: (Passage) -> Unit,
)

private class VerseActions(
    val onPlayHere: () -> Unit,
    val onCommentary: () -> Unit,
    val onCrossRefs: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
    /** Bookmark, note and highlight arrive with sign-in (roadmap M6). */
    val onSignInFeature: () -> Unit,
)

@Composable
fun ReaderScreen(passage: Passage, wide: Boolean, nav: ReaderNav) {
    val services = LocalAppServices.current
    val vm: ReaderViewModel = viewModel(key = "reader") { ReaderViewModel(services, passage) }
    LaunchedEffect(passage) { vm.open(passage) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val audio by services.audio.state.collectAsStateWithLifecycle()
    val lang = LocalUiLang.current
    val context = LocalContext.current
    val c = Ts.colors

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCrossRefs by rememberSaveable { mutableStateOf(false) }
    var showVersions by rememberSaveable { mutableStateOf(false) }

    val book = state.book
    val chapter = state.chapter
    val second = state.second.takeIf { state.dual }
    val items = remember(chapter, second, settings.headings) {
        val a = chapter?.toItems(settings.headings).orEmpty()
        if (second == null) a else dualItems(a, second.toItems(showHeadings = false))
    }
    val versionLabel = state.versionShort + (state.compareShort?.let { " + $it" } ?: "")
    val dualLabels = if (state.dual) state.versionShort to (state.compareShort ?: "") else null
    val listState = rememberLazyListState()
    val hasAudio = services.audio.hasAudio(state.manifest, state.passage.version)
    val title = book?.label(lang, state.passage.chapter) ?: ""
    val playingVerse = audio.verse.takeIf {
        audio.active && audio.book == state.passage.book && audio.chapter == state.passage.chapter && audio.version == state.passage.version
    }
    val sourceName = state.commentarySources.firstOrNull { it.id == settings.commentarySource }?.short

    LaunchedEffect(items, state.passage.verse) {
        val v = state.passage.verse ?: return@LaunchedEffect
        val i = items.indexOfFirst { it.verseNumber == v }
        if (i >= 0) listState.scrollToItem(i)
    }
    // Follow the verse being read while listening (A-6.4).
    LaunchedEffect(playingVerse) {
        val v = playingVerse ?: return@LaunchedEffect
        val i = items.indexOfFirst { it.verseNumber == v }
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.animateScrollToItem(i)
    }

    val selectedRef = book?.let { b ->
        val sel = state.selection
        if (sel.isEmpty()) null else b.label(lang, state.passage.chapter, sel.first(), sel.last().takeIf { sel.size > 1 })
    }

    fun play(fromVerse: Int? = null) {
        val b = book ?: return
        services.audio.play(state.passage.version, b, state.passage.chapter, fromVerse)
    }

    val actions = VerseActions(
        onPlayHere = { vm.recordVerseAction("listen"); play(state.selection.firstOrNull()) },
        onCommentary = {
            vm.recordVerseAction("commentary")
            nav.commentary(state.passage.copy(verse = state.selection.firstOrNull()))
        },
        onCrossRefs = { vm.recordVerseAction("xref"); showCrossRefs = true },
        onCopy = {
            vm.recordVerseAction("copy")
            copyVerses(context, selectedRef, state.selection.map { chapter?.verseText(it).orEmpty() })
        },
        onShare = {
            vm.recordVerseAction("share")
            shareVerses(context, selectedRef, state.selection.map { chapter?.verseText(it).orEmpty() }, shareUrl(state.passage, book, state.selection))
        },
        onSignInFeature = {
            Toast.makeText(context, if (lang == UiLang.Tamil) "உள்நுழைவுடன் விரைவில் வருகிறது" else "Coming with sign-in", Toast.LENGTH_SHORT).show()
        },
    )

    // Right-click menu and drag-out (M2-7, M2-8); each acts on the verse it was opened on.
    val verseRef: (Int) -> String = { v -> book?.label(lang, state.passage.chapter, v) ?: "" }
    val verseText: (Int) -> String = { v -> chapter?.verseText(v).orEmpty() }
    val interactions = VerseInteractions(
        menu = { v, dismiss ->
            @Composable
            fun Entry(label: String, action: () -> Unit) = DropdownMenuItem(
                text = { Text(label, style = Ts.type.body, color = c.ink) },
                onClick = { dismiss(); vm.selectOnly(v); action() },
            )
            if (hasAudio) Entry(tr("இங்கிருந்து கேள்", "Listen from here")) { vm.recordVerseAction("listen"); play(v) }
            Entry(tr("விளக்கவுரை", "Commentary")) { vm.recordVerseAction("commentary"); nav.commentary(state.passage.copy(verse = v)) }
            Entry(tr("தொடர்புள்ள வசனங்கள்", "Cross-references")) { vm.recordVerseAction("xref"); showCrossRefs = true }
            Entry(tr("நகலெடு", "Copy")) { vm.recordVerseAction("copy"); copyVerses(context, verseRef(v), listOf(verseText(v))) }
            Entry(tr("புதிய சாளரத்தில் திற", "Open in new window")) { nav.newWindow(state.passage.copy(verse = v)) }
            Entry(tr("பகிர்", "Share")) {
                vm.recordVerseAction("share")
                shareVerses(context, verseRef(v), listOf(verseText(v)), shareUrl(state.passage, book, listOf(v)))
            }
        },
        dragText = { v -> verseText(v) + "\n— " + verseRef(v) },
    )

    val openPlaying: () -> Unit = {
        val code = audio.book
        if (code != null) vm.open(Passage(audio.version ?: state.passage.version, code, audio.chapter, audio.verse))
    }

    val focus = remember { FocusRequester() }
    // Shortcuts work at every width: a tablet in portrait or a phone can have a keyboard too.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            // Landscape: keep text clear of a side navigation bar or cutout.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.End))
            .ctrlScrollFontSize { step -> vm.updateSettings { it.copy(fontSize = (it.fontSize + step).coerceIn(15, 30)) } }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.N) {
                    nav.newWindow(state.passage.copy(verse = state.selection.firstOrNull()))
                    return@onPreviewKeyEvent true
                }
                e.type == KeyEventType.KeyDown && !e.isCtrlPressed && handleKey(
                    e.key, state, state.commentarySources.map { it.id }, vm, nav, services.audio, audio.active,
                    play = { play(it) },
                    toggleCommentary = { vm.updateSettings { it.copy(commentary = !it.commentary) } },
                    setSource = { id -> vm.updateSettings { it.copy(commentarySource = id, commentary = true) } },
                    versions = { showVersions = true },
                )
            },
    ) {
        if (wide) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                WideTopBar(title, versionLabel, hasAudio, { nav.picker(state.passage) }, nav.search, { play() }, { showSettings = true }, nav.home)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    WideRail(state, settings, items, vm, Modifier.width(200.dp).fillMaxSize())
                    VDivider()
                    Box(Modifier.weight(1f).fillMaxSize().swipeChapters(vm::previousChapter, vm::nextChapter)) {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                title, style = Ts.type.headline.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                                color = c.ink, modifier = Modifier.padding(start = 46.dp, top = 28.dp, bottom = 4.dp),
                            )
                            ChapterBody(
                                state, items, (settings.fontSize - 2).coerceAtLeast(15), 1.85f, settings.footnotes, listState, playingVerse, sourceName,
                                PaddingValues(start = 36.dp, end = 36.dp, top = 8.dp, bottom = 160.dp), vm, nav, Modifier.fillMaxSize(),
                                showInlineCommentary = false, dualLabels = dualLabels, dualColumns = true, interactions = interactions,
                            )
                        }
                        ActionCardOverlay(state.selection.isNotEmpty(), selectedRef, hasAudio, vm, actions, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    }
                    if (settings.commentary && !state.dual) {
                        VDivider()
                        CommentaryPane(state, settings, Modifier.weight(1f).fillMaxSize().background(c.pane))
                    }
                }
                if (audio.active) PlayingBar(audio, state, services.audio, openPlaying)
                KeyHintStrip(
                    hints = listOf(
                        Hint(listOf("J", "K"), tr("வசனம்", "Verse")) { selectAdjacent(state, vm, +1) },
                        Hint(listOf("←", "→"), tr("அதிகாரம்", "Chapter")) { vm.nextChapter() },
                        Hint(listOf("1", "2", "3"), tr("ஆதாரம்", "Source")) { showSettings = true },
                        Hint(listOf("C"), tr("விளக்கவுரை மாற்று", "Toggle commentary")) { vm.updateSettings { it.copy(commentary = !it.commentary) } },
                        Hint(listOf("Space"), tr("ஒலி", "Audio")) { if (audio.active) services.audio.toggle() else play() },
                        Hint(listOf("/"), tr("தேடல்", "Search")) { nav.search() },
                    ),
                    trailing = Hint(listOf("V"), tr("மொழிபெயர்ப்பு", "Translation")) { showVersions = true },
                )
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ReaderTopBar(title, versionLabel, hasAudio, settings.commentary, nav.back, { nav.picker(state.passage) }, { play() }, { showSettings = true })
                if (settings.commentary && state.commentarySources.isNotEmpty()) {
                    CommentaryChipRow(
                        state.commentarySources, settings.commentarySource,
                        { id -> vm.updateSettings { it.copy(commentarySource = id) } },
                        { vm.updateSettings { it.copy(commentary = false) } },
                    )
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().swipeChapters(vm::previousChapter, vm::nextChapter), contentAlignment = Alignment.TopCenter) {
                    // Medium windows show dual view as columns; phones stack the versions (A-3.3).
                    val columns = maxWidth >= 600.dp
                    ChapterBody(
                        state, items, settings.fontSize, 1.8f, settings.footnotes, listState, playingVerse, sourceName,
                        PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 220.dp), vm, nav,
                        Modifier.widthIn(max = if (state.dual && columns) 1100.dp else 720.dp).fillMaxSize(),
                        showInlineCommentary = !state.dual, dualLabels = dualLabels, dualColumns = columns, interactions = interactions,
                    )
                    ActionCardOverlay(
                        state.selection.isNotEmpty(), selectedRef, hasAudio, vm, actions,
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                if (audio.active) {
                    PlayingBar(audio, state, services.audio, openPlaying)
                } else {
                    val m = state.manifest
                    ChapterNavBar(
                        prevLabel = chapter?.prev?.let { r -> m?.book(r.book)?.label(lang, r.chapter) },
                        nextLabel = chapter?.next?.let { r -> m?.book(r.book)?.label(lang, r.chapter) },
                        position = book?.let { "${state.passage.chapter} / ${it.chapters}" } ?: "",
                        onPrev = vm::previousChapter, onNext = vm::nextChapter,
                    )
                }
            }
        }
    }

    if (showSettings) {
        StudySettingsSheet(settings, state.commentarySources, vm::updateSettings, versionLabel, onVersions = { showSettings = false; showVersions = true }) { showSettings = false }
    }
    if (showCrossRefs) {
        val v = state.selection.firstOrNull()
        val refs = v?.let { state.crossRefs["${state.passage.book}.${state.passage.chapter}.$it"] }.orEmpty()
        CrossRefsSheet(selectedRef ?: "", refs, state.manifest, state.passage.version, onOpen = { vid ->
            showCrossRefs = false
            vm.open(Passage(state.passage.version, vid.book, vid.chapter, vid.verse))
        }) { showCrossRefs = false }
    }
    if (showVersions) {
        VersionSheet(
            state.manifest?.versions.orEmpty(), state.passage.version, { code -> showVersions = false; vm.setVersion(code) },
            compare = settings.compare, onCompare = { code -> showVersions = false; vm.setCompare(code) },
        ) { showVersions = false }
    }
}

@Composable
private fun ActionCardOverlay(
    visible: Boolean,
    reference: String?,
    hasAudio: Boolean,
    vm: ReaderViewModel,
    a: VerseActions,
    modifier: Modifier,
) {
    AnimatedVisibility(
        visible, modifier.widthIn(max = 560.dp),
        enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        VerseActionCard(
            reference ?: "", hasAudio, vm::clearSelection, a.onPlayHere, a.onCommentary, a.onCrossRefs,
            onBookmark = a.onSignInFeature, onCopy = a.onCopy, onShare = a.onShare, onNote = a.onSignInFeature, onHighlight = a.onSignInFeature,
        )
    }
}

@Composable
private fun PlayingBar(audio: AudioState, state: ReaderState, controller: AudioController, onTitle: () -> Unit) {
    val b = audio.book?.let { state.manifest?.book(it) }
    PlayerBar(audio, b?.label(LocalUiLang.current, audio.chapter, audio.verse) ?: "", controller::toggle, controller::cycleSpeed, controller::stop, onTitle)
}

@Composable
private fun ChapterBody(
    state: ReaderState,
    items: List<ReaderItem>,
    fontSize: Int,
    lineHeight: Float,
    footnotes: Boolean,
    listState: LazyListState,
    playingVerse: Int?,
    sourceName: String?,
    padding: PaddingValues,
    vm: ReaderViewModel,
    nav: ReaderNav,
    modifier: Modifier,
    showInlineCommentary: Boolean = true,
    dualLabels: kotlin.Pair<String, String>? = null,
    dualColumns: Boolean = false,
    interactions: VerseInteractions? = null,
) {
    val c = Ts.colors
    when {
        state.chapter == null && state.error -> Column(modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("இந்த அதிகாரத்தைத் திறக்க இணைய இணைப்பு தேவை.", "This chapter needs a connection to open."), style = Ts.type.body, color = c.ink2)
            TsPillButton(tr("மீண்டும் முயல்", "Try again"), onClick = vm::retry, style = PillStyle.Filled)
        }
        state.chapter == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
        else -> ReaderTextList(
            items, state.selection, playingVerse, fontSize, lineHeight, footnotes,
            commentary = if (showInlineCommentary) state.commentary else null, commentaryName = sourceName,
            listState = listState, chapterKey = state.passage.chapterKey(), contentPadding = padding,
            onTapVerse = vm::tapVerse, onVerseRead = vm::verseRead,
            onOpenCommentary = { nav.commentary(state.passage) }, modifier = modifier,
            dualLabels = dualLabels, dualColumns = dualColumns, interactions = interactions,
        )
    }
}

/** Left rail on 14″ (2D): commentary sources with their number keys, then the chapter's sections. */
@Composable
private fun WideRail(state: ReaderState, settings: Settings, items: List<ReaderItem>, vm: ReaderViewModel, modifier: Modifier) {
    val c = Ts.colors
    Column(modifier.background(c.surface2).padding(horizontal = 14.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Kicker(tr("விளக்கவுரை", "Commentary"), Modifier.padding(start = 8.dp, bottom = 6.dp))
        state.commentarySources.forEachIndexed { i, s ->
            val on = settings.commentary && s.id == settings.commentarySource
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) c.accent else Color.Transparent)
                    .clickable { vm.updateSettings { it.copy(commentarySource = s.id, commentary = true) } }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(s.short, style = Ts.type.label.copy(fontSize = 14.sp), color = if (on) c.onAccent else c.ink2, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${i + 1}", style = Ts.type.label, color = if (on) c.onAccent.copy(alpha = 0.6f) else c.faint)
            }
        }
        HDivider(Modifier.padding(vertical = 10.dp))
        Kicker(tr("பிரிவுகள்", "Sections"), Modifier.padding(start = 8.dp, bottom = 6.dp))
        val sections = remember(items) { items.mapIndexedNotNull { i, it -> (it as? ReaderItem.Heading)?.let { h -> i to h.text } } }
        val firstSelected = state.selection.firstOrNull()
        sections.forEachIndexed { si, (index, text) ->
            val nextIndex = sections.getOrNull(si + 1)?.first ?: items.size
            val verses = items.subList(index, nextIndex).mapNotNull { it.verseNumber }
            val range = verses.firstOrNull()?.let { f -> "$f–${verses.last()}" } ?: ""
            val on = firstSelected != null && firstSelected in verses
            Text(
                "$range $text",
                style = Ts.type.caption.copy(fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                color = if (on) c.ink else c.ink2,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable { verses.firstOrNull()?.let(vm::selectOnly) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** The commentary column on 14″ (2D): units as cards, the one for the selected verse emphasised. */
@Composable
private fun CommentaryPane(state: ReaderState, settings: Settings, modifier: Modifier) {
    val c = Ts.colors
    val lang = LocalUiLang.current
    val source = state.commentarySources.firstOrNull { it.id == settings.commentarySource }
    val sel = state.selection.firstOrNull()
    val listState = rememberLazyListState()
    val units = state.commentary?.units.orEmpty()
    LaunchedEffect(sel, units) {
        val i = units.indexOfFirst { u -> sel != null && u.verseRange?.contains(sel) == true }
        if (i >= 0) listState.animateScrollToItem(i + 1)
    }
    LazyColumn(
        modifier, state = listState,
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Kicker(
                    listOfNotNull(source?.short, source?.year, if (lang == UiLang.Tamil) "தமிழ்" else null).joinToString(" · "),
                    Modifier.weight(1f), color = c.accent,
                )
                Text(tr("வசனத்துடன் இணைந்து உருளும்", "Follows the selected verse"), style = Ts.type.caption, color = c.muted)
            }
        }
        if (state.commentaryLoading) item { CircularProgressIndicator(color = c.accent) }
        units.forEach { u ->
            item(key = u.id) {
                val on = sel != null && u.verseRange?.contains(sel) == true
                val r = u.verseRange
                val label = when {
                    r == null -> tr("அறிமுகம்", "Introduction")
                    r.first == r.last -> "${r.first}"
                    r.last == Int.MAX_VALUE -> "${r.first}–"
                    else -> "${r.first}–${r.last}"
                }
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(if (on) c.surface else Color.Transparent)
                        .border(1.dp, if (on) c.line2 else c.line, shape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(label, style = Ts.type.labelSmall, color = if (on) c.accent else c.muted)
                    u.paragraphs.filter { !it.footnote }.take(if (on) 60 else 2).forEach { p ->
                        Text(
                            p.text(lang == UiLang.Tamil),
                            style = if (on) Ts.type.body else Ts.type.body.copy(fontSize = 14.sp),
                            color = if (on) c.ink else c.ink2,
                            maxLines = if (on) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun selectAdjacent(state: ReaderState, vm: ReaderViewModel, delta: Int) {
    val verses = state.chapter?.verseNumbers.orEmpty()
    if (verses.isEmpty()) return
    val cur = state.selection.lastOrNull()
    val i = if (cur == null) (if (delta > 0) 0 else verses.lastIndex) else (verses.indexOf(cur) + delta).coerceIn(0, verses.lastIndex)
    vm.selectOnly(verses[i])
}

/** Reader shortcuts (FF-5, design 2D key-hint strip). */
private fun handleKey(
    key: Key,
    state: ReaderState,
    sources: List<String>,
    vm: ReaderViewModel,
    nav: ReaderNav,
    audio: AudioController,
    audioActive: Boolean,
    play: (Int?) -> Unit,
    toggleCommentary: () -> Unit,
    setSource: (String) -> Unit,
    versions: () -> Unit,
): Boolean = when (key) {
    Key.J -> { selectAdjacent(state, vm, +1); true }
    Key.K -> { selectAdjacent(state, vm, -1); true }
    Key.DirectionRight, Key.PageDown -> { vm.nextChapter(); true }
    Key.DirectionLeft, Key.PageUp -> { vm.previousChapter(); true }
    Key.C -> { toggleCommentary(); true }
    Key.V -> { versions(); true }
    Key.Spacebar -> { if (audioActive) audio.toggle() else play(state.selection.firstOrNull()); true }
    Key.Slash -> { nav.search(); true }
    Key.Escape -> if (state.selection.isNotEmpty()) { vm.clearSelection(); true } else false
    Key.One, Key.Two, Key.Three, Key.Four, Key.Five -> {
        sources.getOrNull(listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five).indexOf(key))?.let(setSource)
        true
    }
    else -> false
}

/** Ctrl + mouse wheel changes the text size, as in a browser (M2-7). */
private fun Modifier.ctrlScrollFontSize(onStep: (Int) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Scroll && e.keyboardModifiers.isCtrlPressed) {
                val dy = e.changes.first().scrollDelta.y
                if (dy != 0f) onStep(if (dy < 0) 1 else -1)
                e.changes.forEach { it.consume() }
            }
        }
    }
}

/** Horizontal swipe moves between chapters (A-2.4). */
private fun Modifier.swipeChapters(onPrev: () -> Unit, onNext: () -> Unit): Modifier = this.pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (abs(total) > 120.dp.toPx()) { if (total < 0) onNext() else onPrev() } },
    ) { _, dx -> total += dx }
}

private fun shareUrl(p: Passage, book: Book?, sel: List<Int>): String {
    val slug = book?.slug ?: p.book.lowercase()
    val range = when {
        sel.size > 1 -> "${sel.first()}-${sel.last()}"
        else -> sel.firstOrNull()?.toString()
    }
    return "https://www.tamilscripture.com/${p.version.lowercase()}/$slug/${p.chapter}" + (range?.let { ".$it" } ?: "")
}

private fun copyVerses(context: Context, ref: String?, texts: List<String>) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(ref, texts.joinToString(" ") + "\n— " + (ref ?: "")))
}

private fun shareVerses(context: Context, ref: String?, texts: List<String>, url: String) {
    val text = texts.joinToString(" ") + "\n— " + (ref ?: "") + "\n" + url
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), ref))
}
