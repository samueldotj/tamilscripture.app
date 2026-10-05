package com.tamilscripture.feature.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import android.graphics.Bitmap
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.data.settings.ReadingFormat
import com.tamilscripture.core.designsystem.component.HDivider
import com.tamilscripture.core.designsystem.component.IconBox
import com.tamilscripture.core.designsystem.component.Kicker
import com.tamilscripture.core.designsystem.component.PillStyle
import com.tamilscripture.core.designsystem.component.TsChip
import com.tamilscripture.core.designsystem.component.TsPillButton
import com.tamilscripture.core.designsystem.component.VDivider
import com.tamilscripture.core.designsystem.component.VerseImage
import com.tamilscripture.core.designsystem.icon.TsIcons
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
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Callbacks into the app's navigation; the reader never navigates by itself. */
class ReaderNav(
    val back: () -> Unit,
    val picker: (Passage) -> Unit,
    val commentary: (Passage) -> Unit,
    val search: () -> Unit,
    val home: () -> Unit,
    /** Opens the passage in another window (FF-9). */
    val newWindow: (Passage) -> Unit,
    /** Study pages (M8): a Strong's number, a person, a place. */
    val strongs: (String) -> Unit,
    val person: (String) -> Unit,
    val place: (String) -> Unit,
    /** Present mode from a verse (M8-8). */
    val present: (Passage) -> Unit,
    /** The account screen, to sign in (highlights and notes need an account, design §13.2). */
    val account: () -> Unit = {},
    /** A cross-reference opened as its own reader, so Back returns to the verse it came from. */
    val follow: ((Passage) -> Unit)? = null,
)

private class VerseActions(
    val onPlayHere: () -> Unit,
    val onCommentary: () -> Unit,
    val onCrossRefs: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
    /** The reader's own marks (M6): kept on the device, and in the account when signed in. */
    val onBookmark: () -> Unit,
    val onNote: () -> Unit,
    val onHighlight: () -> Unit,
    val onShareImage: () -> Unit,
    val onOriginal: () -> Unit,
    val onPeople: () -> Unit,
    /** M6-9c: the same passage on tamilscripture.com, in the browser. */
    val onWebsite: () -> Unit,
    val onLarge: () -> Unit,
)

/** "2 கொரி", "2 Cor": the book's first short form, or its name. */
private fun com.tamilscripture.core.model.Book.shortName(lang: UiLang): String =
    (if (lang == UiLang.Tamil) abbrTa else abbrEn).firstOrNull() ?: name(lang)

