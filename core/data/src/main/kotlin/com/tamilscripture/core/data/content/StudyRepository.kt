package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.cache.OnlineCache
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.model.Article
import com.tamilscripture.core.model.ArticleIndexEntry
import com.tamilscripture.core.model.ChapterMentions
import com.tamilscripture.core.model.OriginalChapter
import com.tamilscripture.core.model.Person
import com.tamilscripture.core.model.Place
import com.tamilscripture.core.model.StrongsEntry
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * The website's study data (roadmap M8): any file under `content/{build}/entities/`, read
 * from the study pack that holds it when installed (no network), otherwise from the
 * website and kept in the online cache for the content build.
 */
class StudyRepository(
    private val http: Http,
    private val cache: OnlineCache,
    private val packs: PackRepository,
    private val content: ContentRepository,
    private val json: Json,
) {
    suspend fun original(book: String, chapter: Int): OriginalChapter? = typed("original/$book/$chapter.json")

    suspend fun strongs(number: String): StrongsEntry? = typed("strongs/${StrongsEntry.fileName(number)}.json")

    suspend fun mentions(book: String, chapter: Int): ChapterMentions? = typed("mentions/$book/$chapter.json")

    suspend fun person(id: String): Person? = typed("person/$id.json")

    suspend fun place(id: String): Place? = typed("place/$id.json")

    suspend fun article(id: String): Article? = typed("articles/$id.json")

    suspend fun articleIndex(): List<ArticleIndexEntry> = typed("articles/index.json") ?: emptyList()

    private suspend inline fun <reified T> typed(path: String): T? =
        file(path)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    /** One entities/ file as text: pack, then cache for this build, then the network. */
    suspend fun file(path: String): String? {
        val pack = packFor(path)
        if (pack != null && packs.store.isInstalled(pack)) packs.studyFile(pack, path)?.let { return it }
        val key = "study/$path"
        val cached = cache.get(key)
        val build = content.manifest.value?.build ?: content.refreshManifest()?.build
        if (cached != null && (build == null || cached.tag == build)) return cached.bytes.decodeToString()
        if (build == null) return cached?.bytes?.decodeToString()
        return try {
            val bytes = http.get(Origin.Content, "content/$build/entities/$path")
            cache.put(key, bytes, build)
            bytes.decodeToString()
        } catch (e: IOException) {
            cached?.bytes?.decodeToString()
        }
    }

    companion object {
        /** Which study pack holds a path (pack-build's STUDY_PACKS). */
        fun packFor(path: String): String? = when {
            path.startsWith("strongs/") || path.startsWith("original/") -> "study.words"
            path.startsWith("person/") || path.startsWith("place/") || path.startsWith("mentions/") ||
                path in setOf("people.json", "places.json", "glossary.json", "journeys.json", "church.json") -> "study.people"
            path.startsWith("articles/") -> "study.dictionary"
            path.startsWith("maps/") || path.startsWith("geo/") -> "study.maps"
            else -> null
        }
    }
}
