package com.tamilscripture.core.data.testing

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.tamilscripture.core.model.ContentManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import uniffi.ts_mobile.normalize
import java.io.File
import java.text.Normalizer

/**
 * Packs for tests (M0-14), written with pack-build's schema (website repo,
 * crates/pack-build) from real chapter JSON kept under test resources/fixtures/
 * (fetched from the website's content build named in fixtures/manifest.json).
 */
object FixturePacks {
    private val json = Json { ignoreUnknownKeys = true }

    fun resource(path: String): String =
        requireNotNull(FixturePacks::class.java.classLoader!!.getResource("fixtures/$path")) { "no fixture $path" }.readText()

    val manifest: ContentManifest by lazy { json.decodeFromString(resource("manifest.json")) }

    private const val FTS = "CREATE VIRTUAL TABLE verse_fts USING fts5(norm, content='', contentless_delete=1, " +
        "tokenize=\"unicode61 remove_diacritics 0 categories 'L* N* Co Mn Mc'\")"

    private fun open(file: File): SQLiteConnection {
        file.delete()
        return BundledSQLiteDriver().open(file.path).also { it.execSQL("PRAGMA page_size = 4096; PRAGMA user_version = 1") }
    }

    /** A Bible pack of [version] holding the fixture chapters given as "JHN/3". */
    fun bible(dir: File, version: String, chapters: List<String>): File {
        val file = File(dir, "bible.$version.sqlite")
        open(file).use { c ->
            c.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            c.execSQL("CREATE TABLE book (code TEXT PRIMARY KEY, ord INTEGER NOT NULL, testament TEXT NOT NULL, name TEXT NOT NULL, chapters INTEGER NOT NULL)")
            c.execSQL("CREATE TABLE chapter (book TEXT NOT NULL, chapter INTEGER NOT NULL, body TEXT NOT NULL, PRIMARY KEY (book, chapter)) WITHOUT ROWID")
            c.execSQL("CREATE TABLE verse (id INTEGER PRIMARY KEY, book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, text TEXT NOT NULL)")
            c.execSQL(FTS)
            insert(c, "INSERT INTO meta VALUES (?, ?)", "pack_id", "bible.$version")
            insert(c, "INSERT INTO meta VALUES (?, ?)", "schema", "1")
            insert(c, "INSERT INTO meta VALUES (?, ?)", "version_code", version)
            val books = manifest.books.associateBy { it.code }
            for (code in chapters.map { it.substringBefore('/') }.distinct()) {
                val b = books.getValue(code)
                insert(c, "INSERT INTO book VALUES (?, ?, ?, ?, ?)", b.code, b.order, b.testament, b.nameEn, b.chapters)
            }
            for (path in chapters) {
                val (code, ch) = path.split('/')
                val body = resource("$version/$path.json")
                insert(c, "INSERT INTO chapter VALUES (?, ?, ?)", code, ch.toInt(), body)
                val verses = LinkedHashMap<Int, String>()
                json.parseToJsonElement(body).jsonObject["blocks"]?.jsonArray.orEmpty().forEach { block ->
                    (block.jsonObject["segments"] as? JsonArray).orEmpty().forEach { seg ->
                        val id = seg.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val n = id.split('.').getOrNull(2)?.substringBefore('-')?.toIntOrNull() ?: return@forEach
                        val text = seg.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        verses[n] = verses[n]?.let { "$it $text" } ?: text
                    }
                }
                val order = books.getValue(code).order
                for ((n, text) in verses) {
                    val key = order * 1_000_000L + ch.toInt() * 1_000L + n
                    insert(c, "INSERT OR REPLACE INTO verse VALUES (?, ?, ?, ?, ?)", key, code, ch.toInt(), n, text)
                    insert(c, "INSERT INTO verse_fts(rowid, norm) VALUES (?, ?)", key, normalize(Normalizer.normalize(text, Normalizer.Form.NFC)))
                }
            }
        }
        return file
    }

    /** The cross-reference pack for the fixture chapters given as "JHN/3". */
    fun xref(dir: File, chapters: List<String>): File {
        val file = File(dir, "xref.sqlite")
        open(file).use { c ->
            c.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            c.execSQL("CREATE TABLE xref (from_id TEXT NOT NULL, rank INTEGER NOT NULL, to_id TEXT NOT NULL, to_end TEXT, votes INTEGER NOT NULL, PRIMARY KEY (from_id, rank)) WITHOUT ROWID")
            insert(c, "INSERT INTO meta VALUES (?, ?)", "pack_id", "xref")
            for (path in chapters) {
                json.parseToJsonElement(resource("xref/$path.json")).jsonObject.forEach { (from, list) ->
                    list.jsonArray.forEachIndexed { rank, r ->
                        val o = r.jsonObject
                        insert(
                            c, "INSERT INTO xref VALUES (?, ?, ?, ?, ?)", from, rank,
                            o["to"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["end"]?.jsonPrimitive?.contentOrNull, o["votes"]?.jsonPrimitive?.int ?: 0,
                        )
                    }
                }
            }
        }
        return file
    }

    private fun insert(c: SQLiteConnection, sql: String, vararg args: Any?) {
        c.prepare(sql).use { st ->
            args.forEachIndexed { i, a ->
                when (a) {
                    null -> st.bindNull(i + 1)
                    is Int -> st.bindLong(i + 1, a.toLong())
                    is Long -> st.bindLong(i + 1, a)
                    else -> st.bindText(i + 1, a.toString())
                }
            }
            st.step()
        }
    }
}