/** [select] false scrolls to [passage]'s verse without selecting it (Bible, Continue reading). */
@Composable
fun ReaderScreen(passage: Passage, wide: Boolean, compare: Boolean = false, nav: ReaderNav, select: Boolean = true) {
    val services = LocalAppServices.current
    val vm: ReaderViewModel = viewModel(key = "reader") { ReaderViewModel(services, passage, compare, select) }
    LaunchedEffect(passage) { vm.open(passage) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val audio by services.audio.state.collectAsStateWithLifecycle()
    val lang = LocalUiLang.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Ts.colors

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCrossRefs by rememberSaveable { mutableStateOf(false) }
    var showVersions by rememberSaveable { mutableStateOf(false) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showHighlight by rememberSaveable { mutableStateOf(false) }
    // The size while two fingers pinch; the saved setting otherwise.
    var pinchSize by remember { mutableStateOf<Int?>(null) }
    val textSize = pinchSize ?: settings.fontSize
    var askSignIn by rememberSaveable { mutableStateOf(false) }
    // Highlights and notes need an account, as on the website (design §13.2); bookmarks do not.
    val signedIn = services.graph.account.session.collectAsStateWithLifecycle().value != null
    /** The note open in the editor: its id, [NEW_NOTE], or null. */
    var editingNote by rememberSaveable { mutableStateOf<String?>(null) }
    val userData by vm.userData.collectAsStateWithLifecycle()
    val marks = remember(userData, state.passage.book, state.passage.chapter) { userData.marksFor(state.passage.book, state.passage.chapter, state.passage.version) }
    // Wide windows (M2-2, M8-4): one study pane beside the text, in tabs, sized by a divider.
    var paneTab by rememberSaveable { mutableStateOf<String?>(null) }
    var paneShare by rememberSaveable { mutableFloatStateOf(0.42f) }

    val book = state.book
    val chapter = state.chapter
    val second = state.second.takeIf { state.dual }
    // Reader and Standard flow in paragraphs; Study Bible and dual view go a verse at a time.
    val paragraphs = second == null && settings.format != ReadingFormat.Study
    val items = remember(chapter, second, settings.headings, paragraphs) {
        val a = if (paragraphs) chapter?.toParagraphs(settings.headings).orEmpty() else chapter?.toItems(settings.headings).orEmpty()
        if (second == null) a else dualItems(a, second.toItems(showHeadings = false))
    }
    // Study Bible: cross-references under each verse when the setting is on (the website's fmt-xref).
    val studyXrefs = remember(state.crossRefs, settings.crossRefs, settings.format) {
        if (!settings.crossRefs || settings.format != ReadingFormat.Study) emptyMap()
        else state.crossRefs.mapNotNull { (k, v) -> k.substringAfterLast('.').toIntOrNull()?.let { it to v } }.toMap()
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
        val i = items.indexOfVerse(v)
        if (i >= 0) listState.scrollToItem(i)
    }
    // Switching between one and two columns keeps the verse at the top in place, instead of a
    // pixel offset that means something else in the other layout.
    var topVerse by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { i -> items.getOrNull(i)?.verseNumber?.let { topVerse = it } }
    }
    LaunchedEffect(state.dual) {
        val v = topVerse ?: return@LaunchedEffect
        val i = items.indexOfVerse(v)
        if (i >= 0) listState.scrollToItem(i)
    }
    // A verse selected from the keyboard (M2-6) is scrolled into view.
    LaunchedEffect(state.selection.lastOrNull()) {
        val v = state.selection.lastOrNull() ?: return@LaunchedEffect
        val i = items.indexOfVerse(v)
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.animateScrollToItem(i)
    }
    // Follow the verse being read while listening (A-6.4).
    LaunchedEffect(playingVerse) {
        val v = playingVerse ?: return@LaunchedEffect
        val i = items.indexOfVerse(v)
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
        onBookmark = {
            val on = vm.toggleBookmark()
            Toast.makeText(context, if (on) tr2(lang, "குறிக்கப்பட்டது", "Bookmarked") else tr2(lang, "குறி நீக்கப்பட்டது", "Bookmark removed"), Toast.LENGTH_SHORT).show()
        },
        onNote = { if (signedIn) editingNote = vm.noteForSelection()?.id ?: NEW_NOTE else askSignIn = true },
        onHighlight = { if (signedIn) showHighlight = true else askSignIn = true },
        onShareImage = {
            vm.recordVerseAction("share-image")
            val ref = selectedRef ?: ""
            val text = state.selection.joinToString(" ") { chapter?.verseText(it).orEmpty() }
            val url = shareUrl(state.passage, book, state.selection)
            scope.launch { shareVerseImage(context, ref, text, url) }
        },
        onOriginal = { vm.recordVerseAction("original"); if (wide) paneTab = PANE_ORIGINAL else showOriginal = true },
        onPeople = { vm.recordVerseAction("people"); if (wide) paneTab = PANE_PEOPLE else showPeople = true },
        onWebsite = { vm.recordVerseAction("website"); openInBrowser(context, shareUrl(state.passage, book, state.selection)) },
        onLarge = { vm.recordVerseAction("large"); nav.present(state.passage.copy(verse = state.selection.firstOrNull())) },
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
            Entry(tr("மூல மொழி", "Original words")) { vm.recordVerseAction("original"); if (wide) paneTab = PANE_ORIGINAL else showOriginal = true }
            Entry(tr("நபர்கள் · இடங்கள்", "People and places")) { vm.recordVerseAction("people"); if (wide) paneTab = PANE_PEOPLE else showPeople = true }
            Entry(tr("நகலெடு", "Copy")) { vm.recordVerseAction("copy"); copyVerses(context, verseRef(v), listOf(verseText(v))) }
            Entry(tr("இங்கிருந்து காட்சிப்படுத்து", "Present from here")) { nav.present(state.passage.copy(verse = v)) }
            Entry(tr("படமாகப் பகிர்", "Share as image")) {
                vm.recordVerseAction("share-image")
                scope.launch { shareVerseImage(context, verseRef(v), verseText(v), shareUrl(state.passage, book, listOf(v))) }
            }
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

    // On and off with the version last compared with; the first time, ask which.
    val onCompare: () -> Unit = { if (!vm.toggleCompare()) { showVersions = true } }
    val tabletop = rememberTabletop()
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
            .pinchFontSize({ settings.fontSize }, onLive = { pinchSize = it }, onDone = { n -> vm.updateSettings { it.copy(fontSize = n) } })
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && !e.isCtrlPressed && e.key == Key.P) {
                    nav.present(state.passage.copy(verse = state.selection.firstOrNull()))
                    return@onPreviewKeyEvent true
                }
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
        // A foldable on a table uses the stacked layout, whatever its width, to split at the hinge.
        if (wide && tabletop == null) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                WideTopBar(title, versionLabel, hasAudio, { nav.picker(state.passage) }, nav.search, { play() }, { showSettings = true }, state.compare != null, onCompare, nav.home)
                // The pane shows a study tab, or commentary when that is on; dual view has no pane.
                val tab = if (state.dual) null else paneTab ?: if (settings.commentary) PANE_COMMENTARY else null
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val rowWidth = maxWidth
                    Row(Modifier.fillMaxSize()) {
                        WideRail(state, settings, items, vm, Modifier.width(200.dp).fillMaxSize())
                        VDivider()
                        Box(Modifier.weight(if (tab != null) 1f - paneShare else 1f).fillMaxSize().swipeChapters(vm::previousChapter, vm::nextChapter)) {
                            Column(Modifier.fillMaxSize()) {
                                Text(
                                    title, style = Ts.type.headline.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                                    color = c.ink, modifier = Modifier.padding(start = 46.dp, top = 28.dp, bottom = 4.dp),
                                )
                                ChapterBody(
                                    state, items, (textSize - 2).coerceAtLeast(15), 1.85f, settings.footnotes, listState, playingVerse, sourceName,
                                    PaddingValues(start = 36.dp, end = 36.dp, top = 8.dp, bottom = 160.dp), vm, nav, Modifier.fillMaxSize(),
                                    showInlineCommentary = false, dualLabels = dualLabels, dualColumns = true, interactions = interactions,
                                    marks = marks, onOpenNote = { n -> editingNote = n.id }, notesInMargin = tab == null, format = settings.format, xrefs = studyXrefs, signedIn = signedIn, onAskSignIn = { askSignIn = true },
                                )
                            }
                            ActionCardOverlay(state.selection.isNotEmpty(), selectedRef, hasAudio, vm, actions, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                        }
                        if (tab != null) {
                            PaneDivider(paneShare, (rowWidth - 200.dp).coerceAtLeast(1.dp)) { paneShare = it }
                            Column(Modifier.weight(paneShare).fillMaxSize().background(c.pane)) {
                                PaneTabs(tab, onTab = { paneTab = it }, onClose = {
                                    if (tab == PANE_COMMENTARY) vm.updateSettings { it.copy(commentary = false) }
                                    paneTab = null
                                })
                                val v = state.selection.firstOrNull()
                                when (tab) {
                                    PANE_COMMENTARY -> CommentaryPane(state, settings, Modifier.weight(1f).fillMaxWidth())
                                    PANE_PEOPLE -> PeoplePlacesContent(
                                        title, state.passage.book, state.passage.chapter, v, nav.person, nav.place,
                                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                                    )
                                    else -> OriginalWordsContent(
                                        book?.label(lang, state.passage.chapter, v ?: 1) ?: "", state.passage.book, state.passage.chapter, v ?: 1, nav.strongs,
                                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                                    )
                                }
                            }
                        }
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
                ReaderTopBar(
                    title, versionLabel, hasAudio, settings.commentary, nav.back, { nav.picker(state.passage) }, { play() }, { showSettings = true }, state.compare != null, onCompare,
                    shortTitle = book?.let { "${it.shortName(lang)} ${state.passage.chapter}" } ?: title,
                )
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
                        state, items, textSize, 1.8f, settings.footnotes, listState, playingVerse, sourceName,
                        PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 220.dp), vm, nav,
                        Modifier.widthIn(max = if (state.dual && columns) 1100.dp else 720.dp).fillMaxSize(),
                        showInlineCommentary = !state.dual, dualLabels = dualLabels, dualColumns = columns, interactions = interactions,
                        marks = marks, onOpenNote = { n -> editingNote = n.id }, format = settings.format, xrefs = studyXrefs,
                        signedIn = signedIn, onAskSignIn = { askSignIn = true },
                    )
                    ActionCardOverlay(
                        state.selection.isNotEmpty(), selectedRef, hasAudio, vm, actions,
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                val bottom: @Composable () -> Unit = {
                    if (audio.active) {
                        PlayingBar(audio, state, services.audio, openPlaying)
                    } else {
                        val m = state.manifest
                        ChapterNavBar(
                            prevLabel = chapter?.prev?.let { r -> m?.book(r.book)?.label(lang, r.chapter) },
                            nextLabel = chapter?.next?.let { r -> m?.book(r.book)?.label(lang, r.chapter) },
                            position = book?.let { "${state.passage.chapter} / ${it.chapters}" } ?: "",
                            prevShort = chapter?.prev?.let { r -> m?.book(r.book)?.shortName(lang)?.let { "$it ${r.chapter}" } },
                            nextShort = chapter?.next?.let { r -> m?.book(r.book)?.shortName(lang)?.let { "$it ${r.chapter}" } },
                            onPrev = vm::previousChapter, onNext = vm::nextChapter,
                        )
                    }
                }
                // Tabletop (M2-4): text above the hinge, the player or chapter controls below it.
                if (tabletop != null) {
                    Column(Modifier.fillMaxWidth().height(tabletop.belowHingeTop).background(c.surface2)) {
                        Box(Modifier.fillMaxWidth().height(tabletop.hinge))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { bottom() }
                    }
                } else {
                    bottom()
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
    if (showOriginal) {
        val v = state.selection.firstOrNull() ?: 1
        OriginalWordsSheet(
            book?.label(lang, state.passage.chapter, v) ?: "", state.passage.book, state.passage.chapter, v,
            onStrongs = { n -> showOriginal = false; nav.strongs(n) },
        ) { showOriginal = false }
    }
    if (showPeople) {
        PeoplePlacesSheet(
            title, state.passage.book, state.passage.chapter, state.selection.firstOrNull(),
            onPerson = { id -> showPeople = false; nav.person(id) },
            onPlace = { id -> showPeople = false; nav.place(id) },
        ) { showPeople = false }
    }
    if (askSignIn) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askSignIn = false },
            containerColor = c.surface,
            title = { Text(tr("உள்நுழையுங்கள்", "Sign in"), color = c.ink) },
            text = {
                Text(
                    tr("முனைப்புகளும் குறிப்புகளும் உங்கள் tamilscripture.com கணக்கில் சேமிக்கப்படுகின்றன; இணையதளத்திலும் தெரியும்.",
                        "Highlights and notes are kept in your tamilscripture.com account, and show on the website too."),
                    color = c.ink2,
                )
            },
            confirmButton = { androidx.compose.material3.TextButton({ askSignIn = false; nav.account() }) { Text(tr("உள்நுழை", "Sign in"), color = c.accent) } },
            dismissButton = { androidx.compose.material3.TextButton({ askSignIn = false }) { Text(tr("வேண்டாம்", "Not now"), color = c.ink2) } },
        )
    }
    if (showHighlight) {
        val current = state.selection.firstOrNull()?.let { marks[it]?.color }
        HighlightSheet(selectedRef ?: "", current, lang == UiLang.Tamil, onPick = { color ->
            showHighlight = false
            vm.highlight(color)
            vm.clearSelection()
        }) { showHighlight = false }
    }
    editingNote?.let { id ->
        val note = userData.notes.firstOrNull { it.id == id }
        val ref = note?.let { n -> book?.label(lang, n.chapter, n.verseStart, n.verseEnd.takeIf { it != n.verseStart }) } ?: selectedRef ?: ""
        NoteSheet(
            ref, note,
            onSave = { body -> editingNote = null; vm.saveNote(note, body); vm.clearSelection() },
            onDelete = note?.let { n -> { editingNote = null; vm.deleteNote(n) } },
        ) { editingNote = null }
    }
    if (showVersions) {
        VersionSheet(
            state.manifest?.versions.orEmpty(), state.passage.version, { code -> showVersions = false; vm.setVersion(code) },
            compare = state.compare, onCompare = { code -> showVersions = false; vm.setCompare(code) },
        ) { showVersions = false }
    }
}

