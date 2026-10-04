package com.tamilscripture.core.data.content

import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Community highlight heat (M8-7): how many readers highlighted each verse, from the
 * website's hour-cached /api/heat/{book}.json. Online only and optional; kept in memory
 * for an hour, as the CDN keeps it.
 */
class HeatRepository(private val http: Http, private val json: Json) {
    private class Entry(val at: Long, val chapters: Map<Int, Map<Int, Int>>)
    private val cache = ConcurrentHashMap<String, Entry>()

    /** Chapter → verse → readers, or null when it cannot be had. */
    suspend fun book(slug: String): Map<Int, Map<Int, Int>>? {
        cache[slug]?.takeIf { System.currentTimeMillis() - it.at < 3_600_000 }?.let { return it.chapters }
        val bytes = runCatching { http.get(Origin.Api, "/api/heat/$slug.json") }.getOrNull() ?: return null
        val chapters = runCatching {
            json.parseToJsonElement(bytes.decodeToString()).jsonObject.mapNotNull { (c, verses) ->
                c.toIntOrNull()?.let { ch -> ch to verses.jsonObject.mapNotNull { (v, n) -> v.toIntOrNull()?.let { it to n.jsonPrimitive.int } }.toMap() }
            }.toMap()
        }.getOrNull() ?: return null
        cache[slug] = Entry(System.currentTimeMillis(), chapters)
        return chapters
    }

    companion object {
        /** Quartile 1..4 of [value] among the whole book's [values], as the website buckets it; 0 when absent. */
        fun bucket(value: Int?, values: List<Int>): Int {
            if (value == null || value <= 0 || values.isEmpty()) return 0
            val sorted = values.sorted()
            val rank = sorted.indexOfFirst { it >= value }.toDouble() / sorted.size
            return when {
                rank < 0.25 -> 1
                rank < 0.5 -> 2
                rank < 0.75 -> 3
                else -> 4
            }
        }
    }
}
