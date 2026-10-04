package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.model.CommentaryChapter
import com.tamilscripture.core.model.CommentaryIndex
import com.tamilscripture.core.model.CommentaryLatest
import com.tamilscripture.core.model.CommentarySource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Commentaries (A-5.2). A downloaded commentary pack answers first (DL-1, no network);
 * otherwise the commentary origin's immutable files, cached like chapters. The Early
 * Church Fathers are excluded for now (owner, 3 Oct 2026).
 */
class CommentaryRepository(
    private val http: Http,
    private val cache: OnlineCache,
    private val json: Json,
    private val packs: PackRepository,
) {
    private val lock = Mutex()
    @Volatile private var version: String? = null
    @Volatile private var index: CommentaryIndex? = null

    suspend fun index(): CommentaryIndex? = lock.withLock {
        index?.let { return it }
        val v = currentVersion() ?: return offlineIndex()
        val key = "commentary-index"
        val cached = cache.get(key)
        val idx = if (cached != null && cached.tag == v) {
            json.decodeFromString<CommentaryIndex>(cached.bytes.decodeToString())
        } else {
            try {
                val bytes = http.get(Origin.Commentary, "commentary/$v/index.json")
                cache.put(key, bytes, v)
                json.decodeFromString<CommentaryIndex>(bytes.decodeToString())
            } catch (e: IOException) {
                cached?.let { json.decodeFromString<CommentaryIndex>(it.bytes.decodeToString()) } ?: return offlineIndex()
            }
        }
        val filtered = idx.copy(sources = idx.sources.filter { it.id !in EXCLUDED })
        index = filtered
        filtered
    }

    suspend fun chapter(source: String, book: String, chapter: Int): CommentaryChapter? {
        if (packs.hasCommentary(source)) packs.commentaryChapter(source, book, chapter)?.let { return decode(it) }
        val v = currentVersion()
        val key = "commentary/$source/$book/$chapter"
        val cached = cache.get(key)
        if (cached != null && (v == null || cached.tag == v)) return decode(cached.bytes)
        if (v == null) return cached?.let { decode(it.bytes) }
        return try {
            val bytes = http.get(Origin.Commentary, "commentary/$v/$source/$book/$chapter.json")
            cache.put(key, bytes, v)
            decode(bytes)
        } catch (e: IOException) {
            cached?.let { decode(it.bytes) }
        }
    }

    /** Never online and nothing cached: the downloaded commentaries alone, not memoised. */
    private suspend fun offlineIndex(): CommentaryIndex? {
        val found = ALL.mapNotNull { id ->
            if (!packs.hasCommentary(id)) return@mapNotNull null
            val (meta, chapters) = packs.commentaryCoverage(id) ?: return@mapNotNull null
            runCatching { json.decodeFromString<CommentarySource>(meta) }.getOrNull()?.let { it to chapters }
        }
        if (found.isEmpty()) return null
        return CommentaryIndex(found.map { it.first }, found.associate { (s, ch) -> s.id to ch })
    }

    private fun decode(bytes: ByteArray) = runCatching { json.decodeFromString<CommentaryChapter>(bytes.decodeToString()) }.getOrNull()

    private suspend fun currentVersion(): String? {
        version?.let { return it }
        val key = "commentary-latest"
        val v = try {
            val bytes = http.get(Origin.Commentary, "commentary/latest.json")
            val latest = json.decodeFromString<CommentaryLatest>(bytes.decodeToString())
            cache.put(key, bytes, latest.version)
            latest.version
        } catch (e: IOException) {
            cache.get(key)?.tag
        }
        version = v
        return v
    }

    companion object {
        val EXCLUDED = setOf("ecf")

        /** The commentaries the catalogue can offer as packs, in the website's order. */
        private val ALL = listOf("henry", "calvin", "geneva", "poole", "trapp")
    }
}
