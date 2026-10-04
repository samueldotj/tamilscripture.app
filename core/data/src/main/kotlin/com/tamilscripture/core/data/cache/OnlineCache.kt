package com.tamilscripture.core.data.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The online-reading cache (ON-3, design §7.9). Entries are keyed by a logical content
 * path ("chapter/IRVTAM/JHN/3"), never by URL, so a host change keeps the cache
 * (design §7.11.4). Each entry carries a tag — the content build or commentary version it
 * came from — so stale copies can be shown at once and refreshed in the background.
 * Least recently used entries are removed past the budget; at least [minEntries] stay.
 */
class OnlineCache(
    private val dir: File,
    @Volatile var budgetBytes: Long = 50L * 1024 * 1024,
    private val minEntries: Int = 200,
) {
    data class Entry(val bytes: ByteArray, val tag: String)

    init {
        dir.mkdirs()
    }

    private fun name(key: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    suspend fun get(key: String): Entry? = withContext(Dispatchers.IO) {
        val f = File(dir, name(key))
        val t = File(dir, name(key) + ".tag")
        if (!f.exists()) return@withContext null
        f.setLastModified(System.currentTimeMillis())
        Entry(f.readBytes(), if (t.exists()) t.readText() else "")
    }

    suspend fun put(key: String, bytes: ByteArray, tag: String) = withContext(Dispatchers.IO) {
        val n = name(key)
        val tmp = File(dir, "$n.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, n).also { it.delete() })
        File(dir, "$n.tag").writeText(tag)
        trim()
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun trim() {
        val files = dir.listFiles { f -> !f.name.endsWith(".tag") && !f.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= budgetBytes || files.size <= minEntries) return
        val oldestFirst = files.sortedBy { it.lastModified() }
        var count = files.size
        for (f in oldestFirst) {
            if (total <= budgetBytes || count <= minEntries) break
            total -= f.length()
            f.delete()
            File(dir, f.name + ".tag").delete()
            count--
        }
    }
}