private const val PANE_COMMENTARY = "commentary"
private const val NEW_NOTE = "new"

private fun tr2(lang: UiLang, tamil: String, english: String) = if (lang == UiLang.Tamil) tamil else english
private const val PANE_PEOPLE = "people"
private const val PANE_ORIGINAL = "original"

/** The study pane's tabs (M8-4) and a close button. */
@Composable
private fun PaneTabs(tab: String, onTab: (String) -> Unit, onClose: () -> Unit) {
    val c = Ts.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tabs scroll when the pane is narrow (Tamil labels are long); close stays in reach.
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val pad = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            TsChip(tr("விளக்கவுரை", "Commentary"), tab == PANE_COMMENTARY, { onTab(PANE_COMMENTARY) }, contentPadding = pad)
            TsChip(tr("நபர்கள் · இடங்கள்", "People · places"), tab == PANE_PEOPLE, { onTab(PANE_PEOPLE) }, contentPadding = pad)
            TsChip(tr("மூல மொழி", "Original"), tab == PANE_ORIGINAL, { onTab(PANE_ORIGINAL) }, contentPadding = pad)
        }
        IconBox(TsIcons.Close, tr("பலகத்தை மூடு", "Close pane"), onClose)
    }
    HDivider()
}

/**
 * The divider between text and pane (M2-3): drag it, or focus it and use the arrow keys.
 * The pane keeps between a quarter and two thirds of the width, snapping to a third and a
 * half when released near them.
 */
