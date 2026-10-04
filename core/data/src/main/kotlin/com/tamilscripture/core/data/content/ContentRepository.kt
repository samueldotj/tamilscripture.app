package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.model.AudioTimings
import com.tamilscripture.core.model.Chapter
import com.tamilscripture.core.model.ContentManifest
import com.tamilscripture.core.model.CrossRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException

/** Where a piece of content came from; carried on `view` / `read` stats events. */
enum class ContentSource { Pack, Cache, Online }

data class Loaded<T>(val value: T, val source: ContentSource, val stale: Boolean = false)

/**
 * The one way screens read scripture (design §7.9, ADR-10): installed pack → online
 * cache → network. Reading from a pack never touches the network (NF-6).
 */
class ContentRepository(
    private val http: Http,
    private val cache: OnlineCache,
    private val packs: PackRepository,
    val json: Json,
    private val scope: CoroutineScope,
    /** Verse timings saved with downloaded audio (M5-8), read before any cache or network. */
    private val localTimings: ((version: String, book: String, chapter: Int) -> java.io.File)? = null,
) {

    private val manifestState = MutableStateFlow<ContentManifest?>(null)
    val manifest: StateFlow<ContentManifest?> = manifestState

    private val manifestLock = Mutex()
    private val memory = object : LinkedHashMap<String, Chapter>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Chapter>?) = size > 12
    }

    /** Loads the cached manifest at once, then refreshes it from the network. */
    fun start() {
        // A pack installed, updated or removed: forget chapters parsed from the old file.
        scope.launch {
            packs.store.installed.collect { synchronized(memory) { memory.keys.removeAll { it.startsWith("pack/") } } }
        }
        scope.launch {
            cache.get(MANIFEST_KEY)?.let { e ->
                runCatching { json.decodeFromString<ContentManifest>(e.bytes.decodeToString()) }
                    .onSuccess { if (manifestState.value == null) manifestState.value = it }
            }
            refreshManifest()
        }
    }

    suspend fun refreshManifest(): ContentManifest? = manifestLock.withLock {
        try {
            val bytes = http.get(Origin.Content, "content/manifest.json")
            val m = json.decodeFromString<ContentManifest>(bytes.decodeToString())
            cache.put(MANIFEST_KEY, bytes, m.build)
            manifestState.value = m
            m
        } catch (e: IOException) {
            manifestState.value
        }
    }

    suspend fun awaitManifest(): ContentManifest = manifest.filterNotNull().first()

    /**
     * Emits the chapter as soon as any copy is available, then a fresh copy when the cached
     * one came from an older build (stale-while-revalidate, ON-5). Throws when the chapter is
     * neither cached nor reachable.
     */
    fun chapter(version: String, book: String, chapter: Int): Flow<Loaded<Chapter>> = flow {
        val key = "chapter/$version/$book/$chapter"
        val packKey = "pack/$key"
        synchronized(memory) { memory[packKey] }?.let { emit(Loaded(it, ContentSource.Pack)); return@flow }
        packs.chapterBody(version, book, chapter)?.let { body ->
            runCatching { json.decodeFromString<Chapter>(body) }.getOrNull()?.let { c ->
                synchronized(memory) { memory[packKey] = c }
                emit(Loaded(c, ContentSource.Pack))
                return@flow
            }
        }
        val build = manifest.value?.build
        synchronized(memory) { memory[key] }?.let { mem ->
            if (build == null || mem.build == build) {
                emit(Loaded(mem, ContentSource.Cache))
                return@flow
            }
        }
        val cached = cache.get(key)?.let { e ->
            runCatching { json.decodeFromString<Chapter>(e.bytes.decodeToString()) }.getOrNull()
        }
        if (cached != null) {
            synchronized(memory) { memory[key] = cached }
            val stale = build != null && cached.build != build
            emit(Loaded(cached, ContentSource.Cache, stale))
            if (!stale) return@flow
        }
        val m = manifest.value ?: awaitManifestOrNull()
        if (m == null) {
            if (cached == null) throw IOException("offline")
            return@flow
        }
        try {
            val fresh = fetchChapter(m.build, version, book, chapter, key)
            if (cached == null || fresh != cached) emit(Loaded(fresh, ContentSource.Online))
        } catch (e: IOException) {
            if (cached == null) throw e
        }
    }

    private suspend fun awaitManifestOrNull(): ContentManifest? = manifest.value ?: refreshManifest()

    private suspend fun fetchChapter(build: String, version: String, book: String, chapter: Int, key: String): Chapter {
        val bytes = http.get(Origin.Content, "content/$build/$version/$book/$chapter.json")
        val c = json.decodeFromString<Chapter>(bytes.decodeToString())
        cache.put(key, bytes, build)
        synchronized(memory) { memory[key] = c }
        return c
    }

    /** Fetches the neighbouring chapters after the current one paints (ON-4). */
    fun prefetch(version: String, book: String, chapter: Int) {
        if (packs.hasBible(version)) return
        scope.launch {
            val m = manifest.value ?: return@launch
            val key = "chapter/$version/$book/$chapter"
            if (synchronized(memory) { memory.containsKey(key) }) return@launch
            val cached = cache.get(key)
            if (cached != null && cached.tag == m.build) return@launch
            runCatching { fetchChapter(m.build, version, book, chapter, key) }
        }
    }

    suspend fun crossRefs(book: String, chapter: Int): Map<String, List<CrossRef>> =
        packs.crossRefs(book, chapter) ?: cachedJson("xref/$book/$chapter", { "content/$it/xref/$book/$chapter.json" }) ?: emptyMap()

    suspend fun timings(version: String, book: String, chapter: Int): AudioTimings? {
        val local = localTimings?.invoke(version, book, chapter)
        if (local != null && local.isFile) runCatching { json.decodeFromString<AudioTimings>(local.readText()) }.getOrNull()?.let { return it }
        return cachedJson("timing/$version/$book/$chapter", { "content/$it/$version/$book/$chapter.audio.json" })
    }

    private suspend inline fun <reified T> cachedJson(key: String, path: (String) -> String): T? {
        val build = manifest.value?.build
        val cached = cache.get(key)
        if (cached != null && (build == null || cached.tag == build)) {
            return runCatching { json.decodeFromString<T>(cached.bytes.decodeToString()) }.getOrNull()
        }
        val b = build ?: refreshManifest()?.build
        if (b != null) {
            try {
                val bytes = http.get(Origin.Content, path(b))
                cache.put(key, bytes, b)
                return json.decodeFromString<T>(bytes.decodeToString())
            } catch (_: IOException) {
            } catch (_: kotlinx.serialization.SerializationException) {
            }
        }
        return cached?.let { runCatching { json.decodeFromString<T>(it.bytes.decodeToString()) }.getOrNull() }
    }

    private companion object {
        const val MANIFEST_KEY = "manifest"
    }
}
