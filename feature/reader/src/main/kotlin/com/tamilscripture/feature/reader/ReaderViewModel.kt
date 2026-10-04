package com.tamilscripture.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamilscripture.core.data.content.ContentSource
import com.tamilscripture.core.data.settings.Settings
import com.tamilscripture.core.model.Book
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentarySource
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.CrossRef
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.UserNote
import com.tamilscripture.core.model.Passage
import com.tamilscripture.core.services.AppServices
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderState(
    val passage: Passage,
    val manifest: ContentManifest? = null,
    val chapter: Chapter? = null,
    val source: ContentSource? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Selected verses (tapping verse numbers extends the selection, A-2.5). */
    val selection: List<Int> = emptyList(),
    val crossRefs: Map<String, List<CrossRef>> = emptyMap(),
    val commentarySources: List<CommentarySource> = emptyList(),
    val commentary: CommentaryChapter? = null,
    val commentaryLoading: Boolean = false,
    /** Dual view (A-3.3): the second version's code and its copy of the chapter. */
    val compare: String? = null,
    val second: Chapter? = null,
    /** M1-9f: after 10 chapters read online, offer this version's pack (id, size in bytes). */
    val offer: Pair<String, Long>? = null,
) {
    val book: Book? get() = manifest?.book(passage.book)
    val versionShort: String get() = manifest?.version(passage.version)?.short ?: passage.version
    val compareShort: String? get() = compare?.let { manifest?.version(it)?.short ?: it }
    /** Dual view shows once the second version's chapter is here. */
    val dual: Boolean get() = compare != null && second != null
}

/** Holds one chapter at a time; chapter changes replace the content in place (A-2.4). */
class ReaderViewModel(private val services: AppServices, initial: Passage, compareAtStart: Boolean = false) : ViewModel() {
    /** Two columns in this reader; off unless the reader was opened to compare. */
    private var comparing = compareAtStart

    private val graph = services.graph
    private val mutable = MutableStateFlow(ReaderState(initial))
    val state: StateFlow<ReaderState> = mutable
    val settings: StateFlow<Settings> = graph.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    /** Highlights, notes, bookmarks (M6). */
    val userData = graph.userData.data

    private var loadJob: Job? = null
    private var commentaryJob: Job? = null
    private var secondJob: Job? = null

    init {
        viewModelScope.launch {
            graph.content.manifest.collect { m -> if (m != null) mutable.update { it.copy(manifest = m) } }
        }
        load(initial)
        viewModelScope.launch {
            var lastSource: String? = null
            var lastOn: Boolean? = null
            var lastCompare: String? = null
            settings.collect { s ->
                if (s.compare != lastCompare) {
                    lastCompare = s.compare
                    loadSecond(state.value.passage)
                }
                if (s.commentary != lastOn || s.commentarySource != lastSource) {
                    lastOn = s.commentary
                    lastSource = s.commentarySource
                    loadCommentary()
                }
            }
        }
    }

    fun open(passage: Passage) {
        if (passage.chapterKey() == state.value.passage.chapterKey()) {
            mutable.update { it.copy(passage = passage, selection = listOfNotNull(passage.verse)) }
            return
        }
        load(passage)
    }

    fun retry() = load(state.value.passage)

    private fun load(p: Passage) {
        loadJob?.cancel()
        mutable.update {
            it.copy(passage = p, chapter = if (it.chapter?.book == p.book && it.chapter.chapter == p.chapter && it.chapter.version == p.version) it.chapter else null,
                loading = true, error = false, selection = listOfNotNull(p.verse), crossRefs = emptyMap(), commentary = null)
        }
        loadJob = viewModelScope.launch {
            graph.content.chapter(p.version, p.book, p.chapter)
                .catch { mutable.update { s -> s.copy(loading = false, error = true) } }
                .collect { loaded ->
                    mutable.update { s -> s.copy(chapter = loaded.value, source = loaded.source, loading = false, error = false) }
                    // Not from a pack: read online, now or when it was prefetched into the cache.
                    if (loaded.source != ContentSource.Pack) countOnline(p.version)
                    graph.stats.record("view", version = p.version, book = p.book, chapter = p.chapter, source = loaded.source.name.lowercase(),
                        action = settings.value.compare?.takeIf { comparing && it != p.version }?.let { "dual:$it" })
                }
            graph.settings.update { it.copy(lastRead = p.copy(verse = p.verse ?: state.value.selection.firstOrNull()), version = p.version) }
            graph.userData.recordVisit(p.book, p.chapter, p.version, p.verse)
            neighbours(p)
            val refs = graph.content.crossRefs(p.book, p.chapter)
            mutable.update { s -> if (s.passage.chapterKey() == p.chapterKey()) s.copy(crossRefs = refs) else s }
        }
        loadCommentary()
        loadSecond(p)
    }

