package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.data.packs.PackRepository
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
    data class Result(val response: SearchResponse, val offline: Boolean)

    suspend fun search(query: String, version: String, offset: Int = 0): Result {
        val local = if (packs.hasBible(version)) packs.search(version, query, offset = offset) else null
        if (local != null && local.second > 0) {
            return Result(SearchResponse(query = query, hits = local.first, total = local.second), offline = true)
        }
        return try {
            val bytes = http.get(Origin.Api, "api/search", mapOf("q" to query, "v" to version, "offset" to offset.toString()))
            Result(json.decodeFromString(bytes.decodeToString()), offline = false)
        } catch (e: IOException) {
            if (local != null) Result(SearchResponse(query = query), offline = true) else throw e
        }
    }
}
