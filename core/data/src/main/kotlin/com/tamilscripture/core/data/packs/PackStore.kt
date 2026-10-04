package com.tamilscripture.core.data.packs

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Installed packs on disk (design §7.6) and read-only connections to them (ADR-5:
 * one connection per pack, no ATTACH). Uses the bundled SQLite driver so FTS5 is
 * available on every Android version (ADR-3).
 */
class PackStore(private val root: File, private val json: Json) {
    private val driver = BundledSQLiteDriver()
    private val index = File(root, "installed.json")
    private val lock = Mutex()
    private val open = HashMap<String, Pair<SQLiteConnection, Mutex>>()

    private val mutable = MutableStateFlow<Map<String, InstalledPack>>(emptyMap())
    val installed: StateFlow<Map<String, InstalledPack>> = mutable

    val stagingDir: File get() = File(root, "staging").also { it.mkdirs() }

    init {
        root.mkdirs()
        mutable.value = runCatching {
            json.decodeFromString<List<InstalledPack>>(index.readText()).associateBy { it.id }
        }.getOrDefault(emptyMap()).filterValues { File(it.file).exists() }
    }

    fun isInstalled(id: String) = id in mutable.value

    /** Runs [block] on the pack's connection, serialised per pack; null when it is not installed. */
    suspend fun <T> query(id: String, block: (SQLiteConnection) -> T): T? = withContext(Dispatchers.IO) {
        val (conn, m) = lock.withLock {
            open[id] ?: run {
                val p = mutable.value[id] ?: return@withContext null
                val c = driver.open(p.file, SQLITE_OPEN_READONLY)
                c.prepare("PRAGMA query_only = 1").use { it.step() }
                (c to Mutex()).also { open[id] = it }
            }
        }
        m.withLock { runCatching { block(conn) }.getOrNull() }
    }

    /** Atomic install (DL-5): the verified file is renamed into place, then recorded. */
    suspend fun install(id: String, version: Int, verified: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            val dir = File(root, "$id/$version").also { it.mkdirs() }
            val dest = File(dir, "pack.sqlite")
            if (dest.exists()) dest.delete()
            check(verified.renameTo(dest)) { "could not move pack into place" }
            val previous = mutable.value[id]
            open.remove(id)?.first?.close()
            val now = mutable.value + (id to InstalledPack(id, version, dest.path, dest.length(), System.currentTimeMillis()))
            writeIndex(now)
            mutable.value = now
            if (previous != null && previous.file != dest.path) File(previous.file).parentFile?.deleteRecursively()
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            open.remove(id)?.first?.close()
            val p = mutable.value[id] ?: return@withLock
            File(root, id).deleteRecursively()
            val now = mutable.value - id
            writeIndex(now)
            mutable.value = now
            p.size
        }
    }

    /** Checks a freshly decompressed file before it is installed. */
    fun check(file: File, expectedSchema: Int): Boolean = runCatching {
        driver.open(file.path, SQLITE_OPEN_READONLY).use { c ->
            val version = c.prepare("PRAGMA user_version").use { st -> st.step(); st.getLong(0).toInt() }
            val ok = c.prepare("PRAGMA quick_check").use { st -> st.step() && st.getText(0) == "ok" }
            ok && version <= SUPPORTED_PACK_SCHEMA && version == expectedSchema
        }
    }.getOrDefault(false)

    fun totalBytes(): Long = mutable.value.values.sumOf { it.size }

    private fun writeIndex(m: Map<String, InstalledPack>) {
        val tmp = File(root, "installed.json.tmp")
        tmp.writeText(json.encodeToString(m.values.sortedBy { it.id }))
        tmp.renameTo(index.also { it.delete() })
    }
}
