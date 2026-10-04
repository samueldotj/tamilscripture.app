package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.model.SearchResponse
import kotlinx.serialization.json.Json

/**
 * Word search. Until Bible packs with their FTS5 index exist (roadmap M3), every search
 * goes to the website's `/api/search`, which applies the same Tamil folding (A-4.9).
 */
class SearchRepository(private val http: Http, private val json: Json) {
    suspend fun search(query: String, version: String, offset: Int = 0): SearchResponse {
        val bytes = http.get(Origin.Api, "api/search", mapOf("q" to query, "v" to version, "offset" to offset.toString()))
        return json.decodeFromString(bytes.decodeToString())
    }
}