    private fun loadSecond(p: Passage) {
        secondJob?.cancel()
        val code = settings.value.compare?.takeIf { comparing && it != p.version }
        mutable.update { it.copy(compare = code, second = it.second?.takeIf { c -> c.version == code && c.book == p.book && c.chapter == p.chapter }) }
        if (code == null) return
        secondJob = viewModelScope.launch {
            graph.content.chapter(code, p.book, p.chapter)
                // A version without this book (or offline and not downloaded) leaves the single view.
                .catch { mutable.update { s -> s.copy(second = null) } }
                .collect { loaded -> mutable.update { s -> if (s.passage.chapterKey() == p.chapterKey()) s.copy(second = loaded.value) else s } }
        }
    }

    private var counted: String? = null

    /** One count per chapter shown from the network; the offer appears from the tenth. */
    private fun countOnline(version: String) {
        val key = state.value.passage.chapterKey()
        if (counted == key) return
        counted = key
        viewModelScope.launch {
            var count = 0
            var declined = false
            graph.settings.update { s ->
                count = (s.onlineChapters[version] ?: 0) + 1
                declined = version in s.offersDeclined
                s.copy(onlineChapters = s.onlineChapters + (version to count))
            }
            refreshOffer(version, count, declined)
        }
    }

    private fun refreshOffer(version: String, count: Int, declined: Boolean) {
        val id = "bible.$version"
        val entry = graph.packs.catalogue.value?.entry(id)
        val show = entry != null && !graph.packs.hasBible(version) && !declined && count >= 10
        mutable.update { it.copy(offer = if (show) id to entry!!.size else null) }
    }

    /** Also offered when a chapter cannot open offline (M1-9f). */
    fun packFor(version: String): Pair<String, Long>? =
        graph.packs.catalogue.value?.entry("bible.$version")?.takeIf { !graph.packs.hasBible(version) }?.let { it.id to it.size }

    fun download(id: String) {
        graph.packs.download(id, settings.value.downloadWifiOnly)
        mutable.update { it.copy(offer = null) }
    }

    fun declineOffer() {
        val v = state.value.passage.version
        mutable.update { it.copy(offer = null) }
        updateSettings { it.copy(offersDeclined = it.offersDeclined + v) }
    }

    /** Compare with [code] now (and remember it), or stop comparing when null. */
    fun setCompare(code: String?) {
        comparing = code != null
        if (code != null) updateSettings { it.copy(compare = code) } else loadSecond(state.value.passage)
    }

    /**
     * The top bar's compare button: off, or on with the version last compared with. Returns
     * false when there is none yet, so the caller asks which.
     */
    fun toggleCompare(): Boolean {
        if (comparing) {
            comparing = false
            loadSecond(state.value.passage)
            return true
        }
        val remembered = settings.value.compare?.takeIf { it != state.value.passage.version } ?: return false
        comparing = true
        loadSecond(state.value.passage)
        graph.stats.record("verse", version = state.value.passage.version, action = "compare:$remembered")
        return true
    }

    private fun neighbours(p: Passage) {
        val c = state.value.chapter ?: return
        c.next?.let { graph.content.prefetch(p.version, it.book, it.chapter) }
        c.prev?.let { graph.content.prefetch(p.version, it.book, it.chapter) }
    }

    fun loadCommentary() {
        commentaryJob?.cancel()
        val p = state.value.passage
        commentaryJob = viewModelScope.launch {
            val s = settings.value
            val index = graph.commentary.index()
            mutable.update { it.copy(commentarySources = index?.sources.orEmpty()) }
            if (!s.commentary) {
                mutable.update { it.copy(commentary = null, commentaryLoading = false) }
                return@launch
            }
            mutable.update { it.copy(commentaryLoading = true) }
            val c = graph.commentary.chapter(s.commentarySource, p.book, p.chapter)
            mutable.update { it.copy(commentary = c, commentaryLoading = false) }
            if (c != null) graph.stats.record("commentary", book = p.book, chapter = p.chapter, action = s.commentarySource)
        }
    }

