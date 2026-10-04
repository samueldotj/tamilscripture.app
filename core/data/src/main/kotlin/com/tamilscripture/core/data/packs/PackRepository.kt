package com.tamilscripture.core.data.packs

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tamilscripture.core.data.net.Http
import com.tamilscripture.core.data.net.Origin
import com.tamilscripture.core.model.CrossRef
import com.tamilscripture.core.model.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import uniffi.ts_mobile.normalize
import uniffi.ts_mobile.verifySignature
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloadable content packs (requirements §4, design §7): the signed catalogue,
 * what is installed, downloads through WorkManager, and the queries the reader and
 * search run against installed packs.
 */
class PackRepository(
    private val context: Context,
    private val http: Http,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    val store = PackStore(File(context.filesDir, "packs"), json)
    private val catalogueFile = File(context.filesDir, "packs/catalogue.json")

    private val catalogueState = MutableStateFlow<Catalogue?>(null)
    val catalogue: StateFlow<Catalogue?> = catalogueState

    private val failures = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        runCatching { json.decodeFromString<Catalogue>(catalogueFile.readText()) }.getOrNull()?.let { catalogueState.value = it }
    }

    /** Fetches the catalogue and accepts it only when its signature verifies (design §7.5). */
    suspend fun refreshCatalogue(): Catalogue? {
        return try {
            val body = http.get(Origin.Packs, "packs/catalogue.json")
            val sig = http.get(Origin.Packs, "packs/catalogue.json.sig").decodeToString()
            if (!verifySignature(PUBLIC_KEY, body, sig)) return catalogueState.value
            val c = json.decodeFromString<Catalogue>(body.decodeToString())
            catalogueFile.parentFile?.mkdirs()
            catalogueFile.writeBytes(body)
            catalogueState.value = c
            c
        } catch (e: IOException) {
            catalogueState.value
        } catch (e: kotlinx.serialization.SerializationException) {
            catalogueState.value
        }
    }

    /** Pack id → state for the Downloads screen, merged from the store and WorkManager. */
    val states: Flow<Map<String, PackState>> = combine(
        catalogue,
        store.installed,
        WorkManager.getInstance(context).getWorkInfosByTagFlow(PackDownloadWorker.TAG),
        failures,
    ) { cat, installed, work, failed ->
        val byPack = work.groupBy { it.tags.firstOrNull { t -> t.startsWith("pack:") }?.removePrefix("pack:") }
        val ids = (cat?.packs?.map { it.id }.orEmpty() + installed.keys).distinct()
        ids.associateWith { id ->
            val running = byPack[id]?.firstOrNull { !it.state.isFinished }
            val inst = installed[id]
            val latest = cat?.entry(id)
            when {
                running != null && running.state == WorkInfo.State.RUNNING -> {
                    val p = running.progress.getFloat(PackDownloadWorker.PROGRESS, -1f)
                    if (p >= 1f) PackState.Installing else PackState.Downloading(p.coerceAtLeast(0f))
                }
                running != null -> PackState.Queued(update = inst != null)
                inst != null -> PackState.Installed(inst.version, latest != null && latest.version > inst.version)
                failed[id] != null -> PackState.Failed(failed[id]!!)
                else -> PackState.NotInstalled
            }
        }
    }

    /** Queues a download (or an update) of [id]. */
    fun download(id: String, wifiOnly: Boolean) {
        val e = catalogue.value?.entry(id) ?: return
        if (e.schema > SUPPORTED_PACK_SCHEMA) return
        failures.value = failures.value - id
        val req = OneTimeWorkRequestBuilder<PackDownloadWorker>()
            .setInputData(workDataOf(PackDownloadWorker.ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .addTag(PackDownloadWorker.TAG)
            .addTag("pack:$id")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("pack:$id", ExistingWorkPolicy.KEEP, req)
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("pack:$id")
    }

    fun delete(id: String) {
        cancel(id)
        scope.launch { store.remove(id) }
    }

    internal fun reportFailure(id: String, reason: String) {
        failures.value = failures.value + (id to reason)
    }

    // ---- queries ---------------------------------------------------------------------

    /** The chapter JSON stored in an installed Bible pack (ADR-4), or null. */
    suspend fun chapterBody(version: String, book: String, chapter: Int): String? =
        store.query("bible.$version") { c ->
            c.prepare("SELECT body FROM chapter WHERE book = ? AND chapter = ?").use { st ->
                st.bindText(1, book)
                st.bindLong(2, chapter.toLong())
                if (st.step()) st.getText(0) else null
            }
        }

    fun hasBible(version: String) = store.isInstalled("bible.$version")

    /** Cross-references for a chapter from the `xref` pack, best first per verse. */
    suspend fun crossRefs(book: String, chapter: Int): Map<String, List<CrossRef>>? =
        store.query("xref") { c ->
            val prefix = "$book.$chapter."
            c.prepare("SELECT from_id, to_id, to_end, votes FROM xref WHERE from_id >= ? AND from_id < ? ORDER BY from_id, rank").use { st ->
                st.bindText(1, prefix)
                st.bindText(2, "$book.$chapter/")
                val out = LinkedHashMap<String, MutableList<CrossRef>>()
                while (st.step()) {
                    out.getOrPut(st.getText(0)) { mutableListOf() } +=
                        CrossRef(st.getText(1), if (st.isNull(2)) null else st.getText(2), st.getLong(3).toInt())
                }
                out
            }
        }

    /**
     * Offline word search in an installed Bible pack (design §8.3): words in quotes match as
     * a phrase; other words of three or more letters match as prefixes, like the website.
     */
    suspend fun search(version: String, query: String, limit: Int = 50, offset: Int = 0): Pair<List<SearchHit>, Int>? {
        val match = ftsExpression(query) ?: return null
        return store.query("bible.$version") { c ->
            val total = c.prepare("SELECT count(*) FROM verse_fts WHERE verse_fts MATCH ?").use { st ->
                st.bindText(1, match)
                if (st.step()) st.getLong(0).toInt() else 0
            }
            val hits = c.prepare(
                "SELECT v.book, v.chapter, v.verse, v.text, v.id / 1000000 FROM verse_fts f JOIN verse v ON v.id = f.rowid " +
                    "WHERE verse_fts MATCH ? ORDER BY f.rank LIMIT ? OFFSET ?",
            ).use { st ->
                st.bindText(1, match)
                st.bindLong(2, limit.toLong())
                st.bindLong(3, offset.toLong())
                buildList {
                    while (st.step()) {
                        add(SearchHit("${st.getText(0)}.${st.getLong(1)}.${st.getLong(2)}", version, st.getText(3), 0.0, st.getLong(4).toInt()))
                    }
                }
            }
            hits to total
        }
    }

    companion object {
        /** Ed25519 key that signs catalogue.json and bootstrap.json (pack-build --key). */
        const val PUBLIC_KEY = "97d1f1afd5de85089e5e608a4c11897793ddf42fe4d612589fbbba06487da428"

        fun ftsExpression(query: String): String? {
            val parts = ArrayList<String>()
            val phrase = Regex("[\"“”]([^\"“”]+)[\"“”]")
            phrase.findAll(query).forEach { m ->
                val n = normalize(m.groupValues[1]).trim()
                if (n.isNotEmpty()) parts += "\"" + n.replace("\"", "") + "\""
            }
            val rest = phrase.replace(query, " ")
            normalize(rest).split(' ').filter { it.isNotBlank() }.forEach { t ->
                val safe = t.replace("\"", "")
                parts += if (safe.codePointCount(0, safe.length) >= 3) "\"$safe\"*" else "\"$safe\""
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

        private val WORD = Regex("[\\p{L}\\p{M}\\p{N}]+")

        /**
         * The words of [text] that [query] matches, compared the way the index compares them
         * (M3-4): each word normalised, then matched against the normalised terms, by prefix
         * for terms of three or more letters as in [ftsExpression]. அன்பு marks அன்பாக too.
         */
        fun matchRanges(text: String, query: String): List<IntRange> {
            val terms = normalize(query.replace(Regex("[\"“”]"), " ")).split(' ').filter { it.isNotBlank() }
            if (terms.isEmpty()) return emptyList()
            return WORD.findAll(text).filter { m ->
                normalize(m.value).split(' ').any { n ->
                    n.isNotBlank() && terms.any { t -> if (t.codePointCount(0, t.length) >= 3) n.startsWith(t) else n == t }
                }
            }.map { it.range }.toList()
        }

        /** Daily catalogue check that updates installed packs on Wi-Fi (DL-6, design §7.10). */
        fun scheduleUpdates(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pack-updates", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PackUpdateWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                    .build(),
            )
        }
    }
}
