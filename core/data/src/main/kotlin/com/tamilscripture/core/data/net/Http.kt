package com.tamilscripture.core.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class HttpException(val code: Int, message: String) : IOException(message)

/**
 * A small GET client on HttpURLConnection: content is static JSON, so no extra
 * networking library is shipped (NF-1). Tries each base URL of an origin in order and
 * fails over once on connection errors and 5xx (design §7.11.2); a 404 is not retried.
 */
class Http(private val origins: OriginResolver) {

    /** Set by tests to prove the read path makes no calls (NF-6). */
    @Volatile var guard: ((String) -> Unit)? = null

    suspend fun get(origin: Origin, path: String, query: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            val bases = origins.bases(origin)
            var last: IOException? = null
            for ((i, base) in bases.withIndex()) {
                if (i > 1) break
                val url = buildUrl(base, path, query)
                try {
                    return@withContext fetch(url)
                } catch (e: HttpException) {
                    if (e.code in 400..499) throw e
                    origins.markDown(base)
                    last = e
                } catch (e: IOException) {
                    origins.markDown(base)
                    last = e
                }
            }
            throw last ?: IOException("No base URL for ${origin.key}")
        }

    suspend fun getAbsolute(url: String): ByteArray = withContext(Dispatchers.IO) { fetch(url) }

    /**
     * Downloads [path] into [dest], resuming from the bytes already there with an HTTP
     * Range request (DL-4). Reports bytes on disk through [onProgress].
     */
    suspend fun download(origin: Origin, path: String, dest: java.io.File, onProgress: suspend (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val base = origins.bases(origin).first()
            val url = buildUrl(base, path, emptyMap())
            guard?.invoke(url)
            var have = if (dest.exists()) dest.length() else 0L
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", "TamilScriptureAndroid")
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                when {
                    code == 416 -> return@withContext // already complete
                    code == 200 -> have = 0 // server ignored Range: start over
                    code == 206 -> Unit
                    else -> {
                        if (code >= 500) origins.markDown(base)
                        throw HttpException(code, "HTTP $code for $url")
                    }
                }
                java.io.FileOutputStream(dest, have > 0).use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var sinceReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            sinceReport += n
                            if (sinceReport >= 256 * 1024) {
                                onProgress(have)
                                sinceReport = 0
                            }
                        }
                    }
                }
                onProgress(have)
            } finally {
                conn.disconnect()
            }
        }

    /** POSTs a JSON body; used only by background workers (stats, sync), never the read path. */
    suspend fun postJson(origin: Origin, path: String, body: String, bearer: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val url = buildUrl(origins.bases(origin).first(), path, emptyMap())
            guard?.invoke(url)
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("User-Agent", "TamilScriptureAndroid")
                if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) throw HttpException(code, "HTTP $code for $url")
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }

    private fun buildUrl(base: String, path: String, query: Map<String, String>): String {
        val q = if (query.isEmpty()) "" else query.entries.joinToString("&", "?") { (k, v) ->
            k + "=" + java.net.URLEncoder.encode(v, "UTF-8")
        }
        return base.trimEnd('/') + "/" + path.trimStart('/') + q
    }

    private fun fetch(url: String): ByteArray {
        guard?.invoke(url)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("User-Agent", "TamilScriptureAndroid")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code, "HTTP $code for $url")
            val stream = if (conn.contentEncoding.equals("gzip", ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return stream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
