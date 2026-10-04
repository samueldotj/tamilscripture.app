package com.tamilscripture.core.data.user

import com.tamilscripture.core.data.account.AccountRepository
import com.tamilscripture.core.data.account.Supabase
import com.tamilscripture.core.model.Bookmark
import com.tamilscripture.core.model.Highlight
import com.tamilscripture.core.model.HighlightColor
import com.tamilscripture.core.model.UserNote
import com.tamilscripture.core.model.Visit
import com.tamilscripture.core.model.contiguousRuns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID

/** Everything kept on the device, and what still has to reach the account. */
@Serializable
data class UserData(
    val highlights: List<Highlight> = emptyList(),
    val notes: List<UserNote> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val history: List<Visit> = emptyList(),
    val historyPaused: Boolean = false,
    /** Ids changed here and not yet sent. */
    val dirtyHighlights: Set<String> = emptySet(),
    val dirtyNotes: Set<String> = emptySet(),
    /** Ids deleted here and not yet deleted on the server. */
    val deletedHighlights: Set<String> = emptySet(),
    val deletedNotes: Set<String> = emptySet(),
    /** Visits recorded here and not yet sent (they have no server id yet). */
    val pendingVisits: List<Visit> = emptyList(),
    /** Set when the history should be cleared on the server at the next sync. */
    val clearHistory: Boolean = false,
    /** The account this data belongs to; null while signed out. */
    val owner: String? = null,
)

/**
 * Highlights, notes, bookmarks and history (roadmap M6). They work signed out, on the
 * device alone; signed in, [sync] sends local changes first and then takes the account's
 * rows as they are, so the phone matches the website (deletions there included). The
 * website's tables and RLS are used as they are (design §13.3); bookmarks stay on the
 * device until the website has a table for them.
 */
