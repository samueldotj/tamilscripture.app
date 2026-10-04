package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.model.Romanised
import com.tamilscripture.core.model.SearchHit
import com.tamilscripture.core.model.SearchResponse
import com.tamilscripture.core.model.VerseId
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * Word search (A-4.2, A-4.9). With the version's pack installed, search runs on the
 * device against its FTS5 index (no network). Otherwise, or when the device search
 * finds nothing and the phone is online, the website's `/api/search` answers; it also
 * tolerates misspellings, which the packs leave out to stay small (design §8.3).
 */
class SearchRepository(private val http: Http, private val packs: PackRepository, private val json: Json) {
    @Volatile private var common: Pair<String, List<String>>? = null

    /**
     * What people search for most (the website's common searches, A-4.5): a short list
     * fetched once per app session and language; an empty list offline.
     */
    suspend fun commonSearches(lang: String): List<String> {
        common?.takeIf { it.first == lang }?.let { return it.second }
        return try {
            val bytes = http.get(Origin.Api, "api/common-searches", mapOf("lang" to lang))
            json.decodeFromString<List<String>>(bytes.decodeToString()).also { common = lang to it }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * [shown] is the query the results are for (the Tamil reading when romanised input was
     * used); [romanOffer] is that reading, offered when the words as typed found results.
     */
    data class Result(
        val response: SearchResponse,
        val offline: Boolean,
        val shown: String,
        val romanOffer: String? = null,
        /** Hits per book (canonical order → count) over the whole Bible; on-device searches only. */
        val perBook: List<Pair<Int, Int>> = emptyList(),
    )

    /**
     * Romanised Tamil (A-4.3), as on the website: for a Tamil version, "anbu" is read as
     * அன்பு when the words as typed find nothing; otherwise ("god") the results stand and
     * the Tamil reading is only offered.
     */
    suspend fun search(query: String, version: String, tamil: Boolean, offset: Int = 0, books: IntRange? = null): Result {
        val roman = if (tamil && Romanised.isRomanised(query)) Romanised.toTamil(query).takeIf { it.isNotBlank() } else null
        val typed = searchAs(query, version, offset, books)
        if (roman == null) return typed
        if (typed.response.total > 0) return typed.copy(romanOffer = roman)
        val tamilResult = runCatching { searchAs(roman, version, offset, books) }.getOrNull()
        return if (tamilResult != null && tamilResult.response.total > 0) tamilResult else typed
    }

    /**
     * Several versions at once (M3-5): each searched in parallel, on the device where its
     * pack is installed, and the hits merged in canonical order, versions in the order given.
     */
    suspend fun searchVersions(query: String, versions: List<String>, tamil: (String) -> Boolean, books: IntRange? = null): Result =
        coroutineScope {
            val results = versions.map { v -> async { runCatching { search(query, v, tamil(v), 0, books) }.getOrNull() } }.awaitAll()
            val found = results.filterNotNull()
            if (found.isEmpty()) throw IOException("no version could be searched")
            val order = versions.withIndex().associate { (i, v) -> v to i }
            val hits = found.flatMap { it.response.hits }.sortedWith(
                compareBy<SearchHit>({ it.bookOrder }, { VerseId.parse(it.verseId)?.chapter ?: 0 }, { VerseId.parse(it.verseId)?.verse ?: 0 }, { order[it.version] ?: 0 }),
            )
            val perBook = found.flatMap { it.perBook }.groupBy({ it.first }, { it.second }).map { (o, n) -> o to n.sum() }.sortedBy { it.first }
            Result(
                SearchResponse(query = query, hits = hits, total = found.sumOf { it.response.total }),
                offline = found.all { it.offline },
                shown = found.firstOrNull { it.response.total > 0 }?.shown ?: query,
                perBook = perBook,
            )
        }

    private suspend fun searchAs(query: String, version: String, offset: Int, books: IntRange?): Result {
        val local = if (packs.hasBible(version)) packs.search(version, query, offset = offset, books = books) else null
        // Enough here, a later page, or nothing in this scope but hits elsewhere (the scope row shows where).
        if (local != null && (local.total >= FEW || (offset > 0 && local.total > 0) || (local.total == 0 && local.perBook.isNotEmpty()))) {
            return Result(SearchResponse(query = query, hits = local.hits, total = local.total), offline = true, shown = query, perBook = local.perBook)
        }
        return try {
            val online: SearchResponse = json.decodeFromString(online(query, version, offset, books).decodeToString())
            if (local == null || local.total == 0) return Result(online, offline = false, shown = query)
            // M3-3: a few exact hits on the device; the website adds near spellings (trigram
            // similarity, which packs leave out to stay small). The device's hits come first.
            val seen = local.hits.map { it.verseId }.toSet()
            val extra = online.hits.filter { it.verseId !in seen }
            Result(
                SearchResponse(query = query, hits = local.hits + extra, total = local.total + extra.size),
                offline = false, shown = query, perBook = local.perBook,
            )
        } catch (e: IOException) {
            if (local != null && local.total > 0) {
                return Result(SearchResponse(query = query, hits = local.hits, total = local.total), offline = true, shown = query, perBook = local.perBook)
            }
            if (local != null) Result(SearchResponse(query = query), offline = true, shown = query) else throw e
        }
    }

    private suspend fun online(query: String, version: String, offset: Int, books: IntRange?): ByteArray =
        http.get(Origin.Api, "api/search", buildMap {
            put("q", query); put("v", version); put("offset", offset.toString())
            // The website filters by canonical book order too (R-5.8).
            books?.let { put("bmin", it.first.toString()); put("bmax", it.last.toString()) }
        })

    private companion object {
        /** Fewer device hits than this also ask the website (M3-3). */
        const val FEW = 5
    }
}