@Composable
private fun PaneDivider(share: Float, width: Dp, onChange: (Float) -> Unit) {
    val c = Ts.colors
    val density = LocalDensity.current
    val total = with(density) { width.toPx() }
    var focused by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(share)
    fun snap(f: Float) = listOf(1f / 3f, 0.5f).firstOrNull { abs(it - f) < 0.03f } ?: f
    Box(
        Modifier
            .width(12.dp)
            .fillMaxHeight()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onChange((latest + 0.05f).coerceIn(0.25f, 0.67f)); true }
                    Key.DirectionRight -> { onChange((latest - 0.05f).coerceIn(0.25f, 0.67f)); true }
                    else -> false
                }
            }
            .pointerInput(total) {
                detectHorizontalDragGestures(onDragEnd = { onChange(snap(latest)) }) { change, dx ->
                    change.consume()
                    onChange((latest - dx / total).coerceIn(0.25f, 0.67f))
                }
            }
            .semantics { contentDescription = "Resize pane" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(if (focused) 3.dp else 1.dp).fillMaxHeight().background(if (focused) c.accent else c.line))
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(c.lineStrong))
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
            onBookmark = a.onBookmark, onCopy = a.onCopy, onShare = a.onShare, onNote = a.onNote, onHighlight = a.onHighlight,
            onOriginal = a.onOriginal, onPeople = a.onPeople, onShareImage = a.onShareImage, onWebsite = a.onWebsite, onLarge = a.onLarge,
            // Never more than about half the window, so the verse it is about stays in view.
            modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp),
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
    marks: Map<Int, VerseMarks> = emptyMap(),
    onOpenNote: (com.tamilscripture.core.model.UserNote) -> Unit = {},
    notesInMargin: Boolean = false,
    format: ReadingFormat = ReadingFormat.Study,
    xrefs: Map<Int, List<com.tamilscripture.core.model.CrossRef>> = emptyMap(),
    signedIn: Boolean = false,
    onAskSignIn: () -> Unit = {},
) {
    val c = Ts.colors
    when {
        state.chapter == null && state.error -> Column(modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("இந்த அதிகாரத்தைத் திறக்க இணைய இணைப்பு தேவை.", "This chapter needs a connection to open."), style = Ts.type.body, color = c.ink2)
            TsPillButton(tr("மீண்டும் முயல்", "Try again"), onClick = vm::retry, style = PillStyle.Filled)
            // M1-9f: the way out of this for good.
            vm.packFor(state.passage.version)?.let { (id, size) ->
                Text(
                    tr("இணைப்பின்றி வாசிக்க ${state.versionShort} பதிவிறக்குங்கள் (${mb(size)}); இணைப்பு கிடைத்ததும் பதிவிறக்கம் தொடங்கும்.",
                        "Download ${state.versionShort} (${mb(size)}) to read without a connection; it starts when you are back online."),
                    style = Ts.type.caption, color = c.muted,
                )
                TsPillButton(tr("பதிவிறக்கு", "Download"), onClick = { vm.download(id) })
            }
        }
        state.chapter == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
        else -> Column(modifier) {
            state.offer?.let { (id, size) -> OfferBanner(state.versionShort, size, { vm.download(id) }, vm::declineOffer) }
            ReaderTextList(
                items, state.selection, playingVerse, fontSize, lineHeight, footnotes,
                commentary = if (showInlineCommentary) state.commentary else null, commentaryName = sourceName,
                listState = listState, chapterKey = state.passage.chapterKey(), contentPadding = padding,
                onTapVerse = vm::tapVerse, onVerseRead = vm::verseRead,
                onOpenCommentary = { nav.commentary(state.passage) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                dualLabels = dualLabels, dualColumns = dualColumns, interactions = interactions,
                textLocale = state.manifest?.version(state.passage.version)?.lang?.let(::LocaleList),
                secondLocale = state.compare?.let { state.manifest?.version(it)?.lang }?.let(::LocaleList),
                marks = marks, onOpenNote = onOpenNote, heat = state.heat, notesInMargin = notesInMargin && !state.dual,
                format = format,
                xrefs = xrefs, manifest = state.manifest, version = state.passage.version,
                penColor = vm.penColor,
                onPen = { v, stroke, quote -> if (signedIn) vm.pen(v, stroke.start, stroke.end, quote, stroke.erase) else onAskSignIn() },
                onOpenRef = { vid ->
                    val p = Passage(state.passage.version, vid.book, vid.chapter, vid.verse)
                    nav.follow?.invoke(p) ?: vm.open(p)
                },
            )
        }
    }
}

