package com.tamilscripture.core.data.testing

import com.tamilscripture.core.data.account.Supabase
import com.tamilscripture.core.data.net.HttpException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * The website's personal tables for one account, in memory (M6-11): just the PostgREST
 * calls the app makes, as RLS would answer them. "The website" edits rows here directly.
 * [online] false makes every call fail as with no connection.
 */
class FakeSupabase(private val userId: String = "u1") : Supabase() {
    val highlights = LinkedHashMap<String, JsonObject>()
    val notes = LinkedHashMap<String, JsonObject>()
    val history = ArrayList<JsonObject>()
    /** One per verse: unique (user_id, book, chapter, verse), as the migration has it. */
    val bookmarks = LinkedHashMap<String, JsonObject>()
    var profile: JsonObject = JsonObject(mapOf("history_paused" to JsonPrimitive(false), "settings" to JsonObject(emptyMap())))
    var online = true
    private var clock = Instant.parse("2026-10-04T10:00:00Z")
    private var historyId = 0L

    private fun now(): String { clock = clock.plusSeconds(1); return clock.toString() }

    private fun table(name: String) = when (name) {
        "highlights" -> highlights
        "notes" -> notes
        else -> error("table $name")
    }

    /** A row written on the website. */
    fun websiteUpsert(table: String, row: JsonObject) {
        val id = row["id"]!!.jsonPrimitive.content
        table(table)[id] = JsonObject(row + ("user_id" to JsonPrimitive(userId)) + ("updated_at" to JsonPrimitive(now())))
    }

    override suspend fun request(method: String, path: String, body: String?, token: String?, prefer: String?): String = synchronized(this) {
        if (!online) throw java.io.IOException("offline")
        val route = path.substringBefore('?').removePrefix("/rest/v1/")
        val query = path.substringAfter('?', "")
        when {
            method == "GET" && route in setOf("highlights", "notes") -> JsonArray(table(route).values.toList()).toString()
            method == "GET" && route == "history" -> JsonArray(history.sortedByDescending { it["visited_at"]!!.jsonPrimitive.content }).toString()
            method == "GET" && route == "profiles" -> JsonArray(listOf(profile)).toString()
            method == "GET" && route == "plan_progress" -> "[]"
            method == "GET" && route == "bookmarks" -> JsonArray(bookmarks.values.toList()).toString()
            method == "POST" && route == "bookmarks" -> {
                Json.parseToJsonElement(body!!).jsonArray.forEach { r ->
                    val o = r.jsonObject
                    val key = listOf("book", "chapter", "verse").joinToString(".") { o[it]!!.jsonPrimitive.content }
                    // resolution=ignore-duplicates: a verse already bookmarked keeps its row.
                    if (key !in bookmarks) bookmarks[key] = JsonObject(o + ("created_at" to JsonPrimitive(now())))
                }
                ""
            }
            method == "DELETE" && route == "bookmarks" -> {
                val f = query.split('&').associate { it.substringBefore('=') to it.substringAfter("eq.") }
                bookmarks.remove("${f["book"]}.${f["chapter"]}.${f["verse"]}")
                ""
            }
            method == "POST" && route in setOf("highlights", "notes") -> {
                val rows = Json.parseToJsonElement(body!!).jsonArray
                val keys = rows.map { it.jsonObject.keys }.toSet()
                if (keys.size > 1) throw HttpException(400, "PGRST102 All object keys must match")
                rows.forEach { r ->
                    val o = r.jsonObject
                    val id = o["id"]!!.jsonPrimitive.content
                    table(route)[id] = JsonObject(o + ("updated_at" to JsonPrimitive(now())))
                }
                ""
            }
            method == "DELETE" && route in setOf("highlights", "notes") -> {
                val ids = Regex("""id=in\.\(([^)]*)\)""").find(query)!!.groupValues[1].split(',')
                ids.forEach { table(route).remove(it) }
                ""
            }
            method == "DELETE" && route == "history" -> { history.clear(); "" }
            method == "POST" && route == "rpc/record_visit" -> {
                val p = Json.parseToJsonElement(body!!).jsonObject
                if (profile["history_paused"]?.jsonPrimitive?.contentOrNull != "true") {
                    history += JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(++historyId),
                            "book" to p["p_book"]!!, "chapter" to p["p_chapter"]!!, "version" to p["p_version"]!!,
                            "verse_start" to (p["p_verse_start"] ?: kotlinx.serialization.json.JsonNull),
                            "verse_end" to (p["p_verse_end"] ?: kotlinx.serialization.json.JsonNull),
                            "visited_at" to JsonPrimitive(now()),
                        ),
                    )
                }
                ""
            }
            method == "PATCH" && route == "profiles" -> {
                profile = JsonObject(profile + Json.parseToJsonElement(body!!).jsonObject)
                ""
            }
            method == "POST" && route == "rpc/export_my_data" -> JsonObject(
                mapOf("highlights" to JsonArray(highlights.values.toList()), "notes" to JsonArray(notes.values.toList()), "history" to JsonArray(history)),
            ).toString()
            else -> error("unexpected $method $path")
        }
    }

    fun row(table: String, id: String): JsonObject? = table(table)[id]
    fun field(o: JsonObject, k: String): JsonElement? = o[k]
}