    /** Commentary for the dedicated commentary screen (2A), regardless of the inline setting. */
    fun loadCommentaryFor(source: String) {
        commentaryJob?.cancel()
        val p = state.value.passage
        commentaryJob = viewModelScope.launch {
            val index = graph.commentary.index()
            mutable.update { it.copy(commentarySources = index?.sources.orEmpty(), commentaryLoading = true) }
            val c = graph.commentary.chapter(source, p.book, p.chapter)
            mutable.update { it.copy(commentary = c, commentaryLoading = false) }
        }
    }

    fun nextChapter() = state.value.chapter?.next?.let { load(Passage(state.value.passage.version, it.book, it.chapter)) }
    fun previousChapter() = state.value.chapter?.prev?.let { load(Passage(state.value.passage.version, it.book, it.chapter)) }

    fun tapVerse(verse: Int) {
        mutable.update { s ->
            val sel = s.selection
            val next = when {
                sel.isEmpty() -> listOf(verse)
                verse in sel -> sel - verse
                else -> (sel + verse).sorted()
            }
            s.copy(selection = next)
        }
        state.value.selection.lastOrNull()?.let { v ->
            val p = state.value.passage
            graph.stats.record("verse", verse = "${p.book}.${p.chapter}.$v", version = p.version, action = "select")
        }
    }

    fun clearSelection() = mutable.update { it.copy(selection = emptyList()) }

    fun selectOnly(verse: Int) = mutable.update { it.copy(selection = listOf(verse)) }

    fun recordVerseAction(action: String) {
        val s = state.value
        val v = s.selection.firstOrNull() ?: return
        graph.stats.record("verse", verse = "${s.passage.book}.${s.passage.chapter}.$v", version = s.passage.version, action = action)
    }

    /** `read` events (ST-1): a verse was at least 60% visible for 2 s. */
    fun verseRead(verse: Int, dwellMs: Long) {
        val p = state.value.passage
        graph.stats.record("read", verse = "${p.book}.${p.chapter}.$verse", version = p.version, amount = dwellMs,
            source = state.value.source?.name?.lowercase())
    }

    /** Highlights the selected verses in [color], or removes their highlight when null. */
    fun highlight(color: HighlightColor?) {
        val s = state.value
        val sel = s.selection.ifEmpty { return }
        viewModelScope.launch {
            if (color == null) graph.userData.removeHighlight(s.passage.book, s.passage.chapter, sel)
            else graph.userData.setHighlight(s.passage.book, s.passage.chapter, sel, color)
        }
        recordVerseAction(if (color == null) "unhighlight" else "highlight")
    }

    /** Bookmarks the first selected verse, or removes its bookmark; returns whether it is now bookmarked. */
    fun toggleBookmark(): Boolean {
        val s = state.value
        val v = s.selection.firstOrNull() ?: return false
        val was = userData.value.bookmarks.any { it.book == s.passage.book && it.chapter == s.passage.chapter && it.verse == v }
        viewModelScope.launch { graph.userData.toggleBookmark(s.passage.book, s.passage.chapter, v, s.passage.version) }
        recordVerseAction("bookmark")
        return !was
    }

    /** The note to open for the selection: the one on exactly these verses, if any. */
    fun noteForSelection(): UserNote? {
        val s = state.value
        val sel = s.selection.ifEmpty { return null }
        return userData.value.notes.firstOrNull { it.book == s.passage.book && it.chapter == s.passage.chapter && it.verseStart == sel.first() && it.verseEnd == sel.last() }
    }

    fun saveNote(note: UserNote?, body: String) {
        val s = state.value
        val start = note?.verseStart ?: s.selection.firstOrNull() ?: return
        val end = note?.verseEnd ?: s.selection.lastOrNull() ?: return
        viewModelScope.launch { graph.userData.saveNote(note?.id, note?.book ?: s.passage.book, note?.chapter ?: s.passage.chapter, start, end, body) }
        if (note == null) recordVerseAction("note")
    }

    fun deleteNote(note: UserNote) {
        viewModelScope.launch { graph.userData.deleteNote(note.id) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { graph.settings.update(transform) }
    }

    fun setVersion(code: String) {
        val p = state.value.passage
        load(p.copy(version = code))
    }
}