private fun mb(bytes: Long) = "%.1f MB".format(bytes / 1_048_576.0)

/** M1-9f: offered once a reader has read ten chapters of a version online. */
@Composable
private fun OfferBanner(version: String, size: Long, onDownload: () -> Unit, onDecline: () -> Unit) {
    val c = Ts.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth().clip(shape).background(c.surface2)
            .border(1.dp, c.line, shape).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            tr("$version இணைப்பின்றி வாசிக்கவா? ${mb(size)}", "Read $version without a connection? ${mb(size)}"),
            style = Ts.type.caption, color = c.ink2, modifier = Modifier.weight(1f),
        )
        TsPillButton(tr("வேண்டாம்", "Not now"), onClick = onDecline, style = PillStyle.Ghost, height = 34.dp, textStyle = Ts.type.labelSmall)
        TsPillButton(tr("பதிவிறக்கு", "Download"), onClick = onDownload, style = PillStyle.Filled, height = 34.dp, textStyle = Ts.type.labelSmall)
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
    Key.J, Key.DirectionDown -> { selectAdjacent(state, vm, +1); true }
    Key.K, Key.DirectionUp -> { selectAdjacent(state, vm, -1); true }
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

/**
 * Two fingers pinch the text larger or smaller, as in a browser: the size follows the fingers
 * in whole sp steps ([onLive]) and is kept when they lift ([onDone]). One finger is left to
 * scrolling, swipes and taps; the pinch is taken before the list sees it, so it does not scroll.
 */
