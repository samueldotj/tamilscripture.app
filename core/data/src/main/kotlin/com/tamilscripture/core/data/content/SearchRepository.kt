package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.packs.PackRepository
import com.tamilscripture.core.model.Romanised
import com.tamilscripture.core.model.SearchResponse
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Word search (A-4.2, A-4.9). With the version's pack installed, search runs on the
 * device against its FTS5 index (no network). Otherwise, or when the device search
 * finds nothing and the phone is online, the website's `/api/search` answers; it also
 * tolerates misspellings, which the packs leave out to stay small (design §8.3).
 */
class SearchRepository(private val http: Http, private val packs: PackRepository, private val json: Json) {
    /**
     * [shown] is the query the results are for (the Tamil reading when romanised input was
     * used); [romanOffer] is that reading, offered when the words as typed found results.
     */
    data class Result(val response: SearchResponse, val offline: Boolean, val shown: String, val romanOffer: String? = null)

    /**
     * Romanised Tamil (A-4.3), as on the website: for a Tamil version, "anbu" is read as
     * அன்பு when the words as typed find nothing; otherwise ("god") the results stand and
     * the Tamil reading is only offered.
     */
    suspend fun search(query: String, version: String, tamil: Boolean, offset: Int = 0): Result {
        val roman = if (tamil && Romanised.isRomanised(query)) Romanised.toTamil(query).takeIf { it.isNotBlank() } else null
        val typed = searchAs(query, version, offset)
        if (roman == null) return typed
        if (typed.response.total > 0) return typed.copy(romanOffer = roman)
        val tamilResult = runCatching { searchAs(roman, version, offset) }.getOrNull()
        return if (tamilResult != null && tamilResult.response.total > 0) tamilResult else typed
    }

    private suspend fun searchAs(query: String, version: String, offset: Int): Result {
        val local = if (packs.hasBible(version)) packs.search(version, query, offset = offset) else null
        if (local != null && local.second > 0) {
            return Result(SearchResponse(query = query, hits = local.first, total = local.second), offline = true, shown = query)
        }
        return try {
            val bytes = http.get(Origin.Api, "api/search", mapOf("q" to query, "v" to version, "offset" to offset.toString()))
            Result(json.decodeFromString(bytes.decodeToString()), offline = false, shown = query)
        } catch (e: IOException) {
            if (local != null) Result(SearchResponse(query = query), offline = true, shown = query) else throw e
        }
    }
}