class UserDataRepository(
    private val dir: File,
    private val api: Supabase,
    private val account: AccountRepository,
    private val json: Json,
) {
    private val file = File(dir, "user.json")
    /** Defaults written out, so a row's colour reaches the server even when it is the default. */
    private val rowJson = Json(json) { encodeDefaults = true }
    private val lock = Mutex()
    private val state = MutableStateFlow(load())
    val data: StateFlow<UserData> = state

    private fun load(): UserData = runCatching { json.decodeFromString<UserData>(file.readText()) }.getOrDefault(UserData())

    private suspend fun edit(transform: (UserData) -> UserData) = lock.withLock {
        val next = transform(state.value)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, "user.tmp").apply { writeText(json.encodeToString(UserData.serializer(), next)) }.renameTo(file.also { it.delete() })
        }
        state.value = next
    }

    private fun now() = Instant.now().toString()

    // ---- highlights --------------------------------------------------------------------

    /** Marks [verses] in [color], replacing what they had, one row per run, as the website does. */
    suspend fun setHighlight(book: String, chapter: Int, verses: Collection<Int>, color: HighlightColor) = edit { d ->
        val (cleared, removed) = clearVerses(d.highlights, book, chapter, verses.toSet())
        val added = contiguousRuns(verses).map { r ->
            Highlight(UUID.randomUUID().toString(), book, chapter, r.first, r.last, color.key, updatedAt = now())
        }
        d.copy(
            highlights = cleared + added,
            dirtyHighlights = d.dirtyHighlights + added.map { it.id } + cleared.filter { it.id in removed.keys }.map { it.id },
            deletedHighlights = d.deletedHighlights + removed.filterValues { it }.keys,
        )
    }

    suspend fun removeHighlight(book: String, chapter: Int, verses: Collection<Int>) = edit { d ->
        val (cleared, removed) = clearVerses(d.highlights, book, chapter, verses.toSet())
        d.copy(
            highlights = cleared,
            dirtyHighlights = d.dirtyHighlights + cleared.filter { it.id in removed.keys }.map { it.id },
            deletedHighlights = d.deletedHighlights + removed.filterValues { it }.keys,
        )
    }

    /**
     * Takes [verses] out of every highlight of the chapter: rows wholly inside are deleted
     * (value true in the map), rows that only overlap are cut down to what is left
     * (the remainder keeps the row's id, value false; any second part gets a new id).
     */
    private fun clearVerses(all: List<Highlight>, book: String, chapter: Int, verses: Set<Int>): Pair<List<Highlight>, Map<String, Boolean>> {
        val touched = HashMap<String, Boolean>()
        val out = ArrayList<Highlight>()
        for (h in all) {
            if (h.book != book || h.chapter != chapter || (h.verseStart..h.verseEnd).none { it in verses }) {
                out += h
                continue
            }
            val keep = (h.verseStart..h.verseEnd).filter { it !in verses }
            val runs = contiguousRuns(keep)
            if (runs.isEmpty()) {
                touched[h.id] = true
                continue
            }
            touched[h.id] = false
            runs.forEachIndexed { i, r ->
                out += h.copy(id = if (i == 0) h.id else UUID.randomUUID().toString(), verseStart = r.first, verseEnd = r.last, charStart = null, charEnd = null, quote = null, updatedAt = now())
                    .also { if (i > 0) touched[it.id] = false }
            }
        }
        return out to touched
    }

    // ---- notes -------------------------------------------------------------------------

    suspend fun saveNote(id: String?, book: String, chapter: Int, verseStart: Int, verseEnd: Int, body: String): String {
        val noteId = id ?: UUID.randomUUID().toString()
        edit { d ->
            val existing = d.notes.firstOrNull { it.id == noteId }
            val note = existing?.copy(body = body, updatedAt = now()) ?: UserNote(noteId, book, chapter, verseStart, verseEnd, body, updatedAt = now())
            d.copy(notes = d.notes.filter { it.id != noteId } + note, dirtyNotes = d.dirtyNotes + noteId)
        }
        return noteId
    }

    suspend fun deleteNote(id: String) = edit { d ->
        d.copy(notes = d.notes.filter { it.id != id }, dirtyNotes = d.dirtyNotes - id, deletedNotes = d.deletedNotes + id)
    }

    // ---- bookmarks (device only) --------------------------------------------------------

    suspend fun toggleBookmark(book: String, chapter: Int, verse: Int, version: String) = edit { d ->
        val existing = d.bookmarks.firstOrNull { it.book == book && it.chapter == chapter && it.verse == verse }
        if (existing != null) d.copy(bookmarks = d.bookmarks - existing)
        else d.copy(bookmarks = d.bookmarks + Bookmark(UUID.randomUUID().toString(), book, chapter, verse, version, now()))
    }

    suspend fun deleteBookmark(id: String) = edit { d -> d.copy(bookmarks = d.bookmarks.filter { it.id != id }) }

    // ---- history -----------------------------------------------------------------------

    /** A chapter opened (R-10.7); the same chapter twice in a row counts once. */
    suspend fun recordVisit(book: String, chapter: Int, version: String, verse: Int?) {
        val d = state.value
        if (d.historyPaused) return
        val last = (d.pendingVisits + d.history).maxByOrNull { it.visitedAt }
        if (last != null && last.book == book && last.chapter == chapter && last.version == version) return
        edit { u ->
            val v = Visit(book = book, chapter = chapter, verseStart = verse, verseEnd = verse, version = version, visitedAt = now())
            u.copy(pendingVisits = (u.pendingVisits + v).takeLast(500))
        }
    }

    suspend fun setHistoryPaused(paused: Boolean) = edit { it.copy(historyPaused = paused) }

    suspend fun clearHistory() = edit { it.copy(history = emptyList(), pendingVisits = emptyList(), clearHistory = true) }

    /** All visits, newest first: those sent and those still waiting. */
    fun visits(d: UserData = state.value): List<Visit> = (d.pendingVisits + d.history).sortedByDescending { it.visitedAt }

    // ---- account -----------------------------------------------------------------------

    /**
     * Two-way sync with the account. Local changes go first (upserts, then deletes), then
     * the account's rows replace the local copies. The first sync after signing in sends
     * everything made while signed out, so nothing is lost (as the website does with plan
     * progress). Returns false when signed out.
     */
    suspend fun sync(): Boolean {
        val token = account.accessToken() ?: return false
        val userId = account.session.value?.userId ?: return false
        var d = state.value
        if (d.owner != null && d.owner != userId) {
            // Another account's data: it never mixes with this one.
            edit { UserData(owner = userId) }
            d = state.value
        }
        val firstSync = d.owner == null
        val pushHighlights = if (firstSync) d.highlights else d.highlights.filter { it.id in d.dirtyHighlights }
        val pushNotes = if (firstSync) d.notes else d.notes.filter { it.id in d.dirtyNotes }
        if (pushHighlights.isNotEmpty()) {
            api.request("POST", "/rest/v1/highlights?on_conflict=id", rows(pushHighlights, Highlight.serializer(), userId), token, "resolution=merge-duplicates,return=minimal")
        }
        if (pushNotes.isNotEmpty()) {
            api.request("POST", "/rest/v1/notes?on_conflict=id", rows(pushNotes, UserNote.serializer(), userId), token, "resolution=merge-duplicates,return=minimal")
        }
        d.deletedHighlights.chunked(50).forEach { ids -> api.request("DELETE", "/rest/v1/highlights?id=in.(${ids.joinToString(",")})", null, token) }
        d.deletedNotes.chunked(50).forEach { ids -> api.request("DELETE", "/rest/v1/notes?id=in.(${ids.joinToString(",")})", null, token) }
        if (d.clearHistory) api.request("DELETE", "/rest/v1/history?user_id=eq.$userId", null, token)
        for (v in d.pendingVisits.sortedBy { it.visitedAt }) {
            val body = buildJsonObject {
                put("p_book", v.book); put("p_chapter", v.chapter); put("p_version", v.version)
                put("p_verse_start", v.verseStart); put("p_verse_end", v.verseEnd)
            }
            runCatching { api.request("POST", "/rest/v1/rpc/record_visit", body.toString(), token) }
        }
        val highlights = json.decodeFromString<List<Highlight>>(api.request("GET", "/rest/v1/highlights?select=*&order=book,chapter,verse_start", null, token))
        val notes = json.decodeFromString<List<UserNote>>(api.request("GET", "/rest/v1/notes?select=*&order=updated_at.desc", null, token))
        val history = json.decodeFromString<List<Visit>>(api.request("GET", "/rest/v1/history?select=*&order=visited_at.desc&limit=200", null, token))
        val paused = runCatching {
            json.parseToJsonElement(api.request("GET", "/rest/v1/profiles?select=history_paused", null, token))
                .let { (it as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.jsonObject?.get("history_paused")?.toString() == "true" }
        }.getOrDefault(d.historyPaused)
        val sent = d
        edit { now ->
            // Changes made while this sync ran stay pending for the next one.
            val newHighlights = now.dirtyHighlights - sent.dirtyHighlights
            val newNotes = now.dirtyNotes - sent.dirtyNotes
            now.copy(
                highlights = highlights.filter { it.id !in now.deletedHighlights - sent.deletedHighlights } + now.highlights.filter { it.id in newHighlights && highlights.none { h -> h.id == it.id } },
                notes = notes + now.notes.filter { it.id in newNotes && notes.none { n -> n.id == it.id } },
                history = history,
                historyPaused = paused,
                dirtyHighlights = newHighlights,
                dirtyNotes = newNotes,
                deletedHighlights = now.deletedHighlights - sent.deletedHighlights,
                deletedNotes = now.deletedNotes - sent.deletedNotes,
                pendingVisits = now.pendingVisits - sent.pendingVisits.toSet(),
                clearHistory = now.clearHistory && !sent.clearHistory,
                owner = userId,
            )
        }
        return true
    }

    suspend fun setHistoryPausedOnAccount(paused: Boolean) {
        setHistoryPaused(paused)
        val token = account.accessToken() ?: return
        val userId = account.session.value?.userId ?: return
        runCatching { api.request("PATCH", "/rest/v1/profiles?user_id=eq.$userId", buildJsonObject { put("history_paused", paused) }.toString(), token) }
    }

    /** The account's data as the website exports it (export_my_data). */
    suspend fun exportFromAccount(): String? {
        val token = account.accessToken() ?: return null
        return api.request("POST", "/rest/v1/rpc/export_my_data", "{}", token)
    }

    /** Deletes the account and everything in it (delete_my_account), then signs out and wipes. */
    suspend fun deleteAccount() {
        val token = account.accessToken() ?: return
        api.request("POST", "/rest/v1/rpc/delete_my_account", "{}", token)
        account.signOut()
        edit { UserData() }
    }

    /** Signing out removes the account's data from this device (M6-9b). */
    suspend fun signOutAndWipe() {
        account.signOut()
        edit { UserData() }
    }

    /**
     * Rows for a PostgREST upsert. Every object carries the same keys (PostgREST refuses a
     * batch whose keys differ), nulls included; updated_at is the server's.
     */
    private fun <T> rows(items: List<T>, serializer: kotlinx.serialization.KSerializer<T>, userId: String): String =
        items.joinToString(",", "[", "]") { item ->
            val obj = rowJson.encodeToJsonElement(serializer, item).jsonObject
            val keys = listOf("id", "book", "chapter", "verse_start", "verse_end", "color", "body", "version", "char_start", "char_end", "quote")
                .filter { k -> k != "color" || serializer == Highlight.serializer() }
                .filter { k -> k != "body" || serializer == UserNote.serializer() }
            JsonObject(keys.associateWith { obj[it] ?: kotlinx.serialization.json.JsonNull } + ("user_id" to kotlinx.serialization.json.JsonPrimitive(userId))).toString()
        }
}