internal fun Modifier.pinchFontSize(current: () -> Int, onLive: (Int?) -> Unit, onDone: (Int) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val base = current()
        var scale = 1f
        var pinching = false
        var size = base
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val down = e.changes.count { it.pressed }
            if (down == 0) break
            if (down >= 2) {
                pinching = true
                scale *= e.calculateZoom()
                size = (base * scale).roundToInt().coerceIn(15, 30)
                onLive(size)
                e.changes.forEach { it.consume() }
            } else if (pinching) {
                // One finger left after a pinch: it neither scrolls nor taps.
                e.changes.forEach { it.consume() }
            }
        }
        if (pinching) {
            onLive(null)
            if (size != base) onDone(size)
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

/**
 * Opens [url] in the browser, never in this app: tamilscripture.com links are the app's own
 * App Links, so a plain VIEW intent would come straight back here.
 */
private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    intent.selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
}

private fun copyVerses(context: Context, ref: String?, texts: List<String>) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(ref, texts.joinToString(" ") + "\n— " + (ref ?: "")))
}

/** M8-6: the verse as a picture, made off the main thread, then the share sheet. */
private suspend fun shareVerseImage(context: Context, ref: String, text: String, url: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "verse.png")
        val bitmap = VerseImage.render(context, text, ref)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, context.packageName + ".share", file)
    }
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "$ref\n$url")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, ref))
}

private fun shareVerses(context: Context, ref: String?, texts: List<String>, url: String) {
    val text = texts.joinToString(" ") + "\n— " + (ref ?: "") + "\n" + url
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), ref))
}
